package com.kerneldroid.karchiver.data.trash

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashPathsTest {

    private val fallback = File("/data/user/0/app/files/trash")

    @Test
    fun picksVolumeRootContainingFile() {
        val roots = listOf(File("/storage/emulated/0"), File("/storage/ABCD-1234"))
        val result = trashRootFor(File("/storage/emulated/0/DCIM/photo.jpg"), roots, fallback)
        assertEquals(File("/storage/emulated/0/.karchiver-trash").path, result.path)
    }

    @Test
    fun picksLongestMatchingRoot() {
        val roots = listOf(File("/storage"), File("/storage/emulated/0"))
        val result = trashRootFor(File("/storage/emulated/0/Music/song.mp3"), roots, fallback)
        assertEquals("/storage/emulated/0/.karchiver-trash", result.path)
    }

    @Test
    fun fallsBackWhenNoVolumeMatches() {
        val roots = listOf(File("/storage/emulated/0"))
        val result = trashRootFor(File("/data/local/tmp/file.bin"), roots, fallback)
        assertEquals(fallback, result)
    }

    @Test
    fun fileDirectlyUnderRootMatches() {
        val roots = listOf(File("/storage/ABCD-1234"))
        val result = trashRootFor(File("/storage/ABCD-1234"), roots, fallback)
        assertEquals("/storage/ABCD-1234/.karchiver-trash", result.path)
    }

    @Test
    fun similarPrefixIsNotTreatedAsInsideRoot() {
        val roots = listOf(File("/storage/emulated/0"))
        val result = trashRootFor(File("/storage/emulated/0extra/file.txt"), roots, fallback)
        assertEquals(fallback, result)
    }

    @Test
    fun trashDirNameIsHiddenDotFolder() {
        assertTrue(TRASH_DIR_NAME.startsWith("."))
        assertFalse(TRASH_DIR_NAME.contains("/"))
    }
}
