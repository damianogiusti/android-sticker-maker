package com.damianogiusti.stickermaker.domain.model

/** The two official WhatsApp apps that can receive a sticker pack. */
enum class WhatsAppApp(val packageName: String) {
    WHATSAPP("com.whatsapp"),
    BUSINESS("com.whatsapp.w4b"),
}

/** Content provider authority our app exposes to WhatsApp. */
@JvmInline
value class StickerAuthority(val value: String)

/** Everything WhatsApp needs to start the "add sticker pack" flow. */
data class AddToWhatsAppRequest(
    val packId: StickerPackId,
    val authority: StickerAuthority,
    val packName: String,
)

/** What to do when the user taps "Add to WhatsApp". */
sealed interface AddToWhatsAppPlan {
    data class Launch(val request: AddToWhatsAppRequest, val app: WhatsAppApp) : AddToWhatsAppPlan
    data class ChooseApp(val request: AddToWhatsAppRequest, val apps: List<WhatsAppApp>) : AddToWhatsAppPlan
    data object NoWhatsAppInstalled : AddToWhatsAppPlan
}

/** The outcome reported by WhatsApp in the activity result extras. */
sealed interface AddToWhatsAppResult {
    data object Added : AddToWhatsAppResult
    data object AlreadyAdded : AddToWhatsAppResult

    /** WhatsApp refused the pack; [message] is its `validation_error` text, when present. */
    data class Rejected(val message: String?) : AddToWhatsAppResult

    /** No extras and the activity result says cancelled: the user left WhatsApp without choosing. */
    data object Cancelled : AddToWhatsAppResult

    /** `RESULT_OK` but no extras: WhatsApp did not say what happened, so the screen asks the user to check. */
    data object Unconfirmed : AddToWhatsAppResult

    companion object {
        const val EXTRA_ADD_SUCCESSFUL = "add_successful"
        const val EXTRA_ALREADY_ADDED = "already_added"
        const val EXTRA_VALIDATION_ERROR = "validation_error"

        /**
         * [extraKeys] are the keys present in the result intent (empty when there is no intent) and
         * [isCancelled] is `resultCode == RESULT_CANCELED`. The extras win: WhatsApp can report a
         * rejection with a cancelled code.
         */
        fun fromResult(isCancelled: Boolean, extraKeys: Set<String>, validationError: String?): AddToWhatsAppResult = when {
            EXTRA_ADD_SUCCESSFUL in extraKeys -> Added
            EXTRA_ALREADY_ADDED in extraKeys -> AlreadyAdded
            !validationError.isNullOrBlank() -> Rejected(validationError)
            extraKeys.isEmpty() -> if (isCancelled) Cancelled else Unconfirmed
            else -> Rejected(null)
        }
    }
}
