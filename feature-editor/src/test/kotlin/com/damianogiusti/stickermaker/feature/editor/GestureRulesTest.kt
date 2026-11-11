package com.damianogiusti.stickermaker.feature.editor

import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureRulesTest {

    @Test
    fun `stroke width is dpi over 60 and the lasso step shrinks with zoom`() {
        assertEquals(8f, GestureRules.strokeWidthPx(480f))
        assertEquals(8f, GestureRules.lassoMinStepInImagePx(480f, 1f))
        assertEquals(4f, GestureRules.lassoMinStepInImagePx(480f, 2f))
    }

    @Test
    fun `lasso point needs a minimum move`() {
        val last = CutPoint(0f, 0f)
        assertTrue(GestureRules.shouldAddLassoPoint(null, CutPoint(0f, 0f), 5f))
        assertFalse(GestureRules.shouldAddLassoPoint(last, CutPoint(3f, 3f), 5f))
        assertTrue(GestureRules.shouldAddLassoPoint(last, CutPoint(3f, 4f), 5f))
    }

    @Test
    fun `lasso needs at least 6 points`() {
        assertFalse(GestureRules.isLassoAccepted(List(5) { CutPoint(it.toFloat(), 0f) }))
        assertTrue(GestureRules.isLassoAccepted(List(6) { CutPoint(it.toFloat(), 0f) }))
    }

    @Test
    fun `a shape smaller than 5 strokes on both axes is rejected`() {
        assertNull(GestureRules.normalizedBox(CutPoint(10f, 10f), CutPoint(30f, 30f), 5f))
    }

    @Test
    fun `a shape big enough on one axis is accepted and normalized`() {
        val box = GestureRules.normalizedBox(CutPoint(100f, 50f), CutPoint(20f, 52f), 5f)
        assertEquals(GestureRules.Box(20f, 50f, 100f, 52f), box)
    }

    @Test
    fun `a polygon closes near the first vertex only with 3 vertices`() {
        val two = listOf(CutPoint(0f, 0f), CutPoint(50f, 0f))
        val three = two + CutPoint(25f, 40f)
        assertFalse(GestureRules.isClosingTap(two, CutPoint(1f, 1f), 12f))
        assertTrue(GestureRules.isClosingTap(three, CutPoint(5f, 5f), 12f))
        assertFalse(GestureRules.isClosingTap(three, CutPoint(20f, 20f), 12f))
    }
}
