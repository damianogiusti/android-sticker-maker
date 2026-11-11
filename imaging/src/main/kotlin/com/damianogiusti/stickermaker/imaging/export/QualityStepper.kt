package com.damianogiusti.stickermaker.imaging.export

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.damianogiusti.stickermaker.imaging.ExportError

/** Encodes at a high quality and steps down until the result fits the size limit. */
object QualityStepper {
    const val START_QUALITY = 80
    const val QUALITY_STEP = 10
    const val MIN_QUALITY = 30

    fun encodeWithinLimit(maxBytes: Long, encode: (quality: Int) -> ByteArray): Either<ExportError, ByteArray> {
        var quality = START_QUALITY
        var lastSize = 0L
        while (quality >= MIN_QUALITY) {
            val bytes = encode(quality)
            if (bytes.size <= maxBytes) return bytes.right()
            lastSize = bytes.size.toLong()
            quality -= QUALITY_STEP
        }
        return ExportError.StickerTooLarge(lastSize, maxBytes).left()
    }
}
