package com.damianogiusti.stickermaker.imaging.outline

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The slider (0..40) in thousandths of the bitmap side, like the original `progress / 1000`. */
object OutlineMath {
    const val MAX_WIDTH_PERMILLE = 40
    const val DEFAULT_WIDTH_PERMILLE = 10
    const val DEFAULT_WIDTH_PERMILLE_AFTER_SELECT_ALL = 0

    private const val PERMILLE = 1000f
    private const val SHRINK_MARGIN = 0.01f
    private const val INFINITE = 1e20f

    /** Scale applied to the image before dilation, so the outline fits: `1 - 2w - 0.01`. */
    fun shrinkScale(widthPermille: Int): Float = 1f - 2f * (widthPermille / PERMILLE) - SHRINK_MARGIN

    fun radiusPx(widthPermille: Int, sidePx: Int): Float = widthPermille / PERMILLE * sidePx

    /**
     * Outline coverage (0..255) for every pixel: 255 within [radius] of any pixel with alpha > 0,
     * with a one pixel anti-aliased edge. Uses an exact Euclidean distance transform, so the cost is
     * linear in the pixel count whatever the radius.
     */
    fun outlineAlpha(alpha: IntArray, width: Int, height: Int, radius: Float): IntArray {
        if (radius <= 0f) return IntArray(alpha.size)
        val squared = squaredDistanceToVisible(alpha, width, height)
        return IntArray(alpha.size) { index ->
            val coverage = (radius + 0.5f - sqrt(squared[index])).coerceIn(0f, 1f)
            (coverage * 255f).roundToInt()
        }
    }

    /** Squared distance (in pixels) from each pixel to the nearest pixel with alpha > 0. */
    fun squaredDistanceToVisible(alpha: IntArray, width: Int, height: Int): FloatArray {
        val grid = FloatArray(alpha.size) { if (alpha[it] > 0) 0f else INFINITE }
        val line = FloatArray(max(width, height))
        val result = FloatArray(max(width, height))
        val hull = IntArray(max(width, height))
        val bounds = FloatArray(max(width, height) + 1)
        // Columns first, then rows (Felzenszwalb and Huttenlocher, separable and exact).
        for (x in 0 until width) {
            for (y in 0 until height) line[y] = grid[y * width + x]
            transform1d(line, result, hull, bounds, height)
            for (y in 0 until height) grid[y * width + x] = result[y]
        }
        for (y in 0 until height) {
            for (x in 0 until width) line[x] = grid[y * width + x]
            transform1d(line, result, hull, bounds, width)
            for (x in 0 until width) grid[y * width + x] = result[x]
        }
        return grid
    }

    private fun transform1d(source: FloatArray, target: FloatArray, hull: IntArray, bounds: FloatArray, count: Int) {
        var last = 0
        hull[0] = 0
        bounds[0] = -INFINITE
        bounds[1] = INFINITE
        for (q in 1 until count) {
            var crossing = intersection(source, q, hull[last])
            while (crossing <= bounds[last]) {
                last--
                crossing = intersection(source, q, hull[last])
            }
            last++
            hull[last] = q
            bounds[last] = crossing
            bounds[last + 1] = INFINITE
        }
        var k = 0
        for (q in 0 until count) {
            while (bounds[k + 1] < q) k++
            val delta = (q - hull[k]).toFloat()
            target[q] = delta * delta + source[hull[k]]
        }
    }

    private fun intersection(source: FloatArray, q: Int, p: Int): Float =
        ((source[q] + q.toFloat() * q) - (source[p] + p.toFloat() * p)) / (2f * q - 2f * p)
}
