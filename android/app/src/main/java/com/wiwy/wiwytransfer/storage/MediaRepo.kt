package com.wiwy.wiwytransfer.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.wiwy.wiwytransfer.qs.QsOutgoingFile

/** Un elemento de medios (imagen/vídeo/audio/archivo) listado por MediaStore. */
data class MediaEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val dateMillis: Long = 0L,
) {
    val isImage get() = mime.startsWith("image/")
    val isVideo get() = mime.startsWith("video/")
    val isAudio get() = mime.startsWith("audio/")
}

/** Consultas a MediaStore (sin permiso de "todos los archivos"). */
object MediaRepo {

    fun images(context: Context) =
        query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null)

    fun videos(context: Context) =
        query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)

    fun audio(context: Context) =
        query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null)

    /** Archivos recibidos: Descargas/WiwyTransfer. */
    fun received(context: Context): List<MediaEntry> {
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val sel = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        return query(context, uri, sel, arrayOf("%WiwyTransfer%"))
    }

    /** Documentos/APK en Descargas (lo que recibimos u otros). */
    fun downloads(context: Context): List<MediaEntry> =
        query(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, null, null)

    /** Busca por extensión en MediaStore.Files (sin permiso de "todos los archivos"). */
    fun filesByExtension(context: Context, exts: Set<String>): List<MediaEntry> {
        @Suppress("DEPRECATION")
        val collection = MediaStore.Files.getContentUri("external")
        val sel = exts.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" }
        val args = exts.map { "%.$it" }.toTypedArray()
        return query(context, collection, sel, args)
    }

    private fun query(context: Context, collection: Uri, selection: String?, args: Array<String>?): List<MediaEntry> {
        val out = ArrayList<MediaEntry>()
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        runCatching {
            context.contentResolver.query(collection, proj, selection, args, sort)?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateC = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idC)
                    out.add(
                        MediaEntry(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(nameC) ?: "archivo",
                            size = if (c.isNull(sizeC)) 0 else c.getLong(sizeC),
                            mime = c.getString(mimeC) ?: "application/octet-stream",
                            dateMillis = if (dateC >= 0 && !c.isNull(dateC)) c.getLong(dateC) * 1000 else 0L,
                        )
                    )
                }
            }
        }
        return out
    }

    fun toOutgoing(context: Context, entry: MediaEntry): QsOutgoingFile =
        QsOutgoingFile(entry.name, entry.size, entry.mime) {
            context.contentResolver.openInputStream(entry.uri) ?: error("No se pudo abrir ${entry.name}")
        }

    /**
     * Borra los [uris]. En API 30+ devuelve un IntentSender para que el sistema
     * pida confirmación (hay que lanzarlo); en versiones anteriores borra directo.
     */
    fun deleteRequest(context: Context, uris: List<android.net.Uri>): android.content.IntentSender? {
        if (uris.isEmpty()) return null
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else {
            uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
            null
        }
    }
}
