package com.wiwy.wiwytransfer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiwy.wiwytransfer.net.Peer

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        val isTv = packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("android.hardware.type.television")
        setContent {
            MaterialTheme(colorScheme = wiwyColorScheme()) {
                if (isTv) TvAppScreen(vm) else AppScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND ->
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { listOf(it) } ?: emptyList()
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) vm.setSelectedUris(uris)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val selected by vm.selectedFiles.collectAsStateWithLifecycle()
    LaunchedEffect(selected) { if (selected.isNotEmpty()) tab = 0 }

    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val qsIncoming by vm.qsIncoming.collectAsStateWithLifecycle()
    var showBrowser by remember { mutableStateOf(false) }

    if (showBrowser) {
        FileBrowserScreen(
            onPick = { files ->
                vm.setSelectedFiles(files.map { com.wiwy.wiwytransfer.storage.StorageBrowser.toOutgoing(it) })
                showBrowser = false
            },
            onCancel = { showBrowser = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiwyTransfer") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                    label = { Text("Enviar") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { Text("Recibir") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    label = { Text("Recibidos") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> SendTab(vm, onBrowse = { showBrowser = true })
                1 -> ReceiveTab(vm)
                else -> ReceivedScreen()
            }
        }
    }

    incoming?.let { req ->
        IncomingDialog(
            sender = req.header.sender,
            fileCount = req.header.files.size,
            totalBytes = req.header.totalBytes,
            onAccept = { vm.respondIncoming(true) },
            onDecline = { vm.respondIncoming(false) },
        )
    }

    qsIncoming?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.respondQs(false) },
            icon = { Icon(Icons.Default.Download, contentDescription = null) },
            title = { Text("Quick Share") },
            text = {
                Text(
                    "${req.sender} quiere enviarte ${req.files.size} archivo(s) " +
                        "(${formatBytes(req.totalBytes)})." +
                        (req.pin?.let { "\nPIN: $it" } ?: "")
                )
            },
            confirmButton = { TextButton(onClick = { vm.respondQs(true) }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { vm.respondQs(false) }) { Text("Rechazar") } },
        )
    }

    if (showSettings) {
        SettingsDialog(vm) { showSettings = false }
    }
}

@Composable
fun SendTab(vm: AppViewModel, onBrowse: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selected by vm.selectedFiles.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val sendState by vm.sendState.collectAsStateWithLifecycle()
    val qsPeers by vm.qsPeers.collectAsStateWithLifecycle()
    val qsSend by vm.qsSend.collectAsStateWithLifecycle()

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.setSelectedUris(uris) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(
                onClick = { picker.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Elegir archivos")
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    if (com.wiwy.wiwytransfer.storage.StorageBrowser.hasAllFilesAccess(context)) onBrowse()
                    else com.wiwy.wiwytransfer.storage.StorageBrowser.manageAllFilesIntent(context)?.let {
                        runCatching { context.startActivity(it) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Explorar archivos (recomendado en TV)")
            }
        }

        if (selected.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${selected.size} archivo(s) · ${formatBytes(selected.sumOf { it.size })}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.clearSelection() }) { Text("Quitar") }
                        }
                        selected.take(5).forEach {
                            Text(
                                "• ${it.name}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected.size > 5) Text("…y ${selected.size - 5} más",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            when (val s = sendState) {
                is SendState.Sending -> SendProgress(s)
                is SendState.Done -> StatusBanner(Icons.Default.CheckCircle,
                    "Enviado (${s.received} archivo/s)", true)
                is SendState.Declined -> StatusBanner(Icons.Default.Cancel,
                    "Rechazado${s.reason?.let { ": $it" } ?: ""}", false)
                is SendState.Error -> StatusBanner(Icons.Default.Error, "Error: ${s.message}", false)
                SendState.Idle -> {}
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dispositivos cercanos", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refreshDiscovery() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
        }

        if (peers.isEmpty()) {
            item {
                Text(
                    "Buscando dispositivos en la red WiFi…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(peers, key = { it.id }) { peer ->
                PeerRow(
                    peer = peer,
                    enabled = selected.isNotEmpty() && sendState !is SendState.Sending,
                    onClick = { vm.sendTo(peer) },
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Quick Share cercanos", style = MaterialTheme.typography.titleMedium)
            Text("El otro dispositivo debe tener abierta su pantalla de “Recibir” de Quick Share.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            when (val s = qsSend) {
                is QsSendState.Sending -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Enviando… ${(s.fraction * 100).toInt()}%")
                        LinearProgressIndicator(progress = { s.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                QsSendState.Done -> StatusBanner(Icons.Default.CheckCircle, "Enviado por Quick Share", true)
                is QsSendState.Failed -> StatusBanner(Icons.Default.Error, "Error: ${s.message}", false)
                QsSendState.Idle -> {}
            }
        }
        if (qsPeers.isEmpty()) {
            item {
                Text("Buscando dispositivos Quick Share…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(qsPeers, key = { it.id }) { peer ->
                QsPeerRow(
                    peer = peer,
                    enabled = selected.isNotEmpty() && qsSend !is QsSendState.Sending,
                    onClick = { vm.sendQs(peer) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QsPeerRow(peer: com.wiwy.wiwytransfer.qs.QsPeer, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = { if (enabled) onClick() }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Devices, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.titleSmall)
                Text("Quick Share", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Send, contentDescription = "Enviar",
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerRow(peer: Peer, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (peer.os == "android") Icons.Default.PhoneAndroid else Icons.Default.Laptop,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                Text(peer.host.hostAddress ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Send, contentDescription = "Enviar",
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SendProgress(s: SendState.Sending) {
    val frac = if (s.total > 0) (s.sent.toFloat() / s.total) else 0f
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Enviando… ${formatBytes(s.sent)} / ${formatBytes(s.total)}")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ReceiveTab(vm: AppViewModel) {
    val name by vm.deviceName.collectAsStateWithLifecycle()
    val state by vm.receiveState.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Visible como", style = MaterialTheme.typography.labelMedium)
                }
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text("Mantén esta pantalla abierta para recibir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when (val s = state) {
            ReceiveState.Listening ->
                StatusBanner(Icons.Default.HourglassEmpty, "Esperando archivos…", null)
            is ReceiveState.Receiving -> {
                val p = s.progress
                val frac = if (p.overallTotal > 0) p.overallReceived.toFloat() / p.overallTotal else 0f
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Recibiendo de ${p.sender}")
                        if (p.fileName.isNotEmpty())
                            Text("${p.fileName} (${p.fileIndex}/${p.fileCount})",
                                style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        Text("${formatBytes(p.overallReceived)} / ${formatBytes(p.overallTotal)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is ReceiveState.Done -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Recibido de ${s.sender}",
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        s.paths.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.resetReceive() }) { Text("Listo") }
                    }
                }
            }
            is ReceiveState.Error -> StatusBanner(Icons.Default.Error, "Error: ${s.message}", false)
        }
    }
}

@Composable
fun StatusBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, ok: Boolean?) {
    val tint = when (ok) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint)
    }
}

@Composable
fun IncomingDialog(
    sender: String,
    fileCount: Int,
    totalBytes: Long,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Default.Download, contentDescription = null) },
        title = { Text("Solicitud de transferencia") },
        text = {
            Text("$sender quiere enviarte $fileCount archivo(s) (${formatBytes(totalBytes)}).")
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Rechazar") } },
    )
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val current by vm.deviceName.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del dispositivo") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Visible para otros") },
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.setDeviceName(text); onDismiss() }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
