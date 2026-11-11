package com.damianogiusti.stickermaker.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.damianogiusti.stickermaker.domain.error.KeyboardHistoryError
import com.damianogiusti.stickermaker.domain.error.StickerFileInspectionError
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.ImageFormat
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.KeyboardHistoryRepository
import com.damianogiusti.stickermaker.domain.repository.StickerFileInspector
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository

fun aPack(
    id: String = "1700000000",
    name: String = "Cats",
    publisher: String = "Me",
    trayFileName: String? = "tray.png",
    stickers: List<Sticker> = emptyList(),
) = StickerPack(
    id = StickerPackId(id),
    name = name,
    publisher = publisher,
    trayFileName = trayFileName,
    stickers = stickers,
    imageDataVersion = 1,
    isAvailableInKeyboard = false,
    keyboardAddedAt = null,
    isImported = false,
    position = 0,
)

fun aSticker(index: Int, emojis: List<String> = listOf("😀")) =
    Sticker(fileName = "$index.webp", emojis = emojis, sizeBytes = 1_000, position = index)

fun validStickerFacts() = StickerFileFacts(ImageFormat.WEBP, 512, 512, 50_000, isAnimated = false)
fun validTrayFacts() = StickerFileFacts(ImageFormat.PNG, 96, 96, 4_000, isAnimated = false)

class FakeStickerPackRepository(initial: List<StickerPack> = emptyList()) : StickerPackRepository {
    val packs = MutableStateFlow(initial)
    var nextCreatedId = "1700000001"
    var createCalls = 0
        private set
    var addCalls = 0
        private set

    override fun observePacks(): Flow<Either<StickerPackError, List<StickerPack>>> = packs.map { it.right() }

    override fun observePack(id: StickerPackId): Flow<Either<StickerPackError, StickerPack>> =
        packs.map { list -> list.find { it.id == id }?.right() ?: StickerPackError.NotFound(id).left() }

    override suspend fun getPack(id: StickerPackId): Either<StickerPackError, StickerPack> =
        packs.value.find { it.id == id }?.right() ?: StickerPackError.NotFound(id).left()

    override suspend fun createPack(name: String, publisher: String): Either<StickerPackError, StickerPack> {
        createCalls++
        val pack = aPack(id = nextCreatedId, name = name, publisher = publisher, trayFileName = null)
        packs.value = packs.value + pack
        return pack.right()
    }

    override suspend fun renamePack(id: StickerPackId, name: String, publisher: String) =
        update(id) { it.copy(name = name, publisher = publisher) }

    override suspend fun deletePack(id: StickerPackId): Either<StickerPackError, Unit> {
        packs.value = packs.value.filterNot { it.id == id }
        return Unit.right()
    }

    override suspend fun addSticker(file: PackFile): Either<StickerPackError, Unit> {
        addCalls++
        return update(file.packId) {
            it.copy(
                stickers = it.stickers + Sticker(file.fileName, emptyList(), file.bytes.size.toLong(), it.stickers.size),
                imageDataVersion = it.imageDataVersion + 1,
            )
        }
    }

    override suspend fun replaceSticker(replacedFileName: String, file: PackFile) = update(file.packId) { pack ->
        pack.copy(
            stickers = pack.stickers.map {
                if (it.fileName == replacedFileName) it.copy(fileName = file.fileName) else it
            },
            imageDataVersion = pack.imageDataVersion + 1,
        )
    }

    override suspend fun deleteSticker(packId: StickerPackId, fileName: String) = update(packId) { pack ->
        pack.copy(stickers = pack.stickers.filterNot { it.fileName == fileName }, imageDataVersion = pack.imageDataVersion + 1)
    }

    override suspend fun setTray(file: PackFile) = update(file.packId) {
        it.copy(trayFileName = file.fileName, imageDataVersion = it.imageDataVersion + 1)
    }

    override suspend fun setEmojis(packId: StickerPackId, fileName: String, emojis: List<String>) =
        update(packId) { pack ->
            pack.copy(stickers = pack.stickers.map { if (it.fileName == fileName) it.copy(emojis = emojis) else it })
        }

    override suspend fun setKeyboardAvailability(id: StickerPackId, isAvailable: Boolean) =
        update(id) { it.copy(isAvailableInKeyboard = isAvailable) }

    private fun update(id: StickerPackId, transform: (StickerPack) -> StickerPack): Either<StickerPackError, Unit> {
        val current = packs.value.find { it.id == id } ?: return StickerPackError.NotFound(id).left()
        packs.value = packs.value.map { if (it.id == id) transform(current) else it }
        return Unit.right()
    }
}

class FakeKeyboardHistoryRepository : KeyboardHistoryRepository {
    private val entries = MutableStateFlow<List<KeyboardHistoryEntry>>(emptyList())

    override fun observeHistory(): Flow<Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>> =
        entries.map { it.right() }

    override suspend fun record(entry: KeyboardHistoryEntry): Either<KeyboardHistoryError, Unit> {
        entries.value = listOf(entry) + entries.value.filterNot { it.packId == entry.packId && it.fileName == entry.fileName }
        return Unit.right()
    }
}

class FakeStickerFileInspector(private val facts: Map<String, StickerFileFacts>) : StickerFileInspector {
    override suspend fun inspect(
        packId: StickerPackId,
        fileName: String,
    ): Either<StickerFileInspectionError, StickerFileFacts> =
        facts[fileName]?.right() ?: StickerFileInspectionError.FileNotFound(fileName).left()
}
