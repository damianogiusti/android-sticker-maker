package com.damianogiusti.stickermaker.imaging.cut

import android.graphics.Path
import android.graphics.RectF

/** Builds the android [Path] of a selection, in image coordinates. */
object SelectionPaths {

    fun toPath(selection: CutSelection, imageWidth: Int, imageHeight: Int): Path = when (selection) {
        CutSelection.WholeImage -> Path().apply { addRect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat(), Path.Direction.CW) }
        is CutSelection.Lasso -> lassoPath(selection.points)
        is CutSelection.Polygon -> polygonPath(selection.points)
        is CutSelection.Rectangle -> Path().apply {
            addRect(RectF(selection.left, selection.top, selection.right, selection.bottom), Path.Direction.CW)
        }
        is CutSelection.Oval -> Path().apply {
            addOval(RectF(selection.left, selection.top, selection.right, selection.bottom), Path.Direction.CW)
        }
    }

    /** Quadratic smoothing through the midpoints of consecutive points; the fill closes it. */
    private fun lassoPath(points: List<CutPoint>): Path {
        val path = Path()
        val first = points.firstOrNull() ?: return path
        path.moveTo(first.x, first.y)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            path.quadTo(previous.x, previous.y, (previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
        }
        val last = points.last()
        path.lineTo(last.x, last.y)
        path.close()
        return path
    }

    private fun polygonPath(points: List<CutPoint>): Path {
        val path = Path()
        val first = points.firstOrNull() ?: return path
        path.moveTo(first.x, first.y)
        for (index in 1 until points.size) path.lineTo(points[index].x, points[index].y)
        path.close()
        return path
    }
}
