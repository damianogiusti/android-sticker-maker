package com.damianogiusti.stickermaker.feature.keyboard

import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File

val keyboardModule = module {
    single<StickerFileLocator> {
        val root = File(androidContext().filesDir, "packs")
        StickerFileLocator { packId, fileName -> File(File(root, packId), fileName) }
    }
    single<KeyboardEnabledQuery> { InputMethodManagerKeyboardEnabledQuery(androidContext()) }
    single<KeyboardLogger> { KeyboardLogger { tag, message -> Log.w(tag, message) } }
    viewModelOf(::KeyboardViewModel)
    viewModelOf(::KeyboardSetupViewModel)
}
