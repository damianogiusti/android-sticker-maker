package com.damianogiusti.stickermaker.feature.editor

import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardFilesTest {
    @Test
    fun `file name is the timestamp with the format extension`() {
        assertEquals("1700000000000.webp", ClipboardFiles.fileName(1_700_000_000_000, StickerImageFormat.WEBP))
        assertEquals("5.png", ClipboardFiles.fileName(5, StickerImageFormat.PNG))
    }

    @Test
    fun `every file but the current one is stale`() {
        val stale = ClipboardFiles.staleFileNames(listOf("1.webp", "2.webp", "3.webp", "old.png"), current = "3.webp")

        assertEquals(listOf("1.webp", "2.webp", "old.png"), stale)
    }

    @Test
    fun `nothing is stale when only the current file exists`() {
        assertEquals(emptyList(), ClipboardFiles.staleFileNames(listOf("3.webp"), current = "3.webp"))
    }
}
