package com.damianogiusti.stickermaker.imaging

import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import com.damianogiusti.stickermaker.imaging.cut.CutSelection
import com.damianogiusti.stickermaker.imaging.cut.boundsIn
import com.damianogiusti.stickermaker.imaging.geometry.IntBounds
import com.damianogiusti.stickermaker.imaging.geometry.ImageGeometry
import com.damianogiusti.stickermaker.imaging.geometry.PixelSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageGeometryTest {

    @Test
    fun `limitLongSide keeps a small image as is`() {
        assertEquals(PixelSize(800, 600), ImageGeometry.limitLongSide(800, 600, 2000))
    }

    @Test
    fun `limitLongSide scales the long side down and keeps the ratio`() {
        assertEquals(PixelSize(2000, 1000), ImageGeometry.limitLongSide(4000, 2000, 2000))
        assertEquals(PixelSize(1500, 2000), ImageGeometry.limitLongSide(3000, 4000, 2000))
    }

    @Test
    fun `fitCentered centers a wide image in the square`() {
        val fit = ImageGeometry.fitCentered(200, 100, 1024)
        assertEquals(0f, fit.left)
        assertEquals(1024f, fit.width)
        assertEquals(512f, fit.height)
        assertEquals(256f, fit.top)
    }

    @Test
    fun `fitCentered upscales a small image`() {
        val fit = ImageGeometry.fitCentered(10, 10, 512)
        assertEquals(512f, fit.width)
        assertEquals(0f, fit.left)
    }

    @Test
    fun `findOpaqueBounds returns the box of non transparent pixels`() {
        val width = 5
        val height = 4
        val pixels = IntArray(width * height)
        pixels[1 * width + 2] = 0x01000000
        pixels[2 * width + 4] = 0xFF112233.toInt()
        assertEquals(IntBounds(2, 1, 5, 3), ImageGeometry.findOpaqueBounds(pixels, width, height))
    }

    @Test
    fun `findOpaqueBounds is null for a fully transparent image`() {
        assertNull(ImageGeometry.findOpaqueBounds(IntArray(9), 3, 3))
    }

    @Test
    fun `findOpaqueBounds of a full image is the whole image`() {
        val pixels = IntArray(6) { 0xFF000000.toInt() }
        assertEquals(IntBounds(0, 0, 3, 2), ImageGeometry.findOpaqueBounds(pixels, 3, 2))
    }

    @Test
    fun `inflateAndClamp stays inside the image`() {
        assertEquals(IntBounds(0, 0, 12, 12), ImageGeometry.inflateAndClamp(IntBounds(1, 1, 10, 10), 2, 12, 12))
    }

    @Test
    fun `selection bounds are clamped to the image`() {
        val selection = CutSelection.Rectangle(-5f, 2.5f, 50f, 20f)
        assertEquals(IntBounds(0, 2, 30, 20), selection.boundsIn(30, 40))
    }

    @Test
    fun `selection outside the image has no bounds`() {
        assertNull(CutSelection.Rectangle(100f, 100f, 120f, 120f).boundsIn(50, 50))
        assertNull(CutSelection.Lasso(emptyList()).boundsIn(50, 50))
    }

    @Test
    fun `whole image selection covers the image`() {
        assertEquals(IntBounds(0, 0, 7, 9), CutSelection.WholeImage.boundsIn(7, 9))
    }

    @Test
    fun `polygon bounds follow its vertices`() {
        val polygon = CutSelection.Polygon(listOf(CutPoint(3.2f, 4f), CutPoint(10f, 4.5f), CutPoint(6f, 12.1f)))
        assertEquals(IntBounds(3, 4, 10, 13), polygon.boundsIn(100, 100))
    }
}
