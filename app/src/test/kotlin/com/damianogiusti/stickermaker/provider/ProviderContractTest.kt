package com.damianogiusti.stickermaker.provider

import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderContractTest {
    private val pack = StickerPack(
        id = StickerPackId("1700000000"),
        name = "Cats",
        publisher = "Me",
        trayFileName = "tray.png",
        stickers = listOf(
            Sticker("b.webp", listOf("🐱", "😺"), 1_000, position = 1),
            Sticker("a.webp", listOf("😀"), 1_000, position = 0),
        ),
        imageDataVersion = 4,
        isAvailableInKeyboard = false,
        keyboardAddedAt = null,
        isImported = false,
        position = 0,
    )

    @Test
    fun `metadata has the 13 contract columns in order`() {
        val data = StickerCursorMapper.metadata(listOf(pack))
        assertEquals(13, data.columns.size)
        assertEquals("sticker_pack_identifier", data.columns.first())
        assertEquals("animated_sticker_pack", data.columns.last())
        assertTrue(data.rows.all { it.size == data.columns.size })
    }

    @Test
    fun `metadata row carries pack values and empty web fields`() {
        val row = StickerCursorMapper.metadata(listOf(pack)).rows.single()
        assertEquals(listOf("1700000000", "Cats", "Me", "tray.png"), row.take(4))
        assertEquals(List(6) { "" }, row.subList(4, 10))
        assertEquals(listOf<Any?>(4, 0, 0), row.takeLast(3))
    }

    @Test
    fun `metadata lists only the given packs, no padding`() {
        assertTrue(StickerCursorMapper.metadata(emptyList()).rows.isEmpty())
        assertEquals(2, StickerCursorMapper.metadata(listOf(pack, pack.copy(id = StickerPackId("2")))).rows.size)
    }

    @Test
    fun `a pack without tray gets an empty icon name`() {
        assertEquals("", StickerCursorMapper.metadata(listOf(pack.copy(trayFileName = null))).rows.single()[3])
    }

    @Test
    fun `stickers are in position order with joined emojis and no placeholders`() {
        val data = StickerCursorMapper.stickers(pack)
        assertEquals(listOf("sticker_file_name", "sticker_emoji", "is_animated_sticker"), data.columns)
        assertEquals(
            listOf(listOf<Any?>("a.webp", "😀", 0), listOf<Any?>("b.webp", "🐱,😺", 0)),
            data.rows,
        )
        assertEquals(1, StickerCursorMapper.stickers(pack.copy(stickers = pack.stickers.take(1))).rows.size)
    }

    @Test
    fun `routes parse the four contract paths`() {
        assertEquals(ProviderRoute.Metadata, ProviderRoute.parse(listOf("metadata")))
        assertEquals(ProviderRoute.MetadataItem("7"), ProviderRoute.parse(listOf("metadata", "7")))
        assertEquals(ProviderRoute.Stickers("7"), ProviderRoute.parse(listOf("stickers", "7")))
        assertEquals(ProviderRoute.Asset("7", "a.webp"), ProviderRoute.parse(listOf("stickers_asset", "7", "a.webp")))
    }

    @Test
    fun `unknown paths are not routes`() {
        assertNull(ProviderRoute.parse(emptyList()))
        assertNull(ProviderRoute.parse(listOf("stickers")))
        assertNull(ProviderRoute.parse(listOf("metadata", "7", "x")))
        assertNull(ProviderRoute.parse(listOf("stickers_asset", "7")))
        assertNull(ProviderRoute.parse(listOf("other", "7")))
    }

    @Test
    fun `getType covers every route`() {
        val authority = "com.example.stickercontentprovider"
        assertEquals("vnd.android.cursor.dir/vnd.$authority.metadata", ProviderRoute.Metadata.mimeType(authority))
        assertEquals("vnd.android.cursor.item/vnd.$authority.metadata", ProviderRoute.MetadataItem("1").mimeType(authority))
        assertEquals("vnd.android.cursor.dir/vnd.$authority.stickers", ProviderRoute.Stickers("1").mimeType(authority))
        assertEquals("image/webp", ProviderRoute.Asset("1", "a.webp").mimeType(authority))
        assertEquals("image/png", ProviderRoute.Asset("1", "tray.png").mimeType(authority))
    }

    @Test
    fun `asset requests allow plain webp and png names`() {
        assertTrue(AssetRequestSanitizer.isAllowed("1700000000", "1699999999123.webp"))
        assertTrue(AssetRequestSanitizer.isAllowed("1700000000", "tray.png"))
        assertTrue(AssetRequestSanitizer.isAllowed("1700000000", "TRAY.PNG"))
    }

    @Test
    fun `asset requests reject traversal, separators and other extensions`() {
        val rejected = listOf(
            "1" to "../secret.webp",
            "1" to "..",
            "1" to "a/b.webp",
            "1" to "a\\b.webp",
            "1" to "a..webp",
            "1" to "stickermaker.db",
            "1" to "a.webp.tmp",
            "1" to "",
            ".." to "a.webp",
            "../2" to "a.webp",
            "." to "a.webp",
            "" to "a.webp",
            "1" to "a\u0000.webp",
        )
        rejected.forEach { (id, file) -> assertFalse(AssetRequestSanitizer.isAllowed(id, file), "$id / $file") }
    }
}

class AssetFileResolutionTest {
    private val root = Files.createTempDirectory("packs").toFile()
    private val storage = PackFileStorage(root)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun resolve(segments: List<String>): File {
        val route = ProviderRoute.parse(segments) as ProviderRoute.Asset
        return resolveAssetFile(route, storage::fileOf)
    }

    @Test
    fun `a stored sticker resolves to its real file`() {
        val written = storage.write("1700000000", "1.webp", byteArrayOf(1, 2, 3))

        val resolved = resolve(listOf("stickers_asset", "1700000000", "1.webp"))

        assertEquals(written.canonicalFile, resolved.canonicalFile)
        assertTrue(resolved.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `the tray png resolves too`() {
        storage.write("7", "tray.png", byteArrayOf(9))

        assertTrue(resolve(listOf("stickers_asset", "7", "tray.png")).isFile)
    }

    @Test
    fun `a missing file is not found`() {
        assertFailsWith<FileNotFoundException> { resolve(listOf("stickers_asset", "7", "nope.webp")) }
    }

    @Test
    fun `traversal and odd names are rejected even when a file exists outside the pack`() {
        File(root, "secret.webp").writeBytes(byteArrayOf(1))
        storage.write("7", "1.webp", byteArrayOf(1))

        listOf(
            listOf("stickers_asset", "..", "secret.webp"),
            listOf("stickers_asset", "7", "..%2Fsecret.webp"),
            listOf("stickers_asset", "7", "../secret.webp"),
            listOf("stickers_asset", "7", "1.txt"),
            listOf("stickers_asset", "7", "a/b.webp"),
            listOf("stickers_asset", "7", "1.webp\u0000.png"),
        ).forEach { segments ->
            assertFailsWith<FileNotFoundException>(segments.toString()) { resolve(segments) }
        }
    }
}
