package com.damianogiusti.stickermaker.data.transfer

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import com.damianogiusti.stickermaker.data.inspect.ImageHeaderParser
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.ArchiveSticker
import com.damianogiusti.stickermaker.domain.model.BackupLimits
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `.wastickers`: a flat zip with `*.webp` stickers, one or more `*.png` trays, `title.txt` and
 * `author.txt`. Streams only, so it runs on the JVM in tests.
 */
class WastickersCodec(headerParser: ImageHeaderParser = ImageHeaderParser()) {

    private val imageValidator = StickerImageValidator(headerParser)

    /** [fallbackTitle] is used when `title.txt` is missing or blank (usually the file name). */
    fun read(input: InputStream, fallbackTitle: String?): Either<TransferError, PackArchive> = either {
        val entries = ZipEntries.read(input, LIMITS).bind()
        val root = entries.filterKeys { !it.contains('/') && !it.contains('\\') }
        val stickerNames = root.keys.filter { it.endsWith(WEBP, ignoreCase = true) }.sortedWith(NaturalOrder)
        ensure(stickerNames.isNotEmpty()) { TransferError.NoStickers }
        ensure(stickerNames.size <= StickerPackLimits.MAX_STICKERS) { TransferError.TooManyStickers }
        stickerNames.forEach { imageValidator.validate(it, root.getValue(it)).bind() }
        PackArchive(
            name = root.text(TITLE_FILE) ?: fallbackTitle?.trim()?.takeIf { it.isNotEmpty() }?.limited() ?: UNKNOWN,
            publisher = root.text(AUTHOR_FILE) ?: UNKNOWN,
            tray = root.filterKeys { it.endsWith(PNG, ignoreCase = true) }.values.minByOrNull { it.size },
            stickers = stickerNames.map { ArchiveSticker(root.getValue(it)) },
        )
    }

    fun write(archive: PackArchive, output: OutputStream): Either<TransferError, Unit> = try {
        ZipOutputStream(output).use { zip ->
            zip.put(TITLE_FILE, archive.name.toByteArray(Charsets.UTF_8))
            zip.put(AUTHOR_FILE, archive.publisher.toByteArray(Charsets.UTF_8))
            archive.tray?.let { zip.put(TRAY_FILE, it) }
            archive.stickers.forEachIndexed { index, sticker -> zip.put("${index + 1}$WEBP", sticker.bytes) }
        }
        Unit.right()
    } catch (failure: IOException) {
        TransferError.Io(failure.message).left()
    }

    private fun Map<String, ByteArray>.text(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }?.limited()

    private fun String.limited(): String = take(StickerPackLimits.MAX_TEXT_LENGTH)

    private companion object {
        const val TITLE_FILE = "title.txt"
        const val AUTHOR_FILE = "author.txt"
        const val TRAY_FILE = "tray.png"
        const val WEBP = ".webp"
        const val PNG = ".png"
        const val UNKNOWN = "Unknown"
        // The per-file cap is the backup cap: every file the app stores must fit in a backup it can restore.
        val LIMITS = ZipLimits(
            maxEntries = 200,
            maxTotalBytes = 20L * 1024 * 1024,
            maxFileBytes = BackupLimits.DEFAULT.maxFileBytes,
        )
    }
}

internal fun ZipOutputStream.put(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
}

/** "2.webp" before "10.webp"; digit runs compare as numbers, the rest case-insensitively. */
internal object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val left = chunk.findAll(a).map { it.value }.toList()
        val right = chunk.findAll(b).map { it.value }.toList()
        for (index in 0 until minOf(left.size, right.size)) {
            val result = compareChunks(left[index], right[index])
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }

    private fun compareChunks(a: String, b: String): Int {
        val bothNumbers = a.first().isDigit() && b.first().isDigit()
        if (!bothNumbers) return a.compareTo(b, ignoreCase = true)
        val byLength = a.trimStart('0').length.compareTo(b.trimStart('0').length)
        return if (byLength != 0) byLength else a.trimStart('0').compareTo(b.trimStart('0'))
    }
}
