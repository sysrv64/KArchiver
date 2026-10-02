package com.kerneldroid.karchiver.data

data class CompressionLevelSpec(val min: Int, val max: Int, val default: Int, val zeroMeansStore: Boolean)

enum class CompressionMarker { STORE, FASTEST, BEST, PLAIN }

object CompressionLevels {

    const val NO_LEVEL = 0

    private val specs: Map<CompressFormat, CompressionLevelSpec> = mapOf(
        CompressFormat.ZIP to CompressionLevelSpec(min = 1, max = 9, default = 6, zeroMeansStore = false),
        CompressFormat.SEVEN_Z to CompressionLevelSpec(min = 0, max = 9, default = 5, zeroMeansStore = false),
        CompressFormat.TAR_GZ to CompressionLevelSpec(min = 0, max = 9, default = 6, zeroMeansStore = true),
        CompressFormat.TAR_BZ2 to CompressionLevelSpec(min = 1, max = 9, default = 6, zeroMeansStore = false),
        CompressFormat.TAR_XZ to CompressionLevelSpec(min = 0, max = 9, default = 6, zeroMeansStore = false),
        CompressFormat.TAR_ZST to CompressionLevelSpec(min = 1, max = 22, default = 3, zeroMeansStore = false),
        CompressFormat.RAR to CompressionLevelSpec(min = 0, max = 5, default = 3, zeroMeansStore = true)
    )

    fun spec(format: CompressFormat): CompressionLevelSpec? = specs[format]

    fun supportedFormats(): List<CompressFormat> = CompressFormat.entries.filter { specs.containsKey(it) }

    fun clamp(format: CompressFormat, level: Int): Int {
        val spec = specs[format] ?: return NO_LEVEL
        return level.coerceIn(spec.min, spec.max)
    }

    fun marker(format: CompressFormat, level: Int): CompressionMarker {
        val spec = specs[format] ?: return CompressionMarker.PLAIN
        return markerOf(spec, level)
    }

    fun markerOf(spec: CompressionLevelSpec, level: Int): CompressionMarker {
        val clamped = level.coerceIn(spec.min, spec.max)
        return when {
            spec.zeroMeansStore && clamped == 0 -> CompressionMarker.STORE
            clamped == spec.min && clamped > 0 -> CompressionMarker.FASTEST
            clamped == spec.max && clamped != spec.min -> CompressionMarker.BEST
            else -> CompressionMarker.PLAIN
        }
    }
}

fun AppSettings.compressionLevel(format: CompressFormat): Int = CompressionLevels.clamp(
    format,
    when (format) {
        CompressFormat.ZIP -> zipCompressionLevel
        CompressFormat.SEVEN_Z -> sevenZCompressionLevel
        CompressFormat.TAR -> CompressionLevels.NO_LEVEL
        CompressFormat.TAR_GZ -> tarGzCompressionLevel
        CompressFormat.TAR_BZ2 -> tarBz2CompressionLevel
        CompressFormat.TAR_XZ -> tarXzCompressionLevel
        CompressFormat.TAR_ZST -> tarZstCompressionLevel
        CompressFormat.RAR -> rarCompressionLevel
    }
)
