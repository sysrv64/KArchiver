@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.kerneldroid.karchiver.presentation.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kerneldroid.karchiver.data.formatBytes
import com.kerneldroid.karchiver.data.history.relativeTime
import com.kerneldroid.karchiver.data.trash.TrashEntry
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold
import com.kerneldroid.karchiver.presentation.components.detectBarHold
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    elevationMode: String = "off",
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    BackHandler { onBack() }
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val vm: TrashViewModel = viewModel()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TrashEntry?>(null) }

    val now = remember(entries) { System.currentTimeMillis() }
    val totalSize = remember(entries) { entries.sumOf { it.size } }

    fun restore(entry: TrashEntry) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        vm.restore(entry, elevationMode) { result ->
            result.fold(
                onSuccess = { name ->
                    scope.launch {
                        snackbar.showSnackbar(
                            if (name == entry.name) "Restored \"${entry.name}\""
                            else "Restored as \"$name\""
                        )
                    }
                },
                onFailure = { e ->
                    scope.launch { snackbar.showSnackbar(e.message ?: "Could not restore") }
                }
            )
        }
    }

    fun deleteForever(entry: TrashEntry) {
        vm.deleteForever(entry, elevationMode) { result ->
            scope.launch {
                snackbar.showSnackbar(
                    result.fold(
                        onSuccess = { "Deleted permanently" },
                        onFailure = { e -> e.message ?: "Could not delete" }
                    )
                )
            }
        }
    }

    RoundedTopScaffold(
        barLifted = barLifted,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                modifier = Modifier.detectBarHold {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleBar()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { confirmEmpty = true },
                        enabled = entries.isNotEmpty() && !busy
                    ) {
                        Icon(Icons.Filled.DeleteSweep, "Empty Trash")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                TrashEmptyState()
            } else {
                Text(
                    text = buildString {
                        append(if (entries.size == 1) "1 item" else "${entries.size} items")
                        if (totalSize > 0) append(" • ${formatBytes(totalSize)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                        TrashItemRow(
                            entry = entry,
                            index = index,
                            count = entries.size,
                            now = now,
                            missing = missing.contains(entry.id),
                            enabled = !busy,
                            onRestore = { restore(entry) },
                            onDelete = { pendingDelete = entry }
                        )
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = {
                Text(
                    if (entries.size == 1) "Permanently delete 1 item? This cannot be undone."
                    else "Permanently delete all ${entries.size} items? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEmpty = false
                        vm.empty(elevationMode) { result ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    result.fold(
                                        onSuccess = { "Trash emptied" },
                                        onFailure = { e -> e.message ?: "Could not empty Trash" }
                                    )
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("\"${entry.name}\" will be deleted forever. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        deleteForever(entry)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashItemRow(
    entry: TrashEntry,
    index: Int,
    count: Int,
    now: Long,
    missing: Boolean,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = {
            Icon(
                if (entry.isDirectory) Icons.Filled.Folder
                else Icons.AutoMirrored.Filled.InsertDriveFile,
                null
            )
        },
        supportingContent = {
            Text(
                text = entry.originalPath,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRestore, enabled = enabled && !missing) {
                    Icon(Icons.Filled.Restore, "Restore")
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.Filled.Delete,
                        "Delete permanently",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) {
        Column {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = buildString {
                    if (missing) append("Missing • ")
                    if (!entry.isDirectory) append(formatBytes(entry.size)).append(" • ")
                    append(relativeTime(entry.deletedAt, now))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (missing) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrashEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Trash is empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Files you delete will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
