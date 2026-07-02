package com.wiwy.wiwytransfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiwy.wiwytransfer.storage.MediaRepo
import com.wiwy.wiwytransfer.storage.StorageBrowser
import kotlinx.coroutines.launch

private val BgMain = Color(0xFF0A1020)   // azul muy oscuro (fondo)
private val BgCard = Color(0xFF131C2E)   // tarjeta categoría
private val BgCardFocus = Color(0xFF1E2A45)
private val TileBg = Color(0xFF1565C0)
private val TileFocus = Color(0xFF42A5F5)

private object Exts {
    val DOC = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "zip", "rar")
    val APK = setOf("apk")
}

enum class MediaKind { IMAGES, VIDEOS, AUDIO, RECEIVED }

private sealed interface TvNav {
    data object Home : TvNav
    data class Browse(val title: String, val exts: Set<String>?, val flat: Boolean = false) : TvNav
    data class Media(val title: String, val kind: MediaKind) : TvNav
    data class FilesByExt(val title: String, val exts: Set<String>) : TvNav
    data object Apk : TvNav
    data class Sections(val section: Section) : TvNav
    data object Receive : TvNav
    data object Devices : TvNav
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // OTA + permiso de archivos al inicio
    var update by remember { mutableStateOf<Updater.Update?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var askFiles by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* las pantallas releen el permiso al recargar */ }
    LaunchedEffect(Unit) {
        if (!StorageBrowser.hasAnyFileAccess(context)) askFiles = true
        update = Updater.check(BuildConfig.VERSION_NAME)
    }

    CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(color = Color.White)) {
    Box(Modifier.fillMaxSize().background(BgMain)) {
        when (val n = nav) {
            TvNav.Home -> TvHome(
                vm = vm,
                onSend = { nav = TvNav.Sections(Section.MIS_ARCHIVOS) },
                onReceive = { nav = TvNav.Receive },
                onSection = { s -> nav = TvNav.Sections(s) },
            )
            is TvNav.Sections -> TvSections(
                vm = vm, initial = n.section,
                onSend = { files -> vm.setSelectedFiles(files); nav = TvNav.Devices },
                onExit = { nav = TvNav.Home },
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
            is TvNav.FilesByExt -> {
                val entries = remember(n) { MediaRepo.filesByExtension(context, n.exts) }
                MediaListScreen(
                    title = n.title, entries = entries,
                    onSend = { e ->
                        vm.setSelectedFiles(listOf(MediaRepo.toOutgoing(context, e)))
                        nav = TvNav.Devices
                    },
                    onBack = { nav = TvNav.Home },
                )
            }
            TvNav.Apk -> ApkScreen { nav = TvNav.Home }
            TvNav.Receive -> TvReceive(vm, onViewReceived = { nav = TvNav.Media("Recibidos", MediaKind.RECEIVED) }) { nav = TvNav.Home }
            TvNav.Devices -> TvDevices(vm) { nav = TvNav.Home }
        }
    }
    } // CompositionLocalProvider (ripple blanco)

    // Permiso de archivos (primera apertura)
    if (askFiles) {
        WiwyDialog(
            onDismiss = { askFiles = false },
            icon = Icons.Default.FolderOpen,
            title = "Permiso de archivos",
            body = "Para buscar e instalar APK y enviar tus archivos, WiwyTransfer necesita acceso a los archivos del dispositivo.",
            secondaryLabel = "Ahora no",
            onSecondary = { askFiles = false },
            primaryLabel = "Conceder",
            onPrimary = {
                askFiles = false
                val intent = StorageBrowser.manageAllFilesIntent(context)
                if (intent != null) {
                    runCatching { context.startActivity(intent) }
                } else {
                    // TV sin pantalla de "todos los archivos" (p. ej. Hisense): permisos de medios
                    permLauncher.launch(StorageBrowser.mediaPermissions())
                }
            },
        )
    }

    // Actualización OTA
    update?.let { upd ->
        if (updating) {
            WiwyDialog(
                onDismiss = {},
                icon = Icons.Default.CloudDownload,
                title = "Descargando actualización",
                body = "${(updateProgress * 100).toInt()}%",
                dismissOnOutside = false,
                primaryLabel = "Actualizando…",
                onPrimary = {},
                content = {
                    LinearProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF2979FF), trackColor = Color(0x33FFFFFF),
                    )
                },
            )
        } else {
            WiwyDialog(
                onDismiss = { update = null },
                icon = Icons.Default.CloudDownload,
                title = "Nueva versión disponible",
                badge = "Versión ${upd.version}",
                body = "Mejor rendimiento, nuevas funciones y correcciones de errores.",
                secondaryLabel = "Más tarde",
                onSecondary = { update = null },
                primaryLabel = "Actualizar",
                onPrimary = {
                    updating = true
                    scope.launch {
                        runCatching {
                            val apk = Updater.download(context, upd) { p -> updateProgress = p }
                            Updater.install(context, apk)
                        }
                        updating = false
                    }
                },
            )
        }
    }

    // Solicitud para recibir (protocolo propio)
    incoming?.let { req ->
        WiwyDialog(
            onDismiss = { vm.respondIncoming(false) },
            icon = Icons.Default.Download,
            title = "¿Deseas recibir este archivo?",
            body = "${req.header.sender}\n${req.header.files.size} archivo(s) · ${formatBytes(req.header.totalBytes)}",
            secondaryLabel = "Rechazar",
            onSecondary = { vm.respondIncoming(false) },
            primaryLabel = "Permitir",
            onPrimary = { vm.respondIncoming(true) },
        )
    }

    // Solicitud para recibir (Quick Share)
    qsIncoming?.let { req ->
        WiwyDialog(
            onDismiss = { vm.respondQs(false) },
            icon = Icons.Default.Download,
            title = "¿Deseas recibir este archivo?",
            body = "${req.sender}\n${req.files.size} archivo(s) · ${formatBytes(req.totalBytes)}" +
                (req.pin?.let { "\nPIN: $it" } ?: ""),
            secondaryLabel = "Rechazar",
            onSecondary = { vm.respondQs(false) },
            primaryLabel = "Permitir",
            onPrimary = { vm.respondQs(true) },
        )
    }

    // Transferencia completada
    (qsReceive as? QsReceiveState.Done)?.let { done ->
        WiwyDialog(
            onDismiss = { vm.resetQsReceive() },
            icon = Icons.Default.Check,
            title = "¡Transferencia completada!",
            body = "${done.paths.size} archivo(s) recibido(s) correctamente.",
            primaryLabel = "Aceptar",
            onPrimary = { vm.resetQsReceive() },
        )
    }
}

