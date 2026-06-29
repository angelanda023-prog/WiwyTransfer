package com.wiwy.wiwytransfer.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.wiwy.wiwytransfer.qs.QsOutgoingFile
import java.io.File

/** Una entrada en el explorador de archivos. */
data class FileEntry(
    val file: File,
    val isDir: Boolean,
    val name: String,
    val size: Long,
)

/** Explorador de almacenamiento basado en java.io.File (navegable con D-pad en TV). */
object StorageBrowser {

    /** Raíz de navegación: almacenamiento compartido del dispositivo. */
    fun storageRoot(): File = Environment.getExternalStorageDirectory()

    /** Carpeta donde se guardan los archivos recibidos. */
    fun receivedDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WiwyTransfer")

    /** Lista el contenido de [dir]: carpetas primero, luego archivos, por nombre. */
    fun list(dir: File): List<FileEntry> {
        val items = dir.listFiles()?.asList() ?: emptyList()
        return items
            .filter { !it.isHidden && it.canRead() }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { FileEntry(it, it.isDirectory, it.name, if (it.isFile) it.length() else 0L) }
    }

    /** Convierte un archivo en un envío. */
    fun toOutgoing(file: File): QsOutgoingFile =
        QsOutgoingFile(name = file.name, size = file.length(), mimeType = mimeOf(file)) { file.inputStream() }

    // ---- Permisos de acceso a archivos ----

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Intent para conceder "Acceso a todos los archivos" (API 30+). */
    fun manageAllFilesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))

    // ---- Abrir un archivo recibido con otra app (visor/reproductor) ----

    fun openFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = mimeOf(file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
