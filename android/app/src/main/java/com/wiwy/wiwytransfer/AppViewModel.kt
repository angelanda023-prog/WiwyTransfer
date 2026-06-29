package com.wiwy.wiwytransfer

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wiwy.wiwytransfer.net.Discovery
import com.wiwy.wiwytransfer.net.OutgoingFile
import com.wiwy.wiwytransfer.net.Peer
import com.wiwy.wiwytransfer.net.SendResult
import com.wiwy.wiwytransfer.net.TransferClient
import com.wiwy.wiwytransfer.net.TransferHeader
import com.wiwy.wiwytransfer.net.TransferProgress
import com.wiwy.wiwytransfer.net.TransferServer
import com.wiwy.wiwytransfer.storage.DownloadsSaver
import com.wiwy.wiwytransfer.storage.UriFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class IncomingRequest(
    val header: TransferHeader,
    val peerAddress: String,
    private val deferred: CompletableDeferred<Boolean>,
) {
    fun respond(accept: Boolean) = deferred.complete(accept)
}

sealed interface SendState {
    data object Idle : SendState
    data class Sending(val sent: Long, val total: Long) : SendState
    data class Done(val received: Int) : SendState
    data class Declined(val reason: String?) : SendState
    data class Error(val message: String) : SendState
}

sealed interface ReceiveState {
    data object Listening : ReceiveState
    data class Receiving(val progress: TransferProgress) : ReceiveState
    data class Done(val paths: List<String>, val sender: String) : ReceiveState
    data class Error(val message: String) : ReceiveState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("wiwy", android.content.Context.MODE_PRIVATE)

    private val _deviceName = MutableStateFlow(
        prefs.getString("device_name", null) ?: defaultName()
    )
    val deviceName: StateFlow<String> = _deviceName

    private val discovery = Discovery(app)
    val peers: StateFlow<List<Peer>> get() = discovery.peers

    private val _selectedFiles = MutableStateFlow<List<OutgoingFile>>(emptyList())
    val selectedFiles: StateFlow<List<OutgoingFile>> = _selectedFiles

    private val _incoming = MutableStateFlow<IncomingRequest?>(null)
    val incoming: StateFlow<IncomingRequest?> = _incoming

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Listening)
    val receiveState: StateFlow<ReceiveState> = _receiveState

    private val server = TransferServer(
        scope = viewModelScope,
        askAccept = { header, peerAddr ->
            val deferred = CompletableDeferred<Boolean>()
            _incoming.value = IncomingRequest(header, peerAddr, deferred)
            val accepted = deferred.await()
            _incoming.value = null
            if (accepted) _receiveState.value = ReceiveState.Receiving(
                TransferProgress(header.sender, 0, header.files.size, "", 0, header.totalBytes)
            )
            accepted
        },
        saver = DownloadsSaver(app),
        onProgress = { p -> _receiveState.value = ReceiveState.Receiving(p) },
        onComplete = { paths, header ->
            _receiveState.value = ReceiveState.Done(paths, header.sender)
        },
        onError = { msg -> _receiveState.value = ReceiveState.Error(msg) },
    )

    init {
        val port = server.start()
        discovery.registerReceiver(_deviceName.value, osName(), port)
        discovery.startDiscovery()
    }

    private fun osName() = "android"
    private fun defaultName() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    fun setDeviceName(name: String) {
        val clean = name.trim().ifEmpty { defaultName() }
        _deviceName.value = clean
        prefs.edit().putString("device_name", clean).apply()
        // Re-anunciar con el nuevo nombre.
        discovery.registerReceiver(clean, osName(), server.boundPort)
    }

    fun setSelectedUris(uris: List<Uri>) {
        _selectedFiles.value = UriFiles.fromUris(getApplication(), uris)
    }

    fun clearSelection() {
        _selectedFiles.value = emptyList()
        _sendState.value = SendState.Idle
    }

    fun refreshDiscovery() {
        discovery.startDiscovery()
    }

    fun respondIncoming(accept: Boolean) {
        _incoming.value?.respond(accept)
    }

    fun resetReceive() {
        _receiveState.value = ReceiveState.Listening
    }

    fun sendTo(peer: Peer) {
        val files = _selectedFiles.value
        if (files.isEmpty()) return
        _sendState.value = SendState.Sending(0, files.sumOf { it.size })
        viewModelScope.launch {
            val client = TransferClient(_deviceName.value, osName())
            val result = client.send(peer, files) { sent, total ->
                _sendState.value = SendState.Sending(sent, total)
            }
            _sendState.value = when (result) {
                is SendResult.Success -> SendState.Done(result.received)
                is SendResult.Declined -> SendState.Declined(result.reason)
                is SendResult.Failure -> SendState.Error(result.error)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discovery.unregister()
        discovery.stopDiscovery()
        server.stop()
    }
}

/** Formatea bytes a una cadena legible. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.size - 1) { value /= 1024; i++ }
    return String.format("%.1f %s", value, units[i])
}
