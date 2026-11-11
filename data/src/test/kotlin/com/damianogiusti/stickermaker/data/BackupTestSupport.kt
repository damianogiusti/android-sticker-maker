package com.damianogiusti.stickermaker.data

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Serves files by `<packId>/<fileName>`. [read] fails on purpose: a backup must go through [open], one
 * stream at a time, never through whole-file reads of a pack.
 */
class MapPackFileSource(private val files: Map<String, ByteArray>) : PackFileSource {
    override suspend fun read(packId: StickerPackId, fileName: String): Either<StickerPackError, ByteArray> =
        throw AssertionError("backups must stream with open(), not read()")

    override suspend fun open(packId: StickerPackId, fileName: String): Either<StickerPackError, InputStream> =
        files["${packId.value}/$fileName"]?.let { ByteArrayInputStream(it).right() }
            ?: StickerPackError.StickerNotFound(packId, fileName).left()
}

/** A stored pack whose sticker files are named `s<n>.webp`, so the backup renaming is visible. */
class StoredPack(
    val pack: StickerPack,
    val files: Map<String, ByteArray>,
)

fun aStoredPack(
    id: String,
    name: String = "Pack $id",
    stickerCount: Int = 3,
    emojis: List<String> = emptyList(),
    withTray: Boolean = true,
    isAvailableInKeyboard: Boolean = false,
    sticker: (Int) -> ByteArray = { staticWebp(marker = it) },
    tray: ByteArray = pngBytes(),
): StoredPack {
    val stickers = List(stickerCount) { Sticker("s$it.webp", emojis, 0, it) }
    val files = buildMap {
        stickers.forEach { put("$id/${it.fileName}", sticker(it.position)) }
        if (withTray) put("$id/tray.png", tray)
    }
    val pack = StickerPack(
        id = StickerPackId(id),
        name = name,
        publisher = "Me",
        trayFileName = "tray.png".takeIf { withTray },
        stickers = stickers,
        imageDataVersion = 1,
        isAvailableInKeyboard = isAvailableInKeyboard,
        keyboardAddedAt = null,
        isImported = false,
        position = 0,
    )
    return StoredPack(pack, files)
}

fun List<StoredPack>.packs(): List<StickerPack> = map { it.pack }

fun List<StoredPack>.fileSource() = MapPackFileSource(buildMap { this@fileSource.forEach { putAll(it.files) } })
