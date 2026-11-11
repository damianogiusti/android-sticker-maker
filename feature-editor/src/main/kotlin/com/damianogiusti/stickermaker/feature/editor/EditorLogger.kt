package com.damianogiusti.stickermaker.feature.editor

import android.util.Log

/** Keeps `android.util.Log` out of the ViewModel, so its tests run on the plain JVM. */
fun interface EditorLogger {
    fun warn(tag: String, message: String)
}

class AndroidEditorLogger : EditorLogger {
    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }
}
