package com.damianogiusti.stickermaker

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.damianogiusti.stickermaker.di.uiModule
import com.damianogiusti.stickermaker.feature.editor.di.editorModule

class StickerMakerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@StickerMakerApplication)
            modules(uiModule, editorModule)
        }
    }
}
