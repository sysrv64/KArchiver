package com.kerneldroid.karchiver.data.archive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kerneldroid.karchiver.MainActivity
import com.kerneldroid.karchiver.R
import com.kerneldroid.karchiver.data.CompressFormat
import com.kerneldroid.karchiver.data.FileSystemRepository
import com.kerneldroid.karchiver.data.RustBridge
import com.kerneldroid.karchiver.data.elevation.RootEngine
import com.kerneldroid.karchiver.data.elevation.ShizukuEngine
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ArchiveService : Service() {

    companion object {
        const val ACTION_START = "com.kerneldroid.karchiver.data.archive.ArchiveService.ACTION_START"
        const val ACTION_CANCEL = "com.kerneldroid.karchiver.data.archive.ArchiveService.ACTION_CANCEL"
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SOURCES = "extra_sources"
        const val EXTRA_ARCHIVE = "extra_archive"
        const val EXTRA_DEST = "extra_dest"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_ELEVATION = "extra_elevation"
        const val EXTRA_ONLY_NAMES = "extra_only_names"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val CHANNEL_ID = "archive_ops"
        const val DONE_CHANNEL_ID = "archive_done"
        const val NOTIFICATION_ID = 1
        private const val STALL_TIMEOUT_MS = 5L * 60L * 1000L
        private const val STALL_MESSAGE = "Operation stalled (no progress for 5 minutes)"

        fun startExtractTask(
            context: Context,
            taskId: Long,
            archive: File,
            destDir: File,
            password: String?,
            elevationMode: String,
            onlyNames: List<String>? = null
        ) {
            val intent = Intent(context, ArchiveService::class.java)
            intent.action = ACTION_START
            intent.putExtra(EXTRA_TASK_ID, taskId)
            intent.putExtra(EXTRA_KIND, OpKind.EXTRACT.name)
            intent.putExtra(EXTRA_LABEL, archive.name)
            intent.putExtra(EXTRA_ARCHIVE, archive.absolutePath)
            intent.putExtra(EXTRA_DEST, destDir.absolutePath)
            intent.putExtra(EXTRA_PASSWORD, password)
            intent.putExtra(EXTRA_ELEVATION, elevationMode)
            if (onlyNames != null) {
                intent.putExtra(EXTRA_ONLY_NAMES, onlyNames.toTypedArray())
            }
            context.startForegroundService(intent)
        }

        fun startCompressTask(
            context: Context,
            taskId: Long,
            sources: List<File>,
            dest: File,
            format: CompressFormat,
            password: String?,
            elevationMode: String
        ) {
            val intent = Intent(context, ArchiveService::class.java)
            intent.action = ACTION_START
            intent.putExtra(EXTRA_TASK_ID, taskId)
            intent.putExtra(EXTRA_KIND, OpKind.COMPRESS.name)
            intent.putExtra(EXTRA_LABEL, dest.name)
            intent.putExtra(EXTRA_SOURCES, sources.map { it.absolutePath }.toTypedArray())
            intent.putExtra(EXTRA_DEST, dest.absolutePath)
            intent.putExtra(EXTRA_FORMAT, format.name)
            intent.putExtra(EXTRA_PASSWORD, password)
            intent.putExtra(EXTRA_ELEVATION, elevationMode)
            context.startForegroundService(intent)
        }

        fun cancelTask(context: Context, taskId: Long) {
            val intent = Intent(context, ArchiveService::class.java)
            intent.action = ACTION_CANCEL
            intent.putExtra(EXTRA_TASK_ID, taskId)
            context.startService(intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, ArchiveService::class.java)
            intent.action = ACTION_CANCEL
            context.startService(intent)
        }

        fun startCompress(
            context: Context,
            sources: List<File>,
            dest: File,
            format: CompressFormat,
            password: String?,
            elevationMode: String
        ) {
            val taskId = TaskManager.create(TaskKind.COMPRESS, dest.name, dest.name)
            startCompressTask(context, taskId, sources, dest, format, password, elevationMode)
        }

        fun startExtract(
            context: Context,
            archive: File,
            destDir: File,
            password: String?,
            elevationMode: String,
            onlyNames: List<String>? = null
        ) {
            val taskId = TaskManager.create(TaskKind.EXTRACT, archive.name, destDir.absolutePath)
            startExtractTask(context, taskId, archive, destDir, password, elevationMode, onlyNames)
        }

        fun cancel(context: Context) {
            cancelAll(context)
        }
    }

    private class StallTracker(var lastChangeElapsed: Long) {
        var lastDone: Long = 0L
        var lastTotal: Long = 0L
    }

    private data class OpSpec(
        val taskId: Long,
        val kind: OpKind,
        val label: String,
        val srcPaths: List<String>?,
        val archivePath: String?,
        val destPath: String,
        val formatName: String?,
        val password: String?,
        val elevationMode: String,
        val onlyNames: List<String>?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val queue = ArrayDeque<Long>()
    private val specs = HashMap<Long, OpSpec>()
    private val jobs = HashMap<Long, Job>()
    private val speedWindows = HashMap<Long, ArrayDeque<Pair<Long, Long>>>()
    private val stallTrackers = HashMap<Long, StallTracker>()
    private val stalledIds = HashSet<Long>()
    private var pollJob: Job? = null
    private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                if (taskId > 0L) {
                    cancelTask(taskId)
                } else {
                    cancelAll()
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val spec = parseSpec(intent)
                if (spec == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                synchronized(lock) {
                    specs[spec.taskId] = spec
                    if (!queue.contains(spec.taskId) && !jobs.containsKey(spec.taskId)) {
                        queue.addLast(spec.taskId)
                    }
                }
                ensureForeground()
                startPolling()
                acquireWakeLock()
                pump()
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun parseSpec(intent: Intent): OpSpec? {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) return null
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: return null
        val kind = try {
            OpKind.valueOf(kindName)
        } catch (_: Exception) {
            return null
        }
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD)
        val elevationMode = intent.getStringExtra(EXTRA_ELEVATION) ?: "off"
        val dest = intent.getStringExtra(EXTRA_DEST) ?: return null
        return when (kind) {
            OpKind.COMPRESS -> {
                val src = intent.getStringArrayExtra(EXTRA_SOURCES)?.toList()
                val formatName = intent.getStringExtra(EXTRA_FORMAT)
                if (src.isNullOrEmpty() || formatName.isNullOrEmpty()) return null
                OpSpec(
                    taskId = taskId,
                    kind = kind,
                    label = label,
                    srcPaths = src,
                    archivePath = null,
                    destPath = dest,
                    formatName = formatName,
                    password = password,
                    elevationMode = elevationMode,
                    onlyNames = null
                )
            }
            OpKind.EXTRACT -> {
                val archive = intent.getStringExtra(EXTRA_ARCHIVE) ?: return null
                val onlyNames = intent.getStringArrayExtra(EXTRA_ONLY_NAMES)?.toList()
                OpSpec(
                    taskId = taskId,
                    kind = kind,
                    label = label,
                    srcPaths = null,
                    archivePath = archive,
                    destPath = dest,
                    formatName = null,
                    password = password,
                    elevationMode = elevationMode,
                    onlyNames = onlyNames
                )
            }
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildAggregateNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        foregroundStarted = true
    }

    private fun pump() {
        synchronized(lock) {
            while (jobs.size < TaskManager.MAX_PARALLEL && queue.isNotEmpty()) {
                val id = queue.removeFirst()
                val spec = specs[id] ?: continue
                launchOpLocked(spec)
            }
        }
        updateAggregateNotification()
        maybeStopIfIdle()
    }

    private fun launchOpLocked(spec: OpSpec) {
        TaskManager.markRunning(spec.taskId)
        val job = scope.launch { runTask(spec) }
        jobs[spec.taskId] = job
    }

    private suspend fun runTask(spec: OpSpec) {
        val id = spec.taskId
        synchronized(lock) {
            stallTrackers[id] = StallTracker(SystemClock.elapsedRealtime())
            speedWindows[id] = ArrayDeque()
        }
        try {
            var outcome: OpOutcome = try {
                executeOp(spec)
            } catch (_: CancellationException) {
                OpOutcome.Cancelled
            } catch (e: Exception) {
                mapFailure(e.message)
            }
            val stalled = synchronized(lock) { stalledIds.remove(id) }
            if (stalled) {
                outcome = OpOutcome.Failed(STALL_MESSAGE)
            }
            finishTask(spec, outcome)
        } finally {
            synchronized(lock) {
                jobs.remove(id)
                specs.remove(id)
                speedWindows.remove(id)
                stallTrackers.remove(id)
                stalledIds.remove(id)
            }
            pump()
        }
    }

    private suspend fun executeOp(spec: OpSpec): OpOutcome {
        val repo = FileSystemRepository()
        repo.tempDir = cacheDir
        val engine = when (spec.elevationMode) {
            "shizuku" -> ShizukuEngine
            "root" -> RootEngine
            else -> null
        }
        val result = when (spec.kind) {
            OpKind.COMPRESS -> {
                val sources = spec.srcPaths.orEmpty().map { File(it) }
                val dest = File(spec.destPath)
                val format = try {
                    CompressFormat.valueOf(spec.formatName ?: CompressFormat.ZIP.name)
                } catch (_: Exception) {
                    CompressFormat.ZIP
                }
                repo.compress(sources, dest, format, spec.password, spec.taskId)
            }
            OpKind.EXTRACT -> {
                repo.extract(
                    File(spec.archivePath ?: ""),
                    File(spec.destPath),
                    spec.password,
                    engine,
                    spec.elevationMode,
                    spec.onlyNames,
                    spec.taskId
                )
            }
        }
        if (result.isSuccess) {
            return OpOutcome.Success
        }
        val error = result.exceptionOrNull()
        return if (error is CancellationException) {
            OpOutcome.Cancelled
        } else {
            mapFailure(error?.message)
        }
    }

    private fun finishTask(spec: OpSpec, outcome: OpOutcome) {
        val wasActive = TaskManager.tasks.value.firstOrNull { it.id == spec.taskId }?.isActive == true
        if (!wasActive) return
        val status = when (outcome) {
            is OpOutcome.Success -> TaskStatus.DONE
            is OpOutcome.Cancelled -> TaskStatus.CANCELLED
            is OpOutcome.Failed -> TaskStatus.FAILED
        }
        val message = (outcome as? OpOutcome.Failed)?.message
        TaskManager.markFinished(spec.taskId, status, message)
        postCompletionNotification(spec, outcome)
    }

    private fun startPolling() {
        synchronized(lock) {
            if (pollJob?.isActive == true) return
            pollJob = scope.launch {
                while (isActive) {
                    delay(250)
                    pollOnce()
                }
            }
        }
    }

    private fun pollOnce() {
        val ids = synchronized(lock) { jobs.keys.toList() }
        if (ids.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        for (id in ids) {
            val progress = readTaskProgress(id)
            val done = progress.first
            val total = progress.second
            val speed = recordSample(id, done)
            val speedText = if (speed != null && speed > 0.0) formatSpeed(speed) else null
            val etaText = formatEta(total, done, speed)
            TaskManager.updateProgress(id, done, total, speedText, etaText)
            checkStall(id, done, total, now)
        }
        updateAggregateNotification()
    }

    private fun checkStall(id: Long, done: Long, total: Long, now: Long) {
        val stalled = synchronized(lock) {
            val tracker = stallTrackers[id] ?: return
            if (done != tracker.lastDone || total != tracker.lastTotal) {
                tracker.lastDone = done
                tracker.lastTotal = total
                tracker.lastChangeElapsed = now
                false
            } else if (now - tracker.lastChangeElapsed >= STALL_TIMEOUT_MS) {
                stalledIds.add(id)
                true
            } else {
                false
            }
        }
        if (stalled) {
            stallTask(id)
        }
    }

    private fun stallTask(id: Long) {
        try {
            if (RustBridge.isLoaded()) {
                RustBridge.cancelTask(id)
            }
        } catch (_: Throwable) {
        }
        synchronized(lock) {
            jobs[id]?.cancel()
        }
    }

    private fun cancelTask(id: Long) {
        var removedQueued = false
        synchronized(lock) {
            if (queue.remove(id)) {
                specs.remove(id)
                removedQueued = true
            }
        }
        if (removedQueued) {
            TaskManager.markFinished(id, TaskStatus.CANCELLED)
            updateAggregateNotification()
            maybeStopIfIdle()
            return
        }
        val job = synchronized(lock) { jobs[id] }
        TaskManager.markFinished(id, TaskStatus.CANCELLED)
        if (job != null) {
            try {
                if (RustBridge.isLoaded()) {
                    RustBridge.cancelTask(id)
                }
            } catch (_: Throwable) {
            }
            job.cancel()
        }
        updateAggregateNotification()
        maybeStopIfIdle()
    }

    private fun cancelAll() {
        val queued: List<Long>
        val running: List<Long>
        synchronized(lock) {
            queued = queue.toList()
            queue.clear()
            running = jobs.keys.toList()
        }
        for (id in queued) {
            TaskManager.markFinished(id, TaskStatus.CANCELLED)
            synchronized(lock) { specs.remove(id) }
        }
        for (id in running) {
            TaskManager.markFinished(id, TaskStatus.CANCELLED)
            try {
                if (RustBridge.isLoaded()) {
                    RustBridge.cancelTask(id)
                }
            } catch (_: Throwable) {
            }
        }
        val runningJobs = synchronized(lock) { jobs.values.toList() }
        for (job in runningJobs) {
            job.cancel()
        }
        updateAggregateNotification()
        maybeStopIfIdle()
    }

    private fun maybeStopIfIdle() {
        synchronized(lock) {
            if (queue.isNotEmpty() || jobs.isNotEmpty()) return
            pollJob?.cancel()
            pollJob = null
            releaseWakeLock()
            if (foregroundStarted) {
                try {
                    ServiceCompat.stopForeground(this, Service.STOP_FOREGROUND_REMOVE)
                } catch (_: Exception) {
                }
                foregroundStarted = false
            }
        }
        stopSelf()
    }

    private fun recordSample(id: Long, doneBytes: Long): Double? {
        val now = SystemClock.elapsedRealtime()
        val window = synchronized(lock) {
            val existing = speedWindows[id] ?: ArrayDeque<Pair<Long, Long>>().also {
                speedWindows[id] = it
            }
            existing.addLast(Pair(now, doneBytes))
            while (existing.size > 5) {
                existing.removeFirst()
            }
            existing.toList()
        }
        if (window.size < 2) {
            return null
        }
        val oldest = window.first()
        val newest = window.last()
        val elapsedMs = newest.first - oldest.first
        if (elapsedMs <= 0L) {
            return null
        }
        val deltaBytes = newest.second - oldest.second
        if (deltaBytes <= 0L) {
            return null
        }
        return deltaBytes.toDouble() / (elapsedMs / 1000.0)
    }

    private fun formatSpeed(speed: Double): String {
        return Formatter.formatShortFileSize(this, speed.toLong()) + "/s"
    }

    private fun formatEta(total: Long, done: Long, speed: Double?): String? {
        if (speed == null || speed <= 0.0) {
            return null
        }
        val remaining = total - done
        if (total <= 0L || remaining <= 0L) {
            return null
        }
        val seconds = remaining / speed
        return when {
            seconds < 60.0 -> "less than a minute left"
            seconds < 3600.0 -> "~" + ((seconds + 59.0).toLong() / 60L) + " min left"
            else -> "~" + ((seconds + 3599.0).toLong() / 3600L) + " hr left"
        }
    }

    private fun mapFailure(message: String?): OpOutcome {
        val raw = message ?: ""
        if (raw.contains("ancell", ignoreCase = true)) {
            return OpOutcome.Cancelled
        }
        if (raw.contains("code=2")) {
            return OpOutcome.Cancelled
        }
        return OpOutcome.Failed(sanitizeMessage(raw))
    }

    private fun sanitizeMessage(message: String): String {
        var out = message.trim().replace('\n', ' ').replace('\r', ' ')
        while (out.contains("  ")) {
            out = out.replace("  ", " ")
        }
        if (out.isBlank()) {
            out = "Operation failed"
        }
        if (out.length > 200) {
            out = out.substring(0, 200)
        }
        return out
    }

    private fun readTaskProgress(taskId: Long): Pair<Long, Long> {
        return try {
            if (!RustBridge.isLoaded()) {
                Pair(0L, 0L)
            } else {
                val result = RustBridge.getTaskProgress(taskId)
                if (result.size < 2) Pair(0L, 0L) else Pair(result[0], result[1])
            }
        } catch (_: Throwable) {
            Pair(0L, 0L)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "karchiver:archive-op")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lock.setReferenceCounted(false)
            }
            lock.acquire()
            wakeLock = lock
        } catch (_: Throwable) {
            wakeLock = null
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        try {
            if (lock.isHeld) {
                lock.release()
            }
        } catch (_: Throwable) {
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel =
                    NotificationChannel(CHANNEL_ID, "Archive operations", NotificationManager.IMPORTANCE_LOW)
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(DONE_CHANNEL_ID) == null) {
                val channel =
                    NotificationChannel(DONE_CHANNEL_ID, "Archive finished", NotificationManager.IMPORTANCE_DEFAULT)
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildAggregateNotification(): Notification {
        val active = TaskManager.tasks.value.filter { it.isActive }
        val count = active.size
        val title = when (count) {
            0 -> "Archive operations"
            1 -> active.first().title
            else -> "$count tasks running"
        }
        val done = active.sumOf { it.done }
        val total = active.sumOf { it.total }
        val indeterminate = total <= 0L
        val text = if (indeterminate) {
            if (done > 0L) {
                Formatter.formatShortFileSize(this, done) + " processed"
            } else {
                "Working"
            }
        } else {
            Formatter.formatShortFileSize(this, done) + " of " + Formatter.formatShortFileSize(this, total)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            if (total > Int.MAX_VALUE.toLong() || done > Int.MAX_VALUE.toLong()) {
                val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0
                builder.setProgress(100, percent, false)
            } else {
                builder.setProgress(total.toInt(), done.coerceAtMost(total).toInt(), false)
            }
        }
        val cancelIntent = Intent(this, ArchiveService::class.java)
        cancelIntent.action = ACTION_CANCEL
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val cancelPending = PendingIntent.getService(this, 2, cancelIntent, pendingFlags)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel all", cancelPending)
        return builder.build()
    }

    private fun updateAggregateNotification() {
        if (!foregroundStarted) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildAggregateNotification())
        } catch (_: Exception) {
        }
    }

    private fun postCompletionNotification(spec: OpSpec, outcome: OpOutcome) {
        ensureChannel()
        val title = TaskManager.tasks.value.firstOrNull { it.id == spec.taskId }?.title
            ?: spec.label
        val text = when (outcome) {
            is OpOutcome.Success -> "Completed successfully"
            is OpOutcome.Cancelled -> "Cancelled"
            is OpOutcome.Failed -> "Failed: " + outcome.message
        }
        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPending = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)
        val notification = NotificationCompat.Builder(this, DONE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .build()
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 1000 + (spec.taskId % 100000L).toInt()
            manager.notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            super.onTimeout(startId, fgsType)
            return
        }
        cancelAll()
    }

    override fun onDestroy() {
        val pending: Set<Long>
        synchronized(lock) {
            pending = HashSet<Long>(queue).apply { addAll(jobs.keys) }
            queue.clear()
        }
        for (id in pending) {
            TaskManager.markFinished(id, TaskStatus.CANCELLED)
            try {
                if (RustBridge.isLoaded()) {
                    RustBridge.cancelTask(id)
                }
            } catch (_: Throwable) {
            }
        }
        val runningJobs = synchronized(lock) {
            val copy = jobs.values.toList()
            jobs.clear()
            specs.clear()
            speedWindows.clear()
            stallTrackers.clear()
            stalledIds.clear()
            pollJob?.cancel()
            pollJob = null
            copy
        }
        for (job in runningJobs) {
            job.cancel()
        }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
