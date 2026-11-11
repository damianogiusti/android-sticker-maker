package com.damianogiusti.stickermaker.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import com.damianogiusti.stickermaker.about.AboutViewModel
import com.damianogiusti.stickermaker.about.AssetLicenseTextSource
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import com.damianogiusti.stickermaker.imports.IncomingImportViewModel
import com.damianogiusti.stickermaker.ui.logging.AndroidErrorLogger
import com.damianogiusti.stickermaker.ui.logging.ErrorLogger
import com.damianogiusti.stickermaker.feature.packs.PackFileResolver
import com.damianogiusti.stickermaker.feature.packs.detail.PackDetailViewModel
import com.damianogiusti.stickermaker.feature.packs.emoji.EmojiPickerViewModel
import com.damianogiusti.stickermaker.feature.packs.form.PackFormViewModel
import com.damianogiusti.stickermaker.feature.packs.list.PacksListViewModel

val uiModule = module {
    single<PackFileResolver> {
        val storage = get<PackFileStorage>()
        PackFileResolver { packId, fileName -> storage.fileOf(packId, fileName) }
    }
    single<ErrorLogger> { AndroidErrorLogger }
    viewModelOf(::PacksListViewModel)
    // No parameter = new pack, a pack id = rename.
    viewModel { params ->
        PackFormViewModel(params.getOrNull<String>(), get(), get(), get(), get(), get())
    }
    viewModel { params ->
        PackDetailViewModel(params.get<String>(0), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::IncomingImportViewModel)
    viewModel {
        val context = androidContext()
        AboutViewModel(
            versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            licenseTexts = AssetLicenseTextSource(context.assets, get()),
            errorLogger = get(),
        )
    }
    viewModel { params ->
        EmojiPickerViewModel(params.get<String>(0), params.get<String>(1), get(), get())
    }
}
