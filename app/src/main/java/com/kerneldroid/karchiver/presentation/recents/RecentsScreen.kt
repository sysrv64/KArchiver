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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerneldroid.karchiver.data.FileItem
import com.kerneldroid.karchiver.data.FormatRegistry
import com.kerneldroid.karchiver.data.formatBytes
import com.kerneldroid.karchiver.data.history.relativeTime
import com.kerneldroid.karchiver.presentation.browser.BrowserViewModel
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold
import com.kerneldroid.karchiver.presentation.components.detectBarHold

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
    val items by vm.recents.collectAsStateWithLifecycle()
    val scanning by vm.recentsScanning.collectAsStateWithLifecycle()
    val scanned by vm.recentsScanned.collectAsStateWithLifecycle()
    val capped by vm.recentsCapped.collectAsStateWithLifecycle()

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
                title = { Text("Recents") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshRecents() }, enabled = !scanning) {
                        Icon(Icons.Filled.Refresh, "Rescan")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (items.isEmpty()) "Scanning storage…" else "Scanning… $scanned folders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            } else if (items.isNotEmpty()) {
                Text(
                    text = if (capped) "Newest ${items.size} items" else "${items.size} recent items",
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
}

@Composable
private fun RecentsRow(
    item: FileItem,
    index: Int,
    count: Int,
    now: Long,
    onClick: () -> Unit
) {
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
                    append(if (item.isDirectory) "Folder" else item.extension.uppercase().ifEmpty { "File" })
                    if (!item.isDirectory) append(" • ").append(formatBytes(item.size))
                    append(" • ").append(relativeTime(item.lastModified, now))
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
            text = "Nothing recent",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Files and folders you change will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
