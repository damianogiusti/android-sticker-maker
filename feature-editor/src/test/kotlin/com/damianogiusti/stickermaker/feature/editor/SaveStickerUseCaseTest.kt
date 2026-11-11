package com.damianogiusti.stickermaker.feature.editor

import arrow.core.Either
import arrow.core.left
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.usecase.AddStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.ReplaceStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerPackTrayUseCase
import com.damianogiusti.stickermaker.imaging.ExportError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SaveStickerUseCaseTest {
    private val packId = StickerPackId("1700000000")
    private val exporter = FakeStickerExporter()
    private val repository = FakeStickerPackRepository()
    private val useCase = SaveStickerUseCase(
        exporter = exporter,
        addSticker = AddStickerUseCase(repository),
        replaceSticker = ReplaceStickerUseCase(repository),
        setTray = SetStickerPackTrayUseCase(repository),
        clock = Clock { 1_234L },
    )

    @Test
    fun `new sticker is exported as webp and added to the pack`() = runTest {
        val result = useCase(packId, EditorTarget.NewSticker, null, emptyList())

        assertIs<Either.Right<Unit>>(result)
        val file = repository.added.single()
        assertEquals("1234.webp", file.fileName)
        assertEquals(packId, file.packId)
        assertContentEquals(byteArrayOf(1, 2, 3), file.bytes)
    }

    @Test
    fun `replace keeps the replaced file name and stores the new one`() = runTest {
        useCase(packId, EditorTarget.ReplaceSticker("old.webp"), null, emptyList())

        val (replacedName, file) = repository.replaced.single()
        assertEquals("old.webp", replacedName)
        assertEquals("1234.webp", file.fileName)
    }

    @Test
    fun `tray is exported as png named tray png`() = runTest {
        useCase(packId, EditorTarget.Tray, null, emptyList())

        val file = repository.trays.single()
        assertEquals("tray.png", file.fileName)
        assertContentEquals(byteArrayOf(9), file.bytes)
    }

    @Test
    fun `an export error is returned and nothing is stored`() = runTest {
        exporter.stickerResult = ExportError.StickerTooLarge(200_000, 102_400).left()

        val result = useCase(packId, EditorTarget.NewSticker, null, emptyList())

        assertEquals(SaveStickerError.Export(ExportError.StickerTooLarge(200_000, 102_400)), result.leftOrNull())
        assertEquals(0, repository.added.size)
    }

    @Test
    fun `a full pack is rejected before anything is stored`() = runTest {
        repository.stickerCount = 30

        val result = useCase(packId, EditorTarget.NewSticker, null, emptyList())

        assertEquals(SaveStickerError.Store(StickerPackError.TooManyStickers), result.leftOrNull())
        assertEquals(0, repository.added.size)
    }
}
