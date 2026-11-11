package com.damianogiusti.stickermaker.domain

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.ArchiveSticker
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.TransferSummary
import com.damianogiusti.stickermaker.domain.usecase.CreateBackupUseCase
import com.damianogiusti.stickermaker.domain.usecase.ExportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ImportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.RestoreBackupUseCase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TransferUseCasesTest {
    private val repository = FakeStickerPackRepository(
        listOf(
            aPack(id = "10", name = "Cats & Dogs!", stickers = listOf(aSticker(1, listOf("🐱")), aSticker(0))),
            aPack(id = "11", name = "Bare", trayFileName = null),
        ),
    )
    private val files = FakePackFileSource(
        mapOf(
            "10/tray.png" to byteArrayOf(9),
            "10/0.webp" to byteArrayOf(0),
            "10/1.webp" to byteArrayOf(1),
        ),
    )
    private val store = FakePackArchiveStore()

    private fun archive(name: String = "Imported") = PackArchive(name, "Me", null, listOf(ArchiveSticker(byteArrayOf(1))))

    private fun <E, A> Either<E, A>.expectRight(): A = when (this) {
        is Either.Right -> value
        is Either.Left -> fail("Expected Right but was Left($value)")
    }

    private fun <E, A> Either<E, A>.expectLeft(): E = when (this) {
        is Either.Right -> fail("Expected Left but was Right($value)")
        is Either.Left -> value
    }

    @Test
    fun `import inserts the archive as an imported pack`() = runTest {
        store.wastickers = Either.Right(archive("Fresh"))
        val importRepository = FakePackImportRepository(repository)

        val pack = ImportStickerPackUseCase(store, importRepository)("content://x").expectRight()

        assertEquals("Fresh", pack.name)
        assertTrue(importRepository.inserted.single().second)
    }

    @Test
    fun `import passes archive errors through and inserts nothing`() = runTest {
        store.wastickers = Either.Left(TransferError.AnimatedNotSupported("1.webp"))
        val importRepository = FakePackImportRepository(repository)

        val error = ImportStickerPackUseCase(store, importRepository)("content://x").expectLeft()

        assertEquals(TransferError.AnimatedNotSupported("1.webp"), error)
        assertTrue(importRepository.inserted.isEmpty())
    }

    @Test
    fun `import wraps repository failures`() = runTest {
        store.wastickers = Either.Right(archive())
        val importRepository = FakePackImportRepository(repository, failOnCall = 1)

        val error = ImportStickerPackUseCase(store, importRepository)("content://x").expectLeft()

        assertEquals(TransferError.Pack(StickerPackError.Storage("disk full")), error)
    }

    @Test
    fun `export writes title, publisher, tray and stickers in position order with a safe file name`() = runTest {
        val exported = ExportStickerPackUseCase(repository, files, store)(StickerPackId("10")).expectRight()

        assertEquals("Cats___Dogs.wastickers", exported.fileName)
        assertEquals("/cache/share/Cats___Dogs.wastickers", exported.location)
        val written = assertNotNull(store.writtenShare).second
        assertEquals("Cats & Dogs!", written.name)
        assertEquals("Me", written.publisher)
        assertContentEquals(byteArrayOf(9), written.tray)
        assertEquals(listOf<Byte>(0, 1), written.stickers.map { it.bytes.single() })
        assertEquals(listOf("🐱"), written.stickers[1].emojis)
    }

    @Test
    fun `export of a pack without tray has no tray`() = runTest {
        ExportStickerPackUseCase(repository, files, store)(StickerPackId("11")).expectRight()

        assertNull(assertNotNull(store.writtenShare).second.tray)
    }

    @Test
    fun `export of an unknown pack is a pack error`() = runTest {
        val error = ExportStickerPackUseCase(repository, files, store)(StickerPackId("nope")).expectLeft()

        assertEquals(TransferError.Pack(StickerPackError.NotFound(StickerPackId("nope"))), error)
    }

    @Test
    fun `export reports a missing file`() = runTest {
        val noFiles = FakePackFileSource(emptyMap())

        val error = ExportStickerPackUseCase(repository, noFiles, store)(StickerPackId("10")).expectLeft()

        assertIs<TransferError.Pack>(error)
    }

    @Test
    fun `export passes store failures through`() = runTest {
        store.writeFailure = TransferError.Io("full")

        assertEquals(TransferError.Io("full"), ExportStickerPackUseCase(repository, files, store)(StickerPackId("10")).expectLeft())
    }

    @Test
    fun `backup hands every pack and the file source to the store without reading files`() = runTest {
        val summary = CreateBackupUseCase(repository, files, store)("content://out").expectRight()

        assertEquals(TransferSummary(2), summary)
        val (location, packs) = assertNotNull(store.writtenBackup)
        assertEquals("content://out", location)
        assertEquals(listOf("10", "11"), packs.map { it.id.value })
        // The store copies file by file; the use case itself must not load any pack into memory.
        assertEquals(0, files.reads)
        assertEquals(files, store.backupFiles)
    }

    @Test
    fun `backup passes store failures through`() = runTest {
        store.writeFailure = TransferError.TooLarge

        assertEquals(TransferError.TooLarge, CreateBackupUseCase(repository, files, store)("content://out").expectLeft())
        assertNull(store.writtenBackup)
    }

    private fun readerOf(vararg names: String, failOn: String? = null, log: MutableList<String> = mutableListOf()) =
        FakeBackupReader(names.associateWith { archive(it) }, log, failOn)

    @Test
    fun `restore creates every pack as a new, not imported, pack, one at a time`() = runTest {
        val log = mutableListOf<String>()
        val reader = readerOf("A", "B", log = log)
        store.backupToOpen = Either.Right(reader)
        val importRepository = FakePackImportRepository(repository, log = log)

        val summary = RestoreBackupUseCase(store, importRepository, repository)("content://in").expectRight()

        assertEquals(TransferSummary(2), summary)
        assertEquals(listOf(false, false), importRepository.inserted.map { it.second })
        assertEquals(4, repository.packs.value.size)
        // Memory stays bounded: a pack is inserted before the next one is loaded.
        assertEquals(listOf("load A", "insert A", "load B", "insert B"), log)
        assertEquals(1, reader.discarded)
    }

    @Test
    fun `restore removes the packs it already created when a later one fails`() = runTest {
        val reader = readerOf("A", "B")
        store.backupToOpen = Either.Right(reader)
        val importRepository = FakePackImportRepository(repository, failOnCall = 2)

        val error = RestoreBackupUseCase(store, importRepository, repository)("content://in").expectLeft()

        assertIs<TransferError.Pack>(error)
        assertEquals(listOf("10", "11"), repository.packs.value.map { it.id.value })
        assertEquals(1, reader.discarded)
    }

    @Test
    fun `restore removes the packs it already created when a later pack cannot be loaded`() = runTest {
        val reader = readerOf("A", "B", failOn = "B")
        store.backupToOpen = Either.Right(reader)

        val error = RestoreBackupUseCase(store, FakePackImportRepository(repository), repository)("content://in").expectLeft()

        assertIs<TransferError.InvalidBackup>(error)
        assertEquals(listOf("10", "11"), repository.packs.value.map { it.id.value })
        assertEquals(1, reader.discarded)
    }

    @Test
    fun `restore of a backup without packs is an invalid backup`() = runTest {
        val reader = readerOf()
        store.backupToOpen = Either.Right(reader)

        val error = RestoreBackupUseCase(store, FakePackImportRepository(repository), repository)("content://in").expectLeft()

        assertIs<TransferError.InvalidBackup>(error)
        assertEquals(1, reader.discarded)
    }

    @Test
    fun `restore passes open errors through`() = runTest {
        store.backupToOpen = Either.Left(TransferError.UnsupportedBackupVersion(7))

        val error = RestoreBackupUseCase(store, FakePackImportRepository(repository), repository)("content://in").expectLeft()

        assertEquals(TransferError.UnsupportedBackupVersion(7), error)
    }
}
