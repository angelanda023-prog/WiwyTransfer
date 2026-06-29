package com.wiwy.wiwytransfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wiwy.wiwytransfer.storage.FileEntry
import com.wiwy.wiwytransfer.storage.StorageBrowser
import java.io.File

/** Explorador de archivos navegable con mando (D-pad) para elegir qué enviar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    title: String = "Elegir archivos",
    exts: Set<String>? = null,
    onPick: (List<File>) -> Unit,
    onCancel: () -> Unit,
) {
    var dir by remember { mutableStateOf(StorageBrowser.storageRoot()) }
    val selected = remember { mutableStateListOf<File>() }
    val entries by remember(dir) {
        mutableStateOf(
            StorageBrowser.list(dir).filter { it.isDir || exts == null || it.file.extension.lowercase() in exts }
        )
    }
    val root = remember { StorageBrowser.storageRoot().absolutePath }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            Button(
                onClick = { onPick(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) { Text("Enviar (${selected.size})") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
        Text(dir.absolutePath, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dir.absolutePath != root && dir.parentFile != null) {
                item {
                    Card(onClick = { dir = dir.parentFile!! }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("..")
                        }
                    }
                }
            }
            items(entries, key = { it.file.absolutePath }) { entry ->
                BrowserRow(
                    entry = entry,
                    checked = selected.contains(entry.file),
                    onClick = {
                        if (entry.isDir) dir = entry.file
                        else if (selected.contains(entry.file)) selected.remove(entry.file)
                        else selected.add(entry.file)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserRow(entry: FileEntry, checked: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (entry.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.isDir) Text(formatBytes(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!entry.isDir && checked) Icon(Icons.Default.CheckCircle, contentDescription = "Seleccionado",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Pantalla de archivos recibidos: navegar y abrir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivedScreen(refreshKey: Any = Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val entries by remember(refresh, refreshKey) {
        mutableStateOf(com.wiwy.wiwytransfer.storage.MediaRepo.received(context))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recibidos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
        }
        Text("Descargas/WiwyTransfer", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text("Aún no has recibido archivos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.uri.toString() }) { entry ->
                    Card(
                        onClick = {
                            val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                .setDataAndType(entry.uri, entry.mime)
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(i) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(entry.size), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "Abrir")
                        }
                    }
                }
            }
        }
    }
}
