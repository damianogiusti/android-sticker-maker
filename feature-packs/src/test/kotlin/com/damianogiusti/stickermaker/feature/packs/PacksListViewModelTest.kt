package com.damianogiusti.stickermaker.feature.packs

import app.cash.turbine.test
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.usecase.CheckPackInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.CreateBackupUseCase
import com.damianogiusti.stickermaker.domain.usecase.ImportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.RestoreBackupUseCase
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferKind
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferUiState
import kotlinx.coroutines.CompletableDeferred
import com.damianogiusti.stickermaker.domain.usecase.GetPacksInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.feature.packs.list.PacksListBody
import com.damianogiusti.stickermaker.feature.packs.list.PacksListViewModel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacksListViewModelTest : MainDispatcherTest() {
    // The ViewModel exposes one state; these views keep each test about one slice of it.
    private val PacksListViewModel.body get() = state.map { it.body }.distinctUntilChanged()
    private val PacksListViewModel.transferState get() = state.map { it.transfer }.distinctUntilChanged()

    private val repository = FakeStickerPackRepository()
    private val installations = FakeWhatsAppInstallations(listOf(WhatsAppApp.WHATSAPP))
    private val whitelist = FakeWhatsAppWhitelist()
    private val viewModel by lazy {
        PacksListViewModel(
            ObserveStickerPacksUseCase(repository),
            testFileResolver,
            GetPacksInWhatsAppUseCase(repository, CheckPackInWhatsAppUseCase(installations, whitelist, testAuthority)),
            ImportStickerPackUseCase(store, importRepository),
            CreateBackupUseCase(repository, FakePackFileSource(), store),
            RestoreBackupUseCase(store, importRepository, repository),
            errorLogger,
        )
    }
    private val errorLogger = RecordingErrorLogger()
    private val store = FakePackArchiveStore()
    private val importRepository = FakePackImportRepository()

    @Test
    fun `starts Loading then shows Empty when there are no packs`() = runTest {
        viewModel.body.test {
            assertEquals(PacksListBody.Loading, awaitItem())
            assertEquals(PacksListBody.Empty, awaitItem())
        }
    }

    @Test
    fun `maps packs to render-ready items with tray file and sticker count`() = runTest {
        repository.packs.value = listOf(
            aPack(id = "1", name = "Cats", stickers = listOf(aSticker(0), aSticker(1))),
            aPack(id = "2", name = "Dogs", trayFileName = null),
        ).right()

        viewModel.body.test {
            val content = expectContent(testScheduler)
            assertEquals(listOf("Cats", "Dogs"), content.packs.map { it.name })
            assertEquals(2, content.packs[0].stickerCount)
            assertEquals(File("/packs/1/tray.png"), content.packs[0].tray?.file)
            assertEquals(3, content.packs[0].tray?.version)
            assertNull(content.packs[1].tray)
        }
    }

    @Test
    fun `a repository failure becomes the Error state`() = runTest {
        repository.packs.value = StickerPackError.Database("boom").left()

        viewModel.body.test {
            assertEquals(PacksListBody.Error, settled(testScheduler))
        }
    }

    @Test
    fun `new packs are pushed to the collector`() = runTest {
        viewModel.body.test {
            assertEquals(PacksListBody.Empty, settled(testScheduler))
            repository.packs.value = listOf(aPack()).right()
            assertEquals(1, assertIs<PacksListBody.Content>(awaitItem()).packs.size)
        }
    }

    private fun app.cash.turbine.ReceiveTurbine<PacksListBody>.expectContent(scheduler: TestCoroutineScheduler) =
        assertIs<PacksListBody.Content>(settled(scheduler))

    @Test
    fun `refreshing marks the packs WhatsApp has`() = runTest {
        repository.packs.value = listOf(aPack(id = "1"), aPack(id = "2")).right()
        whitelist.whitelistedIds = setOf("2")

        viewModel.body.test {
            assertEquals(listOf(false, false), expectContent(testScheduler).packs.map { it.isInWhatsApp })
            viewModel.refreshWhatsAppStatus()
            assertEquals(listOf(false, true), expectContent(testScheduler).packs.map { it.isInWhatsApp })
        }
    }

    @Test
    fun `a failed refresh keeps the last known badges`() = runTest {
        repository.packs.value = listOf(aPack(id = "1")).right()
        whitelist.whitelistedIds = setOf("1")

        viewModel.body.test {
            expectContent(testScheduler)
            viewModel.refreshWhatsAppStatus()
            assertEquals(true, expectContent(testScheduler).packs.single().isInWhatsApp)
            whitelist.fails = true
            viewModel.refreshWhatsAppStatus()
            testScheduler.advanceUntilIdle()
            expectNoEvents()
            assertTrue(errorLogger.messages.single().startsWith("PacksListViewModel: "))
        }
    }

    @Test
    fun `import shows progress, then opens the new pack and goes back to Idle`() = runTest {
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        viewModel.importedPackEvents.test {
            viewModel.transferState.test {
                assertEquals(TransferUiState.Idle, awaitItem())
                viewModel.importFrom("content://file")
                assertEquals(TransferUiState.InProgress(TransferKind.IMPORT), awaitItem())
                gate.complete(Unit)
                assertEquals(TransferUiState.Idle, awaitItem())
            }
            assertEquals("500", awaitItem())
        }
    }

    @Test
    fun `a failed import ends in Failed with the typed error until dismissed`() = runTest {
        store.failure = TransferError.AnimatedNotSupported("1.webp")
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        viewModel.transferState.test {
            assertEquals(TransferUiState.Idle, awaitItem())
            viewModel.importFrom("content://file")
            assertEquals(TransferUiState.InProgress(TransferKind.IMPORT), awaitItem())
            gate.complete(Unit)
            assertEquals(
                TransferUiState.Failed(TransferKind.IMPORT, TransferError.AnimatedNotSupported("1.webp")),
                awaitItem(),
            )
            viewModel.dismissTransferResult()
            assertEquals(TransferUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `backup succeeds with the pack count`() = runTest {
        repository.packs.value = listOf(aPack(id = "1"), aPack(id = "2")).right()
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        viewModel.transferState.test {
            assertEquals(TransferUiState.Idle, awaitItem())
            viewModel.backupTo("content://out")
            assertEquals(TransferUiState.InProgress(TransferKind.BACKUP), awaitItem())
            gate.complete(Unit)
            assertEquals(TransferUiState.Succeeded(TransferKind.BACKUP, 2), awaitItem())
        }
        assertEquals("content://out", store.writtenBackupLocation)
    }

    @Test
    fun `restore succeeds with the restored count`() = runTest {
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        viewModel.transferState.test {
            assertEquals(TransferUiState.Idle, awaitItem())
            viewModel.restoreFrom("content://in")
            assertEquals(TransferUiState.InProgress(TransferKind.RESTORE), awaitItem())
            gate.complete(Unit)
            assertEquals(TransferUiState.Succeeded(TransferKind.RESTORE, 1), awaitItem())
        }
    }

    @Test
    fun `a second transfer while one runs is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        viewModel.transferState.test {
            assertEquals(TransferUiState.Idle, awaitItem())
            viewModel.restoreFrom("content://in")
            assertEquals(TransferUiState.InProgress(TransferKind.RESTORE), awaitItem())
            viewModel.backupTo("content://out")
            viewModel.dismissTransferResult()
            expectNoEvents()
            gate.complete(Unit)
            assertIs<TransferUiState.Succeeded>(awaitItem())
        }
    }
}
