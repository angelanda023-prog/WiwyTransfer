package com.wiwy.wiwytransfer.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wiwy.wiwytransfer.net.OutgoingFile

/** Convierte content:// URIs (del selector o de compartir) en [OutgoingFile]. */
object UriFiles {
    fun fromUris(context: Context, uris: List<Uri>): List<OutgoingFile> {
        val resolver = context.contentResolver
        return uris.mapNotNull { uri ->
            var name = "archivo"
            var size = -1L
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { c ->
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                        if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                    }
                }
            }
            if (size < 0) {
                size = runCatching {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                }.getOrNull() ?: -1L
            }
            if (size < 0) return@mapNotNull null
            OutgoingFile(name = name, size = size) {
                resolver.openInputStream(uri) ?: error("No se pudo abrir $name")
            }
        }
    }
}
