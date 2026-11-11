package com.damianogiusti.stickermaker.share

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [pendingImageUri] is the shared image to open until [IncomingShareViewModel.onEditorOpened]. It is state,
 * not a one-shot event: the intent can arrive before the NavHost is collecting, and state is not lost.
 */
data class IncomingShareUiState(val pendingImageUri: String? = null)

class IncomingShareViewModel : ViewModel() {
    private val _state = MutableStateFlow(IncomingShareUiState())
    val state: StateFlow<IncomingShareUiState> = _state.asStateFlow()

    fun onImageShared(uri: String) = _state.update { it.copy(pendingImageUri = uri) }

    fun onEditorOpened() = _state.update { it.copy(pendingImageUri = null) }
}
