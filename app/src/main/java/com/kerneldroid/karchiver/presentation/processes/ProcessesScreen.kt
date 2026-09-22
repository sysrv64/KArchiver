@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.kerneldroid.karchiver.presentation.processes

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerneldroid.karchiver.data.archive.ArchiveTask
import com.kerneldroid.karchiver.data.archive.TaskKind
import com.kerneldroid.karchiver.data.archive.TaskManager
import com.kerneldroid.karchiver.data.archive.TaskStatus
import com.kerneldroid.karchiver.presentation.components.RoundedTopScaffold

private fun kindIcon(kind: TaskKind): ImageVector = when (kind) {
    TaskKind.EXTRACT -> Icons.Filled.FolderOpen
    TaskKind.COMPRESS -> Icons.Filled.Archive
}

private fun activeStatusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.RUNNING -> "Running"
    else -> "Queued"
}

private fun finishedStatusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.DONE -> "Done"
    TaskStatus.FAILED -> "Failed"
    else -> "Cancelled"
}

private fun finishedStatusIcon(status: TaskStatus): ImageVector = when (status) {
    TaskStatus.DONE -> Icons.Filled.CheckCircle
    TaskStatus.FAILED -> Icons.Filled.ErrorOutline
    else -> Icons.Filled.Cancel
}

@Composable
fun ProcessesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val tasks by TaskManager.tasks.collectAsStateWithLifecycle()
    val activeCount by TaskManager.activeCount.collectAsStateWithLifecycle()

    val active = tasks.filter { it.isActive }
    val finished = tasks.filter { !it.isActive }

    RoundedTopScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text("Processes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (finished.isNotEmpty()) {
                        IconButton(onClick = TaskManager::clearFinished) {
                            Icon(Icons.Filled.Close, "Clear finished")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (active.isEmpty() && finished.isEmpty()) {
                ProcessesEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    if (active.isNotEmpty()) {
                        item(key = "active-header") {
                            SectionLabel("Active", activeCount)
                        }
                        items(active, key = { "active-${it.id}" }) { task ->
                            ActiveTaskRow(task, context)
                        }
                    }
                    if (finished.isNotEmpty()) {
                        item(key = "finished-header") {
                            SectionLabel("Finished", finished.size)
                        }
                        items(finished, key = { "finished-${it.id}" }) { task ->
                            FinishedTaskRow(task)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveTaskRow(task: ArchiveTask, context: android.content.Context) {
    val statusLabel = activeStatusLabel(task.status)
    val fraction = task.fraction
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = {
            Icon(kindIcon(task.kind), null)
        },
        trailingContent = {
            IconButton(onClick = { TaskManager.cancel(task.id) }) {
                Icon(Icons.Filled.Close, "Cancel")
            }
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (task.subtitle.isNotBlank()) {
                Text(
                    text = task.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                val sizeText = buildString {
                    append(Formatter.formatShortFileSize(context, task.done))
                    append(" of ")
                    append(Formatter.formatShortFileSize(context, task.total))
                    if (!task.speedText.isNullOrBlank()) append(" · ").append(task.speedText)
                    if (!task.etaText.isNullOrBlank()) append(" · ").append(task.etaText)
                }
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FinishedTaskRow(task: ArchiveTask) {
    val statusLabel = finishedStatusLabel(task.status)
    val statusColor = when (task.status) {
        TaskStatus.DONE -> MaterialTheme.colorScheme.primary
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = {
            Icon(finishedStatusIcon(task.status), null, tint = statusColor)
        },
        trailingContent = {
            IconButton(onClick = { TaskManager.remove(task.id) }) {
                Icon(Icons.Filled.Close, "Remove")
            }
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = task.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.status == TaskStatus.FAILED && !task.message.isNullOrBlank()) {
                Text(
                    text = task.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProcessesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No background tasks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Extract or compress an archive to see it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
