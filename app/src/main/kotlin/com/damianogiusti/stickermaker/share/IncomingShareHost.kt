package com.damianogiusti.stickermaker.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Opens the editor on an image another app shared with us. */
@Composable
fun IncomingShareHost(
    onOpenEditor: (imageUri: String) -> Unit,
    viewModel: IncomingShareViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending = state.pendingImageUri
    LaunchedEffect(pending) {
        pending?.let {
            onOpenEditor(it)
            viewModel.onEditorOpened()
        }
    }
}
