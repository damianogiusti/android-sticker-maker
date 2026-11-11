package com.damianogiusti.stickermaker.data

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.data.transfer.BackupCodec
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.BackupLimits
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.repository.BackupReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class BackupCodecTest {
    private val workDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = workDirs.forEach { it.deleteRecursively() }

    private fun codec(limits: BackupLimits = BackupLimits.DEFAULT) = BackupCodec(limits = limits, ioDispatcher = Dispatchers.Unconfined)

    private suspend fun write(
        stored: List<StoredPack>,
        codec: BackupCodec = codec(),
    ): Either<TransferError, ByteArray> {
        val out = ByteArrayOutputStream()
        return codec.write(stored.packs(), stored.fileSource(), out).map { out.toByteArray() }
    }

    private suspend fun writeOk(stored: List<StoredPack>, codec: BackupCodec = codec()): ByteArray =
        write(stored, codec).right()

    private suspend fun extract(bytes: ByteArray, codec: BackupCodec = codec()): Either<TransferError, BackupReader> {
        val dir = Files.createTempDirectory("restore").toFile().also { workDirs += it }
        return codec.extract(ByteArrayInputStream(bytes), dir)
    }

    private fun <A> Either<TransferError, A>.right(): A = when (this) {
        is Either.Right -> value
        is Either.Left -> fail("Expected Right but was Left($value)")
    }

    private fun <A> Either<TransferError, A>.left(): TransferError = when (this) {
        is Either.Right -> fail("Expected Left but was Right($value)")
        is Either.Left -> value
    }

    private suspend fun BackupReader.loadAll(): List<PackArchive> = sourceIds.map { load(it).right() }

    private fun entryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        return names
    }

    private fun manifestOf(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "manifest.json") return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        fail("no manifest")
    }

    private val sample = listOf(
        aStoredPack("1700000000", name = "Cats", emojis = listOf("🐱", "😀"), isAvailableInKeyboard = true),
        aStoredPack("1700000001", name = "Dogs", stickerCount = 1, withTray = false),
    )

    private val oneStickerManifest = """{"formatVersion":1,"packs":[{"id":"1","name":"A","publisher":"B","position":0,
        "isAvailableInKeyboard":false,"stickers":[{"fileName":"1.webp","emojis":[],"position":0}]}]}"""

    @Test
    fun `backup round trips packs, emojis, tray and keyboard flag in order`() = runTest {
        val reader = extract(writeOk(sample)).right()

        assertEquals(listOf("1700000000", "1700000001"), reader.sourceIds)
        val (cats, dogs) = reader.loadAll()
        assertEquals("Cats", cats.name)
        assertEquals("Me", cats.publisher)
        assertTrue(cats.isAvailableInKeyboard)
        assertEquals(listOf("🐱", "😀"), cats.stickers.first().emojis)
        assertContentEquals(pngBytes(), cats.tray)
        sample[0].pack.stickers.zip(cats.stickers).forEach { (sticker, restored) ->
            assertContentEquals(sample[0].files.getValue("1700000000/${sticker.fileName}"), restored.bytes)
        }
        assertNull(dogs.tray)
        assertEquals(1, dogs.stickers.size)
    }

    @Test
    fun `the zip starts with the manifest and holds packs slash id files`() = runTest {
        val names = entryNames(writeOk(sample))

        assertEquals("manifest.json", names.first())
        assertTrue("packs/1700000000/tray.png" in names)
        assertTrue("packs/1700000000/1.webp" in names)
        assertTrue("packs/1700000001/1.webp" in names)
    }

    @Test
    fun `manifest is json with format version 1`() = runTest {
        val manifest = manifestOf(writeOk(sample))

        assertTrue(manifest.contains("\"formatVersion\":1"), manifest)
        assertTrue(manifest.contains("\"name\":\"Cats\""), manifest)
        assertTrue(manifest.contains("\"isAvailableInKeyboard\":true"), manifest)
        assertTrue(manifest.contains("\"emojis\":[\"🐱\",\"😀\"]"), manifest)
    }

    @Test
    fun `an unknown manifest version is rejected`() = runTest {
        val bytes = zipOf(listOf("manifest.json" to """{"formatVersion":2,"packs":[]}""".toByteArray()))

        assertEquals(TransferError.UnsupportedBackupVersion(2), extract(bytes).left())
    }

    @Test
    fun `a zip without manifest is not a backup`() = runTest {
        assertTrue(extract(zipOf(listOf("a.txt" to byteArrayOf(1)))).left() is TransferError.InvalidBackup)
    }

    @Test
    fun `broken manifest json is an invalid backup`() = runTest {
        assertTrue(extract(zipOf(listOf("manifest.json" to "{nope".toByteArray()))).left() is TransferError.InvalidBackup)
    }

    @Test
    fun `a file listed in the manifest but missing is an invalid backup`() = runTest {
        val bytes = zipOf(listOf("manifest.json" to oneStickerManifest.toByteArray()))

        assertTrue(extract(bytes).left() is TransferError.InvalidBackup)
    }

    @Test
    fun `a manifest pointing outside the pack directory is rejected`() = runTest {
        val manifest = oneStickerManifest.replace("1.webp", "../x.webp")
        val bytes = zipOf(listOf("manifest.json" to manifest.toByteArray()))

        assertEquals(TransferError.UnsafeEntry("../x.webp"), extract(bytes).left())
    }

    @Test
    fun `an animated sticker in a backup is rejected`() = runTest {
        val bytes = zipOf(listOf("manifest.json" to oneStickerManifest.toByteArray(), "packs/1/1.webp" to animatedWebp()))

        assertEquals(TransferError.AnimatedNotSupported("1.webp"), extract(bytes).left())
    }

    @Test
    fun `zip slip entry in a backup is rejected`() = runTest {
        val bytes = zipOf(listOf("manifest.json" to "{}".toByteArray(), "../evil" to byteArrayOf(1)))

        assertEquals(TransferError.UnsafeEntry("../evil"), extract(bytes).left())
    }

    @Test
    fun `bytes that are not a zip are rejected`() = runTest {
        assertEquals(TransferError.NotAZip, extract("nope nope nope".toByteArray()).left())
    }

    @Test
    fun `discarding the reader removes the spooled files`() = runTest {
        val dir = Files.createTempDirectory("restore").toFile().also { workDirs += it }
        val reader = codec().extract(ByteArrayInputStream(writeOk(sample)), dir).right()
        assertTrue(dir.walkTopDown().any { it.isFile })

        reader.discard()

        assertTrue(!dir.exists())
    }

    // The maximum allowed size, scaled down so the test stays fast: every limit is hit exactly.
    private val tight = BackupLimits(maxFileBytes = 2_000, maxPacks = 2, maxManifestBytes = 100_000)

    private fun fullPack(id: String, fileBytes: Int = tight.maxFileBytes.toInt()) = aStoredPack(
        id,
        stickerCount = StickerPackLimits.MAX_STICKERS,
        sticker = { paddedWebp(fileBytes) },
        tray = pngBytes(fileBytes - 8),
    )

    @Test
    fun `a backup at the maximum size round trips with the same limits`() = runTest {
        val stored = listOf(fullPack("1"), fullPack("2"))

        val bytes = writeOk(stored, codec(tight))
        val restored = extract(bytes, codec(tight)).right().loadAll()

        assertEquals(tight.maxEntries, entryNames(bytes).size)
        assertEquals(listOf(30, 30), restored.map { it.stickers.size })
        assertTrue(restored.all { pack -> pack.stickers.all { it.bytes.size.toLong() == tight.maxFileBytes } })
        assertEquals(tight.maxFileBytes, restored.first().tray?.size?.toLong())
    }

    @Test
    fun `the default limits round trip a file of the largest size and allow a full set of entries`() = runTest {
        val limits = BackupLimits.DEFAULT
        // Every pack may hold a tray plus 30 stickers, and one entry is the manifest.
        assertEquals(1 + limits.maxPacks * 31, limits.maxEntries)

        val big = listOf(aStoredPack("big", stickerCount = 1, sticker = { paddedWebp(limits.maxFileBytes.toInt()) }))
        val restored = extract(writeOk(big)).right().loadAll().single()

        assertEquals(limits.maxFileBytes, restored.stickers.single().bytes.size.toLong())
    }

    @Test
    fun `the writer refuses what the reader would refuse`() = runTest {
        assertEquals(TransferError.TooLarge, write(listOf(fullPack("1", fileBytes = 2_002)), codec(tight)).left())
        assertEquals(TransferError.TooLarge, write(listOf(fullPack("1"), fullPack("2"), fullPack("3")), codec(tight)).left())
    }

    @Test
    fun `the reader refuses a file over the cap, too many entries and a huge manifest`() = runTest {
        val oversize = zipOf(listOf("manifest.json" to oneStickerManifest.toByteArray(), "packs/1/1.webp" to paddedWebp(2_002)))
        assertEquals(TransferError.TooLarge, extract(oversize, codec(tight)).left())

        val tooMany = zipOf(List(tight.maxEntries + 1) { "junk/$it" to byteArrayOf(1) })
        assertEquals(TransferError.TooLarge, extract(tooMany, codec(tight)).left())

        val hugeManifest = zipOf(listOf("manifest.json" to ByteArray(100_001)))
        assertEquals(TransferError.TooLarge, extract(hugeManifest, codec(tight)).left())
    }

    @Test
    fun `writing needs only open, never whole-file reads`() = runTest {
        // MapPackFileSource.read throws; reaching here without an AssertionError proves the streaming path.
        writeOk(sample)
    }
}
