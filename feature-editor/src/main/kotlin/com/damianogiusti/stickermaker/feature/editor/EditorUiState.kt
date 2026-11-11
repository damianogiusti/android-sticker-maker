package com.damianogiusti.stickermaker.feature.editor

import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import com.damianogiusti.stickermaker.imaging.outline.OutlineSettings
import com.damianogiusti.stickermaker.imaging.text.StickerFont
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem
import com.damianogiusti.stickermaker.imaging.text.TextDrawStyle

enum class CutTool { FREEHAND, SQUARE, CIRCLE, POLYGON }

/** Zoom and pan of the cutting view: [centerX]/[centerY] in image pixels, [scale] in screen pixels per image pixel. */
data class CutViewport(val scale: Float, val centerX: Float, val centerY: Float)

enum class CuttingBusy { NONE, MAGIC, CUTTING, ROTATING }

enum class CuttingMessage { NO_SUBJECT_FOUND, MAGIC_FAILED, CUT_FAILED, EMPTY_SELECTION, ROTATE_FAILED, OUTLINE_FAILED }

data class CuttingState(
    val image: EditorImage,
    val tool: CutTool = CutTool.FREEHAND,
    val polygonVertices: List<CutPoint> = emptyList(),
    val viewport: CutViewport? = null,
    val busy: CuttingBusy = CuttingBusy.NONE,
    val message: CuttingMessage? = null,
) {
    val isBusy: Boolean get() = busy != CuttingBusy.NONE
    val canUndo: Boolean get() = tool == CutTool.POLYGON && polygonVertices.isNotEmpty()
}

/** The text being typed. [itemId] is null for a new text. */
data class TextEditing(val itemId: Long?, val text: String)

enum class SaveFailure { TOO_LARGE, TOO_MANY_STICKERS, NOTHING_TO_SAVE, STORAGE }

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Saving : SaveStatus
    data class Failed(val reason: SaveFailure) : SaveStatus
}

data class TextState(
    /** The 1024 cutout before the outline; null for a text-only sticker. */
    val cutout: EditorImage?,
    /** The cutout with its outline, what the canvas shows. */
    val outlinedCutout: EditorImage?,
    val isSelectAll: Boolean,
    val outline: OutlineSettings,
    val outlineColorIndex: Int,
    val items: List<StickerTextItem> = emptyList(),
    val selectedId: Long? = null,
    val editing: TextEditing? = null,
    val style: TextDrawStyle = TextDrawStyle.WITH_BACKGROUND,
    val font: StickerFont = StickerFont.ANTON,
    val colorIndex: Int = 0,
    val isOutlineDialogVisible: Boolean = false,
    val isOutlineRendering: Boolean = false,
    val outlineFailed: Boolean = false,
    val save: SaveStatus = SaveStatus.Idle,
) {
    val hasImage: Boolean get() = cutout != null
    val canSave: Boolean get() = hasImage || items.isNotEmpty()
    val selected: StickerTextItem? get() = items.firstOrNull { it.id == selectedId }
    val isBusy: Boolean get() = isOutlineRendering || save == SaveStatus.Saving
}

/** [WORK_LOST]: the app was killed and the saved images could not be read back. */
enum class EditorFailure { IMAGE_UNREADABLE, NO_PICKER_AVAILABLE, WORK_LOST }

sealed interface EditorUiState {
    /** The system picker is open. */
    data object WaitingForPicker : EditorUiState
    data object LoadingImage : EditorUiState
    data class Failed(val reason: EditorFailure) : EditorUiState
    data class Cutting(val state: CuttingState) : EditorUiState
    data class Text(val state: TextState) : EditorUiState
}

sealed interface EditorEvent {
    data object Done : EditorEvent
    data object Cancelled : EditorEvent

    /** Ask the screen to open the source picker (again). */
    data object LaunchPicker : EditorEvent
}
