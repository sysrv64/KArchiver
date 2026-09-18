package com.kerneldroid.karchiver.presentation.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.kerneldroid.karchiver.data.FileProperties
import com.kerneldroid.karchiver.data.FileSystemRepository
import com.kerneldroid.karchiver.data.FormatRegistry
import com.kerneldroid.karchiver.data.formatBytes
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    single: Boolean,
    onProperties: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onCopyPath: () -> Unit
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (single) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 2)
            ) {
                DropdownMenuItem(
                    text = { Text("Properties") },
                    trailingIcon = { Icon(Icons.Filled.Info, null, Modifier.size(20.dp)) },
                    onClick = { onDismiss(); onProperties() }
                )
            }
            Spacer(Modifier.height(3.dp))
        }
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(index = if (single) 1 else 0, count = if (single) 2 else 1)
        ) {
            DropdownMenuItem(
                text = { Text("Share") },
                trailingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(20.dp)) },
                onClick = { onDismiss(); onShare() }
            )
            if (single) {
                DropdownMenuItem(
                    text = { Text("Open with") },
                    trailingIcon = { Icon(Icons.Filled.OpenInNew, null, Modifier.size(20.dp)) },
                    onClick = { onDismiss(); onOpenWith() }
                )
            }
            DropdownMenuItem(
                text = { Text("Copy path") },
                trailingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(20.dp)) },
                onClick = { onDismiss(); onCopyPath() }
            )
        }
    }
}

private fun octalToSymbolic(mode: Int): String {
    val bits = listOf(0x100 to 'r', 0x80 to 'w', 0x40 to 'x', 0x20 to 'r', 0x10 to 'w', 0x8 to 'x', 0x4 to 'r', 0x2 to 'w', 0x1 to 'x')
    return bits.joinToString("") { (mask, c) -> if (mode and mask != 0) c.toString() else "-" }
}

interface PropertiesTarget {
    val title: String
    val canRename: Boolean get() = false
    val canSetModified: Boolean get() = false
    val canChmod: Boolean get() = false
    suspend fun load(): FileProperties
    fun rename(newName: String, onDone: (Result<Unit>) -> Unit) = onDone(Result.failure(UnsupportedOperationException()))
    fun setModified(millis: Long, onDone: (Result<Unit>) -> Unit) = onDone(Result.failure(UnsupportedOperationException()))
    fun chmod(mode: Int, onDone: (Result<Unit>) -> Unit) = onDone(Result.failure(UnsupportedOperationException()))
}

class FilePropertiesTarget(
    private val file: File,
    private val repo: FileSystemRepository,
    private val elevated: Boolean,
    private val onRenameRequest: (String, (Result<Unit>) -> Unit) -> Unit,
    private val onSetModifiedRequest: (Long, (Result<Unit>) -> Unit) -> Unit,
    private val onChmodRequest: (Int, (Result<Unit>) -> Unit) -> Unit
) : PropertiesTarget {
    override val title: String get() = file.name
    override val canRename: Boolean get() = true
    override val canSetModified: Boolean get() = true
    override val canChmod: Boolean get() = true
    override suspend fun load(): FileProperties = repo.loadProperties(file, elevated)
    override fun rename(newName: String, onDone: (Result<Unit>) -> Unit) =
        onRenameRequest(newName) { r -> onDone(r.map { }) }
    override fun setModified(millis: Long, onDone: (Result<Unit>) -> Unit) =
        onSetModifiedRequest(millis, onDone)
    override fun chmod(mode: Int, onDone: (Result<Unit>) -> Unit) =
        onChmodRequest(mode, onDone)
}

