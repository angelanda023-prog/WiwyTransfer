package com.wiwy.wiwytransfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiwy.wiwytransfer.storage.MediaRepo
import com.wiwy.wiwytransfer.storage.StorageBrowser

private val BgTop = Color(0xFF0A2A4A)
private val BgMain = Color(0xFF1976D2)
private val TileBg = Color(0x33FFFFFF)
private val TileFocus = Color(0xFF0D47A1)

private object Exts {
    val DOC = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "zip", "rar")
    val APK = setOf("apk")
}

enum class MediaKind { IMAGES, VIDEOS, AUDIO, RECEIVED }

private sealed interface TvNav {
    data object Home : TvNav
    data class Browse(val title: String, val exts: Set<String>?, val flat: Boolean = false) : TvNav
    data class Media(val title: String, val kind: MediaKind) : TvNav
    data object Receive : TvNav
    data object Devices : TvNav
}

@Composable
fun TvAppScreen(vm: AppViewModel) {
    var nav by remember { mutableStateOf<TvNav>(TvNav.Home) }
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val qsIncoming by vm.qsIncoming.collectAsStateWithLifecycle()
    val qsReceive by vm.qsReceive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Al empezar a recibir, abrir la pantalla de Recibir automáticamente.
    LaunchedEffect(qsReceive) {
        if (qsReceive is QsReceiveState.Receiving && nav != TvNav.Receive) nav = TvNav.Receive
    }

    // Atrás: subpantalla -> inicio. En inicio, doble atrás para salir.
    var lastBack by remember { mutableStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = nav != TvNav.Home) { nav = TvNav.Home }
    androidx.activity.compose.BackHandler(enabled = nav == TvNav.Home) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) activity?.finish()
        else {
            lastBack = now
            android.widget.Toast.makeText(context, "Pulsa atrás de nuevo para salir", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(BgMain)) {
        when (val n = nav) {
            TvNav.Home -> TvHome(
                vm = vm,
                onSend = { nav = TvNav.Browse("Enviar", null) },
                onReceive = { nav = TvNav.Receive },
                onBrowse = { title, exts, flat -> nav = TvNav.Browse(title, exts, flat) },
                onMedia = { title, kind -> nav = TvNav.Media(title, kind) },
            )
            is TvNav.Browse -> FileBrowserScreen(
                title = n.title, exts = n.exts, flat = n.flat,
                onPick = { files ->
                    vm.setSelectedFiles(files.map { StorageBrowser.toOutgoing(it) })
                    nav = TvNav.Devices
                },
                onCancel = { nav = TvNav.Home },
            )
            is TvNav.Media -> {
                val entries = remember(n) {
                    when (n.kind) {
                        MediaKind.IMAGES -> MediaRepo.images(context)
                        MediaKind.VIDEOS -> MediaRepo.videos(context)
                        MediaKind.AUDIO -> MediaRepo.audio(context)
                        MediaKind.RECEIVED -> MediaRepo.received(context)
                    }
                }
                MediaListScreen(
                    title = n.title, entries = entries,
                    onSend = { e ->
                        vm.setSelectedFiles(listOf(MediaRepo.toOutgoing(context, e)))
                        nav = TvNav.Devices
                    },
                    onBack = { nav = TvNav.Home },
                )
            }
            TvNav.Receive -> TvReceive(vm, onViewReceived = { nav = TvNav.Media("Recibidos", MediaKind.RECEIVED) }) { nav = TvNav.Home }
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
    onBrowse: (String, Set<String>?, Boolean) -> Unit,
    onMedia: (String, MediaKind) -> Unit,
) {
    val qsStatus by vm.qsStatus.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            BigTile("Enviar", Icons.Default.Upload, onClick = onSend)
            Spacer(Modifier.width(40.dp))
            BigTile("Recibir", Icons.Default.Download, onClick = onReceive)
        }

        Spacer(Modifier.height(36.dp))

        // Fila de categorías responsive: ocupa todo el ancho
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatTile(Modifier.weight(1f), "Mis archivos", Icons.Default.Folder) { onBrowse("Mis archivos", null, false) }
            CatTile(Modifier.weight(1f), "Vídeos", Icons.Default.Movie) { onMedia("Vídeos", MediaKind.VIDEOS) }
            CatTile(Modifier.weight(1f), "Imágenes", Icons.Default.Image) { onMedia("Imágenes", MediaKind.IMAGES) }
            CatTile(Modifier.weight(1f), "Música", Icons.Default.MusicNote) { onMedia("Música", MediaKind.AUDIO) }
            CatTile(Modifier.weight(1f), "APKs", Icons.Default.Android) { onBrowse("APKs", Exts.APK, true) }
            CatTile(Modifier.weight(1f), "Documentos", Icons.Default.Description) { onBrowse("Documentos", Exts.DOC, true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BigTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(130.dp).onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(14.dp),
            color = if (focused) TileFocus else TileBg,
            border = if (focused) BorderStroke(3.dp, Color.White) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatTile(modifier: Modifier, label: String, icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(90.dp).onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(12.dp),
            color = if (focused) TileFocus else TileBg,
            border = if (focused) BorderStroke(3.dp, Color.White) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
        qsPeers.forEach { p -> DeviceRow(p.name, "Quick Share") { vm.sendQs(p) } }
        peers.forEach { p -> DeviceRow(p.displayName, "WiwyTransfer") { vm.sendTo(p) } }
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
private fun TvReceive(vm: AppViewModel, onViewReceived: () -> Unit, onBack: () -> Unit) {
    val name by vm.deviceName.collectAsStateWithLifecycle()
    val qsStatus by vm.qsStatus.collectAsStateWithLifecycle()
    val qsReceive by vm.qsReceive.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recibir", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            Button(onClick = onViewReceived) { Text("Ver recibidos") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onBack) { Text("Volver", color = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Visible como: $name", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(qsStatus, color = Color(0xCCFFFFFF))
        Spacer(Modifier.height(8.dp))
        when (val s = qsReceive) {
            is QsReceiveState.Receiving -> {
                val frac = if (s.total > 0) s.received.toFloat() / s.total else 0f
                if (s.name.isNotEmpty()) Text(s.name, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text("${formatBytes(s.received)} / ${formatBytes(s.total)}", color = Color(0xCCFFFFFF))
            }
            is QsReceiveState.Done -> {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Color(0xFF4CAF50),
                )
                Spacer(Modifier.height(8.dp))
                Text("✅ Recibido (${s.paths.size}) de ${s.sender} — pulsa “Ver recibidos”", color = Color(0xFFA5D6A7))
            }
            is QsReceiveState.Error -> {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Color(0xFFE53935),
                )
                Spacer(Modifier.height(8.dp))
                Text("❌ ${s.message}", color = Color(0xFFFFCDD2))
            }
            else -> Text("Esperando… Comparte por Quick Share desde tu móvil hacia esta TV.", color = Color(0xCCFFFFFF))
        }
        Spacer(Modifier.height(12.dp))
        val logLines by com.wiwy.wiwytransfer.qs.QsDebug.lines.collectAsStateWithLifecycle()
        if (logLines.isNotEmpty()) {
            Text("Diagnóstico:", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    .background(Color(0x33000000), RoundedCornerShape(8.dp)).padding(8.dp)
            ) {
                logLines.takeLast(12).forEach {
                    Text(it, color = Color(0xFFB3E5FC), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}
