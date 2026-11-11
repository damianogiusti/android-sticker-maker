package com.damianogiusti.stickermaker.feature.editor

import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import com.damianogiusti.stickermaker.imaging.cut.CutSelection
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Thresholds of the cutting gestures (SPEC section 4). Pure, so they are unit-tested. */
object GestureRules {
    const val MIN_LASSO_POINTS = 6
    private const val DPI_PER_STROKE_PX = 60f
    private const val SHAPE_MIN_STROKES = 5f

    /** Screen pixels of the cut stroke at this density. */
    fun strokeWidthPx(densityDpi: Float): Float = densityDpi / DPI_PER_STROKE_PX

    /** A lasso point is added only when the finger moved at least this far, in image pixels. */
    fun lassoMinStepInImagePx(densityDpi: Float, scale: Float): Float = strokeWidthPx(densityDpi) / scale

    fun shouldAddLassoPoint(last: CutPoint?, next: CutPoint, minStep: Float): Boolean =
        last == null || hypot(next.x - last.x, next.y - last.y) >= minStep

    fun isLassoAccepted(points: List<CutPoint>): Boolean = points.size >= MIN_LASSO_POINTS

    /**
     * A drag becomes a shape unless it is smaller than 5 stroke widths on both axes.
     * [strokeWidthInImagePx] is the stroke width converted to image pixels.
     */
    fun normalizedBox(start: CutPoint, end: CutPoint, strokeWidthInImagePx: Float): Box? {
        val minSize = SHAPE_MIN_STROKES * strokeWidthInImagePx
        if (abs(end.x - start.x) < minSize && abs(end.y - start.y) < minSize) return null
        return Box(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
    }

    /** The polygon closes when a tap lands near the first vertex (needs 3 vertices at least). */
    fun isClosingTap(vertices: List<CutPoint>, tap: CutPoint, closeRadiusInImagePx: Float): Boolean {
        val first = vertices.firstOrNull() ?: return false
        return vertices.size >= MIN_POLYGON_VERTICES && hypot(tap.x - first.x, tap.y - first.y) <= closeRadiusInImagePx
    }

    const val MIN_POLYGON_VERTICES = 3

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun toRectangle() = CutSelection.Rectangle(left, top, right, bottom)
        fun toOval() = CutSelection.Oval(left, top, right, bottom)
    }
}
