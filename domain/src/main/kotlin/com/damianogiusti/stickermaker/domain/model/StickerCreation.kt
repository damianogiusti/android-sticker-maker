package com.damianogiusti.stickermaker.domain.model

/** Where the image of a new sticker comes from. */
enum class StickerSource { CAMERA, GALLERY, FILES, TEXT, WHATSAPP_STICKERS }

/** What the editor result is saved as. */
sealed interface StickerTarget {
    data object NewSticker : StickerTarget
    data class ReplaceSticker(val fileName: String) : StickerTarget
    data object Tray : StickerTarget
}
