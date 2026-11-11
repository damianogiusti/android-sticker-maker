package com.damianogiusti.stickermaker.feature.packs.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue
import com.damianogiusti.stickermaker.feature.packs.R

/** Maps every [ValidationIssue] to a user-readable string; the `when` is exhaustive on purpose. */
@Composable
internal fun PackIssue.text(): String {
    val number = stickerNumber ?: 0
    return when (issue) {
        ValidationIssue.InvalidPackId -> stringResource(R.string.issue_invalid_pack_id)
        ValidationIssue.InvalidName -> stringResource(R.string.issue_invalid_name)
        ValidationIssue.InvalidPublisher -> stringResource(R.string.issue_invalid_publisher)
        ValidationIssue.MissingTray -> stringResource(R.string.issue_missing_tray)
        is ValidationIssue.TooFewStickers -> stringResource(R.string.issue_too_few_stickers, issue.count)
        is ValidationIssue.TooManyStickers -> stringResource(R.string.issue_too_many_stickers, issue.count)
        is ValidationIssue.MissingEmoji -> stringResource(R.string.issue_missing_emoji, number)
        is ValidationIssue.TooManyEmojis -> stringResource(R.string.issue_too_many_emojis, number)
        is ValidationIssue.FileFactsMissing -> if (stickerNumber == null) {
            stringResource(R.string.issue_tray_unreadable)
        } else {
            stringResource(R.string.issue_file_unreadable, number)
        }
        is ValidationIssue.StickerNotWebp -> stringResource(R.string.issue_sticker_not_webp, number)
        is ValidationIssue.StickerAnimated -> stringResource(R.string.issue_sticker_animated, number)
        is ValidationIssue.StickerWrongSize ->
            stringResource(R.string.issue_sticker_wrong_size, number, issue.width, issue.height)
        is ValidationIssue.StickerTooLarge -> stringResource(R.string.issue_sticker_too_large, number, issue.sizeBytes / BYTES_PER_KB)
        is ValidationIssue.TrayNotPng -> stringResource(R.string.issue_tray_not_png)
        is ValidationIssue.TrayWrongSize -> stringResource(R.string.issue_tray_wrong_size, issue.width, issue.height)
        is ValidationIssue.TrayTooLarge -> stringResource(R.string.issue_tray_too_large, issue.sizeBytes / BYTES_PER_KB)
    }
}

private const val BYTES_PER_KB = 1024L
