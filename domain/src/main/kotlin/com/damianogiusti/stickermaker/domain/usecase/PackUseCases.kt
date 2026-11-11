package com.damianogiusti.stickermaker.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.coroutines.flow.Flow
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository

class ObserveStickerPacksUseCase(private val repository: StickerPackRepository) {
    operator fun invoke(): Flow<Either<StickerPackError, List<StickerPack>>> = repository.observePacks()
}

class ObserveStickerPackUseCase(private val repository: StickerPackRepository) {
    operator fun invoke(id: StickerPackId): Flow<Either<StickerPackError, StickerPack>> = repository.observePack(id)
}

class CreateStickerPackUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(name: String, publisher: String): Either<StickerPackError, StickerPack> = either {
        val cleanName = name.trim()
        val cleanPublisher = publisher.trim()
        ensure(isValidText(cleanName)) { StickerPackError.InvalidName }
        ensure(isValidText(cleanPublisher)) { StickerPackError.InvalidPublisher }
        repository.createPack(cleanName, cleanPublisher).bind()
    }
}

class RenameStickerPackUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(id: StickerPackId, name: String, publisher: String): Either<StickerPackError, Unit> =
        either {
            val cleanName = name.trim()
            val cleanPublisher = publisher.trim()
            ensure(isValidText(cleanName)) { StickerPackError.InvalidName }
            ensure(isValidText(cleanPublisher)) { StickerPackError.InvalidPublisher }
            repository.renamePack(id, cleanName, cleanPublisher).bind()
        }
}

class DeleteStickerPackUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(id: StickerPackId): Either<StickerPackError, Unit> = repository.deletePack(id)
}

class AddStickerUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(file: PackFile): Either<StickerPackError, Unit> = either {
        val pack = repository.getPack(file.packId).bind()
        ensure(pack.stickers.size < StickerPackLimits.MAX_STICKERS) { StickerPackError.TooManyStickers }
        repository.addSticker(file).bind()
    }
}

class ReplaceStickerUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(replacedFileName: String, file: PackFile): Either<StickerPackError, Unit> =
        repository.replaceSticker(replacedFileName, file)
}

class DeleteStickerUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(packId: StickerPackId, fileName: String): Either<StickerPackError, Unit> =
        repository.deleteSticker(packId, fileName)
}

class SetStickerPackTrayUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(file: PackFile): Either<StickerPackError, Unit> = repository.setTray(file)
}

class SetStickerEmojisUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(
        packId: StickerPackId,
        fileName: String,
        emojis: List<String>,
    ): Either<StickerPackError, Unit> = either {
        ensure(emojis.size <= StickerPackLimits.MAX_EMOJIS_PER_STICKER) { StickerPackError.TooManyEmojis }
        repository.setEmojis(packId, fileName, emojis.distinct()).bind()
    }
}

class SetKeyboardAvailabilityUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(id: StickerPackId, isAvailable: Boolean): Either<StickerPackError, Unit> =
        repository.setKeyboardAvailability(id, isAvailable)
}

private fun isValidText(text: String): Boolean = text.length in 1..StickerPackLimits.MAX_TEXT_LENGTH
