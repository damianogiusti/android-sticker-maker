package com.damianogiusti.stickermaker.domain.model

/** Unix seconds at creation, as in the original app. */
@JvmInline
value class StickerPackId(val value: String)

data class StickerPack(
    val id: StickerPackId,
    val name: String,
    val publisher: String,
    val trayFileName: String?,
    val stickers: List<Sticker>,
    val imageDataVersion: Int,
    val isAvailableInKeyboard: Boolean,
    val keyboardAddedAt: Long?,
    val isImported: Boolean,
    val position: Int,
)

data class Sticker(
    val fileName: String,
    val emojis: List<String>,
    val sizeBytes: Long,
    val position: Int,
)

/** A file to store in a pack directory. [bytes] are already encoded (WebP / PNG). */
class PackFile(
    val packId: StickerPackId,
    val fileName: String,
    val bytes: ByteArray,
)

data class KeyboardHistoryEntry(
    val packId: StickerPackId,
    val fileName: String,
    val usedAt: Long,
)

object StickerPackLimits {
    const val MAX_STICKERS = 30
    const val MIN_STICKERS_TO_SEND = 3
    const val MAX_EMOJIS_PER_STICKER = 3
    const val MAX_TEXT_LENGTH = 128
    const val MAX_KEYBOARD_HISTORY = 100
    const val STICKER_SIDE_PX = 512
    const val STICKER_MAX_BYTES = 100L * 1024
    const val TRAY_SIDE_PX = 96
    const val TRAY_MAX_BYTES = 50L * 1024
}
