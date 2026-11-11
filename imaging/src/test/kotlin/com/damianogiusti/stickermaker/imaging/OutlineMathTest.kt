package com.damianogiusti.stickermaker.imaging

import com.damianogiusti.stickermaker.imaging.outline.OutlineMath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineMathTest {

    @Test
    fun `shrink scale follows 1 minus 2w minus 0_01`() {
        assertEquals(0.99f, OutlineMath.shrinkScale(0), 1e-6f)
        assertEquals(0.97f, OutlineMath.shrinkScale(10), 1e-6f)
        assertEquals(0.91f, OutlineMath.shrinkScale(40), 1e-6f)
    }

    @Test
    fun `radius is the slider in thousandths of the side`() {
        assertEquals(10.24f, OutlineMath.radiusPx(10, 1024), 1e-4f)
        assertEquals(0f, OutlineMath.radiusPx(0, 1024))
    }

    @Test
    fun `zero radius gives no outline`() {
        val alpha = IntArray(25).also { it[12] = 255 }
        assertContentEquals(IntArray(25), OutlineMath.outlineAlpha(alpha, 5, 5, 0f))
    }

    @Test
    fun `a single pixel grows into a disc`() {
        val size = 9
        val alpha = IntArray(size * size).also { it[4 * size + 4] = 255 }
        val outline = OutlineMath.outlineAlpha(alpha, size, size, 3f)
        fun at(dx: Int, dy: Int) = outline[(4 + dy) * size + 4 + dx]
        assertEquals(255, at(0, 0))
        assertEquals(255, at(2, 0)) // distance 2 is inside the radius
        assertEquals(255, at(2, 1)) // distance 2.24
        assertEquals(171, at(2, 2)) // distance 2.83: inside the edge band
        assertEquals(128, at(3, 0)) // on the edge: half coverage
        assertEquals(0, at(3, 3)) // distance 4.24: a square kernel would have painted this corner
        assertEquals(0, at(4, 0))
    }

    @Test
    fun `distance transform matches brute force`() {
        val width = 11
        val height = 7
        val alpha = IntArray(width * height)
        alpha[1 * width + 2] = 255
        alpha[5 * width + 9] = 10
        alpha[3 * width + 6] = 255
        val fast = OutlineMath.squaredDistanceToVisible(alpha, width, height)
        for (y in 0 until height) for (x in 0 until width) {
            var best = Float.MAX_VALUE
            for (sy in 0 until height) for (sx in 0 until width) {
                if (alpha[sy * width + sx] > 0) best = minOf(best, ((x - sx) * (x - sx) + (y - sy) * (y - sy)).toFloat())
            }
            assertTrue(abs(best - fast[y * width + x]) < 1e-3f, "mismatch at $x,$y: $best vs ${fast[y * width + x]}")
        }
    }

    @Test
    fun `an empty image stays without outline`() {
        val outline = OutlineMath.outlineAlpha(IntArray(16), 4, 4, 5f)
        assertTrue(outline.all { it == 0 })
    }

    @Test
    fun `a 1024 square with the widest outline stays fast`() {
        val side = 1024
        val alpha = IntArray(side * side)
        for (y in 300..700) for (x in 300..700) alpha[y * side + x] = 255
        val start = System.nanoTime()
        val outline = OutlineMath.outlineAlpha(alpha, side, side, OutlineMath.radiusPx(40, side))
        val millis = (System.nanoTime() - start) / 1_000_000
        assertEquals(255, outline[299 * side + 500])
        assertTrue(millis < 2_000, "took $millis ms")
    }
}
