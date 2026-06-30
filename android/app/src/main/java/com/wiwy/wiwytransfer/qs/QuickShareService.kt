package com.wiwy.wiwytransfer.qs

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** Un dispositivo Quick Share descubierto en la red. */
data class QsPeer(val id: String, val name: String, val host: InetAddress, val port: Int)

/**
 * Anuncia este dispositivo (TV/móvil) como receptor Quick Share por mDNS y acepta
 * conexiones entrantes, delegando el handshake en [InboundNearbyConnection].
 */
class QuickShareService(
    context: Context,
    private val scope: CoroutineScope,
    private val saver: com.wiwy.wiwytransfer.net.IncomingSaver,
    private val delegate: InboundDelegate,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serverSocket: ServerSocket? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val found = LinkedHashMap<String, QsPeer>()
    val endpointId: ByteArray = QsEndpoint.generateEndpointId()
    private val ownServiceName: String = QsEndpoint.serviceName(endpointId)
    private var displayName: String = "WiwyTransfer"
    var boundPort: Int = 0; private set

    /** Pares Quick Share descubiertos (para enviar). */
    var onPeers: (List<QsPeer>) -> Unit = {}

    /** Estado del anuncio (para mostrar en pantalla). */
    var onStatus: (String) -> Unit = {}

    fun start(displayName: String) {
        stop()
        this.displayName = displayName
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
                val conn = InboundNearbyConnection(socket, saver, delegate)
                scope.launch(Dispatchers.IO) { conn.loop() }
            }
        }
        register(displayName, boundPort)
        startDiscovery()
    }

    // ---- Descubrimiento de pares (para enviar) ----

    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName != ownServiceName) resolve(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)?.let { onPeers(found.values.toList()) }
            }
        }
        discoveryListener = listener
        runCatching { nsd.discoverServices(QsEndpoint.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(s: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(r: NsdServiceInfo) {
                if (r.serviceName == ownServiceName) return
                val host = r.host ?: return
                val epid = QsEndpoint.b64urlDecode(r.serviceName).let {
                    if (it.size >= 5) String(it, 1, 4, Charsets.US_ASCII) else r.serviceName
                }
                val name = r.attributes["n"]?.let { QsEndpoint.parseName(QsEndpoint.b64urlDecode(String(it, Charsets.UTF_8))) }
                    ?: "Dispositivo Quick Share"
                found[r.serviceName] = QsPeer(epid, name, host, r.port)
                onPeers(found.values.toList())
            }
        })
    }

    // ---- Envío a un par ----

    fun sendFiles(peer: QsPeer, files: List<QsOutgoingFile>, delegate: OutboundDelegate) {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(peer.host, peer.port), 10_000)
                val conn = OutboundNearbyConnection(socket, displayName, endpointId, files, delegate)
                conn.loop()
            } catch (e: Exception) {
                delegate.onFailed(e.message ?: "no se pudo conectar")
            }
        }
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
                onStatus("✅ Visible en Quick Share")
            }
            override fun onRegistrationFailed(s: NsdServiceInfo, errorCode: Int) {
                Log.e("WiwyQS", "Fallo al anunciar Quick Share: $errorCode")
                onStatus("❌ Error al anunciar (código $errorCode)")
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
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        found.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
