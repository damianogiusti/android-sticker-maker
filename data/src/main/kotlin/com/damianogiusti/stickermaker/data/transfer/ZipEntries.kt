package com.damianogiusti.stickermaker.data.transfer

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.damianogiusti.stickermaker.domain.error.TransferError
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Limits that keep a hostile or broken zip from exhausting memory or disk. */
internal data class ZipLimits(val maxEntries: Int, val maxTotalBytes: Long, val maxFileBytes: Long = Long.MAX_VALUE)

/** Receives each file entry of a zip as a stream; it must not read past [maxBytes]-style limits itself. */
internal fun interface ZipEntrySink {
    fun accept(name: String, content: InputStream): Either<TransferError, Unit>
}

/**
 * Reads a zip with the safety checks of SPEC section 7: zip magic, entry names that cannot escape the
 * target directory, an entry cap and caps on the bytes actually inflated (the declared sizes in a zip
 * are not trusted).
 */
internal object ZipEntries {

    /** Reads every file into memory. Only for small archives (`.wastickers`). */
    fun read(input: InputStream, limits: ZipLimits): Either<TransferError, Map<String, ByteArray>> {
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        val sink = ZipEntrySink { name, content ->
            val bytes = readLimited(content, minOf(limits.maxFileBytes, limits.maxTotalBytes - totalBytes))
                ?: return@ZipEntrySink TransferError.TooLarge.left()
            totalBytes += bytes.size
            entries[name] = bytes
            Unit.right()
        }
        return stream(input, limits.maxEntries, sink).map { entries }
    }

    /** Hands each non-directory entry to [sink] without keeping it; stops at the first error. */
    fun stream(input: InputStream, maxEntries: Int, sink: ZipEntrySink): Either<TransferError, Unit> {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        return try {
            if (!hasZipMagic(buffered)) return TransferError.NotAZip.left()
            streamEntries(ZipInputStream(buffered), maxEntries, sink)
        } catch (failure: ZipException) {
            TransferError.NotAZip.left()
        } catch (failure: IOException) {
            TransferError.Io(failure.message).left()
        }
    }

    /** Null when more than [maxBytes] come out of the stream. */
    fun readLimited(input: InputStream, maxBytes: Long): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        return if (copyLimited(input, output, maxBytes)) output.toByteArray() else null
    }

    /** Copies at most [maxBytes]; false when the stream holds more (the output then holds a partial copy). */
    fun copyLimited(input: InputStream, output: java.io.OutputStream, maxBytes: Long): Boolean {
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) return true
            total += read
            if (total > maxBytes) return false
            output.write(chunk, 0, read)
        }
    }

    /** True for names that would leave the target directory: `..` segments, absolute paths, drive letters. */
    fun isUnsafeName(name: String): Boolean {
        if (name.isEmpty() || name.contains('\u0000')) return true
        if (name.startsWith("/") || name.startsWith("\\")) return true
        if (name.length >= 2 && name[1] == ':') return true
        return name.split('/', '\\').any { it == ".." }
    }

    private fun streamEntries(zip: ZipInputStream, maxEntries: Int, sink: ZipEntrySink): Either<TransferError, Unit> {
        var count = 0
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++count > maxEntries) return TransferError.TooLarge.left()
            if (isUnsafeName(entry.name)) return TransferError.UnsafeEntry(entry.name).left()
            if (entry.isDirectory) continue
            sink.accept(entry.name, zip).onLeft { return it.left() }
        }
        if (count == 0) return TransferError.NotAZip.left()
        return Unit.right()
    }

    private fun hasZipMagic(input: InputStream): Boolean {
        input.mark(MAGIC.size)
        val head = ByteArray(MAGIC.size)
        var filled = 0
        while (filled < head.size) {
            val read = input.read(head, filled, head.size - filled)
            if (read < 0) break
            filled += read
        }
        input.reset()
        return filled == head.size && head.take(2) == MAGIC.take(2) && (head[2].toInt() to head[3].toInt() in VALID_TAILS)
    }

    private const val CHUNK_SIZE = 8 * 1024
    private val MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    // Local file header (PK 03 04) or the end-of-central-directory of an empty zip (PK 05 06).
    private val VALID_TAILS = setOf(3 to 4, 5 to 6)
}
