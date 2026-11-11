package com.damianogiusti.stickermaker.feature.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StickerMimeTypeTest {

    @Test
    fun `sticker webp wins when accepted together with others`() {
        val accepted = listOf("image/png", "image/webp", "image/webp.wasticker")
        assertEquals(StickerMimeType.WEBP_STICKER, selectStickerMimeType(accepted))
    }

    @Test
    fun `plain webp is chosen over png`() {
        assertEquals(StickerMimeType.WEBP, selectStickerMimeType(listOf("image/png", "image/webp")))
    }

    @Test
    fun `png is the last resort`() {
        assertEquals(StickerMimeType.PNG, selectStickerMimeType(listOf("image/gif", "image/png")))
    }

    @Test
    fun `no accepted image type gives null`() {
        assertNull(selectStickerMimeType(emptyList()))
        assertNull(selectStickerMimeType(listOf("image/gif", "text/plain")))
    }

    @Test
    fun `image wildcard accepts webp but not the sticker type`() {
        assertEquals(StickerMimeType.WEBP, selectStickerMimeType(listOf("image/*")))
    }

    @Test
    fun `full wildcard accepts webp`() {
        assertEquals(StickerMimeType.WEBP, selectStickerMimeType(listOf("*/*")))
    }

    @Test
    fun `matching ignores case and spaces`() {
        assertEquals(StickerMimeType.WEBP_STICKER, selectStickerMimeType(listOf("Image/WebP.wasticker")))
        assertEquals(StickerMimeType.PNG, selectStickerMimeType(listOf(" IMAGE/PNG ")))
    }

    @Test
    fun `non image wildcard does not match`() {
        assertNull(selectStickerMimeType(listOf("text/*", "video/*")))
    }
}
