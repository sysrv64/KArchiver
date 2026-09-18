package com.kerneldroid.karchiver.data.trash

import java.io.File

const val TRASH_DIR_NAME = ".karchiver-trash"

fun trashRootFor(path: File, volumeRoots: List<File>, fallback: File): File {
    val canonical = canonicalPathOf(path)
    var best: File? = null
    var bestLength = -1
    for (root in volumeRoots) {
        val rootPath = canonicalPathOf(root)
        if (rootPath.isEmpty()) continue
        val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
        val matches = canonical == rootPath || canonical.startsWith(prefix)
        if (matches && rootPath.length > bestLength) {
            best = root
            bestLength = rootPath.length
        }
    }
    val base = best ?: return fallback
    return File(base, TRASH_DIR_NAME)
}

private fun canonicalPathOf(file: File): String =
    try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }
