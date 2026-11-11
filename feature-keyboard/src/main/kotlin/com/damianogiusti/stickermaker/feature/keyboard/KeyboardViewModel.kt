package com.damianogiusti.stickermaker.feature.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.usecase.ObserveKeyboardHistoryUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.domain.usecase.RecordKeyboardStickerUseCase

private const val TAG = "KeyboardViewModel"

/** Keeps `android.util.Log` out of the ViewModel, so its tests run on the plain JVM. */
internal fun interface KeyboardLogger {
    fun warn(tag: String, message: String)
}

/** Result of handing a sticker to the editor; computed by the Android sender. */
internal enum class SendOutcome { SENT, NOT_ACCEPTED, FAILED }

internal enum class KeyboardNotice { NOT_ACCEPTED, SEND_FAILED }

internal sealed interface KeyboardUiState {
    data object Loading : KeyboardUiState
    data object NoPacks : KeyboardUiState
    data object Error : KeyboardUiState
    data class Content(val tabs: List<KeyboardTab>, val notice: KeyboardNotice?) : KeyboardUiState
}

internal class KeyboardViewModel(
    observePacks: ObserveStickerPacksUseCase,
    observeHistory: ObserveKeyboardHistoryUseCase,
    private val recordSticker: RecordKeyboardStickerUseCase,
    private val logger: KeyboardLogger,
) : ViewModel() {

    private val notice = MutableStateFlow<KeyboardNotice?>(null)

    val state: StateFlow<KeyboardUiState> = combine(observePacks(), observeHistory(), notice) { packsResult, historyResult, currentNotice ->
        packsResult.fold(
            ifLeft = { KeyboardUiState.Error },
            ifRight = { packs ->
                if (packs.none { it.isAvailableInKeyboard }) {
                    KeyboardUiState.NoPacks
                } else {
                    // History is a convenience: if it fails to load, the pack tabs still work.
                    val history = historyResult.fold(ifLeft = { emptyList() }, ifRight = { it })
                    KeyboardUiState.Content(buildKeyboardTabs(packs, history), currentNotice)
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeyboardUiState.Loading)

    fun onSendOutcome(sticker: KeyboardSticker, outcome: SendOutcome) {
        when (outcome) {
            SendOutcome.SENT -> {
                notice.value = null
                record(sticker)
            }
            SendOutcome.NOT_ACCEPTED -> notice.value = KeyboardNotice.NOT_ACCEPTED
            SendOutcome.FAILED -> notice.value = KeyboardNotice.SEND_FAILED
        }
    }

    fun dismissNotice() {
        notice.value = null
    }

    private fun record(sticker: KeyboardSticker) {
        viewModelScope.launch {
            recordSticker(StickerPackId(sticker.packId), sticker.fileName).fold(
                // Best effort: a failed history write must not disturb a sticker already sent.
                ifLeft = { error -> logger.warn(TAG, "cannot record ${sticker.fileName} in the history: $error") },
                ifRight = { },
            )
        }
    }
}
