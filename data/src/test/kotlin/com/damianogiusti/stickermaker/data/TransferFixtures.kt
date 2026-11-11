package com.damianogiusti.stickermaker.data

import com.damianogiusti.stickermaker.domain.model.ArchiveSticker
import com.damianogiusti.stickermaker.domain.model.PackArchive
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun le32(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

private fun chunk(fourCc: String, payload: ByteArray): ByteArray {
    val padding = if (payload.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    return fourCc.toByteArray() + le32(payload.size) + payload + padding
}

private fun riff(vararg chunks: ByteArray): ByteArray {
    val body = ByteArrayOutputStream().also { out -> chunks.forEach { out.write(it) } }.toByteArray()
    return "RIFF".toByteArray() + le32(body.size + 4) + "WEBP".toByteArray() + body
}

/** Smallest lossless WebP header the parser reads; [marker] makes two stickers differ. */
fun staticWebp(width: Int = 512, height: Int = 512, marker: Int = 0): ByteArray {
    val bits = (width - 1) or ((height - 1) shl 14)
    return riff(chunk("VP8L", byteArrayOf(0x2F) + le32(bits)), chunk("JUNK", byteArrayOf(marker.toByte())))
}

fun animatedWebp(): ByteArray {
    val vp8x = byteArrayOf(0x02, 0, 0, 0, 0xFF.toByte(), 0x01, 0, 0xFF.toByte(), 0x01, 0)
    return riff(chunk("VP8X", vp8x), chunk("ANIM", ByteArray(6)))
}

fun pngBytes(size: Int = 20): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(size)

fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun anArchive(
    name: String = "Cats",
    publisher: String = "Me",
    stickerCount: Int = 3,
    tray: ByteArray? = pngBytes(),
    emojis: List<String> = emptyList(),
    isAvailableInKeyboard: Boolean = false,
) = PackArchive(
    name = name,
    publisher = publisher,
    tray = tray,
    stickers = List(stickerCount) { ArchiveSticker(staticWebp(marker = it), emojis) },
    isAvailableInKeyboard = isAvailableInKeyboard,
)

/** A static 512x512 WebP of exactly [totalBytes] bytes (an even number of at least 40). */
fun paddedWebp(totalBytes: Int): ByteArray {
    val base = staticWebp()
    // staticWebp has a 1-byte JUNK chunk (8 + 1 + 1 padding); swap it for a bigger one.
    val header = base.copyOf(base.size - 10)
    val junk = ByteArray(totalBytes - header.size - 8)
    val withJunk = header + "JUNK".toByteArray() + le32(junk.size) + junk
    return withJunk.also { riffSizeFix(it) }
}

private fun riffSizeFix(bytes: ByteArray) {
    val size = le32(bytes.size - 8)
    size.copyInto(bytes, destinationOffset = 4)
}
