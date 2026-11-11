package com.damianogiusti.stickermaker.feature.keyboard

import android.content.Intent
import androidx.core.view.inputmethod.InputConnectionCompat

/**
 * How to hand a content URI to the target app, by API level. Pure, so it is unit tested.
 *
 * From API 25 the system grants read access when we pass `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`.
 * Below, the flag does nothing: we grant the permission ourselves before `commitContent`, as the
 * official CommitContent sample does.
 */
internal data class CommitPlan(val commitFlags: Int, val manualGrantFlags: Int?)

internal object CommitContentPolicy {
    private const val FIRST_API_WITH_SYSTEM_GRANT = 25

    fun planFor(sdkInt: Int): CommitPlan = if (sdkInt >= FIRST_API_WITH_SYSTEM_GRANT) {
        CommitPlan(commitFlags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, manualGrantFlags = null)
    } else {
        CommitPlan(commitFlags = 0, manualGrantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Only a commit the target accepted counts as sent, so only then it enters the history. */
    fun outcomeOf(committed: Boolean): SendOutcome = if (committed) SendOutcome.SENT else SendOutcome.FAILED
}
