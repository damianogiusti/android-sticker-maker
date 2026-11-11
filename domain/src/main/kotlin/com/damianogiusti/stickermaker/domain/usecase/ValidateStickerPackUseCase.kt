package com.damianogiusti.stickermaker.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.damianogiusti.stickermaker.domain.error.ValidatePackError
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.StickerFileInspector
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository
import com.damianogiusti.stickermaker.domain.validation.StickerPackValidator
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue

class ValidateStickerPackUseCase(
    private val repository: StickerPackRepository,
    private val inspector: StickerFileInspector,
    private val validator: StickerPackValidator,
) {
    /** An empty list means the pack can be sent to WhatsApp. */
    suspend operator fun invoke(id: StickerPackId): Either<ValidatePackError, List<ValidationIssue>> = either {
        val pack = repository.getPack(id).mapLeft(ValidatePackError::Pack).bind()
        val fileNames = pack.stickers.map { it.fileName } + listOfNotNull(pack.trayFileName)
        val facts = mutableMapOf<String, StickerFileFacts>()
        for (fileName in fileNames) {
            facts[fileName] = inspector.inspect(id, fileName).mapLeft(ValidatePackError::Inspection).bind()
        }
        validator.validate(pack, facts)
    }
}
