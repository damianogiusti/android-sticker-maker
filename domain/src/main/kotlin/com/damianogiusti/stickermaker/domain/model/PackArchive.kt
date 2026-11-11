package com.damianogiusti.stickermaker.domain.model

/** One sticker inside an archive: the encoded WebP bytes and its emojis (empty for `.wastickers`). */
class ArchiveSticker(
    val bytes: ByteArray,
    val emojis: List<String> = emptyList(),
)

/** A whole pack as it travels in a `.wastickers` or backup file. [stickers] are in pack order. */
class PackArchive(
    val name: String,
    val publisher: String,
    val tray: ByteArray?,
    val stickers: List<ArchiveSticker>,
    val isAvailableInKeyboard: Boolean = false,
)

/**
 * Limits shared by the backup writer and the backup reader, defined once so a backup the app writes is
 * always one it can restore. Everything is bounded per file or per entry: no limit depends on the total
 * size, because neither side holds more than one file in memory.
 */
data class BackupLimits(
    val maxFileBytes: Long,
    val maxPacks: Int,
    val maxManifestBytes: Long,
) {
    /** One manifest entry plus a tray and a full set of stickers for every pack. */
    val maxEntries: Int get() = 1 + maxPacks * (StickerPackLimits.MAX_STICKERS + 1)

    companion object {
        val DEFAULT = BackupLimits(
            maxFileBytes = 1024L * 1024,
            maxPacks = 1_000,
            maxManifestBytes = 8L * 1024 * 1024,
        )
    }
}

/** A `.wastickers` file ready to be shared. [location] is where the store wrote it. */
data class ExportedPack(val location: String, val fileName: String)

/** Outcome of a backup or restore. */
data class TransferSummary(val packCount: Int)
