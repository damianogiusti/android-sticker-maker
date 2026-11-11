package com.damianogiusti.stickermaker.domain.clipboard

import arrow.core.Either

/**
 * Image formats the sticker can be put on the clipboard in. WebP is the default; PNG is the fallback
 * if an app pastes WebP badly. Switching is one constant in the editor DI module.
 */
enum class StickerImageFormat(val mimeType: String, val fileExtension: String) {
    WEBP("image/webp", "webp"),
    PNG("image/png", "png"),
}

/** An encoded sticker ready to be shared with other apps. */
class EncodedSticker(val bytes: ByteArray, val format: StickerImageFormat)

sealed interface ClipboardError {
    data class Failed(val message: String?) : ClipboardError
}

/** Port: puts a sticker on the system clipboard as an image other apps can paste. */
interface StickerClipboard {
    suspend fun copy(sticker: EncodedSticker): Either<ClipboardError, Unit>
}
