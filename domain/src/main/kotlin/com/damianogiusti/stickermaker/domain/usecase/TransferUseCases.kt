package com.damianogiusti.stickermaker.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.coroutines.flow.first
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.ArchiveSticker
import com.damianogiusti.stickermaker.domain.model.ExportedPack
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.TransferSummary
import com.damianogiusti.stickermaker.domain.repository.PackArchiveStore
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import com.damianogiusti.stickermaker.domain.repository.PackImportRepository
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository

class ImportStickerPackUseCase(
    private val store: PackArchiveStore,
    private val importRepository: PackImportRepository,
) {
    suspend operator fun invoke(location: String): Either<TransferError, StickerPack> = either {
        val archive = store.readWastickers(location).bind()
        importRepository.insertPack(archive, isImported = true).mapLeft { TransferError.Pack(it) }.bind()
    }
}

class ExportStickerPackUseCase(
    private val repository: StickerPackRepository,
    private val files: PackFileSource,
    private val store: PackArchiveStore,
) {
    suspend operator fun invoke(id: StickerPackId): Either<TransferError, ExportedPack> = either {
        val pack = repository.getPack(id).mapLeft { TransferError.Pack(it) }.bind()
        val archive = files.toArchive(pack).bind()
        val fileName = pack.name.toShareFileName() + WASTICKERS_EXTENSION
        val location = store.writeWastickersForSharing(fileName, archive).bind()
        ExportedPack(location, fileName)
    }

    private companion object {
        const val WASTICKERS_EXTENSION = ".wastickers"
    }
}

class CreateBackupUseCase(
    private val repository: StickerPackRepository,
    private val files: PackFileSource,
    private val store: PackArchiveStore,
) {
    /** Hands the packs and the file source to the store, which copies pack by pack. */
    suspend operator fun invoke(location: String): Either<TransferError, TransferSummary> = either {
        val packs = repository.observePacks().first().mapLeft { TransferError.Pack(it) }.bind()
        store.writeBackup(location, packs, files).bind()
        TransferSummary(packs.size)
    }
}

/**
 * Restores every pack as a new pack, loading one pack at a time. If one fails, the packs restored so
 * far are removed again.
 */
class RestoreBackupUseCase(
    private val store: PackArchiveStore,
    private val importRepository: PackImportRepository,
    private val repository: StickerPackRepository,
) {
    suspend operator fun invoke(location: String): Either<TransferError, TransferSummary> = either {
        val backup = store.openBackup(location).bind()
        try {
            ensure(backup.sourceIds.isNotEmpty()) { TransferError.InvalidBackup("The backup holds no packs") }
            val restored = mutableListOf<StickerPackId>()
            for (sourceId in backup.sourceIds) {
                val result = backup.load(sourceId).flatMap { archive ->
                    importRepository.insertPack(archive, isImported = false).mapLeft { TransferError.Pack(it) }
                }
                result.onRight { restored += it.id }
                result.onLeft { error ->
                    restored.forEach { repository.deletePack(it) }
                    raise(error)
                }
            }
            TransferSummary(restored.size)
        } finally {
            backup.discard()
        }
    }
}

private suspend fun PackFileSource.toArchive(pack: StickerPack): Either<TransferError, PackArchive> = either {
    val tray = pack.trayFileName?.let { read(pack.id, it).mapLeft { e -> TransferError.Pack(e) }.bind() }
    val stickers = pack.stickers.sortedBy { it.position }.map { sticker ->
        val bytes = read(pack.id, sticker.fileName).mapLeft { e -> TransferError.Pack(e) }.bind()
        ArchiveSticker(bytes, sticker.emojis)
    }
    PackArchive(pack.name, pack.publisher, tray, stickers, pack.isAvailableInKeyboard)
}

/** A safe file name stem: letters, digits, dot, dash and underscore only. */
private fun String.toShareFileName(): String {
    val cleaned = map { if (it.isLetterOrDigit() && it.code < ASCII_LIMIT || it == '.' || it == '-') it else '_' }
        .joinToString("")
        .trim('_', '.')
        .take(MAX_NAME_LENGTH)
    return cleaned.ifEmpty { "pack" }
}

private const val ASCII_LIMIT = 128
private const val MAX_NAME_LENGTH = 40
