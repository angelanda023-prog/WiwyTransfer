package com.wiwy.wiwytransfer.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Constantes y serialización del protocolo WiwyTransfer (ver PROTOCOL.md). */
object Protocol {
    const val SERVICE_TYPE = "_wiwytransfer._tcp."
    const val VERSION = 1
    const val BUFFER = 64 * 1024
}

/** Metadatos de un archivo dentro de un lote. */
data class FileMeta(val name: String, val size: Long)

/** Cabecera enviada por el emisor al inicio de la conexión. */
data class TransferHeader(
    val version: Int,
    val sender: String,
    val os: String,
    val files: List<FileMeta>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }

    fun toJsonLine(): String {
        val arr = JSONArray()
        files.forEach { f ->
            arr.put(JSONObject().put("name", f.name).put("size", f.size))
        }
        val obj = JSONObject()
            .put("v", version)
            .put("sender", sender)
            .put("os", os)
            .put("files", arr)
        return obj.toString() + "\n"
    }

    companion object {
        fun parse(line: String): TransferHeader {
            val obj = JSONObject(line)
            val arr = obj.getJSONArray("files")
            val files = ArrayList<FileMeta>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                files.add(FileMeta(sanitizeName(o.getString("name")), o.getLong("size")))
            }
            return TransferHeader(
                version = obj.optInt("v", 1),
                sender = obj.optString("sender", "Desconocido"),
                os = obj.optString("os", "?"),
                files = files,
            )
        }

        /** Evita rutas: solo el nombre de archivo. */
        fun sanitizeName(raw: String): String {
            val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
            return if (base.isEmpty() || base == "." || base == "..") "archivo" else base
        }
    }
}

fun decisionLine(accept: Boolean, reason: String? = null): String {
    val obj = JSONObject().put("accept", accept)
    if (reason != null) obj.put("reason", reason)
    return obj.toString() + "\n"
}

fun resultLine(ok: Boolean, received: Int, error: String? = null): String {
    val obj = JSONObject().put("ok", ok).put("received", received)
    if (error != null) obj.put("error", error)
    return obj.toString() + "\n"
}

/** Lee una línea JSON terminada en '\n' como UTF-8. Devuelve null si se cierra el stream. */
fun InputStream.readJsonLine(): String? {
    val buf = ByteArrayOutputStream()
    while (true) {
        val b = read()
        if (b == -1) return if (buf.size() == 0) null else String(buf.toByteArray(), Charsets.UTF_8)
        if (b == '\n'.code) break
        buf.write(b)
    }
    return String(buf.toByteArray(), Charsets.UTF_8)
}
