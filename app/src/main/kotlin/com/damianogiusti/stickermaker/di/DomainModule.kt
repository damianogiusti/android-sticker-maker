package com.damianogiusti.stickermaker.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import com.damianogiusti.stickermaker.domain.usecase.AddStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.CreateBackupUseCase
import com.damianogiusti.stickermaker.domain.usecase.CreateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ExportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ImportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.RestoreBackupUseCase
import com.damianogiusti.stickermaker.domain.usecase.DeleteStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.DeleteStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetLastPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.CheckPackInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetPacksInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ListStickerPacksUseCase
import com.damianogiusti.stickermaker.domain.usecase.PrepareAddToWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveKeyboardHistoryUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.domain.usecase.RecordKeyboardStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.RememberPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.RenameStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ReplaceStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetKeyboardAvailabilityUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerEmojisUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerPackTrayUseCase
import com.damianogiusti.stickermaker.domain.usecase.ValidateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.validation.StickerPackValidator

/** Lives in :app because :domain stays free of Koin. */
val domainModule = module {
    factoryOf(::StickerPackValidator)
    factoryOf(::ObserveStickerPacksUseCase)
    factoryOf(::ObserveStickerPackUseCase)
    factoryOf(::CreateStickerPackUseCase)
    factoryOf(::RenameStickerPackUseCase)
    factoryOf(::DeleteStickerPackUseCase)
    factoryOf(::AddStickerUseCase)
    factoryOf(::ReplaceStickerUseCase)
    factoryOf(::DeleteStickerUseCase)
    factoryOf(::SetStickerPackTrayUseCase)
    factoryOf(::SetStickerEmojisUseCase)
    factoryOf(::SetKeyboardAvailabilityUseCase)
    factoryOf(::ValidateStickerPackUseCase)
    factoryOf(::ObserveKeyboardHistoryUseCase)
    factoryOf(::RecordKeyboardStickerUseCase)
    factoryOf(::GetLastPublisherUseCase)
    factoryOf(::RememberPublisherUseCase)
    factoryOf(::PrepareAddToWhatsAppUseCase)
    factoryOf(::CheckPackInWhatsAppUseCase)
    factoryOf(::GetPacksInWhatsAppUseCase)
    factoryOf(::ListStickerPacksUseCase)
    factoryOf(::GetStickerPackUseCase)
    factoryOf(::ImportStickerPackUseCase)
    factoryOf(::ExportStickerPackUseCase)
    factoryOf(::CreateBackupUseCase)
    factoryOf(::RestoreBackupUseCase)
    // The authority declared in the manifest is `${applicationId}.stickercontentprovider`.
    single { StickerAuthority(androidContext().packageName + ".stickercontentprovider") }
}
