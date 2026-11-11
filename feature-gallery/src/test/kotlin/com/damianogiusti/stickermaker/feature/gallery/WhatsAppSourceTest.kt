package com.damianogiusti.stickermaker.feature.gallery

import kotlin.test.Test
import kotlin.test.assertEquals

class WhatsAppSourceTest {
    @Test
    fun `document ids point at the sticker folders on the primary volume`() {
        assertEquals(
            "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
            WhatsAppSource.WHATSAPP.documentId,
        )
        assertEquals(
            "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers",
            WhatsAppSource.WHATSAPP_BUSINESS.documentId,
        )
    }
}