class ArchiveEntryPropertiesTarget(
    private val vm: ArchiveExplorerViewModel,
    private val path: String,
    private val displayName: String,
    private val isDir: Boolean,
    private val size: Long,
    private val modified: Long,
    private val mode: Int,
    private val writable: Boolean,
    private val metadataEditable: Boolean,
    private val scope: CoroutineScope
) : PropertiesTarget {
    override val title: String get() = displayName
    override val canRename: Boolean get() = writable
    override val canSetModified: Boolean get() = metadataEditable
    override val canChmod: Boolean get() = metadataEditable

    override suspend fun load(): FileProperties = withContext(Dispatchers.IO) {
        FileProperties(
            name = displayName,
            path = "/" + path.trimStart('/'),
            isDir = isDir,
            sizeBytes = if (isDir) null else size,
            modified = modified,
            mime = if (isDir) "inode/directory"
            else FormatRegistry.forExtension(displayName.substringAfterLast('.', "")).mime,
            modeSymbolic = if (mode > 0) octalToSymbolic(mode) else null,
            modeOctal = mode.takeIf { it > 0 },
            canModify = writable,
            elevated = false
        )
    }

    override fun rename(newName: String, onDone: (Result<Unit>) -> Unit) {
        scope.launch {
            val ok = vm.renameEntry(path, newName)
            onDone(if (ok) Result.success(Unit) else Result.failure(Exception("Could not rename")))
        }
    }

    override fun setModified(millis: Long, onDone: (Result<Unit>) -> Unit) {
        scope.launch {
            val ok = vm.setEntryModified(path, millis)
            onDone(if (ok) Result.success(Unit) else Result.failure(Exception("Could not change date")))
        }
    }

    override fun chmod(mode: Int, onDone: (Result<Unit>) -> Unit) {
        scope.launch {
            val ok = vm.setEntryMode(path, mode)
            onDone(if (ok) Result.success(Unit) else Result.failure(Exception("Could not set permissions")))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesSheet(
    target: PropertiesTarget,
    onDismiss: () -> Unit
) {
    var props by remember(target) { mutableStateOf<FileProperties?>(null) }
    var editMode by remember(target) { mutableStateOf(false) }
    var octalText by remember(target) { mutableStateOf("") }
    var reloadTick by remember(target) { mutableStateOf(0) }
    var showRename by remember(target) { mutableStateOf(false) }
    var showDatePicker by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target, reloadTick) {
        props = withContext(Dispatchers.IO) { runCatching { target.load() }.getOrNull() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Properties", style = MaterialTheme.typography.titleLarge)
            val p = props
            if (p == null) {
                Text("Loading", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val canRename = p.canModify && target.canRename
            val canSetDate = p.canModify && target.canSetModified
            val canEditMode = p.canModify && target.canChmod
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Name", style = MaterialTheme.typography.titleSmall)
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (canRename) {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Filled.Edit, "Rename")
                    }
                }
            }
            PropRow("Path", p.path)
            PropRow("Size", p.sizeBytes?.let { formatBytes(it) } ?: "Unknown")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(
                    if (canSetDate) Modifier.clickable { showDatePicker = true } else Modifier
                )
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Modified", style = MaterialTheme.typography.titleSmall)
                    Text(
                        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date(p.modified)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canSetDate) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            PropRow("Type", p.mime)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Permissions", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (p.modeSymbolic != null && p.modeOctal != null) "${p.modeSymbolic} (${p.modeOctal.toString(8).padStart(3, '0')})"
                        else "Unavailable",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (p.elevated) "Elevated access — changes apply with extra rights."
                        else if (p.canModify) "You can change permissions here."
                        else "Read-only location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEditMode && p.modeOctal != null) {
                    IconButton(onClick = {
                        octalText = p.modeOctal.toString(8).padStart(3, '0')
                        editMode = !editMode
                    }) { Icon(Icons.Filled.Edit, "Edit permissions") }
                }
            }
            if (editMode) {
                val parsed = octalText.toIntOrNull(8)?.takeIf { it in 0..0x1FF }
                OutlinedTextField(
                    value = octalText,
                    onValueChange = { v ->
                        if (v.length <= 3 && v.all { it in '0'..'7' }) octalText = v
                    },
                    label = { Text("Octal mode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        Text(parsed?.let { octalToSymbolic(it) } ?: "Three octal digits")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { editMode = false }) { Text("Cancel") }
                    TextButton(
                        enabled = parsed != null,
                        onClick = {
                            val mode = parsed
                            if (mode != null) {
                                editMode = false
                                target.chmod(mode) { reloadTick++ }
                            }
                        }
                    ) { Text("Apply") }
                }
            }
        }
    }
    if (showRename) {
        var draft by remember(props?.path) { mutableStateOf(props?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            icon = { Icon(Icons.Filled.Edit, null) },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank() && draft.trim() != props?.name,
                    onClick = {
                        val name = draft.trim()
                        showRename = false
                        target.rename(name) { reloadTick++ }
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }
    val currentModified = props?.modified
    if (showDatePicker && currentModified != null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = currentModified)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = dateState.selectedDateMillis
                    showDatePicker = false
                    if (selected != null) {
                        target.setModified(mergeDateKeepingTime(selected, currentModified)) { reloadTick++ }
                    }
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    }
}

private fun mergeDateKeepingTime(utcDateMillis: Long, previousMillis: Long): Long {
    val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(previousMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Composable
fun OpenWithDialog(
    fileName: String,
    apps: List<ResolveInfo>,
    packageManager: android.content.pm.PackageManager,
    onDismiss: () -> Unit,
    onPick: (ResolveInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open with", maxLines = 1) },
        text = {
            if (apps.isEmpty()) {
                Text("No apps can open this file.")
            } else {
                LazyColumn {
                    items(apps, key = { it.activityInfo.packageName + it.activityInfo.name }) { app ->
                        val label = remember(app) { app.loadLabel(packageManager).toString() }
                        val icon = remember(app) {
                            runCatching { app.loadIcon(packageManager).toBitmap().asImageBitmap() }
                                .getOrNull()
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 8.dp)
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun shareFile(context: Context, file: File): Result<Unit> = runCatching {
    shareFiles(context, listOf(file)).getOrThrow()
}

fun shareFiles(context: Context, files: List<File>): Result<Unit> = runCatching {
    if (files.isEmpty()) error("Nothing to share")
    if (files.size == 1 && files.first().isDirectory) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, files.first().absolutePath)
        context.startActivity(Intent.createChooser(intent, "Share"))
        return@runCatching
    }
    if (files.any { it.isDirectory }) error("Cannot share folders")
    if (files.size == 1) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", files.first())
        val intent = Intent(Intent.ACTION_SEND)
            .setType(context.contentResolver.getType(uri) ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share"))
        return@runCatching
    }
    val uris = ArrayList(files.map {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
    })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("*/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share"))
}

fun copyPath(context: Context, file: File) = copyPaths(context, listOf(file))

fun copyPaths(context: Context, files: List<File>) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(
        ClipData.newPlainText("paths", files.joinToString("\n") { it.absolutePath })
    )
}

suspend fun queryOpenWith(context: Context, file: File, mime: String): List<ResolveInfo> =
    withContext(Dispatchers.IO) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }.getOrNull() ?: return@withContext emptyList()
        val pm = context.packageManager
        fun query(type: String) = pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addCategory(Intent.CATEGORY_DEFAULT),
            0
        )
        val found = query(mime).ifEmpty { query("*/*") }
        found.filter { it.activityInfo.packageName != context.packageName }
    }

fun launchOpenWith(context: Context, file: File, mime: String, app: ResolveInfo): Result<Unit> =
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .setClassName(app.activityInfo.packageName, app.activityInfo.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

@Composable
private fun PropRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
