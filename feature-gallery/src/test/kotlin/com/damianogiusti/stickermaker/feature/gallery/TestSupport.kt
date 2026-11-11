package com.damianogiusti.stickermaker.feature.gallery

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
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
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

class FakeTreeGrantStore(var granted: Map<WhatsAppSource, String> = emptyMap()) : TreeGrantStore {
    var failsOnSave = false
    override fun treeUri(source: WhatsAppSource): String? = granted[source]
    override fun save(source: WhatsAppSource, treeUri: String): Either<GalleryError, Unit> {
        if (failsOnSave) return GalleryError.Io("no").left()
        granted = granted + (source to treeUri)
        return Unit.right()
    }
}

class FakeFolderReader(var result: Either<GalleryError, List<WhatsAppStickerFile>> = emptyList<WhatsAppStickerFile>().right()) : WhatsAppFolderReader {
    val requested = mutableListOf<String>()
    override suspend fun list(treeUri: String): Either<GalleryError, List<WhatsAppStickerFile>> {
        requested += treeUri
        return result
    }
}

class FakeOwnStickerCopier(var result: Either<GalleryError, String> = "file:///cache/x.webp".right()) : OwnStickerCopier {
    override suspend fun copyToCache(packId: String, fileName: String): Either<GalleryError, String> = result
}

fun aFile(name: String, lastModified: Long) =
    WhatsAppStickerFile("content://tree/$name", name, sizeBytes = 100, lastModified = lastModified)

/** Answers from a fixed set of animated names and records which files it was asked about. */
class FakeAnimationDetector(var animatedNames: Set<String> = emptySet()) : AnimationDetector {
    val inspected = mutableListOf<String>()
    override suspend fun isAnimated(file: WhatsAppStickerFile): Boolean {
        inspected += file.name
        return file.name in animatedNames
    }
}
