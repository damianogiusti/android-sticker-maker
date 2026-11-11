package com.damianogiusti.stickermaker.feature.gallery

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebpHeaderTest {

    /** RIFF + size + WEBP + VP8X chunk (size 10, flags, reserved, canvas 512x512) [+ ANIM chunk]. */
    private fun vp8x(flags: Int, withAnimChunk: Boolean = false): ByteArray {
        val header = ByteArray(64)
        "RIFF".encodeToByteArray().copyInto(header, 0)
        "WEBP".encodeToByteArray().copyInto(header, 8)
        "VP8X".encodeToByteArray().copyInto(header, 12)
        header[16] = 10
        header[20] = flags.toByte()
        if (withAnimChunk) "ANIM".encodeToByteArray().copyInto(header, 30)
        return header
    }

    @Test
    fun `animation flag marks the file as animated`() {
        assertTrue(WebpHeader.isAnimated(vp8x(flags = 0x12)))
    }

    @Test
    fun `ANIM chunk marks the file as animated even without the flag`() {
        assertTrue(WebpHeader.isAnimated(vp8x(flags = 0x00, withAnimChunk = true)))
    }

    @Test
    fun `extended static webp is not animated`() {
        assertFalse(WebpHeader.isAnimated(vp8x(flags = 0x10)))
    }

    @Test
    fun `simple lossy webp is not animated`() {
        val header = ByteArray(64)
        "RIFF".encodeToByteArray().copyInto(header, 0)
        "WEBP".encodeToByteArray().copyInto(header, 8)
        "VP8 ".encodeToByteArray().copyInto(header, 12)

        assertFalse(WebpHeader.isAnimated(header))
    }

    @Test
    fun `non webp bytes are not animated`() {
        assertFalse(WebpHeader.isAnimated(ByteArray(64) { 0x02 }))
        assertFalse(WebpHeader.isAnimated("PNG".encodeToByteArray()))
        assertFalse(WebpHeader.isAnimated(ByteArray(0)))
    }

    @Test
    fun `truncated header does not crash`() {
        val truncated = vp8x(flags = 0x12).copyOf(18)

        assertFalse(WebpHeader.isAnimated(truncated))
    }
}
