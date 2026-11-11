package com.damianogiusti.stickermaker.data.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module
import com.damianogiusti.stickermaker.data.db.StickerDatabase
import com.damianogiusti.stickermaker.data.inspect.AndroidStickerFileInspector
import com.damianogiusti.stickermaker.data.inspect.ImageHeaderParser
import com.damianogiusti.stickermaker.data.repository.DatabaseKeyboardHistoryRepository
import com.damianogiusti.stickermaker.data.repository.DatabaseStickerPackRepository
import com.damianogiusti.stickermaker.data.repository.SharedPreferencesPublisherPreferences
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.repository.KeyboardHistoryRepository
import com.damianogiusti.stickermaker.domain.repository.PublisherPreferences
import com.damianogiusti.stickermaker.domain.repository.StickerFileInspector
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository
import com.damianogiusti.stickermaker.domain.repository.WhatsAppInstallations
import com.damianogiusti.stickermaker.domain.repository.WhatsAppWhitelist
import com.damianogiusti.stickermaker.data.whatsapp.ContentResolverWhatsAppWhitelist
import com.damianogiusti.stickermaker.data.whatsapp.PackageManagerWhatsAppInstallations
import com.damianogiusti.stickermaker.data.storage.FilePackFileSource
import com.damianogiusti.stickermaker.data.transfer.BackupCodec
import com.damianogiusti.stickermaker.data.transfer.ContentResolverStreamOpener
import com.damianogiusti.stickermaker.data.transfer.StreamOpener
import com.damianogiusti.stickermaker.data.transfer.StreamPackArchiveStore
import com.damianogiusti.stickermaker.data.transfer.WastickersCodec
import com.damianogiusti.stickermaker.domain.repository.PackArchiveStore
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import com.damianogiusti.stickermaker.domain.repository.PackImportRepository
import java.io.File

private const val DATABASE_NAME = "stickermaker.db"

val dataModule = module {
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<Clock> { Clock { System.currentTimeMillis() } }
    single<SqlDriver> { createDriver(androidContext()) }
    single { StickerDatabase(get()) }
    single { PackFileStorage(File(androidContext().filesDir, "packs")) }
    single { ImageHeaderParser() }
    single { DatabaseStickerPackRepository(get(), get(), get(), get()) } binds
        arrayOf(StickerPackRepository::class, PackImportRepository::class)
    single<PackFileSource> { FilePackFileSource(get(), get()) }
    single { WastickersCodec(get()) }
    single { BackupCodec(get(), ioDispatcher = get()) }
    single<StreamOpener> { ContentResolverStreamOpener(androidContext().contentResolver) }
    single<PackArchiveStore> {
        StreamPackArchiveStore(
            get(),
            File(androidContext().cacheDir, "share"),
            File(androidContext().cacheDir, "restore"),
            get(),
            get(),
            get(),
        )
    }
    single<KeyboardHistoryRepository> { DatabaseKeyboardHistoryRepository(get(), get()) }
    single<PublisherPreferences> {
        SharedPreferencesPublisherPreferences(
            androidContext().getSharedPreferences("stickermaker_prefs", Context.MODE_PRIVATE),
            get(),
        )
    }
    single<StickerFileInspector> { AndroidStickerFileInspector(get(), get(), get()) }
    single<WhatsAppInstallations> { PackageManagerWhatsAppInstallations(androidContext()) }
    single<WhatsAppWhitelist> { ContentResolverWhatsAppWhitelist(androidContext().contentResolver, get()) }
}

private fun createDriver(context: Context): SqlDriver {
    val callback = object : AndroidSqliteDriver.Callback(StickerDatabase.Schema) {
        // ON DELETE CASCADE only works with foreign keys enabled.
        override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
    return AndroidSqliteDriver(StickerDatabase.Schema, context, DATABASE_NAME, callback = callback)
}
