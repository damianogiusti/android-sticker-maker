package com.damianogiusti.stickermaker.di

import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import com.damianogiusti.stickermaker.about.AboutViewModel
import com.damianogiusti.stickermaker.about.AssetLicenseTextSource
import com.damianogiusti.stickermaker.share.IncomingShareViewModel
import com.damianogiusti.stickermaker.ui.logging.AndroidErrorLogger
import com.damianogiusti.stickermaker.ui.logging.ErrorLogger

val uiModule = module {
    single<ErrorLogger> { AndroidErrorLogger }
    viewModelOf(::IncomingShareViewModel)
    viewModel {
        val context = androidContext()
        AboutViewModel(
            versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            licenseTexts = AssetLicenseTextSource(context.assets, Dispatchers.IO),
            errorLogger = get(),
        )
    }
}
