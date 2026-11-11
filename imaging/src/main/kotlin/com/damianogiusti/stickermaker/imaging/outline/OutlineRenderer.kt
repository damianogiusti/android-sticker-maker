package com.damianogiusti.stickermaker.imaging.outline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.OutlineError
import com.damianogiusti.stickermaker.imaging.asBitmap
import com.damianogiusti.stickermaker.imaging.asEditorImage

/** [widthPermille] is the slider value 0..40, [colorArgb] one of the palette colors. */
data class OutlineSettings(val widthPermille: Int, val colorArgb: Int)

interface OutlineRenderer {
    /** Shrinks [cutout] to make room, then draws the outline under it. Same size as the input. */
    suspend fun render(cutout: EditorImage, settings: OutlineSettings): Either<OutlineError, EditorImage>
}

class BitmapOutlineRenderer(private val dispatcher: CoroutineDispatcher) : OutlineRenderer {

    override suspend fun render(cutout: EditorImage, settings: OutlineSettings): Either<OutlineError, EditorImage> =
        withContext(dispatcher) {
            Either.catch { draw(cutout.asBitmap(), settings).asEditorImage() }.mapLeft { OutlineError.Failed(it.message) }
        }

    private fun draw(cutout: Bitmap, settings: OutlineSettings): Bitmap {
        val side = cutout.width
        val scale = OutlineMath.shrinkScale(settings.widthPermille)
        val shrunk = Bitmap.createBitmap(side, cutout.height, Bitmap.Config.ARGB_8888)
        Canvas(shrunk).apply {
            scale(scale, scale, side / 2f, cutout.height / 2f)
            drawBitmap(cutout, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val result = Bitmap.createBitmap(side, cutout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (settings.widthPermille > 0) canvas.drawBitmap(outlineLayer(shrunk, settings), 0f, 0f, null)
        canvas.drawBitmap(shrunk, 0f, 0f, null)
        shrunk.recycle()
        return result
    }

    private fun outlineLayer(shrunk: Bitmap, settings: OutlineSettings): Bitmap {
        val width = shrunk.width
        val height = shrunk.height
        val pixels = IntArray(width * height)
        shrunk.getPixels(pixels, 0, width, 0, 0, width, height)
        val alpha = IntArray(pixels.size) { pixels[it] ushr 24 }
        val coverage = OutlineMath.outlineAlpha(alpha, width, height, OutlineMath.radiusPx(settings.widthPermille, width))
        val rgb = settings.colorArgb and 0x00FFFFFF
        for (index in pixels.indices) pixels[index] = (coverage[index] shl 24) or rgb
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
