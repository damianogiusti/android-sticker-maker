package com.damianogiusti.stickermaker.imaging.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/**
 * Draws a [StickerTextItem] on a canvas. The editor preview and the export use this same code, so what
 * the user sees is what is saved. Sizes are in 1024x1024 canvas pixels.
 */
class TextItemPainter(private val typefaces: FontTypefaces) {

    fun measure(item: StickerTextItem): TextBox {
        val layout = layoutOf(item, fillPaint(item))
        return boxOf(layout)
    }

    fun draw(canvas: Canvas, item: StickerTextItem) {
        val pair = StickerPalette.pairs[item.colorIndex.coerceIn(StickerPalette.pairs.indices)]
        val fill = fillPaint(item)
        val layout = layoutOf(item, fill)
        val box = boxOf(layout)

        canvas.save()
        canvas.translate(item.centerX, item.centerY)
        canvas.rotate(item.rotationDegrees)
        canvas.scale(item.scale, item.scale)
        canvas.translate(-box.width / 2f, -box.height / 2f)

        when (item.style) {
            TextDrawStyle.WITH_BACKGROUND -> {
                val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pair.color }
                canvas.drawRoundRect(RectF(0f, 0f, box.width, box.height), CORNER_RADIUS, CORNER_RADIUS, background)
                fill.color = pair.textColor
                drawLayout(canvas, layout)
            }
            TextDrawStyle.WITH_STROKE -> {
                val stroke = fillPaint(item).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = STROKE_WIDTH
                    strokeJoin = Paint.Join.ROUND
                    color = pair.color
                }
                drawLayout(canvas, layoutOf(item, stroke))
                fill.color = pair.textColor
                drawLayout(canvas, layout)
            }
            // The swatch color is the text color, so the choice stays visible on a transparent sticker.
            TextDrawStyle.WITHOUT_BACKGROUND -> {
                fill.color = pair.color
                drawLayout(canvas, layout)
            }
        }
        canvas.restore()
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout) {
        canvas.save()
        canvas.translate(PADDING, PADDING)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun fillPaint(item: StickerTextItem): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typefaces.typefaceOf(item.font)
        textSize = BASE_TEXT_SIZE
    }

    private fun layoutOf(item: StickerTextItem, paint: TextPaint): StaticLayout {
        val text = item.text.ifEmpty { " " }
        val lineWidths = text.split('\n').maxOf { Layout.getDesiredWidth(it, paint) }
        val width = ceil(lineWidths).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
    }

    private fun boxOf(layout: StaticLayout) = TextBox(layout.width + 2 * PADDING, layout.height + 2 * PADDING)

    private companion object {
        const val BASE_TEXT_SIZE = 80f
        const val STROKE_WIDTH = 14f
        const val PADDING = 20f
        const val CORNER_RADIUS = 24f
    }
}
