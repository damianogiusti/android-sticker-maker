package com.damianogiusti.stickermaker.feature.keyboard

/** How a sticker is handed to the editor, from the most to the least specific. */
internal enum class StickerMimeType(val value: String) {
    WEBP_STICKER("image/webp.wasticker"),
    WEBP("image/webp"),
    PNG("image/png"),
}

/**
 * Picks the richest format the target editor accepts, or null when it accepts no image.
 *
 * The WhatsApp-style type is only used on an exact match: an image wildcard says nothing about
 * the app understanding a sticker-flagged WebP. Plain WebP and PNG also match the image
 * wildcard and the full wildcard.
 */
internal fun selectStickerMimeType(acceptedMimeTypes: List<String>): StickerMimeType? {
    if (acceptedMimeTypes.any { it.equals(StickerMimeType.WEBP_STICKER.value, ignoreCase = true) }) {
        return StickerMimeType.WEBP_STICKER
    }
    return listOf(StickerMimeType.WEBP, StickerMimeType.PNG).firstOrNull { candidate ->
        acceptedMimeTypes.any { accepted -> accepts(accepted, candidate.value) }
    }
}

private fun accepts(accepted: String, mimeType: String): Boolean {
    val pattern = accepted.trim().lowercase()
    return when {
        pattern == mimeType -> true
        pattern == "*/*" || pattern == "*" -> true
        pattern.endsWith("/*") -> mimeType.startsWith(pattern.removeSuffix("*"))
        else -> false
    }
}
