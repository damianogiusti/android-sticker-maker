package com.damianogiusti.stickermaker.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import com.damianogiusti.stickermaker.data.di.dataModule
import com.damianogiusti.stickermaker.feature.editor.di.editorModule
import com.damianogiusti.stickermaker.feature.gallery.galleryModule
import com.damianogiusti.stickermaker.feature.keyboard.keyboardModule

private val startLock = Any()

/**
 * Starts Koin once, from whichever entry point runs first. Android publishes content providers before
 * `Application.onCreate`, so a WhatsApp query can arrive while the Application has not started Koin
 * yet; both entry points call this and the lock makes the second call wait for, then skip, the first.
 */
fun ensureKoinStarted(context: Context) {
    synchronized(startLock) {
        if (GlobalContext.getOrNull() != null) return
        startKoin {
            androidContext(context.applicationContext ?: context)
            modules(dataModule, domainModule, uiModule, editorModule, galleryModule, keyboardModule)
        }
    }
}
