package com.kerneldroid.karchiver.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFallbackTest {

    @Test
    fun reportStaysActiveUntilAcknowledgedAndIsNotReportedAgain() {
        NativeFallback.report()
        assertTrue(NativeFallback.active.value)

        NativeFallback.report()
        assertTrue(NativeFallback.active.value)

        NativeFallback.acknowledge()
        assertFalse(NativeFallback.active.value)

        NativeFallback.report()
        assertFalse(NativeFallback.active.value)
    }
}
