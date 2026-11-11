package com.damianogiusti.stickermaker.share

/** What the share adapter reads from an Intent, as plain values so the rule below is testable on the JVM. */
data class IncomingIntent(
    val action: String?,
    val mimeType: String?,
    val streamUri: String?,
    val isLaunchedFromHistory: Boolean,
    val isConsumed: Boolean,
)

object SharedImageIntent {
    const val ACTION_SEND = "android.intent.action.SEND"

    /**
     * The image URI to open in the editor, or null when the intent is not a share of one image. The same
     * intent is delivered again when the task is brought back from Recents (or after a rotation); opening
     * it twice would fail on an expired URI grant, so such intents are ignored. Only content URIs are
     * accepted: a file: URI would expose the app to arbitrary paths.
     */
    fun imageUriOf(intent: IncomingIntent): String? {
        if (intent.isLaunchedFromHistory || intent.isConsumed) return null
        if (intent.action != ACTION_SEND) return null
        if (intent.mimeType?.startsWith("image/") != true) return null
        return intent.streamUri?.takeIf { it.startsWith("content://") }
    }
}
