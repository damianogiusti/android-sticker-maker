package com.damianogiusti.stickermaker.feature.packs

import app.cash.turbine.test
import arrow.core.right
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppRequest
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppResult
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.usecase.CheckPackInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.PrepareAddToWhatsAppUseCase
import com.damianogiusti.stickermaker.feature.packs.detail.AddToWhatsAppDialog
import com.damianogiusti.stickermaker.feature.packs.detail.AddToWhatsAppLaunch
import com.damianogiusti.stickermaker.domain.usecase.DeleteStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.DeleteStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetKeyboardAvailabilityUseCase
import com.damianogiusti.stickermaker.domain.usecase.ValidateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.validation.StickerPackValidator
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue
import com.damianogiusti.stickermaker.feature.packs.detail.PackDetailMessage
import com.damianogiusti.stickermaker.feature.packs.detail.PackDetailUiState
import com.damianogiusti.stickermaker.feature.packs.detail.PackDetailViewModel
import com.damianogiusti.stickermaker.feature.packs.detail.PackValidation
import com.damianogiusti.stickermaker.feature.packs.detail.StickerSlot
import java.io.File
import kotlin.test.Test
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.usecase.ExportStickerPackUseCase
import com.damianogiusti.stickermaker.feature.packs.detail.ShareFile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PackDetailViewModelTest : MainDispatcherTest() {
    private val readyPack = aPack(id = "7", stickers = List(3) { aSticker(it) })
    private val repository = FakeStickerPackRepository(listOf(readyPack))
    private val inspector = FakeStickerFileInspector(
        mapOf("tray.png" to validTrayFacts()) + (0..2).associate { "$it.webp" to validStickerFacts() },
    )

    private val installations = FakeWhatsAppInstallations()
    private val whitelist = FakeWhatsAppWhitelist()

    private fun viewModel(packId: String = "7") = PackDetailViewModel(
        packId = packId,
        observePack = ObserveStickerPackUseCase(repository),
        validatePack = ValidateStickerPackUseCase(repository, inspector, StickerPackValidator()),
        deletePack = DeleteStickerPackUseCase(repository),
        deleteSticker = DeleteStickerUseCase(repository),
        setKeyboardAvailability = SetKeyboardAvailabilityUseCase(repository),
        fileResolver = testFileResolver,
        prepareAddToWhatsApp = PrepareAddToWhatsAppUseCase(
            repository,
            ValidateStickerPackUseCase(repository, inspector, StickerPackValidator()),
            installations,
            testAuthority,
        ),
        checkPackInWhatsApp = CheckPackInWhatsAppUseCase(installations, whitelist, testAuthority),
        exportPack = ExportStickerPackUseCase(repository, FakePackFileSource(), archiveStore),
        errorLogger = errorLogger,
    )
    private val errorLogger = RecordingErrorLogger()
    private val archiveStore = FakePackArchiveStore()

    private suspend fun PackDetailViewModel.content() =
        assertIs<PackDetailUiState.Content>(state.first { it !is PackDetailUiState.Loading }).pack

    @Test
    fun `starts Loading`() = runTest {
        viewModel().state.test {
            assertEquals(PackDetailUiState.Loading, awaitItem())
            awaitItem()
        }
    }

    @Test
    fun `content always has 30 slots, filled ones first`() = runTest {
        val pack = viewModel().content()
        assertEquals(30, pack.slots.size)
        assertEquals(3, pack.stickerCount)
        assertTrue(pack.slots.take(3).all { it is StickerSlot.Filled })
        assertTrue(pack.slots.drop(3).all { it == StickerSlot.Empty })
        assertEquals(File("/packs/7/tray.png"), pack.tray?.file)
        assertEquals(PackValidation.Valid, pack.validation)
    }

    @Test
    fun `validation issues carry the slot number of the sticker`() = runTest {
        repository.packs.value = listOf(
            aPack(id = "7", stickers = listOf(aSticker(0), aSticker(1, emojis = emptyList()), aSticker(2))),
        ).right()

        val validation = assertIs<PackValidation.Invalid>(viewModel().content().validation)

        val issue = validation.issues.single()
        assertEquals(ValidationIssue.MissingEmoji("1.webp"), issue.issue)
        assertEquals(2, issue.stickerNumber)
    }

    @Test
    fun `too few stickers is a pack-level issue`() = runTest {
        repository.packs.value = listOf(aPack(id = "7", stickers = listOf(aSticker(0)))).right()
        inspector.facts = mapOf("tray.png" to validTrayFacts(), "0.webp" to validStickerFacts())

        val validation = assertIs<PackValidation.Invalid>(viewModel().content().validation)

        assertEquals(ValidationIssue.TooFewStickers(1), validation.issues.single().issue)
        assertEquals(null, validation.issues.single().stickerNumber)
    }

    @Test
    fun `a failing file inspection makes validation Unavailable`() = runTest {
        inspector.facts = emptyMap()
        assertEquals(PackValidation.Unavailable, viewModel().content().validation)
        assertTrue(errorLogger.messages.first().startsWith("PackDetailViewModel: Could not validate pack 7"))
    }

    @Test
    fun `unknown pack is NotFound and other failures are Error`() = runTest {
        assertEquals(PackDetailUiState.NotFound, viewModel("404").state.first { it !is PackDetailUiState.Loading })

        repository.packs.value = StickerPackError.Database("x").let { arrow.core.Either.Left(it) }
        assertEquals(PackDetailUiState.Error, viewModel().state.first { it !is PackDetailUiState.Loading })
    }

    @Test
    fun `deleting the pack emits a deleted event`() = runTest {
        val vm = viewModel()
        vm.deletedEvents.test {
            vm.deletePack()
            awaitItem()
        }
        assertEquals(listOf(StickerPackId("7")), repository.deletedPacks)
    }

    @Test
    fun `a failed pack delete emits a message and no deleted event`() = runTest {
        repository.failWrites = true
        val vm = viewModel()
        vm.messages.test {
            vm.deletePack()
            assertEquals(PackDetailMessage.ActionFailed, awaitItem())
        }
        assertTrue(repository.deletedPacks.isEmpty())
    }

    @Test
    fun `deleting a sticker updates the grid`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            awaitItem()
            vm.deleteSticker("1.webp")
            val pack = assertIs<PackDetailUiState.Content>(settled(testScheduler)).pack
            assertEquals(2, pack.stickerCount)
        }
        assertEquals(listOf(StickerPackId("7") to "1.webp"), repository.deletedStickers)
    }

    @Test
    fun `keyboard switch is written and reflected in the state`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.setAvailableInKeyboard(true)
            assertTrue(assertIs<PackDetailUiState.Content>(awaitItem()).pack.isAvailableInKeyboard)
        }
        assertEquals(listOf(true), repository.keyboardWrites)
    }

    private val request = AddToWhatsAppRequest(StickerPackId("7"), testAuthority, "Cats")

    @Test
    fun `adding with one WhatsApp installed launches it directly`() = runTest {
        installations.apps = listOf(WhatsAppApp.BUSINESS)
        val vm = viewModel()
        vm.launchEvents.test {
            vm.addToWhatsApp()
            assertEquals(AddToWhatsAppLaunch(request, WhatsAppApp.BUSINESS), awaitItem())
        }
    }

    @Test
    fun `adding with both apps asks the user, then launches the chosen one`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP, WhatsAppApp.BUSINESS)
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.addToWhatsApp()
            val dialog = assertIs<PackDetailUiState.Content>(awaitItem()).pack.addDialog
            assertEquals(AddToWhatsAppDialog.ChooseApp(request, listOf(WhatsAppApp.WHATSAPP, WhatsAppApp.BUSINESS)), dialog)

            vm.launchEvents.test {
                vm.chooseWhatsAppApp(WhatsAppApp.BUSINESS)
                assertEquals(AddToWhatsAppLaunch(request, WhatsAppApp.BUSINESS), awaitItem())
            }
            assertEquals(AddToWhatsAppDialog.Hidden, assertIs<PackDetailUiState.Content>(awaitItem()).pack.addDialog)
        }
    }

    @Test
    fun `adding without WhatsApp shows the not-installed dialog until dismissed`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.addToWhatsApp()
            assertEquals(
                AddToWhatsAppDialog.NoWhatsAppInstalled,
                assertIs<PackDetailUiState.Content>(awaitItem()).pack.addDialog,
            )
            vm.dismissAddDialog()
            assertEquals(AddToWhatsAppDialog.Hidden, assertIs<PackDetailUiState.Content>(awaitItem()).pack.addDialog)
        }
    }

    @Test
    fun `adding a pack that became invalid emits a message and does not launch`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        repository.packs.value = listOf(aPack(id = "7", stickers = listOf(aSticker(0)))).right()
        inspector.facts = mapOf("tray.png" to validTrayFacts(), "0.webp" to validStickerFacts())
        val vm = viewModel()
        vm.messages.test {
            vm.addToWhatsApp()
            assertEquals(PackDetailMessage.PackNotReady, awaitItem())
        }
    }

    @Test
    fun `refresh shows Added to WhatsApp and a later refresh can clear it`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        whitelist.whitelistedIds = setOf("7")
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.refreshWhatsAppStatus()
            assertTrue(assertIs<PackDetailUiState.Content>(awaitItem()).pack.isInWhatsApp)
        }
    }

    @Test
    fun `a failed status check keeps the last answer`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        whitelist.whitelistedIds = setOf("7")
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            vm.refreshWhatsAppStatus()
            assertTrue(assertIs<PackDetailUiState.Content>(awaitItem()).pack.isInWhatsApp)
            whitelist.fails = true
            vm.refreshWhatsAppStatus()
            testScheduler.advanceUntilIdle()
            expectNoEvents()
            assertTrue(errorLogger.messages.single().startsWith("PackDetailViewModel: Could not ask WhatsApp"))
        }
    }

    @Test
    fun `a successful add result shows a message and refreshes the status`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        val vm = viewModel()
        vm.state.test {
            skipItems(2)
            whitelist.whitelistedIds = setOf("7")
            vm.messages.test {
                vm.onAddToWhatsAppResult(AddToWhatsAppResult.Added)
                assertEquals(PackDetailMessage.AddedToWhatsApp, awaitItem())
            }
            assertTrue(assertIs<PackDetailUiState.Content>(awaitItem()).pack.isInWhatsApp)
        }
    }

    @Test
    fun `rejected and cancelled results`() = runTest {
        val vm = viewModel()
        vm.messages.test {
            vm.onAddToWhatsAppResult(AddToWhatsAppResult.Rejected("Tray too big"))
            assertEquals(PackDetailMessage.WhatsAppRejected("Tray too big"), awaitItem())
            vm.onAddToWhatsAppResult(AddToWhatsAppResult.Rejected(null))
            assertEquals(PackDetailMessage.WhatsAppRejected(null), awaitItem())
            vm.onAddToWhatsAppResult(AddToWhatsAppResult.AlreadyAdded)
            assertEquals(PackDetailMessage.AlreadyInWhatsApp, awaitItem())
            vm.onAddToWhatsAppResult(AddToWhatsAppResult.Cancelled)
            expectNoEvents()
            vm.onAddToWhatsAppResult(AddToWhatsAppResult.Unconfirmed)
            assertEquals(PackDetailMessage.CheckWhatsApp, awaitItem())
        }
    }

    @Test
    fun `emoji issues expose the sticker to fix`() = runTest {
        repository.packs.value = listOf(
            aPack(id = "7", stickers = listOf(aSticker(0), aSticker(1, emojis = emptyList()), aSticker(2))),
        ).right()
        val issues = assertIs<PackValidation.Invalid>(viewModel().content().validation).issues
        assertEquals("1.webp", issues.single().emojiFileName)
    }

    @Test
    fun `share writes the wastickers file and fires a share event`() = runTest {
        val viewModel = viewModel()
        viewModel.shareEvents.test {
            viewModel.sharePack()
            assertEquals(ShareFile("/cache/share/Cats.wastickers", "Cats.wastickers"), awaitItem())
        }
    }

    @Test
    fun `share failure is reported as a message and no file is shared`() = runTest {
        archiveStore.failure = TransferError.Io("full")
        val viewModel = viewModel()
        viewModel.messages.test {
            viewModel.sharePack()
            assertEquals(PackDetailMessage.ShareFailed(TransferError.Io("full")), awaitItem())
        }
    }

    @Test
    fun `preparing share is visible in the state while the file is written`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        archiveStore.gate = gate
        val viewModel = viewModel()
        viewModel.state.test {
            assertIs<PackDetailUiState.Loading>(awaitItem())
            assertEquals(false, assertIs<PackDetailUiState.Content>(awaitItem()).pack.isPreparingShare)
            viewModel.sharePack()
            assertEquals(true, assertIs<PackDetailUiState.Content>(awaitItem()).pack.isPreparingShare)
            gate.complete(Unit)
            assertEquals(false, assertIs<PackDetailUiState.Content>(awaitItem()).pack.isPreparingShare)
        }
    }
}
