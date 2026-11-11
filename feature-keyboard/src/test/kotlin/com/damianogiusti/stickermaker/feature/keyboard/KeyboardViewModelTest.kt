package com.damianogiusti.stickermaker.feature.keyboard

import app.cash.turbine.test
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.usecase.ObserveKeyboardHistoryUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.domain.usecase.RecordKeyboardStickerUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KeyboardViewModelTest : MainDispatcherTest() {

    private val packs = FakeStickerPackRepository()
    private val history = FakeKeyboardHistoryRepository()
    // Lazy: the ViewModel scope needs the test Main dispatcher, set after construction of the test class.
    private val viewModel by lazy {
        KeyboardViewModel(
            ObserveStickerPacksUseCase(packs),
            ObserveKeyboardHistoryUseCase(history),
            RecordKeyboardStickerUseCase(history, fixedClock),
            { tag, message -> warnings += "$tag: $message" },
        )
    }
    private val warnings = mutableListOf<String>()
    private val sticker = KeyboardSticker("1", "0.webp", 1)

    @Test
    fun `no pack enabled shows the empty state`() = runTest {
        packs.packs.value = listOf(aPack("1", keyboardAddedAt = null)).right()

        viewModel.state.test {
            assertEquals(KeyboardUiState.NoPacks, expectMostRecentItem())
        }
    }

    @Test
    fun `pack load failure shows the error state`() = runTest {
        packs.packs.value = StickerPackError.Database("x").left()

        viewModel.state.test {
            assertEquals(KeyboardUiState.Error, expectMostRecentItem())
        }
    }

    @Test
    fun `enabled packs show history and pack tabs`() = runTest {
        packs.packs.value = listOf(aPack("1", keyboardAddedAt = 5)).right()
        history.history.value = listOf(KeyboardHistoryEntry(StickerPackId("1"), "1.webp", 1)).right()

        viewModel.state.test {
            val content = assertIs<KeyboardUiState.Content>(expectMostRecentItem())
            assertEquals(2, content.tabs.size)
            assertEquals(listOf("1.webp"), content.tabs.first().stickers.map { it.fileName })
        }
    }

    @Test
    fun `sent sticker is recorded in history`() = runTest {
        viewModel.onSendOutcome(sticker, SendOutcome.SENT)

        assertEquals(listOf(KeyboardHistoryEntry(StickerPackId("1"), "0.webp", 1_000L)), history.recorded.toList())
    }

    @Test
    fun `rejected sticker is not recorded and shows the notice`() = runTest {
        packs.packs.value = listOf(aPack("1", keyboardAddedAt = 5)).right()

        viewModel.state.test {
            viewModel.onSendOutcome(sticker, SendOutcome.NOT_ACCEPTED)
            val content = assertIs<KeyboardUiState.Content>(expectMostRecentItem())
            assertEquals(KeyboardNotice.NOT_ACCEPTED, content.notice)
        }
        assertEquals(emptyList(), history.recorded.toList())
    }

    @Test
    fun `notice clears after a successful send`() = runTest {
        packs.packs.value = listOf(aPack("1", keyboardAddedAt = 5)).right()

        viewModel.state.test {
            viewModel.onSendOutcome(sticker, SendOutcome.FAILED)
            assertEquals(KeyboardNotice.SEND_FAILED, assertIs<KeyboardUiState.Content>(expectMostRecentItem()).notice)
            viewModel.onSendOutcome(sticker, SendOutcome.SENT)
            assertEquals(null, assertIs<KeyboardUiState.Content>(expectMostRecentItem()).notice)
        }
    }

    @Test
    fun `history write failure does not break the state`() = runTest {
        history.failsOnRecord = true
        packs.packs.value = listOf(aPack("1", keyboardAddedAt = 5)).right()

        viewModel.state.test {
            viewModel.onSendOutcome(sticker, SendOutcome.SENT)
            assertIs<KeyboardUiState.Content>(expectMostRecentItem())
        }
        assertEquals(1, warnings.size)
    }
}