@Composable
private fun TvHome(
    vm: AppViewModel,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSection: (Section) -> Unit,
) {
    val qsStatus by vm.qsStatus.collectAsStateWithLifecycle()
    val storage = remember { storageInfo() }

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
        // Cabecera
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_fg_badge),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("WiwyTransfer", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Comparte sin límites", color = Color(0xFF7FA8D9),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(qsStatus, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))

        // ENVIAR / RECIBIR
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BigCard(Modifier.weight(1f), "ENVIAR", "Enviar archivos", Icons.Default.Upload,
                listOf(Color(0xFF2979FF), Color(0xFF1565C0)), onSend)
            BigCard(Modifier.weight(1f), "RECIBIR", "Recibir archivos", Icons.Default.Download,
                listOf(Color(0xFF00BCD4), Color(0xFF00838F)), onReceive)
        }

        Spacer(Modifier.height(20.dp))

        // Categorías
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CatTile(Modifier.weight(1f), "Mis archivos", Icons.Default.Folder, Color(0xFF4FC3F7)) { onSection(Section.MIS_ARCHIVOS) }
            CatTile(Modifier.weight(1f), "Videos", Icons.Default.Movie, Color(0xFFB388FF)) { onSection(Section.VIDEOS) }
            CatTile(Modifier.weight(1f), "Imágenes", Icons.Default.Image, Color(0xFF4DD0E1)) { onSection(Section.IMAGENES) }
            CatTile(Modifier.weight(1f), "Música", Icons.Default.MusicNote, Color(0xFFFF80AB)) { onSection(Section.MUSICA) }
            CatTile(Modifier.weight(1f), "APK", Icons.Default.Android, Color(0xFF8BC34A)) { onSection(Section.APK) }
            CatTile(Modifier.weight(1f), "Documentos", Icons.Default.Description, Color(0xFFFFD54F)) { onSection(Section.DOCUMENTOS) }
        }

        Spacer(Modifier.height(20.dp))

        // Barra de almacenamiento
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF7FA8D9), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Almacenamiento interno", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(storage.first, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { storage.second }, modifier = Modifier.fillMaxWidth().height(6.dp))
            }
        }
    }
}

