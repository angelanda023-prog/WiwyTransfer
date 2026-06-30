package com.wiwy.wiwytransfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.wiwy.wiwytransfer.storage.ApkItem
import com.wiwy.wiwytransfer.storage.ApkRepo
import com.wiwy.wiwytransfer.storage.StorageBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageBrowser.hasAllFilesAccess(context)) }
    var loading by remember { mutableStateOf(true) }
    var apks by remember { mutableStateOf<List<ApkItem>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        hasAccess = StorageBrowser.hasAllFilesAccess(context)
        loading = true
        apks = withContext(Dispatchers.IO) { ApkRepo.list(context) }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("APK", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Default.Download, contentDescription = "Recargar", tint = Color.White)
            }
            TextButton(onClick = onBack) { Text("Volver", color = Color.White) }
        }

        if (!hasAccess) {
            Text("Para ver las APK de todas las carpetas, concede acceso a los archivos.",
                color = Color(0xCCFFFFFF))
            Button(onClick = {
                runCatching { context.startActivity(StorageBrowser.manageAllFilesIntent(context)) }
            }) { Text("Conceder acceso") }
            Spacer(Modifier.height(12.dp))
        }

        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                Spacer(Modifier.width(12.dp)); Text("Buscando APK…", color = Color.White)
            }
            apks.isEmpty() -> Text("No se encontraron APK.", color = Color(0xCCFFFFFF))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apks, key = { it.uri.toString() }) { apk ->
                    ApkRow(apk) { ApkRepo.install(context, apk) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkRow(apk: ApkItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, apk) {
        value = withContext(Dispatchers.IO) {
            ApkRepo.icon(context, apk)?.toBitmap(96, 96)?.asImageBitmap()
        }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        color = if (focused) Color(0xFF0D47A1) else Color(0x33FFFFFF),
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF8BC34A),
                    modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(apk.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(apk.size) + " · pulsa para instalar",
                    color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
