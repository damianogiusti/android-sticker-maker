package com.damianogiusti.stickermaker.data

import arrow.core.Either
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.data.transfer.BackupCodec
import com.damianogiusti.stickermaker.data.transfer.StreamOpener
import com.damianogiusti.stickermaker.data.transfer.StreamPackArchiveStore
import com.damianogiusti.stickermaker.data.transfer.WastickersCodec
import com.damianogiusti.stickermaker.domain.error.TransferError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

private class FakeStreamOpener : StreamOpener {
    val inputs = mutableMapOf<String, ByteArray>()
    val outputs = mutableMapOf<String, ByteArrayOutputStream>()
    val names = mutableMapOf<String, String>()

    override fun openInput(location: String): InputStream =
        ByteArrayInputStream(inputs[location] ?: throw IOException("missing $location"))

    override fun openOutput(location: String): OutputStream = ByteArrayOutputStream().also { outputs[location] = it }
    override fun displayName(location: String): String? = names[location]
}

class StreamPackArchiveStoreTest {
    private val shareDir = Files.createTempDirectory("share").toFile().resolve("share")
    private val workDir = shareDir.parentFile.resolve("restore")
    private val opener = FakeStreamOpener()

    @AfterTest
    fun cleanUp() {
        shareDir.parentFile.deleteRecursively()
    }

    private fun store(testDispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        StreamPackArchiveStore(
            opener,
            shareDir,
            workDir,
            WastickersCodec(),
            BackupCodec(ioDispatcher = testDispatcher),
            testDispatcher,
        )

    @Test
    fun `sharing writes a wastickers file that reads back`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        val location = (store.writeWastickersForSharing("cats.wastickers", anArchive()) as Either.Right).value
        opener.inputs["in"] = File(location).readBytes()
        val read = store.readWastickers("in")

        assertEquals(File(shareDir, "cats.wastickers").absolutePath, location)
        assertEquals("Cats", (read as Either.Right).value.name)
    }

    @Test
    fun `import without title uses the display name without extension`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        opener.inputs["in"] = zipOf(listOf("a.webp" to staticWebp()))
        opener.names["in"] = "Funny dogs.wastickers"

        val read = store.readWastickers("in")

        assertEquals("Funny dogs", (read as Either.Right).value.name)
    }

    @Test
    fun `an unreadable location is an Io error`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        val result = store.readWastickers("nowhere")

        assertIs<TransferError.Io>((result as Either.Left).value)
    }

    @Test
    fun `backup written to a location reads back and its work directory is removed on discard`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val stored = listOf(aStoredPack("9"))

        val written = store.writeBackup("out", stored.packs(), stored.fileSource())
        opener.inputs["in"] = opener.outputs.getValue("out").toByteArray()
        val read = store.openBackup("in")

        assertTrue(written is Either.Right)
        val reader = (read as? Either.Right ?: fail("read failed: $read")).value
        assertEquals(listOf("9"), reader.sourceIds)
        assertTrue(workDir.walkTopDown().any { it.isFile })
        reader.discard()
        assertTrue(workDir.walkTopDown().none { it.isFile })
    }

    @Test
    fun `a backup that fails validation leaves no files behind`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        opener.inputs["in"] = zipOf(listOf("manifest.json" to "{nope".toByteArray(), "packs/1/1.webp" to staticWebp()))

        val read = store.openBackup("in")

        assertIs<TransferError.InvalidBackup>((read as Either.Left).value)
        assertTrue(workDir.walkTopDown().none { it.isFile })
    }
}
