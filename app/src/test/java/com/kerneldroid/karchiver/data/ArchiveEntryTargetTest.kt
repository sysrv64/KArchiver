package com.kerneldroid.karchiver.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEntryTargetTest {

    private fun root(): File = Files.createTempDirectory("karchiver-entry").toFile()

    @Test
    fun plainNameStaysInsideRoot() {
        val dir = root()
        val target = safeArchiveEntryTarget(dir, "file.txt")
        assertEquals(File(dir.canonicalFile, "file.txt"), target)
    }

    @Test
    fun nestedNameStaysInsideRoot() {
        val dir = root()
        val target = safeArchiveEntryTarget(dir, "a/b/c.txt")
        assertEquals(File(dir.canonicalFile, "a/b/c.txt"), target)
        assertTrue(target.path.startsWith(dir.canonicalFile.path + File.separator))
    }

    @Test
    fun backslashesAreTreatedAsSeparators() {
        val dir = root()
        assertEquals(File(dir.canonicalFile, "a/b.txt"), safeArchiveEntryTarget(dir, "a\\b.txt"))
    }

    @Test
    fun currentDirectoryComponentsAreDropped() {
        val dir = root()
        assertEquals(File(dir.canonicalFile, "a/b.txt"), safeArchiveEntryTarget(dir, "./a/./b.txt"))
    }

    @Test
    fun directoryEntryNameResolvesToTheDirectoryItself() {
        val dir = root()
        assertEquals(File(dir.canonicalFile, "a/b"), safeArchiveEntryTarget(dir, "a/b/"))
    }

    @Test
    fun parentTraversalIsRejected() {
        val dir = root()
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "../escape.txt") }
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "../../etc/passwd") }
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "a/../../b.txt") }
    }

    @Test
    fun traversalIsRejectedEvenWhenItResolvesBackInsideTheRoot() {
        val dir = root()
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "a/b/../c.txt") }
    }

    @Test
    fun absolutePathIsRejected() {
        val dir = root()
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "/etc/passwd") }
    }

    @Test
    fun windowsDriveLetterIsRejected() {
        val dir = root()
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "C:\\evil.txt") }
    }

    @Test
    fun nulByteIsRejected() {
        val dir = root()
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "bad\u0000name") }
    }

    @Test
    fun emptyOrEmptyResolvingNamesAreRejected() {
        val dir = root()
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "") }
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, ".") }
        assertThrows(IllegalArgumentException::class.java) { safeArchiveEntryTarget(dir, "./") }
    }
}
