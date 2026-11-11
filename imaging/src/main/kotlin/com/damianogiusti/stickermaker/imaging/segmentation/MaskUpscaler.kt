package com.damianogiusti.stickermaker.imaging.segmentation

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The part of the model input that holds the image; the rest is letterbox padding. */
data class LetterboxRect(val left: Int, val top: Int, val width: Int, val height: Int)

object Letterbox {
    /** Aspect-fit of the image in the model input, centered. */
    fun fit(imageWidth: Int, imageHeight: Int, inputWidth: Int, inputHeight: Int): LetterboxRect {
        val factor = min(inputWidth.toFloat() / imageWidth, inputHeight.toFloat() / imageHeight)
        val width = (imageWidth * factor).roundToInt().coerceIn(1, inputWidth)
        val height = (imageHeight * factor).roundToInt().coerceIn(1, inputHeight)
        return LetterboxRect((inputWidth - width) / 2, (inputHeight - height) / 2, width, height)
    }
}

object MaskUpscaler {
    private const val HALF = 0.5f

    /**
     * Upscales the model mask to the image size. The 0/1 mask is sampled bilinearly (soft edge), then
     * thresholded at 0.5 over about one image pixel, so the edge is anti-aliased but not feathered.
     * Holes and separate subjects survive because every pixel is sampled on its own.
     */
    fun toImageAlpha(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        letterbox: LetterboxRect,
        imageWidth: Int,
        imageHeight: Int,
    ): AlphaMask {
        require(mask.size == maskWidth * maskHeight) { "mask does not match $maskWidth x $maskHeight" }
        val alpha = ByteArray(imageWidth * imageHeight)
        // Image pixels per model pixel: the width of the bilinear ramp, which the edge gain cancels.
        val edgeGain = max(1f, imageWidth.toFloat() / letterbox.width)
        val minX = letterbox.left
        val maxX = letterbox.left + letterbox.width - 1
        val minY = letterbox.top
        val maxY = letterbox.top + letterbox.height - 1
        for (y in 0 until imageHeight) {
            val modelY = letterbox.top + (y + HALF) / imageHeight * letterbox.height - HALF
            val y0 = floor(modelY).toInt()
            val fy = modelY - y0
            val row0 = y0.coerceIn(minY, maxY) * maskWidth
            val row1 = (y0 + 1).coerceIn(minY, maxY) * maskWidth
            for (x in 0 until imageWidth) {
                val modelX = letterbox.left + (x + HALF) / imageWidth * letterbox.width - HALF
                val x0 = floor(modelX).toInt()
                val fx = modelX - x0
                val c0 = x0.coerceIn(minX, maxX)
                val c1 = (x0 + 1).coerceIn(minX, maxX)
                val top = mask[row0 + c0] * (1 - fx) + mask[row0 + c1] * fx
                val bottom = mask[row1 + c0] * (1 - fx) + mask[row1 + c1] * fx
                val value = top * (1 - fy) + bottom * fy
                val coverage = ((value - HALF) * edgeGain + HALF).coerceIn(0f, 1f)
                alpha[y * imageWidth + x] = (coverage * 255f).roundToInt().toByte()
            }
        }
        return AlphaMask(imageWidth, imageHeight, alpha)
    }
}
