package com.wiwy.wiwytransfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiwy.wiwytransfer.storage.StorageBrowser

private val BgTop = Color(0xFF0A2A4A)
private val BgMain = Color(0xFF1976D2)
private val TileBg = Color(0x33FFFFFF)
private val TileFocus = Color(0xFF0D47A1)

private object Exts {
    val VIDEO = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "m4v")
    val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
    val MUSIC = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus")
    val DOC = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "zip", "rar")
    val APK = setOf("apk")
}

private sealed interface TvNav {
    data object Home : TvNav
    data class Browse(val title: String, val exts: Set<String>?) : TvNav
    data object Receive : TvNav
    data object Devices : TvNav
}

@Composable
fun TvAppScreen(vm: AppViewModel) {
    var nav by remember { mutableStateOf<TvNav>(TvNav.Home) }
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val qsIncoming by vm.qsIncoming.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(BgMain)) {
        when (val n = nav) {
            TvNav.Home -> TvHome(
                vm = vm,
                onSend = { nav = TvNav.Browse("Enviar", null) },
                onReceive = { nav = TvNav.Receive },
                onCategory = { title, exts -> nav = TvNav.Browse(title, exts) },
            )
            is TvNav.Browse -> FileBrowserScreen(
                title = n.title, exts = n.exts,
                onPick = { files ->
                    vm.setSelectedFiles(files.map { StorageBrowser.toOutgoing(it) })
                    nav = TvNav.Devices
                },
                onCancel = { nav = TvNav.Home },
            )
            TvNav.Receive -> TvReceive(vm) { nav = TvNav.Home }
            TvNav.Devices -> TvDevices(vm) { nav = TvNav.Home }
        }
    }

    incoming?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.respondIncoming(false) },
            title = { Text("Solicitud de transferencia") },
            text = { Text("${req.header.sender} quiere enviarte ${req.header.files.size} archivo(s) (${formatBytes(req.header.totalBytes)}).") },
            confirmButton = { TextButton(onClick = { vm.respondIncoming(true) }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { vm.respondIncoming(false) }) { Text("Rechazar") } },
        )
    }
    qsIncoming?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.respondQs(false) },
            title = { Text("Quick Share") },
            text = { Text("${req.sender} quiere enviarte ${req.files.size} archivo(s) (${formatBytes(req.totalBytes)})." + (req.pin?.let { "\nPIN: $it" } ?: "")) },
            confirmButton = { TextButton(onClick = { vm.respondQs(true) }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { vm.respondQs(false) }) { Text("Rechazar") } },
        )
    }
}

@Composable
private fun TvHome(
    vm: AppViewModel,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onCategory: (String, Set<String>?) -> Unit,
) {
    val qsStatus by vm.qsStatus.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Barra superior
        Row(
            Modifier.fillMaxWidth().background(BgTop).padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("WiwyTransfer — TV", color = Color.White,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(qsStatus, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))

        // Send / Receive grandes
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Tile("Enviar", Icons.Default.Upload, big = true, onClick = onSend)
            Spacer(Modifier.width(40.dp))
            Tile("Recibir", Icons.Default.Download, big = true, onClick = onReceive)
        }

        Spacer(Modifier.height(36.dp))

        // Categorías
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Tile("Mis archivos", Icons.Default.Folder) { onCategory("Mis archivos", null) }
            Tile("Vídeos", Icons.Default.Movie) { onCategory("Vídeos", Exts.VIDEO) }
            Tile("Imágenes", Icons.Default.Image) { onCategory("Imágenes", Exts.IMAGE) }
            Tile("Música", Icons.Default.MusicNote) { onCategory("Música", Exts.MUSIC) }
            Tile("APKs", Icons.Default.Android) { onCategory("APKs", Exts.APK) }
            Tile("Documentos", Icons.Default.Description) { onCategory("Documentos", Exts.DOC) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tile(label: String, icon: ImageVector, big: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val side = if (big) 130.dp else 100.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(side).onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(14.dp),
            color = if (focused) TileFocus else TileBg,
            border = if (focused) BorderStroke(3.dp, Color.White) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White,
                    modifier = Modifier.size(if (big) 56.dp else 38.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvDevices(vm: AppViewModel, onBack: () -> Unit) {
    val selected by vm.selectedFiles.collectAsStateWithLifecycle()
    val qsPeers by vm.qsPeers.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val qsSend by vm.qsSend.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enviar a…", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Volver", color = Color.White) }
        }
        Text("${selected.size} archivo(s) · ${formatBytes(selected.sumOf { it.size })}",
            color = Color(0xCCFFFFFF))
        Spacer(Modifier.height(8.dp))

        when (val s = qsSend) {
            is QsSendState.Sending -> {
                Text("Enviando… ${(s.fraction * 100).toInt()}%", color = Color.White)
                LinearProgressIndicator(progress = { s.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            QsSendState.Done -> Text("✅ Enviado", color = Color.White)
            is QsSendState.Failed -> Text("❌ ${s.message}", color = Color(0xFFFFCDD2))
            QsSendState.Idle -> {}
        }
        Spacer(Modifier.height(8.dp))
        Text("Quick Share (el receptor debe estar en “Recibir”)", color = Color(0xCCFFFFFF),
            style = MaterialTheme.typography.bodySmall)

        if (qsPeers.isEmpty() && peers.isEmpty()) {
            Text("Buscando dispositivos…", color = Color(0xCCFFFFFF))
        }
        qsPeers.forEach { p ->
            DeviceRow(p.name, "Quick Share") { vm.sendQs(p) }
        }
        peers.forEach { p ->
            DeviceRow(p.displayName, "WiwyTransfer") { vm.sendTo(p) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRow(name: String, kind: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        color = if (focused) TileFocus else TileBg,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Devices, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White)
                Text(kind, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White)
        }
    }
}

@Composable
private fun TvReceive(vm: AppViewModel, onBack: () -> Unit) {
    val name by vm.deviceName.collectAsStateWithLifecycle()
    val qsStatus by vm.qsStatus.collectAsStateWithLifecycle()
    val qsReceive by vm.qsReceive.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recibir", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Volver", color = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Visible como: $name", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(qsStatus, color = Color(0xCCFFFFFF))
        Spacer(Modifier.height(8.dp))
        when (val s = qsReceive) {
            is QsReceiveState.Receiving -> {
                val frac = if (s.total > 0) s.received.toFloat() / s.total else 0f
                Text("Recibiendo ${s.name}…", color = Color.White)
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            }
            is QsReceiveState.Done -> Text("✅ Recibido (${s.paths.size}) de ${s.sender}", color = Color.White)
            else -> Text("Esperando… Comparte por Quick Share desde tu móvil hacia esta TV.",
                color = Color(0xCCFFFFFF))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) { ReceivedScreen(vm.receivedDir) }
    }
}
