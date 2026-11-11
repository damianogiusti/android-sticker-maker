package com.damianogiusti.stickermaker.feature.packs.detail

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppRequest
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppResult
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp

private const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
private const val EXTRA_PACK_ID = "sticker_pack_id"
private const val EXTRA_PACK_AUTHORITY = "sticker_pack_authority"
private const val EXTRA_PACK_NAME = "sticker_pack_name"

/** Thin Android adapter: the decisions (which app, whether to send) are made in the domain. */
internal fun AddToWhatsAppRequest.toIntent(app: WhatsAppApp): Intent =
    Intent(ACTION_ENABLE_STICKER_PACK)
        .setPackage(app.packageName)
        .putExtra(EXTRA_PACK_ID, packId.value)
        .putExtra(EXTRA_PACK_AUTHORITY, authority.value)
        .putExtra(EXTRA_PACK_NAME, packName)

internal fun ActivityResult.toAddToWhatsAppResult(): AddToWhatsAppResult {
    val extras = data?.extras
    return AddToWhatsAppResult.fromResult(
        isCancelled = resultCode == Activity.RESULT_CANCELED,
        extraKeys = extras?.keySet().orEmpty(),
        validationError = extras?.getString(AddToWhatsAppResult.EXTRA_VALIDATION_ERROR),
    )
}
