package com.damianogiusti.stickermaker.feature.gallery

import app.cash.turbine.test
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GalleryViewModelTest : MainDispatcherTest() {

    private val packs = FakeStickerPackRepository()
    private val grants = FakeTreeGrantStore()
    private val reader = FakeFolderReader()
    private val copier = FakeOwnStickerCopier()
    private val detector = FakeAnimationDetector()
    private val viewModel by lazy { GalleryViewModel(ObserveStickerPacksUseCase(packs), grants, reader, copier, detector) }

    @Test
    fun `without a grant the whatsapp tab asks for permission`() = runTest {
        viewModel.state.test {
            assertEquals(WhatsAppUiState.PermissionNeeded, expectMostRecentItem().whatsApp)
        }
    }

    @Test
    fun `granted folder lists stickers newest first`() = runTest {
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/root")
        reader.result = listOf(aFile("old.webp", 1), aFile("new.webp", 9), aFile("mid.webp", 5)).right()

        viewModel.state.test {
            val content = assertIs<WhatsAppUiState.Content>(expectMostRecentItem().whatsApp)
            assertEquals(listOf("new.webp", "mid.webp", "old.webp"), content.stickers.map { it.name })
        }
    }

    @Test
    fun `empty folder shows the empty state`() = runTest {
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/root")

        viewModel.state.test {
            assertEquals(WhatsAppUiState.Empty, expectMostRecentItem().whatsApp)
        }
    }

    @Test
    fun `read failure shows the error state`() = runTest {
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/root")
        reader.result = GalleryError.Io("x").left()

        viewModel.state.test {
            assertEquals(WhatsAppUiState.Error, expectMostRecentItem().whatsApp)
        }
    }

    @Test
    fun `revoked grant goes back to permission needed`() = runTest {
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/root")
        reader.result = GalleryError.TreeUnavailable.left()

        viewModel.state.test {
            assertEquals(WhatsAppUiState.PermissionNeeded, expectMostRecentItem().whatsApp)
        }
    }

    @Test
    fun `new grant is saved and the folder is loaded`() = runTest {
        reader.result = listOf(aFile("a.webp", 1)).right()

        viewModel.state.test {
            assertEquals(WhatsAppUiState.PermissionNeeded, expectMostRecentItem().whatsApp)
            viewModel.onTreeGranted("content://tree/new")
            assertIs<WhatsAppUiState.Content>(expectMostRecentItem().whatsApp)
        }
        assertEquals(listOf("content://tree/new"), reader.requested)
    }

    @Test
    fun `failed grant save shows a message and keeps asking`() = runTest {
        grants.failsOnSave = true

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onTreeGranted("content://tree/new")
            val state = expectMostRecentItem()
            assertEquals(GalleryMessage.GRANT_FAILED, state.message)
            assertEquals(WhatsAppUiState.PermissionNeeded, state.whatsApp)
        }
    }

    @Test
    fun `each whatsapp app has its own grant`() = runTest {
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/wa")
        reader.result = listOf(aFile("a.webp", 1)).right()

        viewModel.state.test {
            assertIs<WhatsAppUiState.Content>(expectMostRecentItem().whatsApp)
            viewModel.selectSource(WhatsAppSource.WHATSAPP_BUSINESS)
            val state = expectMostRecentItem()
            assertEquals(WhatsAppSource.WHATSAPP_BUSINESS, state.source)
            assertEquals(WhatsAppUiState.PermissionNeeded, state.whatsApp)
        }
    }

    @Test
    fun `animated stickers are reported and cannot be picked`() = runTest {
        val animated = aFile("anim.webp", 1)
        detector.animatedNames = setOf("anim.webp")
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/root")
        reader.result = listOf(animated).right()

        viewModel.pickedImages.test {
            viewModel.onWhatsAppStickerPicked(animated)
            expectNoEvents()
        }
        viewModel.state.test {
            viewModel.onWhatsAppStickerVisible(animated)
            val content = assertIs<WhatsAppUiState.Content>(expectMostRecentItem().whatsApp)
            assertEquals(true, content.hasAnimated)
            assertEquals(true, content.isAnimated(animated))
        }
    }

    @Test
    fun `the grid shows before any file is inspected and each visible file is inspected once`() = runTest {
        val first = aFile("a.webp", 1)
        val second = aFile("b.webp", 2)
        grants.granted = mapOf(WhatsAppSource.WHATSAPP to "content://tree/root")
        reader.result = listOf(first, second).right()

        viewModel.state.test {
            val before = assertIs<WhatsAppUiState.Content>(expectMostRecentItem().whatsApp)
            assertEquals(emptyList(), detector.inspected)
            assertEquals(false, before.isAnimated(first))

            viewModel.onWhatsAppStickerVisible(first)
            viewModel.onWhatsAppStickerVisible(first)

            assertEquals(listOf("a.webp"), detector.inspected)
            assertEquals(setOf(first.uriString), assertIs<WhatsAppUiState.Content>(expectMostRecentItem().whatsApp).animatedByUri.keys)
        }
    }

    @Test
    fun `picking a static whatsapp sticker returns its uri`() = runTest {
        val sticker = aFile("a.webp", 1)

        viewModel.pickedImages.test {
            viewModel.onWhatsAppStickerPicked(sticker)
            assertEquals(sticker.uriString, awaitItem())
        }
    }

    @Test
    fun `my stickers groups the packs that have stickers`() = runTest {
        packs.packs.value = listOf(aPack("1", stickerCount = 2), aPack("2", stickerCount = 0)).right()

        viewModel.state.test {
            val content = assertIs<MyStickersUiState.Content>(expectMostRecentItem().myStickers)
            assertEquals(listOf("1"), content.groups.map { it.packId })
            assertEquals(2, content.groups.single().stickers.size)
        }
    }

    @Test
    fun `no stickers in any pack shows the empty state`() = runTest {
        packs.packs.value = listOf(aPack("1", stickerCount = 0)).right()

        viewModel.state.test {
            assertEquals(MyStickersUiState.Empty, expectMostRecentItem().myStickers)
        }
    }

    @Test
    fun `pack load failure shows the error state`() = runTest {
        packs.packs.value = StickerPackError.Database("x").left()

        viewModel.state.test {
            assertEquals(MyStickersUiState.Error, expectMostRecentItem().myStickers)
        }
    }

    @Test
    fun `picking an own sticker returns the copied uri`() = runTest {
        copier.result = "file:///cache/gallery/1_0.webp".right()

        viewModel.pickedImages.test {
            viewModel.onOwnStickerPicked(OwnSticker("1", "0.webp", 1))
            assertEquals("file:///cache/gallery/1_0.webp", awaitItem())
        }
    }

    @Test
    fun `failed copy shows a message and picks nothing`() = runTest {
        copier.result = GalleryError.Io("disk").left()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onOwnStickerPicked(OwnSticker("1", "0.webp", 1))
            assertEquals(GalleryMessage.COPY_FAILED, expectMostRecentItem().message)
        }
    }

    @Test
    fun `tab selection is part of the state`() = runTest {
        viewModel.state.test {
            assertEquals(GalleryTab.WHATSAPP, expectMostRecentItem().tab)
            viewModel.selectTab(GalleryTab.MY_STICKERS)
            assertEquals(GalleryTab.MY_STICKERS, expectMostRecentItem().tab)
        }
    }
}
