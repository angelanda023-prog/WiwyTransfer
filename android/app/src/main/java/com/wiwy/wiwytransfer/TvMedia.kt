package com.wiwy.wiwytransfer

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.wiwy.wiwytransfer.storage.MediaEntry

/** Lista de medios (categoría o recibidos) con ver, enviar, selección múltiple y borrar. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(
    title: String,
    entries: List<MediaEntry>,
    onSend: (MediaEntry) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<MediaEntry?>(null) }
    var localEntries by remember(entries) { mutableStateOf(entries) }
    val selected = remember { mutableStateListOf<MediaEntry>() }
    var confirmDelete by remember { mutableStateOf(false) }

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            localEntries = localEntries.filterNot { selected.contains(it) }
        }
        selected.clear()
    }

    fun deleteSelected() {
        val sender = com.wiwy.wiwytransfer.storage.MediaRepo.deleteRequest(context, selected.map { it.uri })
        if (sender != null) {
            deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
        } else {
            localEntries = localEntries.filterNot { selected.contains(it) }
            selected.clear()
        }
    }

    viewing?.let { e ->
        androidx.activity.compose.BackHandler { viewing = null }
        if (e.isImage) ImageViewer(e.uri) { viewing = null }
        else VideoViewer(e.uri) { viewing = null }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected.isEmpty()) title else "${selected.size} seleccionado(s)",
                color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF8A80))
                }
            }
        }
        if (localEntries.isEmpty()) {
            Text("No hay archivos.", color = Color(0xCCFFFFFF))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(localEntries, key = { it.uri.toString() }) { e ->
                    MediaRow(
                        e = e,
                        checked = selected.contains(e),
                        onClick = {
                            if (selected.isNotEmpty()) {
                                if (selected.contains(e)) selected.remove(e) else selected.add(e)
                            } else if (e.isImage || e.isVideo) viewing = e else onSend(e)
                        },
                        onLong = { if (!selected.contains(e)) selected.add(e) },
                        onSend = { onSend(e) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val n = selected.size
        WiwyDialog(
            onDismiss = { confirmDelete = false },
            icon = Icons.Default.Delete,
            title = "¿Eliminar $n archivo(s)?",
            body = "Se eliminarán permanentemente de tu dispositivo.",
            secondaryLabel = "Rechazar",
            onSecondary = { confirmDelete = false },
            primaryLabel = "Permitir",
            accent = Color(0xFFE53935),
            onPrimary = { confirmDelete = false; deleteSelected() },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaRow(e: MediaEntry, checked: Boolean, onClick: () -> Unit, onLong: () -> Unit, onSend: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .tvSelectable(onClick = onClick, onLongClick = onLong, onFocusChanged = { focused = it }),
        shape = RoundedCornerShape(10.dp),
        color = if (checked) Color(0xFF1B5E20) else if (focused) Color(0xFF0D47A1) else Color(0xFF1565C0),
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.isImage || e.isVideo) {
                AsyncImage(model = e.uri, contentDescription = null,
                    modifier = Modifier.size(56.dp).background(Color.Black, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop)
            } else {
                Icon(if (e.isAudio) Icons.Default.MusicNote else Icons.Default.InsertDriveFile,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(e.size), color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
            if (checked) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            } else {
                IconButton(onClick = onSend) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ImageViewer(uri: Uri, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(model = uri, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Button(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("Cerrar")
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoViewer(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
        Button(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("Cerrar")
        }
    }
}
