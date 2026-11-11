package com.damianogiusti.stickermaker.data.transfer

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.damianogiusti.stickermaker.data.inspect.ImageHeaderParser
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.ImageFormat
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits

/** A sticker taken from a file must be a static WebP of exactly 512x512 (SPEC section 7). */
internal class StickerImageValidator(private val headerParser: ImageHeaderParser) {

    fun validate(fileName: String, bytes: ByteArray): Either<TransferError, Unit> {
        if (headerParser.formatOf(bytes) != ImageFormat.WEBP) return TransferError.NotAWebp(fileName).left()
        if (headerParser.isAnimatedWebp(bytes)) return TransferError.AnimatedNotSupported(fileName).left()
        val (width, height) = headerParser.webpDimensions(bytes) ?: return TransferError.NotAWebp(fileName).left()
        val side = StickerPackLimits.STICKER_SIDE_PX
        if (width != side || height != side) return TransferError.WrongStickerSize(fileName, width, height).left()
        return Unit.right()
    }
}
