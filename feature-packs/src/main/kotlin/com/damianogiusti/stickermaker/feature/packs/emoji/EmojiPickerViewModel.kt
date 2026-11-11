package com.damianogiusti.stickermaker.feature.packs.emoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerEmojisUseCase
import com.damianogiusti.stickermaker.ui.coroutines.SingleEventFlow

sealed interface EmojiPickerUiState {
    data object Loading : EmojiPickerUiState
    data object Error : EmojiPickerUiState
    data class Content(
        val selected: List<String>,
        val isSaving: Boolean = false,
        val hasSaveFailed: Boolean = false,
    ) : EmojiPickerUiState {
        val isFull: Boolean get() = selected.size >= StickerPackLimits.MAX_EMOJIS_PER_STICKER
        val canSave: Boolean get() = !isSaving
    }
}

class EmojiPickerViewModel(
    private val packId: String,
    private val fileName: String,
    private val observePack: ObserveStickerPackUseCase,
    private val setEmojis: SetStickerEmojisUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<EmojiPickerUiState>(EmojiPickerUiState.Loading)
    val state: StateFlow<EmojiPickerUiState> = _state
        .onSubscription { loadSelection() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), _state.value)

    private val _savedEvents = SingleEventFlow<Unit>()
    val savedEvents = _savedEvents.asSharedFlow()

    /** Ignored when the sticker already has 3 emojis or the emoji is already selected. */
    fun onEmojiPicked(emoji: String) = updateContent { content ->
        if (content.isFull || emoji in content.selected) content else content.copy(selected = content.selected + emoji)
    }

    fun onEmojiRemoved(emoji: String) = updateContent { it.copy(selected = it.selected - emoji) }

    fun save() {
        val content = _state.value as? EmojiPickerUiState.Content ?: return
        if (!content.canSave) return
        updateContent { it.copy(isSaving = true, hasSaveFailed = false) }
        viewModelScope.launch {
            setEmojis(StickerPackId(packId), fileName, content.selected).fold(
                ifLeft = { updateContent { it.copy(isSaving = false, hasSaveFailed = true) } },
                ifRight = { _savedEvents.tryEmit(Unit) },
            )
        }
    }

    private fun updateContent(transform: (EmojiPickerUiState.Content) -> EmojiPickerUiState.Content) =
        _state.update { current -> if (current is EmojiPickerUiState.Content) transform(current) else current }

    // Loads once: later subscriptions must not wipe the user's unsaved selection.
    private suspend fun loadSelection() {
        if (_state.value !is EmojiPickerUiState.Loading) return
        observePack(StickerPackId(packId)).first().fold(
            ifLeft = { _state.value = EmojiPickerUiState.Error },
            ifRight = { pack ->
                val sticker = pack.stickers.find { it.fileName == fileName }
                _state.value = if (sticker == null) {
                    EmojiPickerUiState.Error
                } else {
                    EmojiPickerUiState.Content(selected = sticker.emojis)
                }
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
