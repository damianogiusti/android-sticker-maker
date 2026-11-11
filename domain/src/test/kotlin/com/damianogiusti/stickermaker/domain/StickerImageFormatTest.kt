package com.damianogiusti.stickermaker.domain

import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class StickerImageFormatTest {
    // The clipboard adapter trusts these values for the file name and the clip mime type.
    @Test
    fun `webp is the format of a sticker`() {
        assertEquals("image/webp", StickerImageFormat.WEBP.mimeType)
        assertEquals("webp", StickerImageFormat.WEBP.fileExtension)
    }

    @Test
    fun `png is the fallback format`() {
        assertEquals("image/png", StickerImageFormat.PNG.mimeType)
        assertEquals("png", StickerImageFormat.PNG.fileExtension)
    }
}
