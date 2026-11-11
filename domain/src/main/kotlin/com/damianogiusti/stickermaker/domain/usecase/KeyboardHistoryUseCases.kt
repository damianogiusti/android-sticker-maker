package com.damianogiusti.stickermaker.domain.usecase

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import com.damianogiusti.stickermaker.domain.error.KeyboardHistoryError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.repository.KeyboardHistoryRepository

class ObserveKeyboardHistoryUseCase(private val repository: KeyboardHistoryRepository) {
    operator fun invoke(): Flow<Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>> =
        repository.observeHistory()
}

class RecordKeyboardStickerUseCase(
    private val repository: KeyboardHistoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(packId: StickerPackId, fileName: String): Either<KeyboardHistoryError, Unit> =
        repository.record(KeyboardHistoryEntry(packId, fileName, clock.nowMillis()))
}
