package com.damianogiusti.stickermaker.feature.packs.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.usecase.CreateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetLastPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.RememberPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.RenameStickerPackUseCase
import com.damianogiusti.stickermaker.ui.coroutines.SingleEventFlow

enum class FieldError { EMPTY, TOO_LONG }

enum class FormPhase { LOADING, LOAD_FAILED, EDITING, SAVING }

data class PackFormUiState(
    val name: String = "",
    val publisher: String = "",
    val phase: FormPhase = FormPhase.LOADING,
    val hasTriedToSave: Boolean = false,
    val hasSaveFailed: Boolean = false,
) {
    val nameError: FieldError? get() = errorOf(name)
    val publisherError: FieldError? get() = errorOf(publisher)
    val canSave: Boolean get() = phase == FormPhase.EDITING

    // A too-long value is reported while typing; an empty one only after the first save attempt.
    private fun errorOf(value: String): FieldError? = when {
        value.trim().length > StickerPackLimits.MAX_TEXT_LENGTH -> FieldError.TOO_LONG
        value.trim().isEmpty() && hasTriedToSave -> FieldError.EMPTY
        else -> null
    }

    val isValid: Boolean
        get() = listOf(name, publisher).all { it.trim().length in 1..StickerPackLimits.MAX_TEXT_LENGTH }
}

/** Create form when [packId] is null, rename form otherwise. */
class PackFormViewModel(
    private val packId: String?,
    private val observePack: ObserveStickerPackUseCase,
    private val createPack: CreateStickerPackUseCase,
    private val renamePack: RenameStickerPackUseCase,
    private val getLastPublisher: GetLastPublisherUseCase,
    private val rememberPublisher: RememberPublisherUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PackFormUiState())
    val state: StateFlow<PackFormUiState> = _state
        .onSubscription { loadInitialValues() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), _state.value)

    private val _savedEvents = SingleEventFlow<String>()
    val savedEvents = _savedEvents.asSharedFlow()

    private var hasLoaded = false

    fun onNameChange(name: String) = _state.update { it.copy(name = name, hasSaveFailed = false) }

    fun onPublisherChange(publisher: String) = _state.update { it.copy(publisher = publisher, hasSaveFailed = false) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        if (!current.isValid) {
            _state.update { it.copy(hasTriedToSave = true) }
            return
        }
        _state.update { it.copy(phase = FormPhase.SAVING, hasSaveFailed = false) }
        viewModelScope.launch {
            val result = if (packId == null) {
                createPack(current.name, current.publisher).map { it.id.value }
            } else {
                renamePack(StickerPackId(packId), current.name, current.publisher).map { packId }
            }
            result.fold(
                ifLeft = { error -> onSaveFailed(error) },
                ifRight = { savedId ->
                    rememberPublisher(current.publisher)
                    _savedEvents.tryEmit(savedId)
                },
            )
        }
    }

    private fun onSaveFailed(error: StickerPackError) {
        // InvalidName / InvalidPublisher mirror the local validation; anything else is a storage problem.
        val isFieldProblem = error is StickerPackError.InvalidName || error is StickerPackError.InvalidPublisher
        _state.update { it.copy(phase = FormPhase.EDITING, hasTriedToSave = true, hasSaveFailed = !isFieldProblem) }
    }

    private suspend fun loadInitialValues() {
        if (hasLoaded) return
        hasLoaded = true
        if (packId == null) {
            _state.update { it.copy(publisher = getLastPublisher(), phase = FormPhase.EDITING) }
            return
        }
        observePack(StickerPackId(packId)).first().fold(
            ifLeft = { _state.update { it.copy(phase = FormPhase.LOAD_FAILED) } },
            ifRight = { pack ->
                _state.update { it.copy(name = pack.name, publisher = pack.publisher, phase = FormPhase.EDITING) }
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
