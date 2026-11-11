package com.damianogiusti.stickermaker.feature.editor

import arrow.core.Either
import arrow.core.left
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.clipboard.ClipboardError
import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import com.damianogiusti.stickermaker.imaging.ExportError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CopyStickerUseCaseTest {
    private val exporter = FakeStickerExporter()
    private val clipboard = FakeStickerClipboard()
    private val useCase = CopyStickerUseCase(exporter, clipboard, StickerImageFormat.WEBP)

    @Test
    fun `exports the composition in the configured format and copies the bytes`() = runTest {
        val cutout = fakeBitmap()

        val result = useCase(cutout, emptyList())

        assertIs<Either.Right<Unit>>(result)
        assertEquals(listOf(StickerImageFormat.WEBP), exporter.formats)
        assertEquals(cutout, exporter.compositions.single().outlinedCutout)
        val copied = clipboard.copied.single()
        assertEquals(StickerImageFormat.WEBP, copied.format)
        assertContentEquals(byteArrayOf(1, 2, 3), copied.bytes)
    }

    @Test
    fun `the PNG fallback is a constructor argument`() = runTest {
        CopyStickerUseCase(exporter, clipboard, StickerImageFormat.PNG)(fakeBitmap(), emptyList())

        assertEquals(StickerImageFormat.PNG, clipboard.copied.single().format)
    }

    @Test
    fun `an export error is returned and nothing is copied`() = runTest {
        exporter.result = ExportError.EmptyComposite.left()

        val result = useCase(null, emptyList())

        assertEquals(CopyStickerError.Export(ExportError.EmptyComposite), result.leftOrNull())
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test
    fun `a clipboard error is returned`() = runTest {
        clipboard.failure = ClipboardError.Failed("denied")

        val result = useCase(fakeBitmap(), emptyList())

        assertEquals(CopyStickerError.Clipboard(ClipboardError.Failed("denied")), result.leftOrNull())
    }
}
