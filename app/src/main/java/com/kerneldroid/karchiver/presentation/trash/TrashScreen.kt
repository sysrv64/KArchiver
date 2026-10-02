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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kerneldroid.karchiver.R
import com.kerneldroid.karchiver.data.formatBytes
import com.kerneldroid.karchiver.data.history.relativeTime
import com.kerneldroid.karchiver.data.trash.TrashEntry
import com.kerneldroid.karchiver.presentation.browser.BrowserViewModel
import com.kerneldroid.karchiver.presentation.components.CreateFabMenu
import com.kerneldroid.karchiver.presentation.components.CreateKind
import com.kerneldroid.karchiver.presentation.components.CreateNameDialog
import com.kerneldroid.karchiver.presentation.components.FileSearchField
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold
import com.kerneldroid.karchiver.presentation.components.detectBarHold
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    vm: BrowserViewModel,
    onBack: () -> Unit,
    elevationMode: String = "off",
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val trashVm: TrashViewModel = viewModel()
    val entries by trashVm.entries.collectAsStateWithLifecycle()
    val missing by trashVm.missing.collectAsStateWithLifecycle()
    val busy by trashVm.busy.collectAsStateWithLifecycle()
    val hasAny by trashVm.hasAnyEntries.collectAsStateWithLifecycle()
    val query by trashVm.queryText.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TrashEntry?>(null) }

    val now = remember(entries) { System.currentTimeMillis() }
    val totalSize = remember(entries) { entries.sumOf { it.size } }

    BackHandler {
        if (searchActive) {
            searchActive = false
            trashVm.setQuery("")
        } else {
            onBack()
        }
    }

    fun restore(entry: TrashEntry) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        trashVm.restore(entry, elevationMode) { result ->
            result.fold(
                onSuccess = { name ->
                    val message = if (name == entry.name) context.resources.getString(R.string.library_trash_restored, entry.name)
                    else context.resources.getString(R.string.library_trash_restored_as, name)
                    scope.launch {
                        snackbar.showSnackbar(message)
                    }
                },
                onFailure = { e ->
                    val message = e.message ?: context.resources.getString(R.string.library_trash_restore_failed)
                    scope.launch { snackbar.showSnackbar(message) }
                }
            )
        }
    }

    fun deleteForever(entry: TrashEntry) {
        trashVm.deleteForever(entry, elevationMode) { result ->
            val message = result.fold(
                onSuccess = { context.resources.getString(R.string.library_trash_deleted) },
                onFailure = { e -> e.message ?: context.resources.getString(R.string.library_trash_delete_failed) }
            )
            scope.launch {
                snackbar.showSnackbar(message)
            }
        }
    }

    RoundedTopScaffold(
        barLifted = barLifted,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!searchActive) {
                CreateFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    onCreateFolder = { createKind = CreateKind.FOLDER },
                    onCreateFile = { createKind = CreateKind.FILE }
                )
            }
        },
        topBar = {
            Column {
                if (searchActive) {
                    FileSearchField(
                        query = query,
                        onQueryChange = trashVm::setQuery,
                        onClose = {
                            searchActive = false
                            trashVm.setQuery("")
                        },
                        placeholder = stringResource(R.string.library_trash_search_hint)
                    )
                } else {
                    TopAppBar(
                        modifier = Modifier.detectBarHold {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleBar()
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        title = { Text(stringResource(R.string.library_trash_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.library_desc_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Filled.Search, stringResource(R.string.library_trash_search_hint))
                            }
                            IconButton(
                                onClick = { confirmEmpty = true },
                                enabled = entries.isNotEmpty() && !busy
                            ) {
                                Icon(Icons.Filled.DeleteSweep, stringResource(R.string.library_desc_empty_trash))
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                TrashEmptyState(hasAny = hasAny)
            } else {
                val summaryText = buildString {
                    append(context.resources.getQuantityString(R.plurals.library_trash_count, entries.size, entries.size))
                    if (totalSize > 0) append(" • ").append(formatBytes(totalSize))
                }
                Text(
                    text = summaryText,
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
        val emptyText = context.resources.getQuantityString(R.plurals.library_trash_empty_text, entries.size, entries.size)
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.library_trash_empty_title)) },
            text = {
                Text(emptyText)
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEmpty = false
                        trashVm.empty(elevationMode) { result ->
                            val message = result.fold(
                                onSuccess = { context.resources.getString(R.string.library_trash_emptied) },
                                onFailure = { e -> e.message ?: context.resources.getString(R.string.library_trash_empty_failed) }
                            )
                            scope.launch {
                                snackbar.showSnackbar(message)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.library_trash_empty_confirm_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.library_action_cancel)) }
            }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.library_trash_delete_title)) },
            text = { Text(stringResource(R.string.library_trash_delete_text, entry.name)) },
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
                    Text(stringResource(R.string.library_trash_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.library_action_cancel)) }
            }
        )
    }

    createKind?.let { kind ->
        CreateNameDialog(
            kind = kind,
            onConfirm = { name ->
                createKind = null
                if (kind == CreateKind.FOLDER) {
                    vm.createFolder(name) { result ->
                        val message = context.resources.getString(
                            if (result.isSuccess) R.string.browser_folder_created else R.string.browser_create_folder_failed
                        )
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                } else {
                    vm.createFile(name) { result ->
                        val message = context.resources.getString(
                            if (result.isSuccess) R.string.browser_file_created else R.string.browser_create_file_failed
                        )
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                }
            },
            onDismiss = { createKind = null }
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
    val context = LocalContext.current
    val timeLabel = relativeTime(context, entry.deletedAt, now)
    val missingLabel = stringResource(R.string.library_trash_missing)
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
                    Icon(Icons.Filled.Restore, stringResource(R.string.library_desc_restore))
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.Filled.Delete,
                        stringResource(R.string.library_desc_delete_forever),
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
                    if (missing) append(missingLabel).append(" • ")
                    if (!entry.isDirectory) append(formatBytes(entry.size)).append(" • ")
                    append(timeLabel)
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
private fun TrashEmptyState(hasAny: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasAny) stringResource(R.string.library_trash_empty_filtered_title)
            else stringResource(R.string.library_trash_empty_state_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (hasAny) stringResource(R.string.library_trash_empty_filtered_sub)
            else stringResource(R.string.library_trash_empty_state_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
