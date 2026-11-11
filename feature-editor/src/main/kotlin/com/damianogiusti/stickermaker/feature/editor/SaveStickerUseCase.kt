package com.damianogiusti.stickermaker.feature.editor

import arrow.core.Either
import arrow.core.raise.either
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.usecase.AddStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.ReplaceStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerPackTrayUseCase
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.ExportError
import com.damianogiusti.stickermaker.imaging.export.StickerComposition
import com.damianogiusti.stickermaker.imaging.export.StickerExporter
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem

sealed interface SaveStickerError {
    data class Export(val error: ExportError) : SaveStickerError
    data class Store(val error: StickerPackError) : SaveStickerError
}

/** Exports the composition (WebP sticker or PNG tray) and stores it in the pack. */
class SaveStickerUseCase(
    private val exporter: StickerExporter,
    private val addSticker: AddStickerUseCase,
    private val replaceSticker: ReplaceStickerUseCase,
    private val setTray: SetStickerPackTrayUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        packId: StickerPackId,
        target: EditorTarget,
        outlinedCutout: EditorImage?,
        texts: List<StickerTextItem>,
    ): Either<SaveStickerError, Unit> = either {
        val composition = StickerComposition(outlinedCutout, texts)
        when (target) {
            EditorTarget.NewSticker -> {
                val bytes = exporter.exportSticker(composition).mapLeft(SaveStickerError::Export).bind()
                addSticker(PackFile(packId, newStickerFileName(), bytes)).mapLeft(SaveStickerError::Store).bind()
            }
            is EditorTarget.ReplaceSticker -> {
                val bytes = exporter.exportSticker(composition).mapLeft(SaveStickerError::Export).bind()
                replaceSticker(target.fileName, PackFile(packId, newStickerFileName(), bytes))
                    .mapLeft(SaveStickerError::Store).bind()
            }
            EditorTarget.Tray -> {
                val bytes = exporter.exportTray(composition).mapLeft(SaveStickerError::Export).bind()
                setTray(PackFile(packId, TRAY_FILE_NAME, bytes)).mapLeft(SaveStickerError::Store).bind()
            }
        }
    }

    private fun newStickerFileName(): String = "${clock.nowMillis()}.webp"

    private companion object {
        const val TRAY_FILE_NAME = "tray.png"
    }
}
