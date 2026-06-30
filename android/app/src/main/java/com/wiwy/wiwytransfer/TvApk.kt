package com.wiwy.wiwytransfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
    val selected = remember { mutableStateListOf<ApkItem>() }

    LaunchedEffect(reload) {
        hasAccess = StorageBrowser.hasAllFilesAccess(context)
        loading = true
        apks = withContext(Dispatchers.IO) { ApkRepo.list(context) }
        selected.clear()
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected.isEmpty()) "APK" else "${selected.size} seleccionada(s)",
                color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                IconButton(onClick = {
                    selected.toList().forEach { ApkRepo.delete(context, it) }
                    reload++
                }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF8A80)) }
            }
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = Color.White)
            }
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
                    ApkRow(
                        apk = apk,
                        checked = selected.contains(apk),
                        selecting = selected.isNotEmpty(),
                        onClick = {
                            if (selected.isNotEmpty()) {
                                if (selected.contains(apk)) selected.remove(apk) else selected.add(apk)
                            } else ApkRepo.install(context, apk)
                        },
                        onLong = { if (!selected.contains(apk)) selected.add(apk) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApkRow(apk: ApkItem, checked: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, apk) {
        value = withContext(Dispatchers.IO) {
            ApkRepo.icon(context, apk)?.toBitmap(96, 96)?.asImageBitmap()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .tvSelectable(onClick = onClick, onLongClick = onLong, onFocusChanged = { focused = it }),
        shape = RoundedCornerShape(10.dp),
        color = if (checked) Color(0xFF1B5E20) else if (focused) Color(0xFF0D47A1) else Color(0xFF1565C0),
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
                Text(formatBytes(apk.size) + (if (selecting) "" else " · clic para instalar · mantén para seleccionar"),
                    color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (checked) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
        }
    }
}
