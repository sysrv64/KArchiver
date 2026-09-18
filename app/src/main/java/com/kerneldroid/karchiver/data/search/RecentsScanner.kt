package com.kerneldroid.karchiver.data.search

import com.kerneldroid.karchiver.data.FileItem
import com.kerneldroid.karchiver.data.trash.TRASH_DIR_NAME
import java.io.File
import java.util.PriorityQueue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class RecentsResult(
    val items: List<FileItem>,
    val scanned: Int,
    val capped: Boolean
)

suspend fun scanRecents(
    roots: List<File>,
    listDir: suspend (File) -> List<FileItem>,
    maxResults: Int = 300,
    maxScanned: Int = 20000,
    timeBudgetMillis: Long = 8000L,
    onProgress: (scanned: Int) -> Unit = {},
    onBatch: (List<FileItem>) -> Unit = {}
): RecentsResult {
    val limit = maxResults.coerceAtLeast(1)
    val heap = PriorityQueue<FileItem>(limit + 1, compareBy { it.lastModified })
    val seen = HashSet<String>()
    val queue = ArrayDeque<File>()
    roots.forEach { root -> if (root.isDirectory) queue.add(root) }

    var scanned = 0
    var capped = false
    var nextEmit = 0L
    val deadline = System.currentTimeMillis() + timeBudgetMillis

    fun snapshot(): List<FileItem> =
        heap.sortedWith(compareByDescending<FileItem> { it.lastModified }.thenBy { it.name.lowercase() })

    while (queue.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        if (scanned >= maxScanned || System.currentTimeMillis() > deadline) {
            capped = true
            break
        }
        val dir = queue.removeFirst()
        val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!seen.add(canonical)) continue
        val children = runCatching { listDir(dir) }.getOrDefault(emptyList())
        scanned++
        for (child in children) {
            if (child.name == TRASH_DIR_NAME) continue
            if (child.isDirectory) {
                queue.add(child.file)
                if (child.lastModified > 0L) {
                    heap.add(child)
                    if (heap.size > limit) heap.poll()
                }
            } else if (child.lastModified > 0L) {
                heap.add(child)
                if (heap.size > limit) heap.poll()
            }
        }
        val now = System.currentTimeMillis()
        if (now >= nextEmit) {
            onProgress(scanned)
            onBatch(snapshot())
            nextEmit = now + 250L
        }
    }

    currentCoroutineContext().ensureActive()
    onProgress(scanned)
    return RecentsResult(items = snapshot(), scanned = scanned, capped = capped)
}
