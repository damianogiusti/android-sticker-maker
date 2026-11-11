package com.damianogiusti.stickermaker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.data.db.Sticker_pack
import com.damianogiusti.stickermaker.data.db.StickerDatabase
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.repository.PackImportRepository
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository
import com.damianogiusti.stickermaker.domain.model.PackArchive
import java.io.IOException
import com.damianogiusti.stickermaker.data.db.Sticker as StickerRow

class DatabaseStickerPackRepository(
    database: StickerDatabase,
    private val storage: PackFileStorage,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : StickerPackRepository, PackImportRepository {

    private val queries = database.stickerPackQueries

    override fun observePacks(): Flow<Either<StickerPackError, List<StickerPack>>> =
        combine(
            queries.selectPacks().asFlow().mapToList(ioDispatcher),
            queries.selectStickers().asFlow().mapToList(ioDispatcher),
        ) { packs, stickers ->
            val stickersByPack = stickers.groupBy { it.pack_id }
            val result: Either<StickerPackError, List<StickerPack>> =
                packs.map { it.toDomain(stickersByPack[it.id].orEmpty()) }.right()
            result
        }.catch { emit(it.toPackError().left()) }.flowOn(ioDispatcher)

    override fun observePack(id: StickerPackId): Flow<Either<StickerPackError, StickerPack>> =
        combine(
            queries.selectPackById(id.value).asFlow().mapToOneOrNull(ioDispatcher),
            queries.selectStickersByPack(id.value).asFlow().mapToList(ioDispatcher),
        ) { pack, stickers ->
            val result: Either<StickerPackError, StickerPack> =
                pack?.toDomain(stickers)?.right() ?: StickerPackError.NotFound(id).left()
            result
        }.catch { emit(it.toPackError().left()) }.flowOn(ioDispatcher)

    override suspend fun getPack(id: StickerPackId): Either<StickerPackError, StickerPack> = onIo {
        loadPack(id)
    }

    override suspend fun createPack(name: String, publisher: String): Either<StickerPackError, StickerPack> = onIo {
        val newId = queries.transactionWithResult {
            val id = nextFreeId()
            queries.insertPack(id.value, name, publisher, queries.selectNextPackPosition().executeAsOne())
            id
        }
        loadPack(newId)
    }

    override suspend fun renamePack(id: StickerPackId, name: String, publisher: String): Either<StickerPackError, Unit> =
        onIo {
            requirePack(id).map {
                queries.updateNames(name, publisher, id.value)
                Unit
            }
        }

    override suspend fun deletePack(id: StickerPackId): Either<StickerPackError, Unit> = onIo {
        requirePack(id).map {
            queries.deletePack(id.value)
            storage.deletePackDirectory(id.value)
        }
    }

    override suspend fun addSticker(file: PackFile): Either<StickerPackError, Unit> = onIo {
        val packId = file.packId
        requirePack(packId).flatMap {
            val count = queries.selectStickersByPack(packId.value).executeAsList().size
            if (count >= StickerPackLimits.MAX_STICKERS) return@flatMap StickerPackError.TooManyStickers.left()
            storage.write(packId.value, file.fileName, file.bytes)
            try {
                queries.transaction {
                    queries.insertSticker(
                        packId.value,
                        file.fileName,
                        file.bytes.size.toLong(),
                        queries.selectNextStickerPosition(packId.value).executeAsOne().toInt().toLong(),
                    )
                    queries.bumpImageDataVersion(packId.value)
                }
            } catch (failure: Exception) {
                storage.delete(packId.value, file.fileName)
                throw failure
            }
            Unit.right()
        }
    }

    override suspend fun replaceSticker(replacedFileName: String, file: PackFile): Either<StickerPackError, Unit> =
        onIo {
            val packId = file.packId
            requireSticker(packId, replacedFileName).map {
                storage.write(packId.value, file.fileName, file.bytes)
                queries.transaction {
                    queries.replaceStickerFile(file.fileName, file.bytes.size.toLong(), packId.value, replacedFileName)
                    queries.bumpImageDataVersion(packId.value)
                }
                if (replacedFileName != file.fileName) storage.delete(packId.value, replacedFileName)
            }
        }

    override suspend fun deleteSticker(packId: StickerPackId, fileName: String): Either<StickerPackError, Unit> = onIo {
        requireSticker(packId, fileName).map { sticker ->
            queries.transaction {
                queries.deleteSticker(packId.value, fileName)
                queries.shiftPositionsAfter(packId.value, sticker.position)
                queries.bumpImageDataVersion(packId.value)
            }
            storage.delete(packId.value, fileName)
        }
    }

    override suspend fun setTray(file: PackFile): Either<StickerPackError, Unit> = onIo {
        requirePack(file.packId).map {
            val previousTray = queries.selectPackById(file.packId.value).executeAsOne().tray_file_name
            storage.write(file.packId.value, file.fileName, file.bytes)
            // WhatsApp caches the tray icon by imageDataVersion, so a new tray must bump it.
            queries.transaction {
                queries.updateTray(file.fileName, file.packId.value)
                queries.bumpImageDataVersion(file.packId.value)
            }
            if (previousTray != null && previousTray != file.fileName) storage.delete(file.packId.value, previousTray)
        }
    }

    override suspend fun setEmojis(packId: StickerPackId, fileName: String, emojis: List<String>): Either<StickerPackError, Unit> =
        onIo {
            requireSticker(packId, fileName).map {
                // Emojis are visible in WhatsApp too, so a change bumps the version like any other.
                queries.transaction {
                    queries.updateEmojis(emojis.joinToString(EMOJI_SEPARATOR), packId.value, fileName)
                    queries.bumpImageDataVersion(packId.value)
                }
            }
        }

    override suspend fun setKeyboardAvailability(id: StickerPackId, isAvailable: Boolean): Either<StickerPackError, Unit> =
        onIo {
            requirePack(id).map {
                queries.updateKeyboardAvailability(if (isAvailable) 1L else 0L, if (isAvailable) clock.nowMillis() else null, id.value)
                Unit
            }
        }

    override suspend fun insertPack(archive: PackArchive, isImported: Boolean): Either<StickerPackError, StickerPack> =
        onIo {
            if (archive.stickers.size > StickerPackLimits.MAX_STICKERS) return@onIo StickerPackError.TooManyStickers.left()
            val id = nextFreeId()
            // Files first: if anything below fails the directory is removed and the database is untouched.
            storage.deletePackDirectory(id.value)
            try {
                val trayName = archive.tray?.let { storage.write(id.value, TRAY_FILE_NAME, it); TRAY_FILE_NAME }
                val names = archive.stickers.mapIndexed { index, sticker ->
                    "${index + 1}.webp".also { storage.write(id.value, it, sticker.bytes) }
                }
                insertRows(id, archive, trayName, names, isImported)
            } catch (failure: Exception) {
                runCatching { storage.deletePackDirectory(id.value) }
                throw failure
            }
            loadPack(id)
        }

    private fun insertRows(id: StickerPackId, archive: PackArchive, trayName: String?, names: List<String>, isImported: Boolean) {
        queries.transaction {
            val keyboardAddedAt = if (archive.isAvailableInKeyboard) clock.nowMillis() else null
            queries.insertFullPack(
                id.value, archive.name, archive.publisher, trayName,
                if (archive.isAvailableInKeyboard) 1L else 0L, keyboardAddedAt,
                if (isImported) 1L else 0L, queries.selectNextPackPosition().executeAsOne(),
            )
            archive.stickers.forEachIndexed { index, sticker ->
                queries.insertStickerWithEmojis(
                    id.value, names[index], sticker.emojis.joinToString(EMOJI_SEPARATOR), sticker.bytes.size.toLong(), index.toLong(),
                )
            }
        }
    }

    /** The single place where exceptions become domain errors. Runs on the injected dispatcher. */
    private suspend fun <T> onIo(block: () -> Either<StickerPackError, T>): Either<StickerPackError, T> =
        withContext(ioDispatcher) {
            Either.catch { block() }.getOrElse { it.toPackError().left() }
        }

    private fun loadPack(id: StickerPackId): Either<StickerPackError, StickerPack> {
        val row = queries.selectPackById(id.value).executeAsOneOrNull() ?: return StickerPackError.NotFound(id).left()
        return row.toDomain(queries.selectStickersByPack(id.value).executeAsList()).right()
    }

    private fun requirePack(id: StickerPackId): Either<StickerPackError, Unit> =
        if (queries.selectPackExists(id.value).executeAsOne() > 0) Unit.right() else StickerPackError.NotFound(id).left()

    private fun requireSticker(packId: StickerPackId, fileName: String): Either<StickerPackError, StickerRow> =
        queries.selectSticker(packId.value, fileName).executeAsOneOrNull()?.right()
            ?: StickerPackError.StickerNotFound(packId, fileName).left()

    private fun nextFreeId(): StickerPackId {
        var candidate = clock.nowMillis() / MILLIS_PER_SECOND
        while (queries.selectPackExists(candidate.toString()).executeAsOne() > 0) candidate++
        return StickerPackId(candidate.toString())
    }

    private companion object {
        const val EMOJI_SEPARATOR = ","
        const val TRAY_FILE_NAME = "tray.png"
        const val MILLIS_PER_SECOND = 1000L
    }
}

private fun Sticker_pack.toDomain(stickers: List<StickerRow>) = StickerPack(
    id = StickerPackId(id),
    name = name,
    publisher = publisher,
    trayFileName = tray_file_name,
    stickers = stickers.map { it.toDomain() },
    imageDataVersion = image_data_version.toInt(),
    isAvailableInKeyboard = is_available_in_keyboard != 0L,
    keyboardAddedAt = keyboard_added_at,
    isImported = is_imported != 0L,
    position = position.toInt(),
)

private fun StickerRow.toDomain() = Sticker(
    fileName = file_name,
    emojis = emojis.split(",").filter { it.isNotEmpty() },
    sizeBytes = size_bytes,
    position = position.toInt(),
)

private fun Throwable.toPackError(): StickerPackError = when (this) {
    is IOException, is IllegalArgumentException -> StickerPackError.Storage(message)
    else -> StickerPackError.Database(message)
}


