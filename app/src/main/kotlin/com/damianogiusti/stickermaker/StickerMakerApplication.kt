package com.damianogiusti.stickermaker

import android.app.Application
import com.damianogiusti.stickermaker.di.ensureKoinStarted

class StickerMakerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureKoinStarted(this)
    }
}
