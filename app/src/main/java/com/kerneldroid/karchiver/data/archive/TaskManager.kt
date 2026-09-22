package com.kerneldroid.karchiver.data.archive

import android.content.Context
import com.kerneldroid.karchiver.data.CompressFormat
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TaskKind { EXTRACT, COMPRESS }

enum class TaskStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class ArchiveTask(
    val id: Long,
    val kind: TaskKind,
    val title: String,
    val subtitle: String,
    val status: TaskStatus,
    val done: Long,
    val total: Long,
    val createdAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val speedText: String? = null,
    val etaText: String? = null,
    val message: String? = null,
) {
    val isActive: Boolean get() = status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
    val fraction: Float? get() = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
}

object TaskManager {
    const val MAX_PARALLEL = 3

    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val byId = LinkedHashMap<Long, ArchiveTask>()
    private val completions = ConcurrentHashMap<Long, CompletableDeferred<ArchiveTask>>()

    private val _tasks = MutableStateFlow<List<ArchiveTask>>(emptyList())
    val tasks: StateFlow<List<ArchiveTask>> = _tasks.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        if (appContext == null) {
            synchronized(lock) {
                if (appContext == null) {
                    appContext = context.applicationContext
                }
            }
        }
    }

    fun startExtract(
        context: Context,
        archive: File,
        destDir: File,
        password: String?,
        elevationMode: String,
        onlyNames: List<String>? = null,
        title: String? = null
    ): Long {
        attach(context)
        val id = create(TaskKind.EXTRACT, title ?: archive.name, destDir.absolutePath)
        ArchiveService.startExtractTask(context, id, archive, destDir, password, elevationMode, onlyNames)
        return id
    }

    fun startCompress(
        context: Context,
        sources: List<File>,
        dest: File,
        format: CompressFormat,
        password: String?,
        elevationMode: String,
        title: String? = null
    ): Long {
        attach(context)
        val id = create(TaskKind.COMPRESS, title ?: dest.name, dest.name)
        ArchiveService.startCompressTask(context, id, sources, dest, format, password, elevationMode)
        return id
    }

    fun cancel(id: Long) {
        val context = appContext
        if (context != null) {
            try {
                ArchiveService.cancelTask(context, id)
            } catch (_: Throwable) {
                markFinished(id, TaskStatus.CANCELLED)
            }
        } else {
            markFinished(id, TaskStatus.CANCELLED)
        }
    }

    fun cancelAll() {
        val context = appContext
        if (context != null) {
            try {
                ArchiveService.cancelAll(context)
            } catch (_: Throwable) {
                cancelLocally()
            }
        } else {
            cancelLocally()
        }
    }

    fun remove(id: Long) {
        synchronized(lock) {
            val task = byId[id] ?: return
            if (task.isActive) return
            byId.remove(id)
            publishLocked()
        }
        completions.remove(id)
    }

    fun clearFinished() {
        val removed: List<Long>
        synchronized(lock) {
            removed = byId.values.filter { !it.isActive }.map { it.id }
            byId.values.removeAll { !it.isActive }
            publishLocked()
        }
        removed.forEach { completions.remove(it) }
    }

    fun create(kind: TaskKind, title: String, subtitle: String): Long {
        val id = nextId.incrementAndGet()
        synchronized(lock) {
            val now = System.currentTimeMillis()
            byId[id] = ArchiveTask(
                id = id,
                kind = kind,
                title = title,
                subtitle = subtitle,
                status = TaskStatus.QUEUED,
                done = 0L,
                total = 0L,
                createdAtMillis = now
            )
            completions[id] = CompletableDeferred()
            publishLocked()
        }
        return id
    }

    fun markRunning(id: Long) {
        update(id) { task ->
            if (task.status != TaskStatus.QUEUED) task
            else task.copy(status = TaskStatus.RUNNING)
        }
    }

    fun updateProgress(
        id: Long,
        done: Long,
        total: Long,
        speedText: String? = null,
        etaText: String? = null
    ) {
        update(id) { task ->
            if (task.status != TaskStatus.RUNNING) task
            else task.copy(done = done, total = total, speedText = speedText, etaText = etaText)
        }
    }

    fun markFinished(id: Long, status: TaskStatus, message: String? = null) {
        if (status == TaskStatus.QUEUED || status == TaskStatus.RUNNING) return
        update(id) { task ->
            if (!task.isActive) task
            else task.copy(
                status = status,
                message = message,
                finishedAtMillis = System.currentTimeMillis()
            )
        }
        val finished = synchronized(lock) { byId[id] }
        if (finished != null && !finished.isActive) {
            completions[id]?.complete(finished)
        }
    }

    suspend fun await(id: Long): ArchiveTask {
        completions[id]?.let { return it.await() }
        return synchronized(lock) { byId[id] } ?: error("Unknown task $id")
    }

    internal fun orderTasks(list: List<ArchiveTask>): List<ArchiveTask> {
        val running = list
            .filter { it.status == TaskStatus.RUNNING }
            .sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
        val queued = list
            .filter { it.status == TaskStatus.QUEUED }
            .sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
        val finished = list
            .filter { !it.isActive }
            .sortedWith(
                compareByDescending<ArchiveTask> { it.finishedAtMillis ?: it.createdAtMillis }
                    .thenByDescending { it.id }
            )
        return running + queued + finished
    }

    private fun cancelLocally() {
        val finished: List<ArchiveTask>
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val activeIds = byId.values.filter { it.isActive }.map { it.id }
            for (id in activeIds) {
                val task = byId[id] ?: continue
                byId[id] = task.copy(
                    status = TaskStatus.CANCELLED,
                    finishedAtMillis = now
                )
            }
            publishLocked()
            finished = activeIds.mapNotNull { byId[it] }
        }
        finished.forEach { completions[it.id]?.complete(it) }
    }

    private inline fun update(id: Long, transform: (ArchiveTask) -> ArchiveTask) {
        synchronized(lock) {
            val task = byId[id] ?: return
            val updated = transform(task)
            if (updated != task) {
                byId[id] = updated
                publishLocked()
            }
        }
    }

    private fun publishLocked() {
        val ordered = orderTasks(byId.values.toList())
        _tasks.value = ordered
        _activeCount.value = ordered.count { it.isActive }
    }
}
