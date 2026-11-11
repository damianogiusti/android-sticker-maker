package com.damianogiusti.stickermaker.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.usecase.ImportStickerPackUseCase

sealed interface ImportPhase {
    data object Idle : ImportPhase
    data object Importing : ImportPhase
    data class Failed(val error: TransferError) : ImportPhase
}

/**
 * [importedPackId] is the pack to open until [IncomingImportViewModel.onPackOpened]. It is state, not a
 * one-shot event: the intent can arrive before the NavHost is collecting, and state is not lost.
 */
data class IncomingImportUiState(
    val phase: ImportPhase = ImportPhase.Idle,
    val importedPackId: String? = null,
)

/**
 * Imports a `.wastickers` file another app handed to us (VIEW / SEND). The new pack id is state, not
 * a one-shot event: the intent can arrive before the NavHost is collecting, and state is not lost.
 */
class IncomingImportViewModel(
    private val importPack: ImportStickerPackUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomingImportUiState())
    val state: StateFlow<IncomingImportUiState> = _state.asStateFlow()

    fun importFrom(location: String) {
        _state.update { it.copy(phase = ImportPhase.Importing) }
        viewModelScope.launch {
            importPack(location).fold(
                ifLeft = { error -> _state.update { it.copy(phase = ImportPhase.Failed(error)) } },
                ifRight = { pack -> _state.update { it.copy(phase = ImportPhase.Idle, importedPackId = pack.id.value) } },
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(phase = ImportPhase.Idle) }
    }

    fun onPackOpened() {
        _state.update { it.copy(importedPackId = null) }
    }
}
