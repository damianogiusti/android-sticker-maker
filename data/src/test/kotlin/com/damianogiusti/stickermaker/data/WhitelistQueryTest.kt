package com.damianogiusti.stickermaker.data

import com.damianogiusti.stickermaker.data.whatsapp.WhitelistQuery
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhitelistQueryTest {
    private val authority = StickerAuthority("com.damianogiusti.stickermaker.stickercontentprovider")

    @Test
    fun `builds the query uri per WhatsApp package`() {
        assertEquals(
            "content://com.whatsapp.provider.sticker_whitelist_check/is_whitelisted" +
                "?authority=com.damianogiusti.stickermaker.stickercontentprovider&identifier=1700000000",
            WhitelistQuery.uri(WhatsAppApp.WHATSAPP, authority, StickerPackId("1700000000")),
        )
        assertTrue(
            WhitelistQuery.uri(WhatsAppApp.BUSINESS, authority, StickerPackId("1"))
                .startsWith("content://com.whatsapp.w4b.provider.sticker_whitelist_check/"),
        )
    }

    @Test
    fun `escapes the identifier`() {
        val uri = WhitelistQuery.uri(WhatsAppApp.WHATSAPP, authority, StickerPackId("a b&c=d"))
        assertTrue(uri.endsWith("identifier=a%20b%26c%3Dd"))
    }

    @Test
    fun `only the value 1 means whitelisted`() {
        assertTrue(WhitelistQuery.isWhitelisted(1))
        assertFalse(WhitelistQuery.isWhitelisted(0))
        assertFalse(WhitelistQuery.isWhitelisted(2))
        assertFalse(WhitelistQuery.isWhitelisted(null))
    }
}
