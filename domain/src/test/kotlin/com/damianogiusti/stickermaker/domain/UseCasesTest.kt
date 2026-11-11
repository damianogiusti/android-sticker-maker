package com.damianogiusti.stickermaker.domain

import app.cash.turbine.test
import arrow.core.Either
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.ValidatePackError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.usecase.AddStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.CreateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveKeyboardHistoryUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.domain.usecase.RecordKeyboardStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.RenameStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetKeyboardAvailabilityUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerEmojisUseCase
import com.damianogiusti.stickermaker.domain.usecase.ValidateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.validation.StickerPackValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UseCasesTest {
    private val id = StickerPackId("1700000000")

    @Test
    fun `create trims and rejects blank name`() = runTest {
        val repo = FakeStickerPackRepository()
        val useCase = CreateStickerPackUseCase(repo)

        assertEquals(Either.Left(StickerPackError.InvalidName), useCase("   ", "me"))
        assertEquals(Either.Left(StickerPackError.InvalidPublisher), useCase("Cats", ""))
        assertEquals(0, repo.createCalls)

        val created = useCase("  Cats ", " me ").getOrNullForTest()
        assertEquals("Cats", created?.name)
        assertEquals("me", created?.publisher)
    }

    @Test
    fun `rename rejects too long name and updates otherwise`() = runTest {
        val repo = FakeStickerPackRepository(listOf(aPack()))
        val useCase = RenameStickerPackUseCase(repo)

        assertEquals(Either.Left(StickerPackError.InvalidName), useCase(id, "n".repeat(129), "me"))
        assertEquals(Either.Right(Unit), useCase(id, "Dogs", "you"))
        assertEquals("Dogs", repo.getPack(id).getOrNullForTest()?.name)
    }

    @Test
    fun `adding the 31st sticker fails`() = runTest {
        val repo = FakeStickerPackRepository(listOf(aPack(stickers = (0 until 30).map { aSticker(it) })))
        val result = AddStickerUseCase(repo)(PackFile(id, "x.webp", ByteArray(1)))

        assertEquals(Either.Left(StickerPackError.TooManyStickers), result)
        assertEquals(0, repo.addCalls)
    }

    @Test
    fun `adding a sticker bumps imageDataVersion`() = runTest {
        val repo = FakeStickerPackRepository(listOf(aPack()))
        AddStickerUseCase(repo)(PackFile(id, "x.webp", ByteArray(1)))

        val pack = repo.getPack(id).getOrNullForTest()
        assertEquals(1, pack?.stickers?.size)
        assertEquals(2, pack?.imageDataVersion)
    }

    @Test
    fun `more than 3 emojis is rejected and duplicates are dropped`() = runTest {
        val repo = FakeStickerPackRepository(listOf(aPack(stickers = listOf(aSticker(0)))))
        val useCase = SetStickerEmojisUseCase(repo)

        assertEquals(Either.Left(StickerPackError.TooManyEmojis), useCase(id, "0.webp", listOf("a", "b", "c", "d")))
        useCase(id, "0.webp", listOf("a", "a", "b"))
        assertEquals(listOf("a", "b"), repo.getPack(id).getOrNullForTest()?.stickers?.first()?.emojis)
    }

    @Test
    fun `keyboard availability is forwarded`() = runTest {
        val repo = FakeStickerPackRepository(listOf(aPack()))
        SetKeyboardAvailabilityUseCase(repo)(id, true)
        assertEquals(true, repo.getPack(id).getOrNullForTest()?.isAvailableInKeyboard)
    }

    @Test
    fun `observe packs emits updates`() = runTest {
        val repo = FakeStickerPackRepository()
        ObserveStickerPacksUseCase(repo)().test {
            assertEquals(Either.Right(emptyList()), awaitItem())
            CreateStickerPackUseCase(repo)("Cats", "me")
            assertEquals(1, (awaitItem() as Either.Right).value.size)
        }
    }

    @Test
    fun `recording keyboard sticker dedupes and puts it first`() = runTest {
        val repo = FakeKeyboardHistoryRepository()
        var now = 10L
        val record = RecordKeyboardStickerUseCase(repo, Clock { now })
        record(id, "a.webp")
        now = 20L
        record(id, "b.webp")
        now = 30L
        record(id, "a.webp")

        ObserveKeyboardHistoryUseCase(repo)().test {
            val entries = (awaitItem() as Either.Right).value
            assertEquals(listOf(KeyboardHistoryEntry(id, "a.webp", 30L), KeyboardHistoryEntry(id, "b.webp", 20L)), entries)
        }
    }

    @Test
    fun `validate use case reports issues from file facts`() = runTest {
        val pack = aPack(stickers = (0 until 3).map { aSticker(it) })
        val facts = (0 until 3).associate { "$it.webp" to validStickerFacts() } + ("tray.png" to validTrayFacts())
        val useCase = ValidateStickerPackUseCase(
            FakeStickerPackRepository(listOf(pack)),
            FakeStickerFileInspector(facts),
            StickerPackValidator(),
        )
        assertEquals(Either.Right(emptyList()), useCase(id))
    }

    @Test
    fun `validate use case fails when a file cannot be inspected`() = runTest {
        val pack = aPack(stickers = listOf(aSticker(0)))
        val useCase = ValidateStickerPackUseCase(
            FakeStickerPackRepository(listOf(pack)),
            FakeStickerFileInspector(emptyMap()),
            StickerPackValidator(),
        )
        val result = useCase(id)
        assertIs<ValidatePackError.Inspection>((result as Either.Left).value)
    }

    @Test
    fun `validate use case fails for unknown pack`() = runTest {
        val useCase = ValidateStickerPackUseCase(
            FakeStickerPackRepository(),
            FakeStickerFileInspector(emptyMap()),
            StickerPackValidator(),
        )
        assertIs<ValidatePackError.Pack>((useCase(id) as Either.Left).value)
    }

    private fun <A, B> Either<A, B>.getOrNullForTest(): B? = (this as? Either.Right)?.value
}
