@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)

package com.kerneldroid.karchiver.presentation.browser

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kerneldroid.karchiver.data.isRarArchive
import com.kerneldroid.karchiver.data.storage.SafFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
fun ArchiveExplorerRoute(
    archive: File,
    password: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ArchiveExplorerViewModel = viewModel(key = "explorer:" + archive.absolutePath) {
        ArchiveExplorerViewModel(archive, password)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val writable = archive.isFile && archive.canWrite()
    val canEdit = writable && !isRarArchive(archive)
    val selectionMode = state.selected.isNotEmpty()

    var addError by remember(archive.absolutePath) { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var propsTarget by remember { mutableStateOf<String?>(null) }
    var extractMenu by remember { mutableStateOf(false) }
    var pendingTreeAll by remember { mutableStateOf<Boolean?>(null) }
    val dragUris = remember(archive.absolutePath) { mutableStateMapOf<String, Uri>() }
    val dragDir = remember(archive.absolutePath) { File(context.cacheDir, "explorer-drag") }

    BackHandler {
        if (!vm.navigateUp()) onClose()
    }

    LaunchedEffect(state.selected, writable) {
        for (key in dragUris.keys.toList()) {
            if (!state.selected.contains(key)) dragUris.remove(key)
        }
        if (!writable) return@LaunchedEffect
        val rows = state.rows
        val missing = state.selected.filter { path ->
            !dragUris.containsKey(path) && rows.any { it.path == path && !it.isDir }
        }
        if (missing.isEmpty()) return@LaunchedEffect
        dragDir.mkdirs()
        val ok = vm.extractSelected(dragDir)
        if (ok) {
            for (path in state.selected) {
                val extracted = File(dragDir, path.trimStart('/'))
                if (extracted.isFile) {
                    runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", extracted)
                    }.getOrNull()?.let { dragUris[path] = it }
                }
            }
        }
    }

    val addPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var failures = 0
                val staged = ArrayList<File>()
                val stageDir = File(context.cacheDir, "explorer-add-" + System.nanoTime())
                withContext(Dispatchers.IO) {
                    stageDir.mkdirs()
                    for (uri in uris) {
                        try {
                            val name = queryDisplayName(context, uri) ?: ("file-" + System.nanoTime())
                            val dst = uniqueChild(stageDir, name)
                            context.contentResolver.openInputStream(uri)?.use { ins ->
                                dst.outputStream().use { out -> ins.copyTo(out) }
                            } ?: throw IOException("Cannot read file")
                            staged.add(dst)
                        } catch (_: Exception) {
                            failures++
                        }
                    }
                }
                if (staged.isNotEmpty()) vm.addFiles(staged)
                withContext(Dispatchers.IO) {
                    runCatching { stageDir.deleteRecursively() }
                }
                addError = if (failures > 0) "Could not add $failures file(s)" else null
            }
        }
    }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val extractAll = pendingTreeAll
        pendingTreeAll = null
        if (uri != null && extractAll != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val rawDir = treeUriToPrimaryPath(uri)
                if (rawDir != null && (rawDir.isDirectory || rawDir.mkdirs()) && rawDir.canWrite()) {
                    if (extractAll) vm.extractAll(rawDir) else {
                        if (state.selected.isEmpty()) {
                            addError = "Selection changed"
                        } else {
                            vm.extractSelected(rawDir)
                        }
                    }
                } else {
                    val staging = File(context.cacheDir, "explorer-tree-" + System.nanoTime())
                    withContext(Dispatchers.IO) { staging.mkdirs() }
                    val ok = if (extractAll) vm.extractAll(staging) else {
                        if (state.selected.isEmpty()) {
                            addError = "Selection changed"
                            false
                        } else {
                            vm.extractSelected(staging)
                        }
                    }
                    if (ok) {
                        var failures = 0
                        val kids = withContext(Dispatchers.IO) { staging.listFiles() } ?: emptyArray()
                        for (kid in kids) {
                            if (!SafFs.copyIn(context, uri, "", kid)) failures++
                        }
                        if (failures > 0) addError = "Could not copy $failures file(s) to selected folder"
                    }
                    withContext(Dispatchers.IO) {
                        runCatching { staging.deleteRecursively() }
                    }
                }
            }
        }
    }

    fun extractHere() {
        scope.launch {
            val dest = File(archive.parentFile, archive.nameWithoutExtension)
            withContext(Dispatchers.IO) { dest.mkdirs() }
            if (state.selected.isEmpty()) vm.extractAll(dest) else vm.extractSelected(dest)
        }
    }

    Column(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (state.insidePath.isEmpty()) onClose() else vm.navigateUp()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                text = archive.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selectionMode) {
                Text(
                    text = "${state.selected.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = vm::clearSelection) {
                    Icon(Icons.Filled.Close, "Clear selection")
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, "Close")
                }
            }
        }
        ExplorerCrumbs(
            insidePath = state.insidePath,
            onRoot = { vm.openDir("") },
            onSegment = { vm.openDir(it) }
        )
        if (state.isLoading) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
        val errorText = state.error ?: addError
        if (errorText != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.ErrorOutline, null, Modifier.size(20.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { vm.dismissError(); addError = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, "Dismiss", Modifier.size(18.dp))
                    }
                }
            }
        }
        if (!writable) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(20.dp))
                    Text(
                        text = "Archive is read-only",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isLoading && state.rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                state.rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen, null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Empty folder",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.rows, key = { it.path }) { row ->
                            val selected = state.selected.contains(row.path)
                            ExplorerEntryRow(
                                row = row,
                                selected = selected,
                                dragUri = dragUris[row.path],
                                onOpenDir = { vm.openDir(row.path) },
                                onToggleSelect = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.toggleSelect(row.path)
                                },
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    if (selectionMode) vm.toggleSelect(row.path)
                                    else if (row.isDir) vm.openDir(row.path)
                                },
                                onShowProps = { propsTarget = row.path }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (canEdit) {
                ExplorerAction(
                    icon = Icons.Filled.Add,
                    label = "Add",
                    onClick = { addPicker.launch(arrayOf("*/*")) }
                )
            }
            Box {
                ExplorerAction(
                    icon = Icons.Filled.Download,
                    label = "Extract",
                    onClick = { extractMenu = true }
                )
                DropdownMenu(expanded = extractMenu, onDismissRequest = { extractMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Extract here") },
                        onClick = { extractMenu = false; extractHere() }
                    )
                    DropdownMenuItem(
                        text = { Text("Choose folder") },
                        onClick = {
                            extractMenu = false
                            pendingTreeAll = state.selected.isEmpty()
                            treePicker.launch(null)
                        }
                    )
                }
            }
            if (canEdit) {
                ExplorerAction(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    enabled = selectionMode,
                    onClick = { showDeleteConfirm = true }
                )
            }
            ExplorerAction(
                icon = Icons.Filled.Info,
                label = "Properties",
                enabled = state.selected.size == 1,
                onClick = { propsTarget = state.selected.singleOrNull() }
            )
        }
    }

    if (showDeleteConfirm) {
        val count = state.selected.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (count == 1) "Delete 1 item?" else "Delete $count items?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch { vm.deleteSelected() }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    propsTarget?.let { target ->
        val entry = state.rows.firstOrNull { it.path == target }
        EntryPropertiesSheet(
            entryPath = target,
            displayName = entry?.displayName ?: target.trimEnd('/').substringAfterLast('/'),
            isDir = entry?.isDir ?: false,
            size = entry?.size ?: 0L,
            canEdit = canEdit,
            onDismiss = { propsTarget = null },
            onSave = { newName ->
                scope.launch {
                    if (vm.renameEntry(target, newName)) propsTarget = null
                }
            }
        )
    }
}

