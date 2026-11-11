package com.damianogiusti.stickermaker.imaging.text

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Toggle order of the style button. */
enum class TextDrawStyle {
    WITH_BACKGROUND,
    WITH_STROKE,
    WITHOUT_BACKGROUND;

    fun next(): TextDrawStyle = entries[(ordinal + 1) % entries.size]
}

/** Toggle order of the font button. */
enum class StickerFont(val displayName: String) {
    ANTON("Anton"),
    ALFA_SLAB_ONE("Alfa Slab One"),
    DYNAPUFF_SEMIBOLD("DynaPuff"),
    CUTIVE_MONO("Cutive Mono"),
    FREDOKA_ONE("Fredoka"),
    FRECKLE_FACE("Freckle Face"),
    SEDGWICK_AVE_DISPLAY("Sedgwick Ave Display"),
    NUNITO("Nunito"),
    COMIC_NEUE("Comic Neue"),
    SYSTEM("System");

    fun next(): StickerFont = entries[(ordinal + 1) % entries.size]
}

/** [color] is the swatch (background or stroke), [textColor] the fill. Both ARGB. */
data class TextColorPair(val color: Int, val textColor: Int)

object StickerPalette {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    val pairs: List<TextColorPair> = listOf(
        TextColorPair(BLACK, WHITE),
        TextColorPair(0xFFFA3439.toInt(), WHITE),
        TextColorPair(0xFF0C35EB.toInt(), WHITE),
        TextColorPair(WHITE, BLACK),
        TextColorPair(0xFFD132EE.toInt(), WHITE),
        TextColorPair(0xFFEFF054.toInt(), BLACK),
        TextColorPair(0xFF5AEC45.toInt(), WHITE),
    )

    /** The outline uses the same swatches; white by default. */
    const val DEFAULT_COLOR_INDEX = 3
}

/**
 * A text on the 1024x1024 sticker canvas. [centerX]/[centerY] are in canvas pixels, [scale] multiplies
 * the base text size, [rotationDegrees] turns clockwise around the center.
 */
data class StickerTextItem(
    val id: Long,
    val text: String,
    val style: TextDrawStyle,
    val font: StickerFont,
    val colorIndex: Int,
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotationDegrees: Float,
) {
    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 10f
    }
}

data class TextBox(val width: Float, val height: Float)

/** Pure hit-testing of a text item, in canvas coordinates. */
object TextItemGeometry {
    fun contains(item: StickerTextItem, box: TextBox, canvasX: Float, canvasY: Float): Boolean {
        val dx = canvasX - item.centerX
        val dy = canvasY - item.centerY
        val radians = Math.toRadians(item.rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        // Undo the rotation, then the scale.
        val localX = (dx * cosine + dy * sine) / item.scale
        val localY = (-dx * sine + dy * cosine) / item.scale
        return abs(localX) <= box.width / 2f && abs(localY) <= box.height / 2f
    }

    fun clampScale(scale: Float): Float = scale.coerceIn(StickerTextItem.MIN_SCALE, StickerTextItem.MAX_SCALE)
}
