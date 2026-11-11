package com.damianogiusti.stickermaker.data

import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PackFileStorageTest {
    private val root = Files.createTempDirectory("storage").toFile()
    private val storage = PackFileStorage(root)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `write overwrites atomically and leaves no temp file`() {
        storage.write("1", "a.webp", byteArrayOf(1, 2))
        val file = storage.write("1", "a.webp", byteArrayOf(3))

        assertContentEquals(byteArrayOf(3), file.readBytes())
        assertEquals(listOf("a.webp"), file.parentFile?.list()?.toList())
    }

    @Test
    fun `delete and delete pack directory`() {
        storage.write("1", "a.webp", byteArrayOf(1))
        storage.delete("1", "a.webp")
        assertFalse(storage.exists("1", "a.webp"))

        storage.write("1", "b.webp", byteArrayOf(1))
        storage.deletePackDirectory("1")
        assertFalse(java.io.File(root, "1").exists())
    }

    @Test
    fun `unsafe names are rejected`() {
        assertFailsWith<IllegalArgumentException> { storage.write("1", "../x", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { storage.write("1", "a/b", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { storage.write("..", "x", byteArrayOf(1)) }
    }
}
