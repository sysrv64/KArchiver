package com.kerneldroid.karchiver.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NameWithoutArchiveExtensionTest {

    @Test
    fun stripsCompoundSuffix() {
        assertEquals("archive", nameWithoutArchiveExtension("archive.tar.gz"))
        assertEquals("backup", nameWithoutArchiveExtension("backup.tar.bz2"))
        assertEquals("logs", nameWithoutArchiveExtension("logs.tar.xz"))
        assertEquals("data", nameWithoutArchiveExtension("data.tar.zst"))
    }

    @Test
    fun stripsSingleKnownSuffix() {
        assertEquals("archive", nameWithoutArchiveExtension("archive.zip"))
        assertEquals("archive", nameWithoutArchiveExtension("archive.7z"))
        assertEquals("archive", nameWithoutArchiveExtension("archive.rar"))
        assertEquals("archive", nameWithoutArchiveExtension("archive.tar"))
        assertEquals("archive", nameWithoutArchiveExtension("archive.gz"))
        assertEquals("archive", nameWithoutArchiveExtension("archive.tgz"))
    }

    @Test
    fun preservesOriginalCase() {
        assertEquals("Archive", nameWithoutArchiveExtension("Archive.ZIP"))
        assertEquals("Archive", nameWithoutArchiveExtension("Archive.TAR.GZ"))
    }

    @Test
    fun fallsBackToLastDotForUnknownSuffix() {
        assertEquals("notes", nameWithoutArchiveExtension("notes.txt"))
        assertEquals("a.b", nameWithoutArchiveExtension("a.b.c"))
    }

    @Test
    fun returnsNameWhenNoExtension() {
        assertEquals("noext", nameWithoutArchiveExtension("noext"))
    }

    @Test
    fun keepsDotfilesIntact() {
        assertEquals(".bashrc", nameWithoutArchiveExtension(".bashrc"))
        assertEquals(".zip", nameWithoutArchiveExtension(".zip"))
        assertEquals(".config", nameWithoutArchiveExtension(".config"))
    }
}
