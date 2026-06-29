package com.wiwy.wiwytransfer.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

/** Un par descubierto en la red local. */
data class Peer(
    val serviceName: String,
    val displayName: String,
    val os: String,
    val host: InetAddress,
    val port: Int,
) {
    val id: String get() = "$serviceName@${host.hostAddress}:$port"
}

/**
 * Anuncia este dispositivo como receptor y descubre otros pares vía mDNS (NsdManager).
 */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val tag = "WiwyDiscovery"

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val found = LinkedHashMap<String, Peer>()
    @Volatile private var ownServiceName: String? = null

    // ---- Registro (ser visible como receptor) ----

    fun registerReceiver(displayName: String, os: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "WiwyTransfer-$displayName"
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
            setAttribute("v", Protocol.VERSION.toString())
            setAttribute("name", displayName)
            setAttribute("os", os)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {
                ownServiceName = s.serviceName
                Log.i(tag, "Registrado como ${s.serviceName}")
            }
            override fun onRegistrationFailed(s: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Registro falló: $errorCode")
            }
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    // ---- Descubrimiento (encontrar receptores) ----

    fun startDiscovery() {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Descubrimiento falló al iniciar: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == ownServiceName) return
                resolve(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                _peers.value = found.values.toList()
            }
        }
        discovery = listener
        nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        found.clear()
        _peers.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                override fun onServiceUpdated(updated: NsdServiceInfo) {
                    addResolved(updated)
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            }
            runCatching { nsd.registerServiceInfoCallback(info, { it.run() }, cb) }
        } else {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(resolved: NsdServiceInfo) = addResolved(resolved)
            })
        }
    }

    private fun addResolved(info: NsdServiceInfo) {
        if (info.serviceName == ownServiceName) return
        val host = hostOf(info) ?: return
        val name = attr(info, "name") ?: info.serviceName.removePrefix("WiwyTransfer-")
        val os = attr(info, "os") ?: "?"
        val peer = Peer(info.serviceName, name, os, host, info.port)
        found[info.serviceName] = peer
        _peers.value = found.values.toList()
    }

    @Suppress("DEPRECATION")
    private fun hostOf(info: NsdServiceInfo): InetAddress? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return info.hostAddresses.firstOrNull()
        }
        return info.host
    }

    private fun attr(info: NsdServiceInfo, key: String): String? =
        info.attributes[key]?.let { String(it, Charsets.UTF_8) }
}
