package com.damianogiusti.stickermaker.domain.repository

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import com.damianogiusti.stickermaker.domain.error.KeyboardHistoryError
import com.damianogiusti.stickermaker.domain.error.StickerFileInspectionError
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.error.WhitelistError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp

interface StickerPackRepository {
    fun observePacks(): Flow<Either<StickerPackError, List<StickerPack>>>
    fun observePack(id: StickerPackId): Flow<Either<StickerPackError, StickerPack>>
    suspend fun getPack(id: StickerPackId): Either<StickerPackError, StickerPack>

    suspend fun createPack(name: String, publisher: String): Either<StickerPackError, StickerPack>
    suspend fun renamePack(id: StickerPackId, name: String, publisher: String): Either<StickerPackError, Unit>
    suspend fun deletePack(id: StickerPackId): Either<StickerPackError, Unit>

    /** Appends a sticker at the next position and bumps imageDataVersion. */
    suspend fun addSticker(file: PackFile): Either<StickerPackError, Unit>

    /** Replaces [replacedFileName] keeping position and emojis; bumps imageDataVersion. */
    suspend fun replaceSticker(replacedFileName: String, file: PackFile): Either<StickerPackError, Unit>
    suspend fun deleteSticker(packId: StickerPackId, fileName: String): Either<StickerPackError, Unit>
    suspend fun setTray(file: PackFile): Either<StickerPackError, Unit>
    suspend fun setEmojis(packId: StickerPackId, fileName: String, emojis: List<String>): Either<StickerPackError, Unit>
    suspend fun setKeyboardAvailability(id: StickerPackId, isAvailable: Boolean): Either<StickerPackError, Unit>
}

interface KeyboardHistoryRepository {
    /** Most recent first, at most 100 entries. */
    fun observeHistory(): Flow<Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>>

    /** Dedupes by (pack, file), moves it to the top and trims to the limit. */
    suspend fun record(entry: KeyboardHistoryEntry): Either<KeyboardHistoryError, Unit>
}

/** Reads file facts (dimensions, size, format) so the domain stays free of Android types. */
interface StickerFileInspector {
    suspend fun inspect(packId: StickerPackId, fileName: String): Either<StickerFileInspectionError, StickerFileFacts>
}

fun interface Clock {
    fun nowMillis(): Long
}

/** Remembers the last publisher name typed by the user, to prefill the new pack form. */
interface PublisherPreferences {
    suspend fun getLastPublisher(): String?
    suspend fun setLastPublisher(publisher: String)
}

/** Which official WhatsApp apps are installed on this device. */
interface WhatsAppInstallations {
    suspend fun installedApps(): List<WhatsAppApp>
}

/** Asks an installed WhatsApp app whether it already has a pack from our provider. */
interface WhatsAppWhitelist {
    suspend fun isWhitelisted(
        app: WhatsAppApp,
        authority: StickerAuthority,
        packId: StickerPackId,
    ): Either<WhitelistError, Boolean>
}

/** Inserts a whole pack at once. Kept apart from [StickerPackRepository]: only import and restore need it. */
interface PackImportRepository {
    /**
     * Writes the pack files first, then the pack, tray and stickers in one transaction. Any failure
     * removes the files again, so nothing half-imported stays. The pack always gets a fresh id.
     */
    suspend fun insertPack(archive: PackArchive, isImported: Boolean): Either<StickerPackError, StickerPack>
}

/** Reads the bytes of a pack file (sticker or tray) so the domain stays free of java.io.File. */
interface PackFileSource {
    suspend fun read(packId: StickerPackId, fileName: String): Either<StickerPackError, ByteArray>

    /** A stream over the file; the caller closes it. Lets backups copy file by file without loading a pack. */
    suspend fun open(packId: StickerPackId, fileName: String): Either<StickerPackError, InputStream>
}

/**
 * Reads and writes pack archives at an opaque [location] (a content URI on Android). The adapter
 * owns zip handling and validation; failures come back as [TransferError].
 */
interface PackArchiveStore {
    suspend fun readWastickers(location: String): Either<TransferError, PackArchive>

    /** Writes `<fileName>` into the app share cache and returns its location. */
    suspend fun writeWastickersForSharing(fileName: String, archive: PackArchive): Either<TransferError, String>

    /**
     * Opens a backup: validates it and spools its files to a temporary directory, so memory does not
     * grow with the backup. The caller must call [BackupReader.discard] when done.
     */
    suspend fun openBackup(location: String): Either<TransferError, BackupReader>

    /** Streams [packs] into a backup at [location], reading each file from [files] one at a time. */
    suspend fun writeBackup(location: String, packs: List<StickerPack>, files: PackFileSource): Either<TransferError, Unit>
}

/** An opened backup. Packs load one at a time, so only one pack is in memory. */
interface BackupReader {
    /** Ids the packs had when the backup was made, in list order. */
    val sourceIds: List<String>

    suspend fun load(sourceId: String): Either<TransferError, PackArchive>

    /** Deletes the temporary files. Safe to call more than once. */
    suspend fun discard()
}
