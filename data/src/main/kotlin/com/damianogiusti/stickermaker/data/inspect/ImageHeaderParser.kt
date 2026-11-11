package com.damianogiusti.stickermaker.data.inspect

import com.damianogiusti.stickermaker.domain.model.ImageFormat

/** Pure byte-level sniffing, so it is testable on the JVM without BitmapFactory. */
class ImageHeaderParser {

    fun formatOf(bytes: ByteArray): ImageFormat = when {
        isWebp(bytes) -> ImageFormat.WEBP
        bytes.startsWith(PNG_SIGNATURE) -> ImageFormat.PNG
        else -> ImageFormat.UNKNOWN
    }

    /** True when the RIFF container holds an animation: VP8X animation flag, or an ANIM / ANMF chunk. */
    fun isAnimatedWebp(bytes: ByteArray): Boolean {
        if (!isWebp(bytes)) return false
        var offset = RIFF_HEADER_SIZE
        while (offset + CHUNK_HEADER_SIZE <= bytes.size) {
            val fourCc = String(bytes, offset, FOUR_CC_SIZE, Charsets.US_ASCII)
            if (fourCc == "ANIM" || fourCc == "ANMF") return true
            val payloadStart = offset + CHUNK_HEADER_SIZE
            if (fourCc == "VP8X" && payloadStart < bytes.size && (bytes[payloadStart].toInt() and ANIMATION_FLAG) != 0) {
                return true
            }
            val size = readLittleEndianInt(bytes, offset + FOUR_CC_SIZE)
            if (size < 0) return false
            offset = payloadStart + size + (size and 1)
        }
        return false
    }

    /**
     * Pixel size of a WebP, read from the first VP8 / VP8L / VP8X chunk, or null when the header is
     * unreadable. Lets the zip codecs check 512x512 without BitmapFactory.
     */
    fun webpDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (!isWebp(bytes)) return null
        var offset = RIFF_HEADER_SIZE
        while (offset + CHUNK_HEADER_SIZE <= bytes.size) {
            val fourCc = String(bytes, offset, FOUR_CC_SIZE, Charsets.US_ASCII)
            val payload = offset + CHUNK_HEADER_SIZE
            dimensionsOfChunk(bytes, fourCc, payload)?.let { return it }
            val size = readLittleEndianInt(bytes, offset + FOUR_CC_SIZE)
            if (size < 0) return null
            offset = payload + size + (size and 1)
        }
        return null
    }

    private fun dimensionsOfChunk(bytes: ByteArray, fourCc: String, payload: Int): Pair<Int, Int>? = when (fourCc) {
        "VP8 " -> if (payload + VP8_HEADER_SIZE <= bytes.size && bytes[payload + 3] == VP8_START_0 &&
            bytes[payload + 4] == VP8_START_1 && bytes[payload + 5] == VP8_START_2
        ) {
            (u16(bytes, payload + 6) and VP8_DIMENSION_MASK) to (u16(bytes, payload + 8) and VP8_DIMENSION_MASK)
        } else {
            null
        }
        "VP8L" -> if (payload + VP8L_HEADER_SIZE <= bytes.size && bytes[payload] == VP8L_SIGNATURE) {
            val bits = readLittleEndianInt(bytes, payload + 1)
            ((bits and VP8L_DIMENSION_MASK) + 1) to (((bits ushr VP8L_DIMENSION_BITS) and VP8L_DIMENSION_MASK) + 1)
        } else {
            null
        }
        "VP8X" -> if (payload + VP8X_HEADER_SIZE <= bytes.size) {
            (u24(bytes, payload + 4) + 1) to (u24(bytes, payload + 7) + 1)
        } else {
            null
        }
        else -> null
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u24(bytes: ByteArray, offset: Int): Int = u16(bytes, offset) or ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= RIFF_HEADER_SIZE &&
            bytes.startsWith("RIFF".toByteArray(Charsets.US_ASCII)) &&
            String(bytes, FOUR_CC_SIZE + 4, FOUR_CC_SIZE, Charsets.US_ASCII) == "WEBP"

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private companion object {
        const val FOUR_CC_SIZE = 4
        const val CHUNK_HEADER_SIZE = 8
        const val RIFF_HEADER_SIZE = 12
        const val ANIMATION_FLAG = 0x02
        const val VP8_HEADER_SIZE = 10
        const val VP8L_HEADER_SIZE = 5
        const val VP8X_HEADER_SIZE = 10
        const val VP8_DIMENSION_MASK = 0x3FFF
        const val VP8L_DIMENSION_MASK = 0x3FFF
        const val VP8L_DIMENSION_BITS = 14
        const val VP8L_SIGNATURE: Byte = 0x2F
        const val VP8_START_0: Byte = 0x9D.toByte()
        const val VP8_START_1: Byte = 0x01
        const val VP8_START_2: Byte = 0x2A
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
