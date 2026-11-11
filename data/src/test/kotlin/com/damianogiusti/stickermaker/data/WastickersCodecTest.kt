package com.damianogiusti.stickermaker.data

import arrow.core.Either
import com.damianogiusti.stickermaker.data.transfer.WastickersCodec
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.PackArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WastickersCodecTest {
    private val codec = WastickersCodec()

    private fun read(entries: List<Pair<String, ByteArray>>, fallbackTitle: String? = null) =
        codec.read(ByteArrayInputStream(zipOf(entries)), fallbackTitle)

    private fun Either<TransferError, PackArchive>.right(): PackArchive = when (this) {
        is Either.Right -> value
        is Either.Left -> fail("Expected Right but was Left($value)")
    }

    private fun Either<TransferError, PackArchive>.left(): TransferError = when (this) {
        is Either.Right -> fail("Expected Left but was Right")
        is Either.Left -> value
    }

    @Test
    fun `write then read gives the same pack`() {
        val original = anArchive(name = "Gatti", publisher = "Damiano", stickerCount = 3)
        val out = ByteArrayOutputStream()
        assertTrue(codec.write(original, out) is Either.Right)

        val restored = codec.read(ByteArrayInputStream(out.toByteArray()), null).right()

        assertEquals("Gatti", restored.name)
        assertEquals("Damiano", restored.publisher)
        assertContentEquals(original.tray, restored.tray)
        assertEquals(3, restored.stickers.size)
        original.stickers.zip(restored.stickers).forEach { (a, b) -> assertContentEquals(a.bytes, b.bytes) }
    }

    @Test
    fun `stickers are read in natural order`() {
        val pack = read(
            listOf(
                "10.webp" to staticWebp(marker = 10),
                "2.webp" to staticWebp(marker = 2),
                "1.webp" to staticWebp(marker = 1),
                "tray.png" to pngBytes(),
            ),
        ).right()

        assertEquals(listOf<Byte>(1, 2, 10), pack.stickers.map { it.bytes[it.bytes.size - 2] })
    }

    @Test
    fun `the smallest png is the tray`() {
        val small = pngBytes(5)
        val pack = read(listOf("a.webp" to staticWebp(), "big.png" to pngBytes(500), "small.png" to small)).right()

        assertContentEquals(small, pack.tray)
    }

    @Test
    fun `missing title and author fall back to the file name and Unknown`() {
        val pack = read(listOf("a.webp" to staticWebp()), fallbackTitle = "My cats").right()

        assertEquals("My cats", pack.name)
        assertEquals("Unknown", pack.publisher)
        assertNull(pack.tray)
    }

    @Test
    fun `blank title with no file name falls back to Unknown`() {
        val pack = read(listOf("a.webp" to staticWebp(), "title.txt" to "  \n".toByteArray())).right()

        assertEquals("Unknown", pack.name)
    }

    @Test
    fun `title and author are trimmed`() {
        val pack = read(
            listOf("a.webp" to staticWebp(), "title.txt" to " Dogs\n".toByteArray(), "author.txt" to "Ann ".toByteArray()),
            fallbackTitle = "ignored",
        ).right()

        assertEquals("Dogs", pack.name)
        assertEquals("Ann", pack.publisher)
    }

    @Test
    fun `an animated sticker is rejected with its name`() {
        val error = read(listOf("a.webp" to staticWebp(), "b.webp" to animatedWebp())).left()

        assertEquals(TransferError.AnimatedNotSupported("b.webp"), error)
    }

    @Test
    fun `a sticker that is not 512 by 512 is rejected`() {
        val error = read(listOf("a.webp" to staticWebp(width = 256, height = 512))).left()

        assertEquals(TransferError.WrongStickerSize("a.webp", 256, 512), error)
    }

    @Test
    fun `a webp entry with other bytes is rejected`() {
        assertEquals(TransferError.NotAWebp("a.webp"), read(listOf("a.webp" to pngBytes())).left())
    }

    @Test
    fun `more than 30 stickers is rejected`() {
        val entries = List(31) { "$it.webp" to staticWebp(marker = it) }

        assertEquals(TransferError.TooManyStickers, read(entries).left())
    }

    @Test
    fun `no stickers is rejected`() {
        assertEquals(TransferError.NoStickers, read(listOf("title.txt" to "x".toByteArray())).left())
    }

    @Test
    fun `bytes that are not a zip are rejected`() {
        val result = codec.read(ByteArrayInputStream("hello, definitely not a zip".toByteArray()), null)

        assertEquals(TransferError.NotAZip, result.left())
    }

    @Test
    fun `an empty stream is not a zip`() {
        assertIs<TransferError.NotAZip>(codec.read(ByteArrayInputStream(ByteArray(0)), null).left())
    }

    @Test
    fun `zip slip entries are rejected`() {
        val names = listOf("../evil.webp", "/abs/evil.webp", "dir/../../evil.webp", "C:\\evil.webp", "..\\evil.webp")
        names.forEach { name ->
            val error = read(listOf("a.webp" to staticWebp(), name to staticWebp())).left()
            assertEquals(TransferError.UnsafeEntry(name), error, name)
        }
    }

    @Test
    fun `a file name that only contains two dots is not unsafe`() {
        read(listOf("a..b.webp" to staticWebp())).right()
    }

    @Test
    fun `more than 20 MB inflated is rejected even if the zip is tiny`() {
        val bomb = zipOf(listOf("a.webp" to staticWebp(), "zeros.bin" to ByteArray(21 * 1024 * 1024)))

        assertEquals(TransferError.TooLarge, codec.read(ByteArrayInputStream(bomb), null).left())
    }

    @Test
    fun `too many entries are rejected`() {
        val entries = List(201) { "junk$it.txt" to byteArrayOf(1) } + ("a.webp" to staticWebp())

        assertEquals(TransferError.TooLarge, read(entries).left())
    }
}
