package com.damianogiusti.stickermaker.imaging

import com.damianogiusti.stickermaker.imaging.segmentation.ClassMask
import com.damianogiusti.stickermaker.imaging.segmentation.Letterbox
import com.damianogiusti.stickermaker.imaging.segmentation.LetterboxRect
import com.damianogiusti.stickermaker.imaging.segmentation.MaskUpscaler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentationMathTest {

    @Test
    fun `kept classes are car cat cow dog horse motorbike person and potted plant`() {
        assertEquals(setOf(7, 8, 10, 12, 13, 14, 15, 16), ClassMask.keptClasses)
        assertFalse(ClassMask.isKept(0))
        assertFalse(ClassMask.isKept(9))
        assertFalse(ClassMask.isKept(20))
    }

    @Test
    fun `fromIndices marks only kept classes`() {
        val mask = ClassMask.fromIndices(intArrayOf(0, 15, 9, 8, 20, 16))
        assertContentEquals(floatArrayOf(0f, 1f, 0f, 1f, 0f, 1f), mask)
    }

    @Test
    fun `fromLogits takes the argmax of every pixel`() {
        val classes = 21
        val logits = FloatArray(2 * classes)
        logits[0 * classes + 15] = 5f // pixel 0: person
        logits[1 * classes + 1] = 3f // pixel 1: aeroplane, not kept
        logits[1 * classes + 15] = 2f
        assertContentEquals(floatArrayOf(1f, 0f), ClassMask.fromLogits(logits, classes))
    }

    @Test
    fun `a pixel with equal scores falls on class 0 background`() {
        assertContentEquals(floatArrayOf(0f), ClassMask.fromLogits(FloatArray(21), 21))
    }

    @Test
    fun `letterbox fits a wide image in a square input`() {
        assertEquals(LetterboxRect(0, 64, 257, 129), Letterbox.fit(1000, 500, 257, 257))
    }

    @Test
    fun `letterbox of a tall image pads left and right`() {
        val box = Letterbox.fit(500, 1000, 256, 256)
        assertEquals(LetterboxRect(64, 0, 128, 256), box)
    }

    @Test
    fun `upscaling keeps a subject and its hole`() {
        // 8x8 model mask: a ring (subject with a hole) and a separate dot.
        val size = 8
        val mask = FloatArray(size * size)
        for (y in 1..5) for (x in 1..5) mask[y * size + x] = 1f
        for (y in 2..4) for (x in 2..4) mask[y * size + x] = 0f
        mask[7 * size + 7] = 1f
        val alpha = MaskUpscaler.toImageAlpha(mask, size, size, LetterboxRect(0, 0, size, size), 32, 32).alpha
        fun at(x: Int, y: Int) = alpha[y * 32 + x].toInt() and 0xFF
        assertEquals(255, at(5, 5)) // ring
        assertEquals(0, at(14, 14)) // hole center
        assertEquals(0, at(1, 1)) // outside
        assertEquals(255, at(31, 31)) // second subject
    }

    @Test
    fun `an empty class mask gives an empty alpha mask`() {
        val mask = MaskUpscaler.toImageAlpha(FloatArray(16), 4, 4, LetterboxRect(0, 0, 4, 4), 10, 10)
        assertTrue(mask.isEmpty)
    }

    @Test
    fun `the edge is soft over about one pixel`() {
        // Left half foreground, right half background.
        val size = 4
        val mask = FloatArray(size * size) { if (it % size < 2) 1f else 0f }
        val alpha = MaskUpscaler.toImageAlpha(mask, size, size, LetterboxRect(0, 0, size, size), 8, 8).alpha
        val row = (0 until 8).map { alpha[3 * 8 + it].toInt() and 0xFF }
        assertEquals(255, row[0])
        assertEquals(0, row[7])
        assertTrue(row.zipWithNext().all { (a, b) -> b <= a }, "monotonic edge: $row")
    }

    @Test
    fun `letterbox padding does not leak into the alpha`() {
        // The image occupies rows 1..2 of a 4x4 model input; the padding rows are foreground by mistake.
        val size = 4
        val mask = FloatArray(size * size)
        for (x in 0 until size) { mask[0 * size + x] = 1f; mask[3 * size + x] = 1f }
        val alpha = MaskUpscaler.toImageAlpha(mask, size, size, LetterboxRect(0, 1, 4, 2), 8, 4)
        assertTrue(alpha.isEmpty)
    }
}
