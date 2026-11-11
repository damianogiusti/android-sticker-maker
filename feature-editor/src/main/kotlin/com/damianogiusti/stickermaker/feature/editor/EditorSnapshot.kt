package com.damianogiusti.stickermaker.feature.editor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.damianogiusti.stickermaker.imaging.text.StickerFont
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem
import com.damianogiusti.stickermaker.imaging.text.TextDrawStyle

/** The steps of the editor that can be restored after process death. */
enum class EditorStep { CUTTING, TEXT }

@Serializable
data class TextItemSnapshot(
    val id: Long,
    val text: String,
    val style: TextDrawStyle,
    val font: StickerFont,
    val colorIndex: Int,
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotationDegrees: Float,
) {
    fun toItem() = StickerTextItem(id, text, style, font, colorIndex, centerX, centerY, scale, rotationDegrees)

    companion object {
        fun of(item: StickerTextItem) = TextItemSnapshot(
            item.id, item.text, item.style, item.font, item.colorIndex,
            item.centerX, item.centerY, item.scale, item.rotationDegrees,
        )
    }
}

/**
 * What the text screen needs besides the images (those live in files). Stored as one JSON string in the
 * `SavedStateHandle`, which only accepts Bundle-friendly values.
 */
@Serializable
data class TextSnapshot(
    val hasImage: Boolean,
    val isSelectAll: Boolean,
    val outlineWidthPermille: Int,
    val outlineColorIndex: Int,
    val items: List<TextItemSnapshot>,
    val selectedId: Long?,
    val style: TextDrawStyle,
    val font: StickerFont,
    val colorIndex: Int,
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun of(state: TextState) = TextSnapshot(
            hasImage = state.hasImage,
            isSelectAll = state.isSelectAll,
            outlineWidthPermille = state.outline.widthPermille,
            outlineColorIndex = state.outlineColorIndex,
            items = state.items.map(TextItemSnapshot::of),
            selectedId = state.selectedId,
            style = state.style,
            font = state.font,
            colorIndex = state.colorIndex,
        )

        /** Null when the stored text is missing or unreadable. */
        fun decode(json: String?): TextSnapshot? =
            json?.let { arrow.core.Either.catch { Json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}
