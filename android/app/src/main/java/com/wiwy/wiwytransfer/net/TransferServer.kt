package com.wiwy.wiwytransfer.net

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/** Destino donde el receptor guarda un archivo entrante. */
interface IncomingSaver {
    /** Crea un destino para [name]. Llama a onDone(true/false) al terminar; devuelve ruta visible. */
    fun create(name: String): SaveTarget
}

class SaveTarget(val output: OutputStream, val onDone: (ok: Boolean) -> String)

data class TransferProgress(
    val sender: String,
    val fileIndex: Int,
    val fileCount: Int,
    val fileName: String,
    val overallReceived: Long,
    val overallTotal: Long,
)

/**
 * Servidor TCP que recibe lotes de archivos. Escucha en un puerto efímero
 * (anunciar luego por mDNS con [boundPort]).
 */
class TransferServer(
    private val scope: CoroutineScope,
    private val askAccept: suspend (header: TransferHeader, peerAddress: String) -> Boolean,
    private val saver: IncomingSaver,
    private val onProgress: (TransferProgress) -> Unit,
    private val onComplete: (savedPaths: List<String>, header: TransferHeader) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val tag = "WiwyServer"
    private var serverSocket: ServerSocket? = null
    var boundPort: Int = 0; private set

    fun start(): Int {
        val ss = ServerSocket(0)
        serverSocket = ss
        boundPort = ss.localPort
        scope.launch(Dispatchers.IO) {
            while (scope.isActive && !ss.isClosed) {
                val client = try {
                    ss.accept()
                } catch (e: Exception) {
                    if (ss.isClosed) break else continue
                }
                scope.launch(Dispatchers.IO) { handle(client) }
            }
        }
        return boundPort
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handle(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val peerAddr = s.inetAddress?.hostAddress ?: "?"
            try {
                val headerLine = input.readJsonLine() ?: return
                val header = TransferHeader.parse(headerLine)

                val accepted = askAccept(header, peerAddr)
                output.write(decisionLine(accepted).toByteArray(Charsets.UTF_8))
                output.flush()
                if (!accepted) return

                val saved = ArrayList<String>()
                var overall = 0L
                val total = header.totalBytes
                val buf = ByteArray(Protocol.BUFFER)

                header.files.forEachIndexed { idx, meta ->
                    val target = saver.create(meta.name)
                    var ok = false
                    try {
                        var remaining = meta.size
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buf.size.toLong()).toInt()
                            val n = input.read(buf, 0, toRead)
                            if (n == -1) throw java.io.EOFException("conexión cerrada")
                            target.output.write(buf, 0, n)
                            remaining -= n
                            overall += n
                            onProgress(
                                TransferProgress(
                                    sender = header.sender,
                                    fileIndex = idx + 1,
                                    fileCount = header.files.size,
                                    fileName = meta.name,
                                    overallReceived = overall,
                                    overallTotal = total,
                                )
                            )
                        }
                        target.output.flush()
                        ok = true
                    } finally {
                        saved.add(target.onDone(ok))
                    }
                }

                output.write(resultLine(true, saved.size).toByteArray(Charsets.UTF_8))
                output.flush()
                withContext(Dispatchers.Main) { onComplete(saved, header) }
            } catch (e: Exception) {
                Log.e(tag, "Error recibiendo", e)
                runCatching {
                    output.write(resultLine(false, 0, e.message).toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                withContext(Dispatchers.Main) { onError(e.message ?: "Error recibiendo") }
            }
        }
    }
}
