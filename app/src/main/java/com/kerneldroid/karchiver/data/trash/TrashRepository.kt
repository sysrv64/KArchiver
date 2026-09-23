package com.kerneldroid.karchiver.data.trash

import android.content.Context
import com.kerneldroid.karchiver.data.elevation.ElevatedFS
import com.kerneldroid.karchiver.data.storage.loadAppVolumes
import com.kerneldroid.karchiver.data.uniqueName
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class TrashReport(
    val trashed: List<TrashEntry>,
    val failed: List<String>
) {
    val isComplete: Boolean get() = failed.isEmpty()
}

class TrashRepository private constructor(
    private val appContext: Context,
    private val db: TrashDatabase
) {

    private val dao = db.trashDao()

    val entries: Flow<List<TrashEntry>> = dao.observeAll()

    suspend fun trash(files: List<File>, elevated: ElevatedFS? = null): TrashReport = withContext(Dispatchers.IO) {
        val trashed = ArrayList<TrashEntry>()
        val failed = ArrayList<String>()
        val roots = runCatching { loadAppVolumes(appContext).map { it.root } }.getOrDefault(emptyList())
        val fallback = File(appContext.filesDir, "trash")
        for (file in files) {
            val entry = runCatching { trashOne(file, roots, fallback, elevated) }.getOrNull()
            if (entry == null) failed.add(file.name.ifEmpty { file.absolutePath }) else trashed.add(entry)
        }
        TrashReport(trashed, failed)
    }

    private suspend fun trashOne(
        file: File,
        volumeRoots: List<File>,
        fallback: File,
        elevated: ElevatedFS?
    ): TrashEntry {
        if (!file.exists()) error("Not accessible")
        val id = UUID.randomUUID().toString()
        val root = trashRootFor(file, volumeRoots, fallback)
        val entryDir = File(root, id)
        if (!entryDir.exists() && !entryDir.mkdirs()) {
            if (elevated?.mkdirs(entryDir) != true) error("Could not create Trash")
        }
        val marker = File(root, ".nomedia")
        if (!marker.exists()) runCatching { marker.createNewFile() }
        val name = file.name.ifEmpty { id }
        val dest = File(entryDir, name)
        if (!relocate(file, dest, elevated)) {
            runCatching { entryDir.deleteRecursively() }
            error("Could not move to Trash")
        }
        val entry = TrashEntry(
            id = id,
            originalPath = file.absolutePath,
            storedPath = dest.absolutePath,
            name = name,
            isDirectory = dest.isDirectory,
            size = if (dest.isDirectory) 0L else dest.length(),
            deletedAt = System.currentTimeMillis()
        )
        dao.insert(entry)
        return entry
    }

    suspend fun restore(entry: TrashEntry, elevated: ElevatedFS? = null): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val stored = File(entry.storedPath)
            if (!stored.exists()) error("Item is missing")
            val original = File(entry.originalPath)
            val parent = original.parentFile ?: error("Cannot restore to this location")
            if (!parent.exists()) {
                val made = parent.mkdirs() || elevated?.mkdirs(parent) == true
                if (!made && !parent.exists()) error("Cannot create the original folder")
            }
            val dest = if (original.exists()) {
                File(parent, uniqueName(entry.name, namesIn(parent, elevated)))
            } else {
                original
            }
            if (!relocate(stored, dest, elevated)) error("Could not restore")
            if (stored.exists()) error("Could not restore")
            removeEntryDir(entry)
            dao.remove(entry.id)
            dest
        }
    }

    suspend fun deletePermanently(entry: TrashEntry, elevated: ElevatedFS? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stored = File(entry.storedPath)
            if (stored.exists()) {
                val deleted = runCatching {
                    if (stored.isDirectory) stored.deleteRecursively() else stored.delete()
                }.getOrDefault(false)
                if (!deleted && stored.exists()) {
                    if (elevated?.deleteRecursively(listOf(stored)) != true) error("Could not delete")
                }
            }
            removeEntryDir(entry)
            dao.remove(entry.id)
        }
    }

    suspend fun empty(elevated: ElevatedFS? = null): Result<Int> = withContext(Dispatchers.IO) {
        val all = dao.allOnce()
        var deleted = 0
        val failures = ArrayList<String>()
        for (entry in all) {
            deletePermanently(entry, elevated).fold(
                onSuccess = { deleted++ },
                onFailure = { failures.add(entry.name) }
            )
        }
        if (failures.isEmpty()) {
            Result.success(deleted)
        } else {
            Result.failure(Exception("Could not delete: " + failures.joinToString(", ")))
        }
    }

    private suspend fun relocate(src: File, dst: File, elevated: ElevatedFS?): Boolean {
        dst.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && elevated?.mkdirs(parent) != true) return false
        }
        if (src.renameTo(dst)) return true
        if (elevated != null && elevated.rename(src, dst)) return true
        val copied = runCatching {
            if (src.isDirectory) {
                val part = File(dst.parentFile, ".${dst.name}.karchiver-part")
                runCatching { if (part.exists()) part.deleteRecursively() }
                val copiedPart = runCatching { src.copyRecursively(part, overwrite = false) }.getOrDefault(false)
                if (!copiedPart) {
                    runCatching { part.deleteRecursively() }
                    false
                } else if (!part.renameTo(dst)) {
                    runCatching { part.deleteRecursively() }
                    false
                } else {
                    true
                }
            } else {
                transactionalCopy(src, dst)
                true
            }
        }.getOrDefault(false)
        if (!copied) return false
        val removed = runCatching {
            if (src.isDirectory) src.deleteRecursively() else src.delete()
        }.getOrDefault(false)
        if (!removed) {
            runCatching { dst.deleteRecursively() }
            runCatching {
                val part = File(dst.parentFile, ".${dst.name}.karchiver-part")
                if (part.exists()) part.deleteRecursively()
            }
            return false
        }
        return true
    }

    private fun transactionalCopy(src: File, dst: File) {
        val part = File(dst.parentFile, ".${dst.name}.karchiver-part")
        try {
            Files.copy(src.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(part.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            runCatching { if (part.exists()) part.delete() }
        }
    }

    private suspend fun namesIn(parent: File, elevated: ElevatedFS?): Set<String> {
        val listed = parent.list()
        if (listed != null) return listed.toHashSet()
        return elevated?.listFiles(parent)?.mapTo(HashSet()) { it.name } ?: emptySet()
    }

    private suspend fun removeEntryDir(entry: TrashEntry) {
        val dir = File(entry.storedPath).parentFile ?: return
        if (dir.name != entry.id) return
        runCatching { if (dir.list()?.isEmpty() == true) dir.delete() }
    }

    companion object {

        @Volatile
        private var instance: TrashRepository? = null

        fun get(context: Context): TrashRepository =
            instance ?: synchronized(this) {
                instance ?: TrashRepository(context.applicationContext, TrashDatabase.get(context))
                    .also { instance = it }
            }
    }
}
