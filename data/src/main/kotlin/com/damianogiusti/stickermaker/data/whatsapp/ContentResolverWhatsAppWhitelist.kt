package com.damianogiusti.stickermaker.data.whatsapp

import android.content.ContentResolver
import android.net.Uri
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.domain.error.WhitelistError
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.repository.WhatsAppWhitelist

class ContentResolverWhatsAppWhitelist(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher,
) : WhatsAppWhitelist {

    override suspend fun isWhitelisted(
        app: WhatsAppApp,
        authority: StickerAuthority,
        packId: StickerPackId,
    ): Either<WhitelistError, Boolean> = withContext(dispatcher) {
        Either.catch { query(WhitelistQuery.uri(app, authority, packId)) }
            .mapLeft { WhitelistError.Unavailable(app.packageName, it.message) }
    }

    private fun query(uri: String): Boolean {
        val cursor = contentResolver.query(Uri.parse(uri), null, null, null, null)
            ?: error("The whitelist provider returned no cursor")
        return cursor.use {
            val value = if (it.moveToFirst()) it.getInt(it.getColumnIndexOrThrow(WhitelistQuery.RESULT_COLUMN)) else null
            WhitelistQuery.isWhitelisted(value)
        }
    }
}
