package com.kerneldroid.karchiver.data.watch

import android.os.FileObserver
import java.io.File

class DirectoryWatcher(
    private val onChange: () -> Unit
) {

    companion object {
        private const val MASK = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.ATTRIB or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF
    }

    private var observer: FileObserver? = null

    var directory: String? = null
        private set

    val isActive: Boolean get() = observer != null

    fun watch(dir: File): Boolean {
        stop()
        if (!dir.isDirectory) return false
        val path = dir.absolutePath
        val created = try {
            @Suppress("DEPRECATION")
            object : FileObserver(path, MASK) {
                override fun onEvent(event: Int, name: String?) {
                    runCatching { onChange() }
                }
            }
        } catch (_: Throwable) {
            return false
        }
        val started = runCatching { created.startWatching() }.isSuccess
        if (!started) return false
        observer = created
        directory = path
        return true
    }

    fun stop() {
        observer?.let { runCatching { it.stopWatching() } }
        observer = null
        directory = null
    }
}
