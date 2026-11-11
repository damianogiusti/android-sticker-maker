package com.damianogiusti.stickermaker.imaging

import com.damianogiusti.stickermaker.imaging.text.StickerFont
import com.damianogiusti.stickermaker.imaging.text.StickerPalette
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem
import com.damianogiusti.stickermaker.imaging.text.TextBox
import com.damianogiusti.stickermaker.imaging.text.TextDrawStyle
import com.damianogiusti.stickermaker.imaging.text.TextItemGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextGeometryTest {
    private val box = TextBox(200f, 100f)

    private fun item(scale: Float = 1f, rotation: Float = 0f) = StickerTextItem(
        id = 1, text = "Hi", style = TextDrawStyle.WITH_BACKGROUND, font = StickerFont.ANTON, colorIndex = 0,
        centerX = 500f, centerY = 500f, scale = scale, rotationDegrees = rotation,
    )

    @Test
    fun `a point inside the box hits and outside misses`() {
        assertTrue(TextItemGeometry.contains(item(), box, 590f, 540f))
        assertFalse(TextItemGeometry.contains(item(), box, 610f, 500f))
    }

    @Test
    fun `scale grows the hit area`() {
        assertTrue(TextItemGeometry.contains(item(scale = 2f), box, 690f, 500f))
        assertFalse(TextItemGeometry.contains(item(scale = 1f), box, 690f, 500f))
    }

    @Test
    fun `rotation turns the hit area`() {
        // Rotated by 90 degrees the box is 100 wide and 200 tall.
        assertTrue(TextItemGeometry.contains(item(rotation = 90f), box, 500f, 590f))
        assertFalse(TextItemGeometry.contains(item(rotation = 90f), box, 590f, 500f))
    }

    @Test
    fun `scale is clamped to 0_5 and 10`() {
        assertEquals(0.5f, TextItemGeometry.clampScale(0.1f))
        assertEquals(10f, TextItemGeometry.clampScale(50f))
        assertEquals(3f, TextItemGeometry.clampScale(3f))
    }

    @Test
    fun `style and font toggles cycle`() {
        assertEquals(TextDrawStyle.WITH_STROKE, TextDrawStyle.WITH_BACKGROUND.next())
        assertEquals(TextDrawStyle.WITH_BACKGROUND, TextDrawStyle.WITHOUT_BACKGROUND.next())
        assertEquals(StickerFont.ANTON, StickerFont.SYSTEM.next())
        assertEquals(10, StickerFont.entries.size)
    }

    @Test
    fun `palette has the 7 pairs`() {
        assertEquals(7, StickerPalette.pairs.size)
        assertEquals(0xFFFFFFFF.toInt(), StickerPalette.pairs[StickerPalette.DEFAULT_COLOR_INDEX].color)
    }
}
