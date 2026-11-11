package com.damianogiusti.stickermaker.data.whatsapp

import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import java.net.URLEncoder

/** Pure parts of the WhatsApp whitelist check: the query URI and the answer column. */
internal object WhitelistQuery {
    const val RESULT_COLUMN = "result"
    private const val IS_WHITELISTED_VALUE = 1

    fun uri(app: WhatsAppApp, authority: StickerAuthority, packId: StickerPackId): String =
        "content://${app.packageName}.provider.sticker_whitelist_check/is_whitelisted" +
            "?authority=${encode(authority.value)}&identifier=${encode(packId.value)}"

    /** Anything but `1` (including a missing value) means "not added". */
    fun isWhitelisted(resultValue: Int?): Boolean = resultValue == IS_WHITELISTED_VALUE

    // URLEncoder writes a space as "+", which not every query parser reads back as a space.
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
