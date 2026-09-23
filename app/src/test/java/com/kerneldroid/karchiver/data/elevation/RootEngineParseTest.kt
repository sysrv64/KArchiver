package com.kerneldroid.karchiver.data.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootEngineParseTest {

    @Test
    fun parsesStandaloneMarkedLine() {
        assertEquals(0, parseMarkedExitCode("__KARCHIVER_MARK_abc 0", "__KARCHIVER_MARK_abc"))
    }

    @Test
    fun parsesGluedMarkedLine() {
        assertEquals(
            12,
            parseMarkedExitCode("partial output__KARCHIVER_MARK_abc 12", "__KARCHIVER_MARK_abc")
        )
    }

    @Test
    fun returnsNullWhenMarkAbsent() {
        assertNull(parseMarkedExitCode("plain output line", "__KARCHIVER_MARK_abc"))
    }

    @Test
    fun garbageExitCodeFallsBackToOne() {
        assertEquals(1, parseMarkedExitCode("__KARCHIVER_MARK_abc garbage", "__KARCHIVER_MARK_abc"))
    }
}
