package com.damianogiusti.stickermaker.imaging.geometry

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PixelSize(val width: Int, val height: Int)

/** Pixel bounds, [right] and [bottom] exclusive. */
data class IntBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Pure geometry of the pipeline, kept free of Android types so it runs on the JVM. */
object ImageGeometry {

    /** Same aspect ratio, long side at most [maxLongSide]. Never upscales. */
    fun limitLongSide(width: Int, height: Int, maxLongSide: Int): PixelSize {
        val longSide = max(width, height)
        if (longSide <= maxLongSide) return PixelSize(width, height)
        val factor = maxLongSide.toFloat() / longSide
        return PixelSize(
            (width * factor).roundToInt().coerceAtLeast(1),
            (height * factor).roundToInt().coerceAtLeast(1),
        )
    }

    /** Aspect-fit of a [srcWidth]x[srcHeight] image in a transparent square, centered. May upscale. */
    fun fitCentered(srcWidth: Int, srcHeight: Int, side: Int): FitRect {
        val factor = min(side.toFloat() / srcWidth, side.toFloat() / srcHeight)
        val width = srcWidth * factor
        val height = srcHeight * factor
        return FitRect((side - width) / 2f, (side - height) / 2f, width, height)
    }

    /** Smallest bounds holding every pixel whose alpha is not 0, or null if the image is fully transparent. */
    fun findOpaqueBounds(argbPixels: IntArray, width: Int, height: Int): IntBounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                if ((argbPixels[rowStart + x] ushr 24) == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                bottom = y
            }
        }
        if (right < 0) return null
        return IntBounds(left, top, right + 1, bottom + 1)
    }

    /** Grows [bounds] by [margin] on each side and clamps the result to the image. */
    fun inflateAndClamp(bounds: IntBounds, margin: Int, imageWidth: Int, imageHeight: Int): IntBounds = IntBounds(
        left = (bounds.left - margin).coerceAtLeast(0),
        top = (bounds.top - margin).coerceAtLeast(0),
        right = (bounds.right + margin).coerceAtMost(imageWidth),
        bottom = (bounds.bottom + margin).coerceAtMost(imageHeight),
    )
}
