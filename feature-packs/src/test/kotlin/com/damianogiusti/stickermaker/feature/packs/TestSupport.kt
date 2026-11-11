package com.damianogiusti.stickermaker.feature.packs

import app.cash.turbine.ReceiveTurbine
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.damianogiusti.stickermaker.domain.error.StickerFileInspectionError
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.ImageFormat
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.error.WhitelistError
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.repository.PublisherPreferences
import com.damianogiusti.stickermaker.domain.repository.WhatsAppInstallations
import com.damianogiusti.stickermaker.domain.repository.WhatsAppWhitelist
import com.damianogiusti.stickermaker.domain.repository.StickerFileInspector
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Base for ViewModel tests: viewModelScope runs on Dispatchers.Main, replaced here. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDownMain() = Dispatchers.resetMain()
}

val testFileResolver = PackFileResolver { packId, fileName -> File("/packs/$packId/$fileName") }

fun aPack(
    id: String = "100",
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
    imageDataVersion = 3,
    isAvailableInKeyboard = false,
    keyboardAddedAt = null,
    isImported = false,
    position = 0,
)

fun aSticker(index: Int, emojis: List<String> = listOf("😀")) =
    Sticker(fileName = "$index.webp", emojis = emojis, sizeBytes = 1_000, position = index)

fun validStickerFacts() = StickerFileFacts(ImageFormat.WEBP, 512, 512, 50_000, isAnimated = false)
fun validTrayFacts() = StickerFileFacts(ImageFormat.PNG, 96, 96, 4_000, isAnimated = false)

class FakeStickerFileInspector(var facts: Map<String, StickerFileFacts> = emptyMap()) : StickerFileInspector {
    override suspend fun inspect(packId: StickerPackId, fileName: String) =
        facts[fileName]?.right() ?: StickerFileInspectionError.FileNotFound(fileName).left()
}

val testAuthority = StickerAuthority("com.example.stickercontentprovider")

class FakeWhatsAppInstallations(var apps: List<WhatsAppApp> = emptyList()) : WhatsAppInstallations {
    override suspend fun installedApps() = apps
}

/** [whitelistedIds] are the pack ids WhatsApp "has"; [fails] makes every query return an error. */
class FakeWhatsAppWhitelist(var whitelistedIds: Set<String> = emptySet(), var fails: Boolean = false) : WhatsAppWhitelist {
    override suspend fun isWhitelisted(app: WhatsAppApp, authority: StickerAuthority, packId: StickerPackId) =
        if (fails) WhitelistError.Unavailable(app.packageName, "denied").left() else (packId.value in whitelistedIds).right()
}

class FakePublisherPreferences(var stored: String? = null) : PublisherPreferences {
    override suspend fun getLastPublisher(): String? = stored
    override suspend fun setLastPublisher(publisher: String) {
        stored = publisher
    }
}

/** Observation flows are driven by [packs]; [failWrites] makes every write return a Storage error. */
class FakeStickerPackRepository(initial: List<StickerPack> = emptyList()) : StickerPackRepository {
    val packs = MutableStateFlow<Either<StickerPackError, List<StickerPack>>>(initial.right())
    var failWrites = false
    var createdPacks = mutableListOf<Pair<String, String>>()
    var renamedPacks = mutableListOf<Triple<StickerPackId, String, String>>()
    var deletedPacks = mutableListOf<StickerPackId>()
    var deletedStickers = mutableListOf<Pair<StickerPackId, String>>()
    var emojiWrites = mutableListOf<List<String>>()
    var keyboardWrites = mutableListOf<Boolean>()

    private val writeFailure = StickerPackError.Storage("disk")

    private fun current(): List<StickerPack> = packs.value.getOrNull().orEmpty()

    override fun observePacks(): Flow<Either<StickerPackError, List<StickerPack>>> = packs

    override fun observePack(id: StickerPackId): Flow<Either<StickerPackError, StickerPack>> = packs.map { result ->
        result.flatMap { list -> list.find { it.id == id }?.right() ?: StickerPackError.NotFound(id).left() }
    }

    override suspend fun getPack(id: StickerPackId): Either<StickerPackError, StickerPack> =
        current().find { it.id == id }?.right() ?: StickerPackError.NotFound(id).left()

    override suspend fun createPack(name: String, publisher: String): Either<StickerPackError, StickerPack> {
        if (failWrites) return writeFailure.left()
        createdPacks += name to publisher
        val pack = aPack(id = "new-${createdPacks.size}", name = name, publisher = publisher, trayFileName = null)
        packs.value = (current() + pack).right()
        return pack.right()
    }

    override suspend fun renamePack(id: StickerPackId, name: String, publisher: String): Either<StickerPackError, Unit> {
        if (failWrites) return writeFailure.left()
        renamedPacks += Triple(id, name, publisher)
        packs.value = current().map { if (it.id == id) it.copy(name = name, publisher = publisher) else it }.right()
        return Unit.right()
    }

