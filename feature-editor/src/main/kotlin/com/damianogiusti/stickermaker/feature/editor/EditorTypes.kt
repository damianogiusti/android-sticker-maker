package com.damianogiusti.stickermaker.feature.editor

import android.net.Uri

/** Where the picture comes from. The editor launches the matching picker itself. */
sealed interface EditorSource {
    data object Camera : EditorSource
    data object Gallery : EditorSource
    data object Files : EditorSource

    /** Text-only sticker: skips cutting. */
    data object Text : EditorSource
    data class ImageUri(val uri: Uri) : EditorSource
}

/** What happens to the result when the user saves. */
sealed interface EditorTarget {
    data object NewSticker : EditorTarget
    data class ReplaceSticker(val fileName: String) : EditorTarget
    data object Tray : EditorTarget
}
