package com.kerneldroid.karchiver.data.elevation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsLongParserTest {

    private val dir = File("/data/data")

    @Test
    fun parsesRegularFile() {
        val output = "-rw-r--r-- 1 root root 12450 2026-09-18 12:34 notes.txt\n"
        val entries = parseLsLong(dir, output, now = 0L)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("notes.txt", entry.file.name)
        assertFalse(entry.isDirectory)
        assertEquals(12450L, entry.size)
        assertEquals(0x1A4, entry.mode)
    }

    @Test
    fun parsesDirectoryIgnoringItsSize() {
        val output = "drwxr-xr-x 2 system system 4096 2026-09-18 12:34 files\n"
        val entry = parseLsLong(dir, output, now = 0L).single()
        assertTrue(entry.isDirectory)
        assertEquals(0L, entry.size)
        assertEquals(0x1ED, entry.mode)
    }

    @Test
    fun stripsSymlinkTargetAndKeepsName() {
        val output = "lrwxrwxrwx 1 root root 21 2026-09-18 12:34 sdcard -> /storage/self/primary\n"
        val entry = parseLsLong(dir, output, now = 0L).single()
        assertEquals("sdcard", entry.file.name)
        assertFalse(entry.isDirectory)
    }

    @Test
    fun keepsSpacesInName() {
        val output = "-rw-r--r-- 1 root root 5 2026-09-18 12:34 my notes.txt\n"
        val entry = parseLsLong(dir, output, now = 0L).single()
        assertEquals("my notes.txt", entry.file.name)
    }

    @Test
    fun skipsTotalAndDotEntries() {
        val output = "total 32\n" +
            "drwxr-xr-x 2 root root 4096 2026-09-18 12:34 .\n" +
            "drwxr-xr-x 2 root root 4096 2026-09-18 12:34 ..\n" +
            "-rw-r--r-- 1 root root 1 2026-09-18 12:34 only.txt\n"
        val entries = parseLsLong(dir, output, now = 0L)
        assertEquals(listOf("only.txt"), entries.map { it.file.name })
    }

    @Test
    fun parsesOldDateFormatWithYearAdjustment() {
        val output = "-rw-r--r-- 1 root root 5 Sep 18 12:34 old.txt\n"
        val now = 1_700_000_000_000L
        val entry = parseLsLong(dir, output, now = now).single()
        assertTrue(entry.modified > 0L)
    }

    @Test
    fun setuidBitIsPreserved() {
        assertEquals(0x9ED, permissionsToMode("-rwsr-xr-x"))
        assertEquals(0x9ED, permissionsToMode("-rwsr-xr-x."))
    }

    @Test
    fun unparsableLinesAreSkipped() {
        assertTrue(parseLsLong(dir, "garbage\n\n", now = 0L).isEmpty())
    }
}
