@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.kerneldroid.karchiver.presentation.recents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kerneldroid.karchiver.R
import com.kerneldroid.karchiver.data.FileItem
import com.kerneldroid.karchiver.data.FormatRegistry
import com.kerneldroid.karchiver.data.formatBytes
import com.kerneldroid.karchiver.data.history.relativeTime
import com.kerneldroid.karchiver.presentation.browser.BrowserViewModel
import com.kerneldroid.karchiver.presentation.components.CreateFabMenu
import com.kerneldroid.karchiver.presentation.components.CreateKind
import com.kerneldroid.karchiver.presentation.components.CreateNameDialog
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold
import com.kerneldroid.karchiver.presentation.components.detectBarHold
import kotlinx.coroutines.launch

@Composable
fun RecentsScreen(
    vm: BrowserViewModel,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    barLifted: Boolean = false,
    onToggleBar: () -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val items by vm.recents.collectAsStateWithLifecycle()
    val scanning by vm.recentsScanning.collectAsStateWithLifecycle()
    val scanned by vm.recentsScanned.collectAsStateWithLifecycle()
    val capped by vm.recentsCapped.collectAsStateWithLifecycle()
    var fabExpanded by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }

    LaunchedEffect(Unit) { vm.loadRecents() }

    val now = remember(items) { System.currentTimeMillis() }

    fun open(item: FileItem) {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        when {
            item.isDirectory -> {
                vm.navigateTo(item.file)
                onOpenBrowser()
            }
            FormatRegistry.isArchive(item.extension) -> {
                val parent = item.file.parentFile
                if (parent != null) {
                    vm.navigateTo(parent)
                    onOpenBrowser()
                }
            }
            else -> vm.openFile(context, item.file)
        }
    }

    RoundedTopScaffold(
        barLifted = barLifted,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            CreateFabMenu(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onCreateFolder = { createKind = CreateKind.FOLDER },
                onCreateFile = { createKind = CreateKind.FILE }
            )
        },
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
                title = { Text(stringResource(R.string.library_recents_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.library_desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshRecents() }, enabled = !scanning) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.library_desc_rescan))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                val scanningText = if (items.isEmpty()) stringResource(R.string.library_recents_scanning_storage)
                else context.resources.getQuantityString(R.plurals.library_recents_scanning_folders, scanned, scanned)
                Text(
                    text = scanningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            } else if (items.isNotEmpty()) {
                val countText = if (capped) context.resources.getQuantityString(R.plurals.library_recents_newest_items, items.size, items.size)
                else context.resources.getQuantityString(R.plurals.library_recents_count, items.size, items.size)
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            if (items.isEmpty() && !scanning) {
                RecentsEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    itemsIndexed(items, key = { _, item -> item.file.absolutePath }) { index, item ->
                        RecentsRow(
                            item = item,
                            index = index,
                            count = items.size,
                            now = now,
                            onClick = { open(item) }
                        )
                    }
                }
            }
        }
    }

    createKind?.let { kind ->
        CreateNameDialog(
            kind = kind,
            onConfirm = { name ->
                createKind = null
                if (kind == CreateKind.FOLDER) {
                    vm.createFolder(name) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    if (r.isSuccess) R.string.browser_folder_created else R.string.browser_create_folder_failed
                                )
                            )
                        }
                    }
                } else {
                    vm.createFile(name) { r ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    if (r.isSuccess) R.string.browser_file_created else R.string.browser_create_file_failed
                                )
                            )
                        }
                    }
                }
            },
            onDismiss = { createKind = null }
        )
    }
}

@Composable
private fun RecentsRow(
    item: FileItem,
    index: Int,
    count: Int,
    now: Long,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val folderLabel = stringResource(R.string.library_recents_folder)
    val fileLabel = stringResource(R.string.library_recents_file)
    val timeLabel = relativeTime(context, item.lastModified, now)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = {
            Icon(
                if (item.isDirectory) Icons.Filled.Folder
                else Icons.AutoMirrored.Filled.InsertDriveFile,
                null
            )
        },
        supportingContent = {
            Text(
                text = item.file.parent ?: item.file.absolutePath,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    ) {
        Column {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = buildString {
                    append(if (item.isDirectory) folderLabel else item.extension.uppercase().ifEmpty { fileLabel })
                    if (!item.isDirectory) append(" • ").append(formatBytes(item.size))
                    append(" • ").append(timeLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.library_recents_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.library_recents_empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
