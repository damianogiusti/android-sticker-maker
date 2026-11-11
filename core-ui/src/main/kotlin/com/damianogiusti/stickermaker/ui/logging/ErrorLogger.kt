package com.damianogiusti.stickermaker.ui.logging

import android.util.Log

/**
 * Where ViewModels record errors they cannot show. A seam instead of a direct `Log` call, so JVM unit
 * tests do not need `isReturnDefaultValues` and can assert that an error was logged.
 */
fun interface ErrorLogger {
    fun log(tag: String, message: String)
}

object AndroidErrorLogger : ErrorLogger {
    override fun log(tag: String, message: String) {
        Log.w(tag, message)
    }
}
