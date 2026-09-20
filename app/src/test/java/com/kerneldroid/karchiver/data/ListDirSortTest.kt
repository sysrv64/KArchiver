package com.kerneldroid.karchiver.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ListDirSortTest {

    private fun makeDir(): File {
        val dir = Files.createTempDirectory("karchiver-sort").toFile()
        File(dir, "a").mkdirs()
        File(dir, "z").mkdirs()
        File(dir, "b.txt").writeText("b")
        File(dir, "y.txt").writeText("y")
        return dir
    }

    @Test
    fun foldersFirstAscending() = runBlocking {
        val items = FileSystemRepository().listDir(makeDir(), SortBy.NAME, ascending = true, foldersFirst = true)
        assertEquals(listOf("a", "z", "b.txt", "y.txt"), items.map { it.name })
    }

    @Test
    fun foldersStayFirstWhenDescending() = runBlocking {
        val items = FileSystemRepository().listDir(makeDir(), SortBy.NAME, ascending = false, foldersFirst = true)
        assertEquals(listOf("z", "a", "y.txt", "b.txt"), items.map { it.name })
    }

    @Test
    fun noFoldersFirstKeepsPureOrder() = runBlocking {
        val items = FileSystemRepository().listDir(makeDir(), SortBy.NAME, ascending = true, foldersFirst = false)
        assertEquals(listOf("a", "b.txt", "y.txt", "z"), items.map { it.name })
    }
}
