package com.damianogiusti.stickermaker.feature.keyboard

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.damianogiusti.stickermaker.domain.error.KeyboardHistoryError
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.repository.KeyboardHistoryRepository
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDownMain() = Dispatchers.resetMain()
}

fun aPack(
    id: String,
    keyboardAddedAt: Long? = null,
    stickerCount: Int = 2,
    isAvailable: Boolean = keyboardAddedAt != null,
) = StickerPack(
    id = StickerPackId(id),
    name = "Pack $id",
    publisher = "Me",
    trayFileName = "tray.png",
    stickers = (0 until stickerCount).map { Sticker("$it.webp", listOf("x"), 1_000, it) },
    imageDataVersion = 1,
    isAvailableInKeyboard = isAvailable,
    keyboardAddedAt = keyboardAddedAt,
    isImported = false,
    position = 0,
)

class FakeStickerPackRepository(
    initial: Either<StickerPackError, List<StickerPack>> = emptyList<StickerPack>().right(),
) : StickerPackRepository {
    val packs = MutableStateFlow(initial)
    override fun observePacks(): Flow<Either<StickerPackError, List<StickerPack>>> = packs
    override fun observePack(id: StickerPackId): Flow<Either<StickerPackError, StickerPack>> = unused()
    override suspend fun getPack(id: StickerPackId): Either<StickerPackError, StickerPack> = unused()
    override suspend fun createPack(name: String, publisher: String): Either<StickerPackError, StickerPack> = unused()
    override suspend fun renamePack(id: StickerPackId, name: String, publisher: String): Either<StickerPackError, Unit> = unused()
    override suspend fun deletePack(id: StickerPackId): Either<StickerPackError, Unit> = unused()
    override suspend fun addSticker(file: PackFile): Either<StickerPackError, Unit> = unused()
    override suspend fun replaceSticker(replacedFileName: String, file: PackFile): Either<StickerPackError, Unit> = unused()
    override suspend fun deleteSticker(packId: StickerPackId, fileName: String): Either<StickerPackError, Unit> = unused()
    override suspend fun setTray(file: PackFile): Either<StickerPackError, Unit> = unused()
    override suspend fun setEmojis(packId: StickerPackId, fileName: String, emojis: List<String>): Either<StickerPackError, Unit> = unused()
    override suspend fun setKeyboardAvailability(id: StickerPackId, isAvailable: Boolean): Either<StickerPackError, Unit> = unused()

    private fun unused(): Nothing = error("Not used by these tests")
}

class FakeKeyboardHistoryRepository : KeyboardHistoryRepository {
    val history = MutableStateFlow<Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>>(emptyList<KeyboardHistoryEntry>().right())
    val recorded = mutableListOf<KeyboardHistoryEntry>()
    var failsOnRecord = false

    override fun observeHistory(): Flow<Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>> = history

    override suspend fun record(entry: KeyboardHistoryEntry): Either<KeyboardHistoryError, Unit> {
        if (failsOnRecord) return KeyboardHistoryError.Database("boom").left()
        recorded += entry
        return Unit.right()
    }
}

val fixedClock = Clock { 1_000L }
