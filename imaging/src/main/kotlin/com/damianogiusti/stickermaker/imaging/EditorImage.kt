package com.damianogiusti.stickermaker.imaging

import android.graphics.Bitmap

/**
 * An opaque image handle. Callers above the imaging layer (ViewModels and their tests) only need
 * the size, so they stay free of `android.graphics`. The Android implementation is [BitmapEditorImage].
 */
interface EditorImage {
    val width: Int
    val height: Int
}

class BitmapEditorImage(val bitmap: Bitmap) : EditorImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}

/** The pixels behind this handle. Only the imaging implementations and the UI draw it. */
fun EditorImage.asBitmap(): Bitmap =
    requireNotNull((this as? BitmapEditorImage)?.bitmap) { "Not a bitmap-backed image: ${this::class.java.name}" }

fun Bitmap.asEditorImage(): EditorImage = BitmapEditorImage(this)
