package com.wiwy.wiwytransfer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wiwy.wiwytransfer.qs.QsOutgoingFile
import com.wiwy.wiwytransfer.storage.ApkRepo
import com.wiwy.wiwytransfer.storage.MediaRepo
import com.wiwy.wiwytransfer.storage.StorageBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Section(val title: String, val icon: ImageVector, val color: Color) {
    MIS_ARCHIVOS("Mis archivos", Icons.Default.Folder, Color(0xFF4FC3F7)),
    VIDEOS("Videos", Icons.Default.Movie, Color(0xFFB388FF)),
    IMAGENES("Imágenes", Icons.Default.Image, Color(0xFF4DD0E1)),
    MUSICA("Música", Icons.Default.MusicNote, Color(0xFFFF80AB)),
    APK("APK", Icons.Default.Android, Color(0xFF8BC34A)),
    DOCUMENTOS("Documentos", Icons.Default.Description, Color(0xFFFFD54F)),
}

private class SectionItem(
    val name: String,
    val info: String,
    val dateMillis: Long,
    val isDir: Boolean,
    val thumbUri: Uri?,
    val icon: ImageVector,
    val iconColor: Color,
    val key: String,
    val onOpen: () -> Unit,
    val outgoing: (() -> QsOutgoingFile)?,
    val uri: Uri?,         // medios: para borrar con confirmación del sistema
    val deleteFile: (() -> Unit)?, // archivos/apk: borrado directo
    val apkFile: File? = null, // si es APK: para cargar su icono real
)

