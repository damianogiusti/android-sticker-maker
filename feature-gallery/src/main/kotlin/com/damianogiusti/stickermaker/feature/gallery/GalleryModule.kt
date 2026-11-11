package com.damianogiusti.stickermaker.feature.gallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File

val galleryModule = module {
    single<GalleryFileLocator> {
        val root = File(androidContext().filesDir, "packs")
        GalleryFileLocator { packId, fileName -> File(File(root, packId), fileName) }
    }
    single<TreeGrantStore> {
        SharedPreferencesTreeGrantStore(
            androidContext().contentResolver,
            androidContext().getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE),
        )
    }
    single<WhatsAppFolderReader> { DocumentsContractFolderReader(androidContext().contentResolver, Dispatchers.IO) }
    single<AnimationDetector> { HeaderAnimationDetector(androidContext().contentResolver, Dispatchers.IO) }
    single<OwnStickerCopier> { FileOwnStickerCopier(get(), androidContext().cacheDir, Dispatchers.IO) }
    viewModelOf(::GalleryViewModel)
}