@Composable
private fun EntryPropertiesSheet(
    entryPath: String,
    displayName: String,
    isDir: Boolean,
    size: Long,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(entryPath) { mutableStateOf(displayName) }
    var validationError by remember(entryPath) { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Properties", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Name") },
                singleLine = true,
                enabled = canEdit,
                readOnly = !canEdit,
                isError = validationError != null,
                supportingText = {
                    val message = validationError
                        ?: if (canEdit) null else "Archive is read-only"
                    if (message != null) {
                        Text(
                            message,
                            color = if (validationError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            PropLine("Path", entryPath)
            PropLine("Type", if (isDir) "Folder" else "File")
            PropLine("Size", if (isDir) "Folder" else formatExplorerSize(size))
            if (canEdit) {
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        validationError = when {
                            trimmed.isEmpty() -> "Name cannot be empty"
                            trimmed.contains("/") -> "Name cannot contain /"
                            else -> null
                        }
                        if (validationError == null) onSave(trimmed)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun PropLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExplorerCrumbs(
    insidePath: String,
    onRoot: () -> Unit,
    onSegment: (String) -> Unit
) {
    val segments = remember(insidePath) {
        insidePath.trim('/').split("/").filter { it.isNotEmpty() }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(insidePath) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(onClick = onRoot, label = { Text("Root") })
        segments.forEachIndexed { index, segment ->
            Icon(
                Icons.Filled.ChevronRight, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val isCurrent = index == segments.lastIndex
            AssistChip(
                onClick = {
                    if (!isCurrent) {
                        onSegment(segments.subList(0, index + 1).joinToString("/") + "/")
                    }
                },
                label = { Text(segment, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun ExplorerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = null
        ).padding(vertical = 4.dp)
    ) {
        Icon(
            icon, label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1
        )
    }
}

@Composable
private fun ExplorerEntryRow(
    row: ExplorerRow,
    selected: Boolean,
    dragUri: Uri?,
    onOpenDir: () -> Unit,
    onToggleSelect: () -> Unit,
    onTap: () -> Unit,
    onShowProps: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(if (selected) 16.dp else 12.dp)
    val dragModifier = if (!row.isDir) {
        Modifier.dragAndDropSource(transferData = { _: Offset ->
            dragUri?.let {
                DragAndDropTransferData(
                    ClipData.newUri(context.contentResolver, row.displayName, it),
                    flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
                )
            }
        })
    } else {
        Modifier
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = Modifier.fillMaxWidth().then(dragModifier)
            .combinedClickable(onClick = onTap, onLongClick = onToggleSelect)
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    if (row.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingContent = {
                Text(
                    if (row.isDir) "Folder" else formatExplorerSize(row.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShowProps,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Info, "Properties",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when {
                        selected -> Icon(
                            Icons.Filled.CheckCircle, "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        row.isDir -> Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Icon(
                            Icons.Filled.DragHandle, "Drag out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) {
            Text(
                row.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatExplorerSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (_: Exception) {
        null
    }
}

private fun uniqueChild(dir: File, name: String): File {
    val clean = name.ifBlank { "file" }
    var candidate = File(dir, clean)
    if (!candidate.exists()) return candidate
    val dot = clean.lastIndexOf('.')
    val base = if (dot > 0) clean.substring(0, dot) else clean
    val ext = if (dot > 0) clean.substring(dot) else ""
    var counter = 1
    while (candidate.exists() && counter < 9999) {
        counter++
        candidate = File(dir, base + "-" + counter + ext)
    }
    return candidate
}

private fun treeUriToPrimaryPath(uri: Uri): File? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
        val parts = docId.split(":")
        if (parts.size < 2 || !parts[0].equals("primary", ignoreCase = true)) return null
        File(Environment.getExternalStorageDirectory(), parts[1])
    } catch (_: Exception) {
        null
    }
}