private fun storageInfo(): Pair<String, Float> {
    return runCatching {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val used = total - stat.availableBytes
        Pair("${formatBytes(used)} / ${formatBytes(total)}", if (total > 0) used.toFloat() / total else 0f)
    }.getOrDefault(Pair("", 0f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BigCard(modifier: Modifier, title: String, subtitle: String, icon: ImageVector, gradient: List<Color>, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = if (focused) BorderStroke(3.dp, Color.White) else null,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(gradient)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = gradient.last(), modifier = Modifier.size(76.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xE6FFFFFF), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatTile(modifier: Modifier, label: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(120.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(16.dp),
        color = if (focused) BgCardFocus else BgCard,
        border = if (focused) BorderStroke(3.dp, Color.White) else null,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvDevices(vm: AppViewModel, onBack: () -> Unit) {
    val selected by vm.selectedFiles.collectAsStateWithLifecycle()
    val qsPeers by vm.qsPeers.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val qsSend by vm.qsSend.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
        // Cabecera
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_fg_badge),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Enviar archivos", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Comparte sin límites, de forma rápida y segura",
                    color = Color(0xFF7FA8D9), style = MaterialTheme.typography.bodyMedium)
            }
            PillButton(Icons.Default.QrCodeScanner, "Escanear código QR") {
                android.widget.Toast.makeText(context, "Próximamente", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        Spacer(Modifier.height(20.dp))

        // Tarjeta de progreso (solo al enviar)
        when (val s = qsSend) {
            is QsSendState.Sending -> SendProgressCard(
                fraction = s.fraction.toFloat(),
                name = s.name,
                sent = s.sent,
                total = s.total,
                etaSeconds = s.etaSeconds,
            )
            QsSendState.Done -> StatusCard("✅ Enviado", Color(0xFFA5D6A7))
            is QsSendState.Failed -> StatusCard("❌ ${s.message}", Color(0xFFFFCDD2))
            QsSendState.Idle -> {}
        }

        if (qsSend != QsSendState.Idle) Spacer(Modifier.height(20.dp))

        Text("Enviar a…", color = Color.White,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Selecciona un dispositivo para enviar tus archivos",
            color = Color(0xFF7FA8D9), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        // Lista de dispositivos
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
            if (qsPeers.isEmpty() && peers.isEmpty()) {
                Text("Buscando dispositivos…", color = Color(0xCCFFFFFF),
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            qsPeers.forEach { p -> DeviceRow(p.name, "Quick Share", quickShare = true) { vm.sendQs(p) } }
            peers.forEach { p -> DeviceRow(p.displayName, "WiwyTransfer", quickShare = false) { vm.sendTo(p) } }
        }

        Spacer(Modifier.height(12.dp))

        // Pie: aviso + historial
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(BgCard).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null,
                    tint = Color(0xFFFFD54F), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Asegúrate de que el dispositivo receptor tenga “Visible en Quick Share” activado",
                    color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(16.dp))
            PillButton(Icons.Default.History, "Ver historial de transferencias") {
                android.widget.Toast.makeText(context, "Próximamente", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun SendProgressCard(fraction: Float, name: String, sent: Long, total: Long, etaSeconds: Long) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BgCard).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 6.dp,
                color = Color(0xFF2979FF),
                trackColor = Color(0x33FFFFFF),
            )
            Text("${(fraction * 100).toInt()}%", color = Color.White,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Enviando 1 archivo", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$name · ${formatBytes(total)}", color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF2979FF),
                trackColor = Color(0x33FFFFFF),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("Restante: ${formatEta(etaSeconds)}", color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.bodySmall)
            Text("${formatBytes(sent)} / ${formatBytes(total)}", color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusCard(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BgCard).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatEta(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(24.dp),
        color = if (focused) BgCardFocus else BgCard,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF7FA8D9), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRow(name: String, kind: String, quickShare: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                if (focused) Brush.linearGradient(listOf(Color(0xFF2979FF), Color(0xFF1565C0)))
                else Brush.linearGradient(listOf(BgCard, BgCard))
            )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(if (focused) Color.White else Color(0xFF1E2A45)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null,
                        tint = if (focused) Color(0xFF1565C0) else Color(0xFF7FA8D9),
                        modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(kind, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    if (quickShare) Icons.Default.Wifi else Icons.Default.Send,
                    contentDescription = kind,
                    tint = if (quickShare) Color(0xFF4CAF50) else Color(0xFF42A5F5),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xCCFFFFFF))
            }
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
    }
}
