package com.damianogiusti.stickermaker.feature.editor

import arrow.core.Either
import arrow.core.raise.either
import com.damianogiusti.stickermaker.domain.clipboard.ClipboardError
import com.damianogiusti.stickermaker.domain.clipboard.EncodedSticker
import com.damianogiusti.stickermaker.domain.clipboard.StickerClipboard
import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.ExportError
import com.damianogiusti.stickermaker.imaging.export.StickerComposition
import com.damianogiusti.stickermaker.imaging.export.StickerExporter
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem

sealed interface CopyStickerError {
    data class Export(val error: ExportError) : CopyStickerError
    data class Clipboard(val error: ClipboardError) : CopyStickerError
}

/** Exports the composition as a 512x512 sticker and puts it on the clipboard. */
class CopyStickerUseCase(
    private val exporter: StickerExporter,
    private val clipboard: StickerClipboard,
    private val format: StickerImageFormat,
) {
    suspend operator fun invoke(
        outlinedCutout: EditorImage?,
        texts: List<StickerTextItem>,
    ): Either<CopyStickerError, Unit> = either {
        val bytes = exporter.exportSticker(StickerComposition(outlinedCutout, texts), format)
            .mapLeft(CopyStickerError::Export).bind()
        clipboard.copy(EncodedSticker(bytes, format)).mapLeft(CopyStickerError::Clipboard).bind()
    }
}
