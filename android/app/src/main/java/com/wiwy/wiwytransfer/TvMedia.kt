package com.wiwy.wiwytransfer

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

/** Lista de medios (categoría o recibidos) con ver y enviar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    title: String,
    entries: List<MediaEntry>,
    onSend: (MediaEntry) -> Unit,
    onBack: () -> Unit,
) {
    var viewing by remember { mutableStateOf<MediaEntry?>(null) }

    viewing?.let { e ->
        androidx.activity.compose.BackHandler { viewing = null }
        if (e.isImage) ImageViewer(e.uri) { viewing = null }
        else VideoViewer(e.uri) { viewing = null }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Volver", color = Color.White) }
        }
        if (entries.isEmpty()) {
            Text("No hay archivos.", color = Color(0xCCFFFFFF))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.uri.toString() }) { e ->
                    MediaRow(e, onOpen = {
                        if (e.isImage || e.isVideo) viewing = e else onSend(e)
                    }, onSend = { onSend(e) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaRow(e: MediaEntry, onOpen: () -> Unit, onSend: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        color = if (focused) Color(0xFF0D47A1) else Color(0x33FFFFFF),
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
                Text(formatBytes(e.size) + (if (e.isImage || e.isVideo) " · pulsa para ver" else ""),
                    color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White)
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
