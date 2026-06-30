package com.wiwy.wiwytransfer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Auto-actualización OTA desde GitHub Releases. */
object Updater {
    private const val LATEST_API =
        "https://api.github.com/repos/angelanda023-prog/WiwyTransfer/releases/latest"

    data class Update(val version: String, val apkUrl: String, val notes: String)

    /** Devuelve una actualización si hay una versión más reciente que [current]. */
    suspend fun check(current: String): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            val tag = obj.getString("tag_name").removePrefix("v")
            if (!isNewer(tag, current)) return@withContext null
            val assets = obj.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", true)) {
                    url = a.getString("browser_download_url"); break
                }
            }
            url?.let { Update(tag, it, obj.optString("body", "")) }
        }.getOrNull()
    }

    /** Descarga la APK y devuelve el archivo local. [onProgress] 0..1. */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "WiwyTransfer-${update.version}.apk")
            val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 20000; instanceFollowRedirects = true
            }
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            out
        }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