    override suspend fun deletePack(id: StickerPackId): Either<StickerPackError, Unit> {
        if (failWrites) return writeFailure.left()
        deletedPacks += id
        packs.value = current().filterNot { it.id == id }.right()
        return Unit.right()
    }

    override suspend fun addSticker(file: PackFile): Either<StickerPackError, Unit> = Unit.right()

    override suspend fun replaceSticker(replacedFileName: String, file: PackFile): Either<StickerPackError, Unit> =
        Unit.right()

    override suspend fun deleteSticker(packId: StickerPackId, fileName: String): Either<StickerPackError, Unit> {
        if (failWrites) return writeFailure.left()
        deletedStickers += packId to fileName
        packs.value = current().map { pack ->
            if (pack.id == packId) pack.copy(stickers = pack.stickers.filterNot { it.fileName == fileName }) else pack
        }.right()
        return Unit.right()
    }

    override suspend fun setTray(file: PackFile): Either<StickerPackError, Unit> = Unit.right()

    override suspend fun setEmojis(
        packId: StickerPackId,
        fileName: String,
        emojis: List<String>,
    ): Either<StickerPackError, Unit> {
        if (failWrites) return writeFailure.left()
        emojiWrites += emojis
        return Unit.right()
    }

    override suspend fun setKeyboardAvailability(id: StickerPackId, isAvailable: Boolean): Either<StickerPackError, Unit> {
        if (failWrites) return writeFailure.left()
        keyboardWrites += isAvailable
        packs.value = current().map { if (it.id == id) it.copy(isAvailableInKeyboard = isAvailable) else it }.right()
        return Unit.right()
    }
}

/** Runs the pending main-dispatcher work, then returns the latest emitted item. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> ReceiveTurbine<T>.settled(scheduler: TestCoroutineScheduler): T {
    scheduler.advanceUntilIdle()
    return expectMostRecentItem()
}

/** Runs the pending main-dispatcher work, then asserts that nothing new was emitted. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> ReceiveTurbine<T>.assertNoChange(scheduler: TestCoroutineScheduler) {
    scheduler.advanceUntilIdle()
    expectNoEvents()
}

class FakeBackupReader(private val archive: com.damianogiusti.stickermaker.domain.model.PackArchive) :
    com.damianogiusti.stickermaker.domain.repository.BackupReader {
    override val sourceIds = listOf("1")
    override suspend fun load(sourceId: String) = archive.right()
    override suspend fun discard() = Unit
}

class FakePackArchiveStore : com.damianogiusti.stickermaker.domain.repository.PackArchiveStore {
    var failure: com.damianogiusti.stickermaker.domain.error.TransferError? = null
    var wastickers = com.damianogiusti.stickermaker.domain.model.PackArchive("Imported", "Me", null, emptyList())
    var writtenBackupLocation: String? = null
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private suspend fun <T> answer(value: T): Either<com.damianogiusti.stickermaker.domain.error.TransferError, T> {
        gate?.await()
        return failure?.left() ?: value.right()
    }

    override suspend fun readWastickers(location: String) = answer(wastickers)
    override suspend fun writeWastickersForSharing(fileName: String, archive: com.damianogiusti.stickermaker.domain.model.PackArchive) =
        answer("/cache/share/$fileName")

    override suspend fun openBackup(location: String) =
        answer<com.damianogiusti.stickermaker.domain.repository.BackupReader>(FakeBackupReader(wastickers))

    override suspend fun writeBackup(
        location: String,
        packs: List<StickerPack>,
        files: com.damianogiusti.stickermaker.domain.repository.PackFileSource,
    ) = answer(Unit).also { writtenBackupLocation = location }
}

class FakePackImportRepository : com.damianogiusti.stickermaker.domain.repository.PackImportRepository {
    var nextId = 500

    override suspend fun insertPack(
        archive: com.damianogiusti.stickermaker.domain.model.PackArchive,
        isImported: Boolean,
    ): Either<StickerPackError, StickerPack> = aPack(id = (nextId++).toString(), name = archive.name).right()
}

class FakePackFileSource : com.damianogiusti.stickermaker.domain.repository.PackFileSource {
    override suspend fun read(packId: StickerPackId, fileName: String): Either<StickerPackError, ByteArray> =
        byteArrayOf(1).right()

    override suspend fun open(packId: StickerPackId, fileName: String): Either<StickerPackError, java.io.InputStream> =
        java.io.ByteArrayInputStream(byteArrayOf(1)).right()
}

/** Keeps the logged errors so tests can assert that a swallowed error was at least logged. */
class RecordingErrorLogger : com.damianogiusti.stickermaker.ui.logging.ErrorLogger {
    val messages = mutableListOf<String>()

    override fun log(tag: String, message: String) {
        messages += "$tag: $message"
    }
}
