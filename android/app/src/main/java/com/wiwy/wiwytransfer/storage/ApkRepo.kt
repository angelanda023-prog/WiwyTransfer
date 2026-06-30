package com.wiwy.wiwytransfer.storage

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Una APK encontrada en el almacenamiento. */
data class ApkItem(
    val name: String,
    val size: Long,
    val file: File?,
    val uri: Uri,
)

/** Busca, muestra icono e instala APK del almacenamiento. */
object ApkRepo {

    /** Lista todas las APK: escaneo de carpetas (si hay acceso) + MediaStore. */
    fun list(context: Context): List<ApkItem> {
        val byKey = LinkedHashMap<String, ApkItem>()
        if (StorageBrowser.hasAllFilesAccess(context)) {
            for (e in StorageBrowser.scan(setOf("apk"))) {
                byKey[e.file.absolutePath] = ApkItem(
                    name = e.name, size = e.size, file = e.file,
                    uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", e.file),
                )
            }
        }
        for (m in MediaRepo.filesByExtension(context, setOf("apk"))) {
            // dedup por nombre+tamaño si ya está por ruta
            if (byKey.values.none { it.name == m.name && it.size == m.size }) {
                byKey[m.uri.toString()] = ApkItem(m.name, m.size, null, m.uri)
            }
        }
        return byKey.values.sortedBy { it.name.lowercase() }
    }

    /** Icono de la APK (solo si tenemos su ruta de archivo). */
    fun icon(context: Context, item: ApkItem): Drawable? {
        val path = item.file?.absolutePath ?: return null
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        info.applicationInfo?.apply { sourceDir = path; publicSourceDir = path }
        return runCatching { info.applicationInfo?.loadIcon(pm) }.getOrNull()
    }

    fun install(context: Context, item: ApkItem) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
