package com.damianogiusti.stickermaker.feature.packs

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerEmojisUseCase
import com.damianogiusti.stickermaker.feature.packs.emoji.EmojiPickerUiState
import com.damianogiusti.stickermaker.feature.packs.emoji.EmojiPickerViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmojiPickerViewModelTest : MainDispatcherTest() {
    private val repository = FakeStickerPackRepository(
        listOf(aPack(id = "7", stickers = listOf(aSticker(0, emojis = listOf("😀"))))),
    )

    private fun viewModel(fileName: String = "0.webp") = EmojiPickerViewModel(
        packId = "7",
        fileName = fileName,
        observePack = ObserveStickerPackUseCase(repository),
        setEmojis = SetStickerEmojisUseCase(repository),
    )

    private suspend fun EmojiPickerViewModel.content() =
        assertIs<EmojiPickerUiState.Content>(state.first { it !is EmojiPickerUiState.Loading })

    @Test
    fun `starts Loading then shows the current emojis`() = runTest {
        viewModel().state.test {
            assertEquals(EmojiPickerUiState.Loading, awaitItem())
            assertEquals(listOf("😀"), assertIs<EmojiPickerUiState.Content>(awaitItem()).selected)
        }
    }

    @Test
    fun `unknown sticker is an Error`() = runTest {
        assertEquals(EmojiPickerUiState.Error, viewModel("nope.webp").state.first { it !is EmojiPickerUiState.Loading })
    }

    @Test
    fun `at most 3 distinct emojis can be selected`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.onEmojiPicked("😀")
            assertNoChange(testScheduler)
            vm.onEmojiPicked("🐱")
            vm.onEmojiPicked("🐶")
            val full = assertIs<EmojiPickerUiState.Content>(settled(testScheduler))
            assertEquals(listOf("😀", "🐱", "🐶"), full.selected)
            assertTrue(full.isFull)
            vm.onEmojiPicked("🦊")
            assertNoChange(testScheduler)
        }
    }

    @Test
    fun `removing an emoji frees a slot`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.onEmojiRemoved("😀")
            val state = assertIs<EmojiPickerUiState.Content>(awaitItem())
            assertEquals(emptyList(), state.selected)
            assertFalse(state.isFull)
        }
    }

    @Test
    fun `save writes the selection and emits saved`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.onEmojiPicked("🐱")
            skipItems(1)
            vm.savedEvents.test {
                vm.save()
                awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(listOf("😀", "🐱")), repository.emojiWrites)
    }

    @Test
    fun `a failed save keeps the selection and reports the failure`() = runTest {
        repository.failWrites = true
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.save()
            val state = assertIs<EmojiPickerUiState.Content>(settled(testScheduler))
            assertTrue(state.hasSaveFailed)
            assertFalse(state.isSaving)
            assertEquals(listOf("😀"), state.selected)
        }
    }
}
