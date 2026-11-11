package com.damianogiusti.stickermaker.feature.gallery

/** Number of leading bytes needed by [WebpHeader.isAnimated]. */
internal const val WEBP_HEADER_BYTES = 64

internal object WebpHeader {
    private const val VP8X_OFFSET = 12
    private const val VP8X_FLAGS_OFFSET = 20
    private const val ANIM_CHUNK_OFFSET = 30
    private const val ANIMATION_FLAG = 0x02

    /**
     * True when the leading bytes of a WebP file describe an animation: the VP8X animation flag
     * is set, or an `ANIM` chunk follows the VP8X chunk. Anything that is not a RIFF/WEBP file
     * is reported as not animated.
     */
    fun isAnimated(header: ByteArray): Boolean {
        if (!header.hasAscii(0, "RIFF") || !header.hasAscii(8, "WEBP")) return false
        if (!header.hasAscii(VP8X_OFFSET, "VP8X")) return false
        val flagsAreSet = header.size > VP8X_FLAGS_OFFSET && (header[VP8X_FLAGS_OFFSET].toInt() and ANIMATION_FLAG) != 0
        return flagsAreSet || header.hasAscii(ANIM_CHUNK_OFFSET, "ANIM")
    }

    private fun ByteArray.hasAscii(offset: Int, text: String): Boolean {
        if (size < offset + text.length) return false
        return text.indices.all { this[offset + it] == text[it].code.toByte() }
    }
}
