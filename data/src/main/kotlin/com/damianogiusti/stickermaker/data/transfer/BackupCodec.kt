package com.damianogiusti.stickermaker.data.transfer

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.damianogiusti.stickermaker.data.inspect.ImageHeaderParser
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.ArchiveSticker
import com.damianogiusti.stickermaker.domain.model.BackupLimits
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.repository.BackupReader
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipOutputStream

@Serializable
internal data class BackupManifest(val formatVersion: Int, val packs: List<BackupManifestPack>)

@Serializable
internal data class BackupManifestPack(
    val id: String,
    val name: String,
    val publisher: String,
    val position: Int,
    val isAvailableInKeyboard: Boolean,
    val tray: String? = null,
    val stickers: List<BackupManifestSticker>,
)

@Serializable
internal data class BackupManifestSticker(val fileName: String, val emojis: List<String>, val position: Int)

/**
 * Backup zip: `manifest.json` (format version 1) plus `packs/<id>/<files>`.
 *
 * Both directions are streaming. The writer copies one pack file at a time into the zip. The reader
 * spools every entry to a directory and keeps only the manifest in memory. Both apply the same
 * [limits], so a backup written here can always be read back here.
 */
class BackupCodec(
    headerParser: ImageHeaderParser = ImageHeaderParser(),
    private val limits: BackupLimits = BackupLimits.DEFAULT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val imageValidator = StickerImageValidator(headerParser)

    /** The manifest goes first: it is built from the pack list alone, before any file is touched. */
    suspend fun write(packs: List<StickerPack>, files: PackFileSource, output: OutputStream): Either<TransferError, Unit> =
        try {
            writeChecked(packs, files, output)
        } catch (failure: IOException) {
            TransferError.Io(failure.message).left()
        }

    private suspend fun writeChecked(
        packs: List<StickerPack>,
        files: PackFileSource,
        output: OutputStream,
    ): Either<TransferError, Unit> = either {
        ensure(packs.size <= limits.maxPacks) { TransferError.TooLarge }
        packs.forEach { ensure(it.stickers.size <= StickerPackLimits.MAX_STICKERS) { TransferError.TooManyStickers } }
        val manifest = BackupManifest(FORMAT_VERSION, packs.mapIndexed(::toManifestPack))
        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        ensure(manifestBytes.size <= limits.maxManifestBytes) { TransferError.TooLarge }
        ZipOutputStream(output).use { zip ->
            zip.put(MANIFEST, manifestBytes)
            packs.zip(manifest.packs).forEach { (pack, entry) ->
                val directory = "$PACKS_DIR/${pack.id.value}"
                val stickers = pack.stickers.sortedBy { it.position }
                entry.tray?.let { copyFile(zip, "$directory/$it", files, pack, pack.trayFileName.orEmpty(), isSticker = false).bind() }
                entry.stickers.zip(stickers).forEach { (listed, sticker) ->
                    copyFile(zip, "$directory/${listed.fileName}", files, pack, sticker.fileName, isSticker = true).bind()
                }
            }
        }
    }

    private suspend fun copyFile(
        zip: ZipOutputStream,
        entryName: String,
        files: PackFileSource,
        pack: StickerPack,
        fileName: String,
        isSticker: Boolean,
    ): Either<TransferError, Unit> = either {
        val bytes = files.open(pack.id, fileName).mapLeft { TransferError.Pack(it) }.bind().use {
            ZipEntries.readLimited(it, limits.maxFileBytes)
        } ?: raise(TransferError.TooLarge)
        // The reader checks stickers too; failing here keeps the app from writing what it cannot restore.
        if (isSticker) imageValidator.validate(fileName, bytes).bind()
        zip.put(entryName, bytes)
    }

    private fun toManifestPack(position: Int, pack: StickerPack): BackupManifestPack = BackupManifestPack(
        id = pack.id.value,
        name = pack.name,
        publisher = pack.publisher,
        position = position,
        isAvailableInKeyboard = pack.isAvailableInKeyboard,
        tray = TRAY_FILE.takeIf { pack.trayFileName != null },
        stickers = pack.stickers.sortedBy { it.position }.mapIndexed { index, sticker ->
            BackupManifestSticker("${index + 1}.webp", sticker.emojis, index)
        },
    )

    /**
     * Spools the zip into [directory] (created by the caller), validates it and returns a reader.
     * On failure the caller owns the cleanup of [directory].
     */
    suspend fun extract(input: InputStream, directory: File): Either<TransferError, BackupReader> = either {
        val manifestBytes = withContext(ioDispatcher) { spool(input, directory) }.bind()
        val manifest = parseManifest(manifestBytes).bind()
        ensure(manifest.formatVersion == FORMAT_VERSION) { TransferError.UnsupportedBackupVersion(manifest.formatVersion) }
        ensure(manifest.packs.size <= limits.maxPacks) { TransferError.TooLarge }
        val reader = DirectoryBackupReader(directory, manifest)
        // Validate everything before the first insert, one pack in memory at a time.
        reader.sourceIds.forEach { reader.load(it).bind() }
        reader
    }

    /** Returns the manifest bytes; every other entry under `packs/<id>/<file>` goes to disk. */
    private fun spool(input: InputStream, directory: File): Either<TransferError, ByteArray> {
        var manifest: ByteArray? = null
        val sink = ZipEntrySink { name, content ->
            if (name == MANIFEST) {
                manifest = ZipEntries.readLimited(content, limits.maxManifestBytes) ?: return@ZipEntrySink TransferError.TooLarge.left()
                return@ZipEntrySink Unit.right()
            }
            val parts = name.split('/')
            if (parts.size != 3 || parts[0] != PACKS_DIR || !isPlainName(parts[1]) || !isPlainName(parts[2])) {
                return@ZipEntrySink Unit.right()
            }
            val target = File(File(directory, parts[1]), parts[2])
            target.parentFile?.mkdirs()
            val fits = target.outputStream().use { ZipEntries.copyLimited(content, it, limits.maxFileBytes) }
            if (fits) Unit.right() else TransferError.TooLarge.left()
        }
        return ZipEntries.stream(input, limits.maxEntries, sink).flatMap {
            manifest?.right() ?: TransferError.InvalidBackup("manifest.json is missing").left()
        }
    }

    private fun parseManifest(bytes: ByteArray): Either<TransferError, BackupManifest> = try {
        json.decodeFromString(BackupManifest.serializer(), bytes.toString(Charsets.UTF_8)).right()
    } catch (failure: SerializationException) {
        TransferError.InvalidBackup(failure.message).left()
    } catch (failure: IllegalArgumentException) {
        TransferError.InvalidBackup(failure.message).left()
    }

    private inner class DirectoryBackupReader(
        private val directory: File,
        manifest: BackupManifest,
    ) : BackupReader {
        private val packs = manifest.packs.sortedBy { it.position }

        override val sourceIds: List<String> = packs.map { it.id }

        override suspend fun load(sourceId: String): Either<TransferError, PackArchive> = withContext(ioDispatcher) {
            val pack = packs.firstOrNull { it.id == sourceId }
                ?: return@withContext TransferError.InvalidBackup("Unknown pack $sourceId").left()
            try {
                readPack(pack)
            } catch (failure: IOException) {
                TransferError.Io(failure.message).left()
            }
        }

        override suspend fun discard() {
            withContext(NonCancellable + ioDispatcher) { directory.deleteRecursively() }
        }

        private fun readPack(pack: BackupManifestPack): Either<TransferError, PackArchive> = either {
            ensure(isPlainName(pack.id)) { TransferError.UnsafeEntry(pack.id) }
            ensure(pack.stickers.size <= StickerPackLimits.MAX_STICKERS) { TransferError.TooManyStickers }
            fun fileOf(name: String): Either<TransferError, ByteArray> = either {
                ensure(isPlainName(name)) { TransferError.UnsafeEntry(name) }
                val file = File(File(directory, pack.id), name)
                ensure(file.isFile) { TransferError.InvalidBackup("Missing file ${pack.id}/$name") }
                file.readBytes()
            }
            val stickers = pack.stickers.sortedBy { it.position }.map { sticker ->
                val bytes = fileOf(sticker.fileName).bind()
                imageValidator.validate(sticker.fileName, bytes).bind()
                ArchiveSticker(bytes, sticker.emojis.take(StickerPackLimits.MAX_EMOJIS_PER_STICKER))
            }
            PackArchive(
                name = pack.name.take(StickerPackLimits.MAX_TEXT_LENGTH),
                publisher = pack.publisher.take(StickerPackLimits.MAX_TEXT_LENGTH),
                tray = pack.tray?.let { fileOf(it).bind() },
                stickers = stickers,
                isAvailableInKeyboard = pack.isAvailableInKeyboard,
            )
        }
    }

    private fun isPlainName(name: String): Boolean =
        !ZipEntries.isUnsafeName(name) && name != "." && !name.contains('/') && !name.contains('\\')

    private companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST = "manifest.json"
        const val PACKS_DIR = "packs"
        const val TRAY_FILE = "tray.png"
        val json = Json { ignoreUnknownKeys = true }
    }
}
