package com.damianogiusti.stickermaker.feature.packs.transfer

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.feature.packs.R

enum class TransferKind { IMPORT, BACKUP, RESTORE }

/** Progress, success and failure of a file transfer, explicit so the screen never guesses. */
sealed interface TransferUiState {
    data object Idle : TransferUiState
    data class InProgress(val kind: TransferKind) : TransferUiState
    data class Succeeded(val kind: TransferKind, val packCount: Int) : TransferUiState
    data class Failed(val kind: TransferKind, val error: TransferError) : TransferUiState
}

@StringRes
fun TransferError.messageRes(): Int = when (this) {
    TransferError.NotAZip -> R.string.transfer_error_not_a_zip
    is TransferError.UnsafeEntry -> R.string.transfer_error_unsafe
    TransferError.TooLarge -> R.string.transfer_error_too_large
    TransferError.TooManyStickers -> R.string.transfer_error_too_many_stickers
    TransferError.NoStickers -> R.string.transfer_error_no_stickers
    is TransferError.NotAWebp -> R.string.transfer_error_not_webp
    is TransferError.AnimatedNotSupported -> R.string.transfer_error_animated
    is TransferError.WrongStickerSize -> R.string.transfer_error_wrong_size
    is TransferError.InvalidBackup -> R.string.transfer_error_invalid_backup
    is TransferError.UnsupportedBackupVersion -> R.string.transfer_error_backup_version
    is TransferError.Io -> R.string.transfer_error_io
    is TransferError.Pack -> if (error is StickerPackError.TooManyStickers) {
        R.string.transfer_error_too_many_stickers
    } else {
        R.string.transfer_error_generic
    }
}

@Composable
fun TransferErrorDialog(error: TransferError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_error_title)) },
        text = { Text(stringResource(error.messageRes())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_ok)) } },
    )
}

@Composable
fun TransferProgressDialog(kind: TransferKind) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    when (kind) {
                        TransferKind.IMPORT -> R.string.transfer_progress_import
                        TransferKind.BACKUP -> R.string.transfer_progress_backup
                        TransferKind.RESTORE -> R.string.transfer_progress_restore
                    },
                ),
            )
        },
        text = { CircularProgressIndicator() },
        confirmButton = {},
    )
}
