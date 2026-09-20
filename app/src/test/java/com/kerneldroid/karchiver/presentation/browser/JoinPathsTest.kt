package com.kerneldroid.karchiver.presentation.browser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class JoinPathsTest {

    @Test
    fun plainPathsAreNotQuoted() {
        val files = listOf(File("/sdcard/Download/a.txt"), File("/sdcard/Download/b c.txt"))
        assertEquals("/sdcard/Download/a.txt\n/sdcard/Download/b c.txt", joinPaths(files, quote = false))
    }

    @Test
    fun quotedPathsWrapEachPath() {
        val files = listOf(File("/sdcard/Download/a.txt"), File("/sdcard/Download/b c.txt"))
        assertEquals(
            "'/sdcard/Download/a.txt'\n'/sdcard/Download/b c.txt'",
            joinPaths(files, quote = true)
        )
    }

    @Test
    fun singlePathQuoted() {
        assertEquals("'/data/local/tmp/x'", joinPaths(listOf(File("/data/local/tmp/x")), quote = true))
    }

    @Test
    fun emptyListYieldsEmptyString() {
        assertEquals("", joinPaths(emptyList(), quote = true))
        assertEquals("", joinPaths(emptyList(), quote = false))
    }
}
