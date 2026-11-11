package com.damianogiusti.stickermaker.feature.packs

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.usecase.CreateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetLastPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.RememberPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.RenameStickerPackUseCase
import com.damianogiusti.stickermaker.feature.packs.form.FieldError
import com.damianogiusti.stickermaker.feature.packs.form.FormPhase
import com.damianogiusti.stickermaker.feature.packs.form.PackFormUiState
import com.damianogiusti.stickermaker.feature.packs.form.PackFormViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackFormViewModelTest : MainDispatcherTest() {
    private val repository = FakeStickerPackRepository(listOf(aPack(id = "7", name = "Cats", publisher = "Me")))
    private val preferences = FakePublisherPreferences(stored = "Last Publisher")

    private fun viewModel(packId: String?) = PackFormViewModel(
        packId = packId,
        observePack = ObserveStickerPackUseCase(repository),
        createPack = CreateStickerPackUseCase(repository),
        renamePack = RenameStickerPackUseCase(repository),
        getLastPublisher = GetLastPublisherUseCase(preferences),
        rememberPublisher = RememberPublisherUseCase(preferences),
    )

    private suspend fun PackFormViewModel.loaded(): PackFormUiState =
        state.first { it.phase != FormPhase.LOADING }

    @Test
    fun `new pack form prefills the remembered publisher`() = runTest {
        val vm = viewModel(null)
        vm.state.test {
            assertEquals(FormPhase.LOADING, awaitItem().phase)
            val loaded = awaitItem()
            assertEquals(FormPhase.EDITING, loaded.phase)
            assertEquals("Last Publisher", loaded.publisher)
            assertEquals("", loaded.name)
        }
    }

    @Test
    fun `rename form prefills the current name and publisher`() = runTest {
        val loaded = viewModel("7").loaded()
        assertEquals("Cats", loaded.name)
        assertEquals("Me", loaded.publisher)
    }

    @Test
    fun `rename form for a missing pack fails to load`() = runTest {
        assertEquals(FormPhase.LOAD_FAILED, viewModel("404").loaded().phase)
    }

    @Test
    fun `empty name is reported only after the first save attempt`() = runTest {
        val vm = viewModel(null)
        vm.state.test {
            skipItems(1)
            assertNull(awaitItem().nameError)
            vm.save()
            assertEquals(FieldError.EMPTY, awaitItem().nameError)
            assertTrue(repository.createdPacks.isEmpty())
        }
    }

    @Test
    fun `a name longer than 128 chars is reported while typing`() = runTest {
        val vm = viewModel(null)
        vm.state.test {
            skipItems(2)
            vm.onNameChange("a".repeat(129))
            val state = awaitItem()
            assertEquals(FieldError.TOO_LONG, state.nameError)
            assertFalse(state.isValid)
            vm.onNameChange("a".repeat(128))
            assertNull(awaitItem().nameError)
        }
    }

    @Test
    fun `saving a new pack creates it, remembers the publisher and emits its id`() = runTest {
        val vm = viewModel(null)
        vm.state.test {
            skipItems(2)
            vm.savedEvents.test {
                vm.onNameChange("  Dogs ")
                vm.onPublisherChange("Someone")
                vm.save()
                assertEquals("new-1", awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("Dogs" to "Someone"), repository.createdPacks)
        assertEquals("Someone", preferences.stored)
    }

    @Test
    fun `saving a rename updates the pack and emits the same id`() = runTest {
        val vm = viewModel("7")
        vm.state.test {
            skipItems(2)
            vm.savedEvents.test {
                vm.onNameChange("Big Cats")
                vm.save()
                assertEquals("7", awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(Triple(StickerPackId("7"), "Big Cats", "Me")), repository.renamedPacks)
    }

    @Test
    fun `a storage failure returns to editing with a save error`() = runTest {
        repository.failWrites = true
        val vm = viewModel(null)
        vm.state.test {
            skipItems(2)
            vm.onNameChange("Dogs")
            skipItems(1)
            vm.save()
            val state = settled(testScheduler)
            assertEquals(FormPhase.EDITING, state.phase)
            assertTrue(state.hasSaveFailed)
            assertEquals(emptyList(), repository.createdPacks)
        }
    }
}
