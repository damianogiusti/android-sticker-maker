package com.damianogiusti.stickermaker.imaging.cut

import com.damianogiusti.stickermaker.imaging.geometry.IntBounds
import kotlin.math.ceil
import kotlin.math.floor

data class CutPoint(val x: Float, val y: Float)

/** What the user selected on the working image. Coordinates are in image pixels. */
sealed interface CutSelection {
    /** Select all: the whole image, no mask. */
    data object WholeImage : CutSelection
    data class Lasso(val points: List<CutPoint>) : CutSelection
    data class Polygon(val points: List<CutPoint>) : CutSelection
    data class Rectangle(val left: Float, val top: Float, val right: Float, val bottom: Float) : CutSelection
    data class Oval(val left: Float, val top: Float, val right: Float, val bottom: Float) : CutSelection
}

/** Pixel bounds of the selection clamped to the image, or null when it has no area inside the image. */
fun CutSelection.boundsIn(imageWidth: Int, imageHeight: Int): IntBounds? {
    val raw = when (this) {
        CutSelection.WholeImage -> IntBounds(0, 0, imageWidth, imageHeight)
        is CutSelection.Lasso -> pointBounds(points)
        is CutSelection.Polygon -> pointBounds(points)
        is CutSelection.Rectangle -> IntBounds(floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())
        is CutSelection.Oval -> IntBounds(floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())
    } ?: return null
    val clamped = IntBounds(
        raw.left.coerceAtLeast(0),
        raw.top.coerceAtLeast(0),
        raw.right.coerceAtMost(imageWidth),
        raw.bottom.coerceAtMost(imageHeight),
    )
    return clamped.takeUnless { it.isEmpty }
}

private fun pointBounds(points: List<CutPoint>): IntBounds? {
    if (points.isEmpty()) return null
    return IntBounds(
        floor(points.minOf { it.x }).toInt(),
        floor(points.minOf { it.y }).toInt(),
        ceil(points.maxOf { it.x }).toInt(),
        ceil(points.maxOf { it.y }).toInt(),
    )
}