private val DOC_EXTS = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "zip", "rar")
private val BgMainS = Color(0xFF0A1020)
private val BgSide = Color(0xFF0E1626)
private val SideSel = Color(0xFF1976D2)
private val RowBg = Color(0xFF131C2E)
private val RowFocus = Color(0xFF22314F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSections(vm: AppViewModel, initial: Section, onSend: (List<QsOutgoingFile>) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(initial) }
    var dir by remember { mutableStateOf(StorageBrowser.storageRoot()) }
    val root = remember { StorageBrowser.storageRoot().absolutePath }
    var reload by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<SectionItem>>(emptyList()) }
    var grid by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() } // por key
    var confirmDelete by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    fun clearSelection() { selectMode = false; selected.clear() }

    LaunchedEffect(section, dir, reload) {
        loading = true
        clearSelection()
        items = withContext(Dispatchers.IO) { buildItems(context, section, dir) }
        loading = false
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        reload++
    }

    fun deleteSelected() {
        val chosen = items.filter { selected.contains(it.key) }
        chosen.forEach { it.deleteFile?.invoke() }
        val uris = chosen.mapNotNull { it.uri }
        if (uris.isNotEmpty()) {
            val sender = MediaRepo.deleteRequest(context, uris)
            if (sender != null) { deleteLauncher.launch(IntentSenderRequest.Builder(sender).build()); return }
        }
        reload++
    }

    BackHandler {
        when {
            selectMode -> clearSelection()
            section == Section.MIS_ARCHIVOS && dir.absolutePath != root && dir.parentFile != null -> dir = dir.parentFile!!
            else -> onExit()
        }
    }

    Row(Modifier.fillMaxSize().background(BgMainS)) {
        // Barra lateral
        Column(Modifier.width(240.dp).fillMaxHeight().background(BgSide).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("WiwyTransfer", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Comparte sin límites", color = Color(0xFF7FA8D9), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(20.dp))
            Section.entries.forEach { s ->
                SidebarItem(s, selected = s == section) { section = s; dir = StorageBrowser.storageRoot() }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.weight(1f))
            val storage = remember { storageInfoPair() }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RowBg).padding(12.dp)) {
                Text("Almacenamiento interno", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(storage.first, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { storage.second }, modifier = Modifier.fillMaxWidth().height(5.dp))
            }
        }

        // Contenido
        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            Text(if (selectMode) "${selected.size} seleccionado(s)" else section.title,
                color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Toolbar
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectMode) {
                    Button(onClick = {
                        val files = items.filter { selected.contains(it.key) }.mapNotNull { it.outgoing?.invoke() }
                        if (files.isNotEmpty()) onSend(files)
                    }, enabled = selected.isNotEmpty()) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Enviar") }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Eliminar")
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { clearSelection() }) { Text("Cancelar", color = Color.White) }
                } else {
                    OutlinedButton(onClick = { selectMode = true }) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Seleccionar")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { grid = !grid }) {
                    Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = "Vista", tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))

            val onItemClick: (SectionItem) -> Unit = { item ->
                if (selectMode) {
                    if (selected.contains(item.key)) selected.remove(item.key) else selected.add(item.key)
                } else if (item.isDir) {
                    dir = File(item.key)
                } else item.onOpen()
            }
            val onItemLong: (SectionItem) -> Unit = { item ->
                if (!item.isDir) {
                    selectMode = true
                    if (!selected.contains(item.key)) selected.add(item.key)
                }
            }

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    Spacer(Modifier.width(10.dp)); Text("Cargando…", color = Color.White)
                }
                items.isEmpty() -> Text("No hay archivos.", color = Color(0xCCFFFFFF))
                grid -> LazyVerticalGrid(columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = { it.key }) { item ->
                        GridItem(item, selected.contains(item.key), { onItemClick(item) }, { onItemLong(item) })
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.key }) { item ->
                        RowItem(item, df, selected.contains(item.key), { onItemClick(item) }, { onItemLong(item) })
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidebarItem(s: Section, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SideSel else if (focused) RowFocus else Color.Transparent,
        border = if (focused && !selected) BorderStroke(2.dp, Color.White) else null,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, contentDescription = null, tint = if (selected) Color.White else s.color)
            Spacer(Modifier.width(12.dp))
            Text(s.title, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowItem(item: SectionItem, df: SimpleDateFormat, checked: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .tvSelectable(onClick = onClick, onLongClick = onLong, onFocusChanged = { focused = it }),
        shape = RoundedCornerShape(12.dp),
        color = if (checked) Color(0xFF1B5E20) else if (focused) RowFocus else RowBg,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadingVisual(item, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.info, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
            if (item.dateMillis > 0) {
                Text(df.format(Date(item.dateMillis)), color = Color(0x99FFFFFF), style = MaterialTheme.typography.bodySmall)
            }
            if (checked) { Spacer(Modifier.width(10.dp)); Icon(Icons.Default.CheckCircle, null, tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridItem(item: SectionItem, checked: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.height(150.dp).tvSelectable(onClick = onClick, onLongClick = onLong, onFocusChanged = { focused = it }),
        shape = RoundedCornerShape(12.dp),
        color = if (checked) Color(0xFF1B5E20) else if (focused) RowFocus else RowBg,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (item.thumbUri != null) {
                    AsyncImage(model = item.thumbUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black),
                        contentScale = ContentScale.Crop)
                } else {
                    LeadingVisual(item, 56.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.name, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LeadingVisual(item: SectionItem, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    when {
        item.apkFile != null -> {
            val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.key) {
                value = withContext(Dispatchers.IO) {
                    ApkRepo.iconFromFile(context, item.apkFile)?.toBitmap(96, 96)?.asImageBitmap()
                }
            }
            if (bmp != null) Image(bitmap = bmp!!, contentDescription = null, modifier = Modifier.size(size))
            else Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(size))
        }
        item.thumbUri != null -> AsyncImage(
            model = item.thumbUri, contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.Black),
            contentScale = ContentScale.Crop,
        )
        else -> Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(size))
    }
}

// Carga de elementos por sección (en hilo IO)
private fun buildItems(context: android.content.Context, section: Section, dir: File): List<SectionItem> {
    return when (section) {
        Section.MIS_ARCHIVOS -> StorageBrowser.list(dir).map { e ->
            SectionItem(
                name = e.name,
                info = if (e.isDir) "Carpeta" else formatBytes(e.size),
                dateMillis = e.file.lastModified(),
                isDir = e.isDir,
                thumbUri = null,
                icon = if (e.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                iconColor = if (e.isDir) Color(0xFF4FC3F7) else Color(0xFFB0BEC5),
                key = e.file.absolutePath,
                onOpen = { /* se asigna fuera para carpetas */ },
                outgoing = if (e.isDir) null else ({ StorageBrowser.toOutgoing(e.file) }),
                uri = null,
                deleteFile = if (e.isDir) null else ({ e.file.delete(); Unit }),
            )
        }
        Section.VIDEOS, Section.IMAGENES, Section.MUSICA -> {
            val list = when (section) {
                Section.VIDEOS -> MediaRepo.videos(context)
                Section.IMAGENES -> MediaRepo.images(context)
                else -> MediaRepo.audio(context)
            }
            list.map { m ->
                SectionItem(
                    name = m.name, info = formatBytes(m.size), dateMillis = m.dateMillis, isDir = false,
                    thumbUri = if (m.isImage || m.isVideo) m.uri else null,
                    icon = if (m.isAudio) Icons.Default.MusicNote else Icons.Default.InsertDriveFile,
                    iconColor = section.color, key = m.uri.toString(),
                    onOpen = { openMedia(context, m.uri, m.mime) },
                    outgoing = ({ MediaRepo.toOutgoing(context, m) }),
                    uri = m.uri, deleteFile = null,
                )
            }
        }
        Section.DOCUMENTOS -> MediaRepo.filesByExtension(context, DOC_EXTS).map { m ->
            SectionItem(
                name = m.name, info = formatBytes(m.size), dateMillis = m.dateMillis, isDir = false,
                thumbUri = null, icon = Icons.Default.Description, iconColor = Color(0xFFFFD54F),
                key = m.uri.toString(), onOpen = { openMedia(context, m.uri, m.mime) },
                outgoing = ({ MediaRepo.toOutgoing(context, m) }), uri = m.uri, deleteFile = null,
            )
        }
        Section.APK -> ApkRepo.list(context).map { a ->
            SectionItem(
                name = a.name, info = formatBytes(a.size), dateMillis = a.file?.lastModified() ?: 0L, isDir = false,
                thumbUri = null, icon = Icons.Default.Android, iconColor = Color(0xFF8BC34A),
                key = a.uri.toString(), onOpen = { ApkRepo.install(context, a) },
                outgoing = ({ MediaRepo.toOutgoing(context, com.wiwy.wiwytransfer.storage.MediaEntry(a.uri, a.name, a.size, "application/vnd.android.package-archive")) }),
                uri = if (a.file == null) a.uri else null,
                deleteFile = a.file?.let { f -> ({ f.delete(); Unit }) },
                apkFile = a.file,
            )
        }
    }
}

private fun openMedia(context: android.content.Context, uri: Uri, mime: String) {
    val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

private fun storageInfoPair(): Pair<String, Float> = runCatching {
    val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
    val total = stat.totalBytes
    val used = total - stat.availableBytes
    Pair("${formatBytes(used)} / ${formatBytes(total)}", if (total > 0) used.toFloat() / total else 0f)
}.getOrDefault(Pair("", 0f))
