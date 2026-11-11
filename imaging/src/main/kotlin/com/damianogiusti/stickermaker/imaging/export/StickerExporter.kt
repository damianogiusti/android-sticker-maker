package com.damianogiusti.stickermaker.imaging.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.Raise
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import com.damianogiusti.stickermaker.domain.model.StickerLimits
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.ExportError
import com.damianogiusti.stickermaker.imaging.asBitmap
import com.damianogiusti.stickermaker.imaging.cut.CUTOUT_SIDE_PX
import com.damianogiusti.stickermaker.imaging.geometry.ImageGeometry
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem
import com.damianogiusti.stickermaker.imaging.text.TextItemPainter

/** What the user built: the outlined cutout (null for a text-only sticker) and the texts on top of it. */
class StickerComposition(val outlinedCutout: EditorImage?, val texts: List<StickerTextItem>)

interface StickerExporter {
    /** 512x512 transparent image. WebP is lossy and at most 100 KB; PNG has no size limit. */
    suspend fun exportSticker(composition: StickerComposition, format: StickerImageFormat): Either<ExportError, ByteArray>
}

class BitmapStickerExporter(
    private val textPainter: TextItemPainter,
    private val dispatcher: CoroutineDispatcher,
) : StickerExporter {

    override suspend fun exportSticker(
        composition: StickerComposition,
        format: StickerImageFormat,
    ): Either<ExportError, ByteArray> =
        guarded {
            val fitted = composeAndFit(composition, StickerLimits.SIDE_PX).bind()
            when (format) {
                StickerImageFormat.WEBP -> QualityStepper.encodeWithinLimit(StickerLimits.WEBP_MAX_BYTES) { quality ->
                    encode(fitted, webpFormat(), quality)
                }.bind()
                StickerImageFormat.PNG -> encode(fitted, Bitmap.CompressFormat.PNG, PNG_QUALITY)
            }
        }

    /** Composite on a 1024 canvas, trim to the visible bounds, fit centered in a transparent square. */
    private fun composeAndFit(composition: StickerComposition, side: Int): Either<ExportError, Bitmap> {
        val canvasSide = composition.outlinedCutout?.width ?: CUTOUT_SIDE_PX
        val composite = Bitmap.createBitmap(canvasSide, canvasSide, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)
        composition.outlinedCutout?.let { canvas.drawBitmap(it.asBitmap(), 0f, 0f, null) }
        composition.texts.forEach { textPainter.draw(canvas, it) }

        val pixels = IntArray(canvasSide * canvasSide)
        composite.getPixels(pixels, 0, canvasSide, 0, 0, canvasSide, canvasSide)
        val bounds = ImageGeometry.findOpaqueBounds(pixels, canvasSide, canvasSide) ?: return ExportError.EmptyComposite.left()
        val fit = ImageGeometry.fitCentered(bounds.width, bounds.height, side)
        val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            composite,
            Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            RectF(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        composite.recycle()
        return result.right()
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(format, quality, output)) { "Bitmap.compress returned false" }
        return output.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private suspend fun guarded(block: Raise<ExportError>.() -> ByteArray): Either<ExportError, ByteArray> =
        withContext(dispatcher) {
            Either.catch { either { block() } }.fold(
                ifLeft = { ExportError.EncodeFailed(it.message).left() },
                ifRight = { it },
            )
        }

    private companion object {
        const val PNG_QUALITY = 100
    }
}
