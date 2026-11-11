package com.damianogiusti.stickermaker.data

import app.cash.turbine.test
import arrow.core.Either
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.data.repository.DatabaseStickerPackRepository
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.PackFile
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DatabaseStickerPackRepositoryTest {
    private val root: File = Files.createTempDirectory("packs").toFile()
    private val storage = PackFileStorage(root)
    private val clock = FakeClock()
    private val database = createInMemoryDatabase()

    private fun TestScopeRepository(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        DatabaseStickerPackRepository(database, storage, dispatcher, clock)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun <E, A> Either<E, A>.expectRight(): A = when (this) {
        is Either.Right -> value
        is Either.Left -> fail("Expected Right but was Left($value)")
    }

    private fun file(pack: StickerPack, name: String, size: Int = 10) =
        PackFile(pack.id, name, ByteArray(size) { 7 })

    @Test
    fun `create pack uses seconds as id and starts at version 1`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()

        assertEquals(StickerPackId("1700000000"), pack.id)
        assertEquals(1, pack.imageDataVersion)
        assertEquals(0, pack.position)
        assertNull(pack.trayFileName)
        assertEquals(emptyList(), pack.stickers)
    }

    @Test
    fun `ids are unique within the same second and positions increase`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val first = repo.createPack("A", "me").expectRight()
        val second = repo.createPack("B", "me").expectRight()

        assertEquals(StickerPackId("1700000001"), second.id)
        assertEquals(listOf(first.position, second.position), listOf(0, 1))
    }

    @Test
    fun `add sticker stores the file, appends position and bumps version`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()

        repo.addSticker(file(pack, "1.webp", 42)).expectRight()
        repo.addSticker(file(pack, "2.webp")).expectRight()

        val loaded = repo.getPack(pack.id).expectRight()
        assertEquals(listOf("1.webp", "2.webp"), loaded.stickers.map { it.fileName })
        assertEquals(listOf(0, 1), loaded.stickers.map { it.position })
        assertEquals(42L, loaded.stickers.first().sizeBytes)
        assertEquals(3, loaded.imageDataVersion)
        assertTrue(storage.exists(pack.id.value, "1.webp"))
        assertFalse(File(root, "${pack.id.value}/1.webp.tmp").exists())
    }

    @Test
    fun `adding beyond 30 stickers fails and writes nothing`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        repeat(30) { repo.addSticker(file(pack, "$it.webp")).expectRight() }

        val result = repo.addSticker(file(pack, "extra.webp"))

        assertEquals(Either.Left(StickerPackError.TooManyStickers), result)
        assertFalse(storage.exists(pack.id.value, "extra.webp"))
    }

    @Test
    fun `replace sticker keeps position and emojis and removes the old file`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        repo.addSticker(file(pack, "1.webp")).expectRight()
        repo.addSticker(file(pack, "2.webp")).expectRight()
        repo.setEmojis(pack.id, "2.webp", listOf("a", "b")).expectRight()

        repo.replaceSticker("2.webp", file(pack, "3.webp", 99)).expectRight()

        val loaded = repo.getPack(pack.id).expectRight()
        val replaced = loaded.stickers.last()
        assertEquals("3.webp", replaced.fileName)
        assertEquals(1, replaced.position)
        assertEquals(listOf("a", "b"), replaced.emojis)
        assertEquals(99L, replaced.sizeBytes)
        assertEquals(5, loaded.imageDataVersion)
        assertFalse(storage.exists(pack.id.value, "2.webp"))
        assertTrue(storage.exists(pack.id.value, "3.webp"))
    }

    @Test
    fun `delete sticker removes file, compacts positions and bumps version`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        listOf("1.webp", "2.webp", "3.webp").forEach { repo.addSticker(file(pack, it)).expectRight() }

        repo.deleteSticker(pack.id, "1.webp").expectRight()

        val loaded = repo.getPack(pack.id).expectRight()
        assertEquals(listOf("2.webp", "3.webp"), loaded.stickers.map { it.fileName })
        assertEquals(listOf(0, 1), loaded.stickers.map { it.position })
        assertEquals(5, loaded.imageDataVersion)
        assertFalse(storage.exists(pack.id.value, "1.webp"))
    }

    @Test
    fun `emojis round trip and unknown sticker is a typed error`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        repo.addSticker(file(pack, "1.webp")).expectRight()

        repo.setEmojis(pack.id, "1.webp", listOf("😀", "🐱")).expectRight()

        assertEquals(listOf("😀", "🐱"), repo.getPack(pack.id).expectRight().stickers.single().emojis)
        assertEquals(
            Either.Left(StickerPackError.StickerNotFound(pack.id, "nope.webp")),
            repo.setEmojis(pack.id, "nope.webp", listOf("😀")),
        )
    }

    @Test
    fun `changing emojis bumps imageDataVersion`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        repo.addSticker(file(pack, "1.webp")).expectRight()
        val before = repo.getPack(pack.id).expectRight().imageDataVersion

        repo.setEmojis(pack.id, "1.webp", listOf("😀")).expectRight()

        assertEquals(before + 1, repo.getPack(pack.id).expectRight().imageDataVersion)
    }

    @Test
    fun `set tray replaces the previous tray file`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()

        repo.setTray(file(pack, "tray.png")).expectRight()
        repo.setTray(file(pack, "tray2.png")).expectRight()

        assertEquals("tray2.png", repo.getPack(pack.id).expectRight().trayFileName)
        assertFalse(storage.exists(pack.id.value, "tray.png"))
        assertTrue(storage.exists(pack.id.value, "tray2.png"))
    }

    @Test
    fun `set tray bumps imageDataVersion so WhatsApp refreshes its cache`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()

        repo.setTray(file(pack, "tray.png")).expectRight()
        assertEquals(2, repo.getPack(pack.id).expectRight().imageDataVersion)

        repo.setTray(file(pack, "tray.png")).expectRight()
        assertEquals(3, repo.getPack(pack.id).expectRight().imageDataVersion)
    }

    @Test
    fun `keyboard availability sets and clears the added timestamp`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()

        repo.setKeyboardAvailability(pack.id, true).expectRight()
        val on = repo.getPack(pack.id).expectRight()
        assertTrue(on.isAvailableInKeyboard)
        assertEquals(clock.now, on.keyboardAddedAt)

        repo.setKeyboardAvailability(pack.id, false).expectRight()
        val off = repo.getPack(pack.id).expectRight()
        assertFalse(off.isAvailableInKeyboard)
        assertNull(off.keyboardAddedAt)
    }

    @Test
    fun `rename updates name and publisher`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        repo.renamePack(pack.id, "Dogs", "you").expectRight()

        val loaded = repo.getPack(pack.id).expectRight()
        assertEquals("Dogs", loaded.name)
        assertEquals("you", loaded.publisher)
    }

    @Test
    fun `delete pack cascades to stickers and removes the directory`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val pack = repo.createPack("Cats", "me").expectRight()
        repo.addSticker(file(pack, "1.webp")).expectRight()

        repo.deletePack(pack.id).expectRight()

        assertIs<Either.Left<StickerPackError>>(repo.getPack(pack.id))
        assertEquals(0, database.stickerPackQueries.selectStickers().executeAsList().size)
        assertFalse(File(root, pack.id.value).exists())
    }

    @Test
    fun `unknown pack is NotFound and path traversal is a storage error`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val missing = StickerPackId("999")
        assertEquals(Either.Left(StickerPackError.NotFound(missing)), repo.getPack(missing))
        assertEquals(Either.Left(StickerPackError.NotFound(missing)), repo.deletePack(missing))

        val pack = repo.createPack("Cats", "me").expectRight()
        val result = repo.addSticker(PackFile(pack.id, "../evil.webp", ByteArray(1)))
        assertIs<StickerPackError.Storage>((result as Either.Left).value)
    }

    @Test
    fun `observePacks emits on every change ordered by position`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        repo.observePacks().test {
            assertEquals(0, awaitItem().expectRight().size)
            val pack = repo.createPack("Cats", "me").expectRight()
            assertEquals(1, awaitItem().expectRight().size)
            repo.addSticker(file(pack, "1.webp")).expectRight()
            assertEquals(1, awaitItem().expectRight().single().stickers.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observePack reports NotFound for a missing pack`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        repo.observePack(StickerPackId("1")).test {
            assertEquals(Either.Left(StickerPackError.NotFound(StickerPackId("1"))), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insert pack stores files, rows, emojis and keeps order`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val archive = com.damianogiusti.stickermaker.domain.model.PackArchive(
            name = "Cats",
            publisher = "Me",
            tray = ByteArray(5) { 1 },
            stickers = listOf(
                com.damianogiusti.stickermaker.domain.model.ArchiveSticker(ByteArray(10), listOf("a", "b")),
                com.damianogiusti.stickermaker.domain.model.ArchiveSticker(ByteArray(20)),
            ),
            isAvailableInKeyboard = true,
        )

        val pack = repo.insertPack(archive, isImported = true).expectRight()

        assertEquals("tray.png", pack.trayFileName)
        assertEquals(listOf("1.webp", "2.webp"), pack.stickers.map { it.fileName })
        assertEquals(listOf("a", "b"), pack.stickers.first().emojis)
        assertEquals(listOf(10L, 20L), pack.stickers.map { it.sizeBytes })
        assertTrue(pack.isImported)
        assertTrue(pack.isAvailableInKeyboard)
        assertEquals(clock.now, pack.keyboardAddedAt)
        assertEquals(1, pack.imageDataVersion)
        assertTrue(storage.exists(pack.id.value, "tray.png"))
        assertTrue(storage.exists(pack.id.value, "2.webp"))
    }

    @Test
    fun `insert pack gets a new id when the id is taken`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val existing = repo.createPack("Old", "me").expectRight()
        val archive = com.damianogiusti.stickermaker.domain.model.PackArchive("New", "me", null, emptyList())

        val inserted = repo.insertPack(archive, isImported = false).expectRight()

        assertTrue(inserted.id != existing.id)
        assertEquals(1, inserted.position)
    }

    @Test
    fun `insert pack that fails writing files leaves no pack behind`() = runTest {
        val blocker = File.createTempFile("not-a-directory", ".tmp")
        val brokenStorage = PackFileStorage(blocker)
        val repo = DatabaseStickerPackRepository(database, brokenStorage, StandardTestDispatcher(testScheduler), clock)
        val archive = com.damianogiusti.stickermaker.domain.model.PackArchive(
            "A", "B", null, listOf(com.damianogiusti.stickermaker.domain.model.ArchiveSticker(ByteArray(3))),
        )

        val result = repo.insertPack(archive, isImported = true)

        assertIs<StickerPackError.Storage>((result as Either.Left).value)
        assertEquals(0L, database.stickerPackQueries.selectPackExists("1700000000").executeAsOne())
        blocker.delete()
    }

    @Test
    fun `insert pack rejects more than 30 stickers`() = runTest {
        val repo = TestScopeRepository(StandardTestDispatcher(testScheduler))
        val archive = com.damianogiusti.stickermaker.domain.model.PackArchive(
            "A", "B", null, List(31) { com.damianogiusti.stickermaker.domain.model.ArchiveSticker(ByteArray(1)) },
        )

        assertEquals(Either.Left(StickerPackError.TooManyStickers), repo.insertPack(archive, isImported = false))
    }
}
