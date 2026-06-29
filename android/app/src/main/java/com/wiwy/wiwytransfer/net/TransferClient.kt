package com.wiwy.wiwytransfer.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Un archivo a enviar: metadatos + proveedor de su flujo de bytes. */
class OutgoingFile(
    val name: String,
    val size: Long,
    val openStream: () -> InputStream,
)

sealed class SendResult {
    data class Success(val received: Int) : SendResult()
    data class Declined(val reason: String?) : SendResult()
    data class Failure(val error: String) : SendResult()
}

/** Cliente TCP que envía un lote de archivos a un par. */
class TransferClient(
    private val senderName: String,
    private val os: String,
) {
    suspend fun send(
        peer: Peer,
        files: List<OutgoingFile>,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): SendResult = withContext(Dispatchers.IO) {
        val total = files.sumOf { it.size }
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.host, peer.port), 10_000)
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                val header = TransferHeader(
                    version = Protocol.VERSION,
                    sender = senderName,
                    os = os,
                    files = files.map { FileMeta(it.name, it.size) },
                )
                out.write(header.toJsonLine().toByteArray(Charsets.UTF_8))
                out.flush()

                val decisionLine = input.readJsonLine()
                    ?: return@withContext SendResult.Failure("sin respuesta del receptor")
                val decision = org.json.JSONObject(decisionLine)
                if (!decision.optBoolean("accept", false)) {
                    return@withContext SendResult.Declined(decision.optString("reason", null))
                }

                var sent = 0L
                val buf = ByteArray(Protocol.BUFFER)
                for (f in files) {
                    f.openStream().use { stream ->
                        while (true) {
                            val n = stream.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            sent += n
                            onProgress(sent, total)
                        }
                    }
                }
                out.flush()

                val resultLine = input.readJsonLine()
                    ?: return@withContext SendResult.Failure("sin confirmación del receptor")
                val result = org.json.JSONObject(resultLine)
                if (result.optBoolean("ok", false)) {
                    SendResult.Success(result.optInt("received", files.size))
                } else {
                    SendResult.Failure(result.optString("error", "error en el receptor"))
                }
            }
        } catch (e: Exception) {
            SendResult.Failure(e.message ?: "error de conexión")
        }
    }
}
