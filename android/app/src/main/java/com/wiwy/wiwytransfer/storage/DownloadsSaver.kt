package com.wiwy.wiwytransfer.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.wiwy.wiwytransfer.net.IncomingSaver
import com.wiwy.wiwytransfer.net.SaveTarget
import java.io.File
import java.io.OutputStream

/**
 * Guarda archivos entrantes en Descargas/WiwyTransfer.
 * - API 29+: MediaStore.Downloads (sin permisos).
 * - API 26-28: carpeta pública de Descargas (requiere WRITE_EXTERNAL_STORAGE).
 */
class DownloadsSaver(private val context: Context) : IncomingSaver {

    private val subDir = "WiwyTransfer"

    override fun create(name: String): SaveTarget {
        val unique = uniqueName(name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createViaMediaStore(unique)
        } else {
            createViaLegacy(unique)
        }
    }

    private fun createViaMediaStore(name: String): SaveTarget {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subDir")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo en Descargas")
        val output: OutputStream = resolver.openOutputStream(uri)
            ?: error("No se pudo abrir el archivo para escritura")
        return SaveTarget(output) { ok ->
            runCatching { output.close() }
            if (ok) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            "Descargas/$subDir/$name"
        }
    }

    private fun createViaLegacy(name: String): SaveTarget {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            subDir,
        ).apply { mkdirs() }
        val file = File(dir, name)
        val output = file.outputStream()
        return SaveTarget(output) { ok ->
            runCatching { output.close() }
            if (!ok) file.delete()
            file.absolutePath
        }
    }

    private fun uniqueName(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = name
        var i = 1
        // Solo dedup en legacy (en MediaStore el sistema también añade sufijos).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subDir,
            )
            while (File(dir, candidate).exists()) {
                candidate = "$base ($i)$ext"; i++
            }
        }
        return candidate
    }
}
