package com.damianogiusti.stickermaker.data

import com.damianogiusti.stickermaker.data.inspect.ImageHeaderParser
import com.damianogiusti.stickermaker.domain.model.ImageFormat
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageHeaderParserTest {
    private val parser = ImageHeaderParser()

    private fun le32(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun chunk(fourCc: String, payload: ByteArray): ByteArray {
        val padding = if (payload.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        return fourCc.toByteArray() + le32(payload.size) + payload + padding
    }

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { out -> chunks.forEach { out.write(it) } }.toByteArray()
        return "RIFF".toByteArray() + le32(body.size + 4) + "WEBP".toByteArray() + body
    }

    @Test
    fun `static lossy webp is not animated`() {
        val bytes = webp(chunk("VP8 ", ByteArray(11)))
        assertEquals(ImageFormat.WEBP, parser.formatOf(bytes))
        assertFalse(parser.isAnimatedWebp(bytes))
    }

    @Test
    fun `ANIM chunk marks the webp as animated`() {
        val bytes = webp(chunk("VP8X", ByteArray(10)), chunk("ANIM", ByteArray(6)), chunk("ANMF", ByteArray(16)))
        assertTrue(parser.isAnimatedWebp(bytes))
    }

    @Test
    fun `VP8X animation flag marks the webp as animated`() {
        val flags = ByteArray(10).also { it[0] = 0x02 }
        assertTrue(parser.isAnimatedWebp(webp(chunk("VP8X", flags))))
    }

    @Test
    fun `static VP8X webp with odd sized chunks is not animated`() {
        val bytes = webp(chunk("VP8X", ByteArray(10)), chunk("ICCP", ByteArray(5)), chunk("VP8 ", ByteArray(7)))
        assertFalse(parser.isAnimatedWebp(bytes))
    }

    @Test
    fun `png and garbage are recognised`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        assertEquals(ImageFormat.PNG, parser.formatOf(png))
        assertEquals(ImageFormat.UNKNOWN, parser.formatOf(ByteArray(3)))
        assertFalse(parser.isAnimatedWebp(png))
    }
}

class ImageHeaderParserDimensionsTest {
    private val parser = ImageHeaderParser()

    @Test
    fun `reads dimensions from a lossless webp`() {
        assertEquals(512 to 512, parser.webpDimensions(staticWebp(512, 512)))
        assertEquals(100 to 37, parser.webpDimensions(staticWebp(100, 37)))
    }

    @Test
    fun `reads dimensions from an extended webp`() {
        assertEquals(512 to 512, parser.webpDimensions(animatedWebp()))
    }

    @Test
    fun `reads dimensions from a lossy webp`() {
        val payload = byteArrayOf(0, 0, 0, 0x9D.toByte(), 0x01, 0x2A, 0x00, 0x02, 0x00, 0x02)
        val chunk = "VP8 ".toByteArray() + byteArrayOf(10, 0, 0, 0) + payload
        val bytes = "RIFF".toByteArray() + byteArrayOf((chunk.size + 4).toByte(), 0, 0, 0) + "WEBP".toByteArray() + chunk

        assertEquals(512 to 512, parser.webpDimensions(bytes))
    }

    @Test
    fun `non webp has no dimensions`() {
        assertEquals(null, parser.webpDimensions(pngBytes()))
    }
}
