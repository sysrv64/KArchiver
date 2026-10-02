package com.kerneldroid.karchiver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionLevelsTest {

    private val expectedSpecs: List<Pair<CompressFormat, CompressionLevelSpec>> = listOf(
        CompressFormat.ZIP to CompressionLevelSpec(min = 1, max = 9, default = 6, zeroMeansStore = false),
        CompressFormat.SEVEN_Z to CompressionLevelSpec(min = 0, max = 9, default = 5, zeroMeansStore = false),
        CompressFormat.TAR_GZ to CompressionLevelSpec(min = 0, max = 9, default = 6, zeroMeansStore = true),
        CompressFormat.TAR_BZ2 to CompressionLevelSpec(min = 1, max = 9, default = 6, zeroMeansStore = false),
        CompressFormat.TAR_XZ to CompressionLevelSpec(min = 0, max = 9, default = 6, zeroMeansStore = false),
        CompressFormat.TAR_ZST to CompressionLevelSpec(min = 1, max = 22, default = 3, zeroMeansStore = false),
        CompressFormat.RAR to CompressionLevelSpec(min = 0, max = 5, default = 3, zeroMeansStore = true)
    )

    private fun spec(format: CompressFormat) = CompressionLevels.spec(format)!!

    @Test
    fun specsMatchEncoderRanges() {
        expectedSpecs.forEach { (format, expected) ->
            assertEquals(format.name, expected, CompressionLevels.spec(format))
        }
    }

    @Test
    fun everySpecHasMinAtMostDefaultAtMostMax() {
        CompressionLevels.supportedFormats().forEach { format ->
            val s = spec(format)
            assertTrue(format.name, s.min <= s.default)
            assertTrue(format.name, s.default <= s.max)
        }
    }

    @Test
    fun supportedFormatsFollowDeclarationOrderAndSkipTar() {
        assertEquals(expectedSpecs.map { it.first }, CompressionLevels.supportedFormats())
        assertFalse(CompressionLevels.supportedFormats().contains(CompressFormat.TAR))
        assertEquals(7, CompressionLevels.supportedFormats().size)
    }

    @Test
    fun tarHasNoSpecAndClampReturnsSentinel() {
        assertNull(CompressionLevels.spec(CompressFormat.TAR))
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, 0))
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, 6))
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, 99))
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, -5))
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, Int.MIN_VALUE))
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, Int.MAX_VALUE))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.TAR, 6))
    }

    @Test
    fun clampsBelowMinAndAboveMaxForEveryFormat() {
        CompressionLevels.supportedFormats().forEach { format ->
            val s = spec(format)
            assertEquals(format.name, s.min, CompressionLevels.clamp(format, s.min - 1))
            assertEquals(format.name, s.max, CompressionLevels.clamp(format, s.max + 1))
            assertEquals(format.name, s.max, CompressionLevels.clamp(format, 99))
            assertEquals(format.name, s.min, CompressionLevels.clamp(format, -5))
            assertEquals(format.name, s.max, CompressionLevels.clamp(format, Int.MAX_VALUE))
            assertEquals(format.name, s.min, CompressionLevels.clamp(format, Int.MIN_VALUE))
        }
        assertEquals(CompressionLevels.NO_LEVEL, CompressionLevels.clamp(CompressFormat.TAR, 99))
    }

    @Test
    fun clampKeepsValidValuesUnchangedForEveryFormat() {
        CompressionLevels.supportedFormats().forEach { format ->
            val s = spec(format)
            (s.min..s.max).forEach { level ->
                assertEquals(format.name, level, CompressionLevels.clamp(format, level))
            }
        }
    }

    @Test
    fun clampBzip2ZeroBecomesOne() {
        assertEquals(1, CompressionLevels.clamp(CompressFormat.TAR_BZ2, 0))
        assertEquals(1, CompressionLevels.clamp(CompressFormat.TAR_BZ2, -1))
        assertEquals(1, CompressionLevels.clamp(CompressFormat.TAR_BZ2, Int.MIN_VALUE))
        assertTrue(CompressionLevels.clamp(CompressFormat.TAR_BZ2, 0) >= 1)
    }

    @Test
    fun markerStoreOnlyForZeroOnStoreCapableFormats() {
        assertEquals(CompressionMarker.STORE, CompressionLevels.marker(CompressFormat.TAR_GZ, 0))
        assertEquals(CompressionMarker.STORE, CompressionLevels.marker(CompressFormat.RAR, 0))
    }

    @Test
    fun fastestPresetOnSevenZAndXzIsNotStore() {
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.SEVEN_Z, 0))
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.TAR_XZ, 0))
    }

    @Test
    fun zipHasNoLevelZeroSoItIsClampedUpToItsFastestLevel() {
        assertEquals(1, CompressionLevels.spec(CompressFormat.ZIP)!!.min)
        assertEquals(1, CompressionLevels.clamp(CompressFormat.ZIP, 0))
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.ZIP, 0))
    }

    @Test
    fun markerStoreNeverLeaksIntoNonZeroLevels() {
        CompressionLevels.supportedFormats().forEach { format ->
            val s = spec(format)
            (s.min..s.max).forEach { level ->
                if (level == 0 && s.zeroMeansStore) {
                    assertEquals(format.name, CompressionMarker.STORE, CompressionLevels.marker(format, level))
                } else {
                    assertTrue(
                        "$format $level",
                        CompressionLevels.marker(format, level) != CompressionMarker.STORE
                    )
                }
            }
        }
    }

    @Test
    fun markerFastestAtPositiveMin() {
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.TAR_BZ2, 1))
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.TAR_ZST, 1))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.TAR_BZ2, 2))
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.ZIP, 1))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.ZIP, 2))
        assertEquals(
            CompressionMarker.FASTEST,
            CompressionLevels.marker(CompressFormat.RAR, 1)
        )
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.RAR, 2))
        assertEquals(CompressionMarker.FASTEST, CompressionLevels.marker(CompressFormat.TAR_GZ, 1))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.TAR_GZ, 2))
    }

    @Test
    fun markerBestAtMax() {
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.ZIP, 9))
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.SEVEN_Z, 9))
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.TAR_GZ, 9))
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.TAR_BZ2, 9))
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.TAR_XZ, 9))
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.TAR_ZST, 22))
        assertEquals(CompressionMarker.BEST, CompressionLevels.marker(CompressFormat.RAR, 5))
    }

    @Test
    fun markerPlainInBetween() {
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.ZIP, 6))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.SEVEN_Z, 5))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.TAR_ZST, 3))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.marker(CompressFormat.RAR, 3))
    }

    @Test
    fun markerForSingleLevelSpecStaysSensible() {
        val fixed = CompressionLevelSpec(min = 7, max = 7, default = 7, zeroMeansStore = false)
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.markerOf(fixed, 7))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.markerOf(fixed, 99))
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.markerOf(fixed, -1))
        val singleZero = CompressionLevelSpec(min = 0, max = 0, default = 0, zeroMeansStore = true)
        assertEquals(CompressionMarker.STORE, CompressionLevels.markerOf(singleZero, 0))
        assertEquals(CompressionMarker.STORE, CompressionLevels.markerOf(singleZero, 5))
        val singleZeroNoStore = CompressionLevelSpec(min = 0, max = 0, default = 0, zeroMeansStore = false)
        assertEquals(CompressionMarker.PLAIN, CompressionLevels.markerOf(singleZeroNoStore, 0))
    }

    @Test
    fun defaultCompressFormatEnumFallsBackToZipForUnknownName() {
        assertEquals(
            CompressFormat.ZIP,
            AppSettings(defaultCompressFormat = "NOT_A_FORMAT").defaultCompressFormatEnum()
        )
        assertEquals(
            CompressFormat.ZIP,
            AppSettings(defaultCompressFormat = "").defaultCompressFormatEnum()
        )
        assertEquals(
            CompressFormat.ZIP,
            AppSettings().defaultCompressFormatEnum()
        )
        assertEquals(
            CompressFormat.TAR_BZ2,
            AppSettings(defaultCompressFormat = "TAR_BZ2").defaultCompressFormatEnum()
        )
    }

    @Test
    fun appSettingsDefaultsMatchSpecs() {
        val defaults = AppSettings()
        CompressionLevels.supportedFormats().forEach { format ->
            assertEquals(format.name, spec(format).default, defaults.compressionLevel(format))
        }
        assertEquals(CompressionLevels.NO_LEVEL, defaults.compressionLevel(CompressFormat.TAR))
    }

    @Test
    fun appSettingsCompressionLevelClampsStoredValues() {
        val settings = AppSettings(
            zipCompressionLevel = 99,
            sevenZCompressionLevel = -5,
            tarBz2CompressionLevel = 0,
            tarZstCompressionLevel = 30,
            rarCompressionLevel = 2
        )
        assertEquals(9, settings.compressionLevel(CompressFormat.ZIP))
        assertEquals(0, settings.compressionLevel(CompressFormat.SEVEN_Z))
        assertEquals(1, settings.compressionLevel(CompressFormat.TAR_BZ2))
        assertEquals(22, settings.compressionLevel(CompressFormat.TAR_ZST))
        assertEquals(2, settings.compressionLevel(CompressFormat.RAR))
    }

    @Test
    fun appSettingsCompressionLevelReadsMatchingFields() {
        val settings = AppSettings(
            zipCompressionLevel = 1,
            sevenZCompressionLevel = 2,
            tarGzCompressionLevel = 3,
            tarBz2CompressionLevel = 4,
            tarXzCompressionLevel = 7,
            tarZstCompressionLevel = 11,
            rarCompressionLevel = 5
        )
        assertEquals(1, settings.compressionLevel(CompressFormat.ZIP))
        assertEquals(2, settings.compressionLevel(CompressFormat.SEVEN_Z))
        assertEquals(3, settings.compressionLevel(CompressFormat.TAR_GZ))
        assertEquals(4, settings.compressionLevel(CompressFormat.TAR_BZ2))
        assertEquals(7, settings.compressionLevel(CompressFormat.TAR_XZ))
        assertEquals(11, settings.compressionLevel(CompressFormat.TAR_ZST))
        assertEquals(5, settings.compressionLevel(CompressFormat.RAR))
    }
}
