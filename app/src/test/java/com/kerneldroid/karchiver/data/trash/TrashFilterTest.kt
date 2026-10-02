package com.kerneldroid.karchiver.data.trash

import com.kerneldroid.karchiver.data.search.parseSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashFilterTest {

    private val now = 1_750_000_000_000L
    private val hour = 3_600_000L
    private val day = 86_400_000L

    private fun entry(
        path: String,
        name: String = path.substringAfterLast('/'),
        isDirectory: Boolean = true,
        size: Long = 0L,
        age: Long = 0L
    ) = TrashEntry(
        id = path,
        originalPath = "/sdcard/$path",
        storedPath = "/storage/emulated/0/.Trash/$path",
        name = name,
        isDirectory = isDirectory,
        size = size,
        deletedAt = now - age
    )

    private fun filter(entries: List<TrashEntry>, query: String = "") =
        filterTrash(entries, parseSearchQuery(query, now))

    @Test
    fun emptyQueryReturnsAllEntriesUnchanged() {
        val first = entry("/Documents", isDirectory = true)
        val second = entry("/notes.txt", isDirectory = false, size = 12)
        val third = entry("/report.pdf", isDirectory = false, size = 2_048)
        val entries = listOf(first, second, third)

        val result = filter(entries)

        assertSame(entries, result)
        assertEquals(entries, result)
    }

    @Test
    fun filtersByNameToken() {
        val report = entry("/report.pdf", isDirectory = false, size = 2_048)
        val notes = entry("/notes.txt", isDirectory = false, size = 12)

        assertEquals(listOf(report), filter(listOf(report, notes), query = "n:report"))
        assertTrue(filter(listOf(report, notes), query = "n:missing").isEmpty())
    }

    @Test
    fun filtersByDerivedExtension() {
        val pdf = entry("/report.pdf", isDirectory = false, size = 2_048)
        val zip = entry("/bundle.zip", isDirectory = false, size = 4_096)
        val noExt = entry("/noext", isDirectory = false, size = 32)
        val folder = entry("/Documents.pdf", isDirectory = true)
        val entries = listOf(pdf, zip, noExt, folder)

        assertEquals("pdf", pdf.trashExtension())
        assertEquals("", noExt.trashExtension())

        assertEquals(listOf(pdf), filter(entries, query = "ext:pdf"))
        assertEquals(listOf(zip), filter(entries, query = "format:ZIP"))
        assertTrue(filter(entries, query = "ext:noext").isEmpty())
    }

    @Test
    fun filtersByTypeToken() {
        val folder = entry("/Documents", isDirectory = true)
        val file = entry("/notes.txt", isDirectory = false, size = 12)
        val entries = listOf(folder, file)

        assertEquals(listOf(folder), filter(entries, query = "type:dir"))
        assertEquals(listOf(file), filter(entries, query = "type:file"))
        assertEquals(listOf(folder), filter(entries, query = "is:folder"))
        assertEquals(entries, filter(entries, query = ""))
    }

    @Test
    fun filtersBySizeToken() {
        val small = entry("/small.txt", isDirectory = false, size = 1_000)
        val big = entry("/big.bin", isDirectory = false, size = 5_000_000)
        val folder = entry("/Big", isDirectory = true)
        val entries = listOf(small, big, folder)

        assertEquals(listOf(big, folder), filter(entries, query = "size:>1MB"))
        assertEquals(listOf(small, folder), filter(entries, query = "size:<1MB"))
    }

    @Test
    fun filtersByDeletedAtDate() {
        val recent = entry("/recent", age = hour)
        val old = entry("/old", age = 30 * day)
        val entries = listOf(recent, old)

        assertEquals(entries, filter(entries, query = "date:>=2020-01-01"))
        assertEquals(entries, filter(entries, query = "date:<2026-01-01"))
        assertTrue(filter(entries, query = "date:<2019-01-01").isEmpty())
    }

    @Test
    fun handlesDottedNames() {
        val archive = entry("/archive.tar.gz", isDirectory = false, size = 900)
        val dottedFolder = entry("/my.photos", isDirectory = true)
        val entries = listOf(archive, dottedFolder)

        assertEquals("gz", archive.trashExtension())
        assertEquals("photos", dottedFolder.trashExtension())
        assertEquals(listOf(archive), filter(entries, query = "ext:gz"))
        assertEquals(listOf(archive), filter(entries, query = "n:tar.gz"))
        assertTrue(filter(entries, query = "ext:photos").isEmpty())
    }

    @Test
    fun handlesHiddenNames() {
        val hidden = entry("/.config", isDirectory = false, size = 10)
        val visible = entry("/config.txt", isDirectory = false, size = 10)
        val entries = listOf(hidden, visible)

        assertEquals("config", hidden.trashExtension())
        assertEquals(listOf(hidden), filter(entries, query = "ext:config"))
        assertEquals(listOf(hidden), filter(entries, query = "n:.config"))
        assertEquals(listOf(visible), filter(entries, query = "ext:txt"))
    }

    @Test
    fun preservesOriginalOrder() {
        val a = entry("/aaa.txt", isDirectory = false, size = 10)
        val b = entry("/bbb.txt", isDirectory = false, size = 20)
        val c = entry("/ccc.txt", isDirectory = false, size = 30)

        assertEquals(listOf(c, a, b), filter(listOf(c, a, b), query = "ext:txt"))
    }
}