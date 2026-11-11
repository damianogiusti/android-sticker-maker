package com.damianogiusti.stickermaker.navigation

import kotlinx.serialization.Serializable
import com.damianogiusti.stickermaker.domain.model.StickerSource

@Serializable
data object PacksList

@Serializable
data class PackDetail(val packId: String)

@Serializable
data class EmojiPicker(val packId: String, val fileName: String)

/** What the editor result is saved as. [replaceFileName] is set only for [EditorTargetKind.REPLACE]. */
@Serializable
enum class EditorTargetKind { NEW, REPLACE, TRAY }

@Serializable
data object About

@Serializable
data object KeyboardSetup

/** Picks a source image for the editor; then continues to [Editor] with the same target. */
@Serializable
data class StickerGallery(
    val packId: String,
    val targetKind: EditorTargetKind,
    val replaceFileName: String? = null,
)

@Serializable
data class Editor(
    val packId: String,
    val source: StickerSource,
    val targetKind: EditorTargetKind,
    val replaceFileName: String? = null,
    /** Set when the image was already chosen elsewhere (Sticker Gallery); skips the picker. */
    val imageUri: String? = null,
)
