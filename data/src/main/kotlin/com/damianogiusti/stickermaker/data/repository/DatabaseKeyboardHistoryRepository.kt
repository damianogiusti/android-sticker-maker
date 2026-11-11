package com.damianogiusti.stickermaker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.data.db.StickerDatabase
import com.damianogiusti.stickermaker.domain.error.KeyboardHistoryError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.repository.KeyboardHistoryRepository

class DatabaseKeyboardHistoryRepository(
    database: StickerDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : KeyboardHistoryRepository {

    private val queries = database.keyboardHistoryQueries
    private val limit = StickerPackLimits.MAX_KEYBOARD_HISTORY.toLong()

    override fun observeHistory(): Flow<Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>> =
        queries.selectHistory(limit).asFlow().mapToList(ioDispatcher)
            .map { rows ->
                val result: Either<KeyboardHistoryError, List<KeyboardHistoryEntry>> =
                    rows.map { KeyboardHistoryEntry(StickerPackId(it.pack_id), it.file_name, it.used_at) }.right()
                result
            }
            .catch { emit(KeyboardHistoryError.Database(it.message).left()) }
            .flowOn(ioDispatcher)

    override suspend fun record(entry: KeyboardHistoryEntry): Either<KeyboardHistoryError, Unit> =
        withContext(ioDispatcher) {
            Either.catch {
                queries.transaction {
                    queries.upsertEntry(entry.packId.value, entry.fileName, entry.usedAt)
                    queries.trim(limit)
                }
            }.mapLeft<KeyboardHistoryError> { KeyboardHistoryError.Database(it.message) }
        }
}
