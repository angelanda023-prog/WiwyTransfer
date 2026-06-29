package com.wiwy.wiwytransfer.qs

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket

/**
 * Anuncia este dispositivo (TV/móvil) como receptor Quick Share por mDNS y acepta
 * conexiones entrantes, delegando el handshake en [InboundNearbyConnection].
 */
class QuickShareService(
    context: Context,
    private val scope: CoroutineScope,
    private val saveDir: File,
    private val delegate: InboundDelegate,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serverSocket: ServerSocket? = null
    private var regListener: NsdManager.RegistrationListener? = null
    val endpointId: ByteArray = QsEndpoint.generateEndpointId()
    var boundPort: Int = 0; private set

    fun start(displayName: String) {
        stop()
        val ss = ServerSocket(0)
        serverSocket = ss
        boundPort = ss.localPort
        Log.i("WiwyQS", "Listener Quick Share en puerto $boundPort")

        scope.launch(Dispatchers.IO) {
            while (!ss.isClosed) {
                val socket = try {
                    ss.accept()
                } catch (e: Exception) {
                    break
                }
                Log.i("WiwyQS", "Conexión entrante de ${socket.inetAddress?.hostAddress}")
                val conn = InboundNearbyConnection(socket, saveDir, delegate)
                scope.launch(Dispatchers.IO) { conn.loop() }
            }
        }
        register(displayName, boundPort)
    }

    private fun register(displayName: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceType = QsEndpoint.SERVICE_TYPE
            serviceName = QsEndpoint.serviceName(endpointId)
            setPort(port)
            setAttribute("n", QsEndpoint.endpointInfo(displayName))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {
                Log.i("WiwyQS", "Anunciado en Quick Share: ${s.serviceName}")
            }
            override fun onRegistrationFailed(s: NsdServiceInfo, errorCode: Int) {
                Log.e("WiwyQS", "Fallo al anunciar Quick Share: $errorCode")
            }
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, errorCode: Int) {}
        }
        regListener = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun restart(displayName: String) {
        start(displayName)
    }

    fun stop() {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
