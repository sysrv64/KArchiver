package com.kerneldroid.karchiver.data

import com.kerneldroid.karchiver.data.elevation.ElevatedEntry
import com.kerneldroid.karchiver.data.elevation.ElevatedFS
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSystemRepositoryElevatedTest {

    private class FakeElevated(private val entries: Map<String, List<ElevatedEntry>>) : ElevatedFS {
        override suspend fun listFiles(dir: File): List<File>? =
            entries[dir.absolutePath]?.map { it.file }

        override suspend fun listDetailed(dir: File): List<ElevatedEntry>? = entries[dir.absolutePath]

        override suspend fun deleteRecursively(targets: List<File>): Boolean = true

        override suspend fun mkdirs(dir: File): Boolean = true

        override suspend fun chmod(path: File, mode: Int): Boolean = true
    }

    @Test
    fun elevatedFirstListingIgnoresLocalContent() {
        val local = Files.createTempDirectory("karchiver-elevated").toFile()
        File(local, "local.txt").writeText("local")
        val engine = FakeElevated(
            mapOf(
                local.absolutePath to listOf(
                    ElevatedEntry(File(local, "system.txt"), false, 10L, 100L, 0x1A4)
                )
            )
        )
        val items = runBlocking {
            FileSystemRepository().listDir(local, elevated = engine, elevatedFirst = true)
        }
        assertEquals(listOf("system.txt"), items.map { it.name })
        assertEquals(10L, items.first().size)
    }

    @Test
    fun defaultListingStillPrefersLocalContent() {
        val local = Files.createTempDirectory("karchiver-local").toFile()
        File(local, "local.txt").writeText("local")
        val engine = FakeElevated(
            mapOf(
                local.absolutePath to listOf(
                    ElevatedEntry(File(local, "system.txt"), false, 10L, 100L, 0x1A4)
                )
            )
        )
        val items = runBlocking {
            FileSystemRepository().listDir(local, elevated = engine, elevatedFirst = false)
        }
        assertEquals(listOf("local.txt"), items.map { it.name })
    }


    @Test
    fun stageForOpenCopiesReadableFile() {
        val local = Files.createTempDirectory("karchiver-stage").toFile()
        val source = File(local, "notes.txt").apply { writeText("hello") }
        val cache = Files.createTempDirectory("karchiver-cache").toFile()
        val repo = FileSystemRepository().apply { tempDir = cache }
        val staged = runBlocking { repo.stageForOpen(source, elevated = null) }.getOrThrow()
        assertTrue(staged.exists())
        assertEquals("hello", staged.readText())
    }
}
