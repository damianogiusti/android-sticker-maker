package com.damianogiusti.stickermaker.feature.editor

import android.net.Uri

/** Where the picture comes from. */
sealed interface EditorSource {
    /** The editor opens the Photo Picker itself. */
    data object PhotoPicker : EditorSource

    /** The image was already chosen (shared from another app, or picked on Home). */
    data class ImageUri(val uri: Uri) : EditorSource
}
