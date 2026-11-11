package com.damianogiusti.stickermaker.imaging.cut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.imaging.CutError
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.asBitmap
import com.damianogiusti.stickermaker.imaging.asEditorImage
import com.damianogiusti.stickermaker.imaging.geometry.ImageGeometry
import com.damianogiusti.stickermaker.imaging.segmentation.AlphaMask

const val CUTOUT_SIDE_PX = 1024

interface ImageCutter {
    /** Rotates the working image by 90 degrees clockwise. */
    suspend fun rotateClockwise(source: EditorImage): Either<CutError, EditorImage>

    /** Cuts [selection] out of [source]: trimmed and fitted centered in a transparent 1024x1024 bitmap. */
    suspend fun cut(source: EditorImage, selection: CutSelection): Either<CutError, EditorImage>

    /** Same as [cut] for a per-pixel mask (magic cutout). */
    suspend fun cut(source: EditorImage, mask: AlphaMask): Either<CutError, EditorImage>
}

class BitmapImageCutter(private val dispatcher: CoroutineDispatcher) : ImageCutter {

    override suspend fun rotateClockwise(source: EditorImage): Either<CutError, EditorImage> = guarded {
        val bitmap = source.asBitmap()
        val matrix = Matrix().apply { postRotate(ROTATION_DEGREES) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override suspend fun cut(source: EditorImage, selection: CutSelection): Either<CutError, EditorImage> = guarded {
        val bitmap = source.asBitmap()
        val bounds = ensureNotNull(selection.boundsIn(source.width, source.height)) { CutError.EmptySelection }
        val layer = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layer)
        val sourceRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val layerRect = Rect(0, 0, bounds.width, bounds.height)
        if (selection == CutSelection.WholeImage) {
            canvas.drawBitmap(bitmap, sourceRect, layerRect, null)
        } else {
            val path = SelectionPaths.toPath(selection, source.width, source.height)
            path.offset(-bounds.left.toFloat(), -bounds.top.toFloat())
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
            val srcIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
            canvas.drawBitmap(bitmap, sourceRect, layerRect, srcIn)
        }
        trimAndFit(layer).bind()
    }

    override suspend fun cut(source: EditorImage, mask: AlphaMask): Either<CutError, EditorImage> = guarded {
        val bitmap = source.asBitmap()
        ensureNotNull(mask.takeIf { it.width == source.width && it.height == source.height }) {
            CutError.Failed("mask size does not match the image")
        }
        val pixels = IntArray(source.width * source.height)
        bitmap.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = (pixel ushr 24) * (mask.alpha[index].toInt() and 0xFF) / 255
            pixels[index] = (alpha shl 24) or (pixel and 0x00FFFFFF)
        }
        val layer = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        layer.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        trimAndFit(layer).bind()
    }

    private fun trimAndFit(layer: Bitmap): Either<CutError, Bitmap> = either {
        val pixels = IntArray(layer.width * layer.height)
        layer.getPixels(pixels, 0, layer.width, 0, 0, layer.width, layer.height)
        val opaque = ensureNotNull(ImageGeometry.findOpaqueBounds(pixels, layer.width, layer.height)) {
            CutError.EmptySelection
        }
        val fit = ImageGeometry.fitCentered(opaque.width, opaque.height, CUTOUT_SIDE_PX)
        val result = Bitmap.createBitmap(CUTOUT_SIDE_PX, CUTOUT_SIDE_PX, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            layer,
            Rect(opaque.left, opaque.top, opaque.right, opaque.bottom),
            RectF(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        result
    }

    private suspend fun guarded(block: suspend arrow.core.raise.Raise<CutError>.() -> Bitmap): Either<CutError, EditorImage> =
        withContext(dispatcher) {
            Either.catch { either { block() } }.fold(
                ifLeft = { Either.Left(CutError.Failed(it.message)) },
                ifRight = { result -> result.map(Bitmap::asEditorImage) },
            )
        }

    private companion object {
        const val ROTATION_DEGREES = 90f
    }
}
