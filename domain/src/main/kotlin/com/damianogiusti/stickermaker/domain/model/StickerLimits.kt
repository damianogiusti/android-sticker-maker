package com.damianogiusti.stickermaker.domain.model

/** Size limits of the exported sticker, the same ones WhatsApp applies to its stickers. */
object StickerLimits {
    const val SIDE_PX = 512
    const val WEBP_MAX_BYTES = 100L * 1024
}
