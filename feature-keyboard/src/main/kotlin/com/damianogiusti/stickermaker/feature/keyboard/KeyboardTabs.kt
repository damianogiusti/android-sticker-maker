package com.damianogiusti.stickermaker.feature.keyboard

import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.StickerPack

/** [version] is the pack's imageDataVersion: part of the image cache key, files can be replaced in place. */
internal data class KeyboardSticker(val packId: String, val fileName: String, val version: Int)

internal sealed interface KeyboardTab {
    val stickers: List<KeyboardSticker>

    data class History(override val stickers: List<KeyboardSticker>) : KeyboardTab

    data class Pack(
        val packId: String,
        val name: String,
        val trayFileName: String?,
        val version: Int,
        override val stickers: List<KeyboardSticker>,
    ) : KeyboardTab
}

/**
 * History first, then one tab per pack available in the keyboard, most recently added first.
 * History entries whose pack or file no longer exists are dropped.
 */
internal fun buildKeyboardTabs(packs: List<StickerPack>, history: List<KeyboardHistoryEntry>): List<KeyboardTab> {
    val stickersByPack = packs.associate { pack ->
        pack.id.value to pack.stickers.associate { it.fileName to KeyboardSticker(pack.id.value, it.fileName, pack.imageDataVersion) }
    }
    val historyStickers = history.mapNotNull { stickersByPack[it.packId.value]?.get(it.fileName) }
    val packTabs = packs
        .filter { it.isAvailableInKeyboard }
        .sortedByDescending { it.keyboardAddedAt ?: Long.MIN_VALUE }
        .map { pack ->
            KeyboardTab.Pack(
                packId = pack.id.value,
                name = pack.name,
                trayFileName = pack.trayFileName,
                version = pack.imageDataVersion,
                stickers = pack.stickers.sortedBy { it.position }
                    .map { KeyboardSticker(pack.id.value, it.fileName, pack.imageDataVersion) },
            )
        }
    return listOf(KeyboardTab.History(historyStickers)) + packTabs
}
