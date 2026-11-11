package com.damianogiusti.stickermaker.data.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.repository.BackupReader
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.repository.PackArchiveStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Opens streams for an opaque location, so the codecs and the store never see ContentResolver. */
interface StreamOpener {
    fun openInput(location: String): InputStream
    fun openOutput(location: String): OutputStream

    /** The file name the user sees for [location], or null when unknown. */
    fun displayName(location: String): String?
}

class ContentResolverStreamOpener(private val contentResolver: ContentResolver) : StreamOpener {

    override fun openInput(location: String): InputStream =
        contentResolver.openInputStream(Uri.parse(location)) ?: throw IOException("Cannot open $location")

    override fun openOutput(location: String): OutputStream =
        contentResolver.openOutputStream(Uri.parse(location), "wt") ?: throw IOException("Cannot open $location")

    override fun displayName(location: String): String? = try {
        contentResolver.query(Uri.parse(location), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (failure: RuntimeException) {
        null
    } ?: Uri.parse(location).lastPathSegment
}

/**
 * [shareDirectory] is `cacheDir/share`, the directory the FileProvider exposes. [workDirectory] holds the
 * files of a backup being restored.
 */
class StreamPackArchiveStore(
    private val opener: StreamOpener,
    private val shareDirectory: File,
    private val workDirectory: File,
    private val wastickers: WastickersCodec,
    private val backup: BackupCodec,
    private val ioDispatcher: CoroutineDispatcher,
) : PackArchiveStore {

    override suspend fun readWastickers(location: String): Either<TransferError, PackArchive> = onIo {
        opener.openInput(location).use { wastickers.read(it, fallbackTitle = opener.displayName(location)?.stem()) }
    }

    override suspend fun writeWastickersForSharing(fileName: String, archive: PackArchive): Either<TransferError, String> =
        onIo {
            if (!shareDirectory.isDirectory && !shareDirectory.mkdirs()) throw IOException("Cannot create $shareDirectory")
            val target = File(shareDirectory, File(fileName).name)
            target.outputStream().use { wastickers.write(archive, it) }.map { target.absolutePath }
        }

    override suspend fun openBackup(location: String): Either<TransferError, BackupReader> = onIo {
        val directory = newWorkDirectory()
        val result = opener.openInput(location).use { backup.extract(it, directory) }
        // A reader owns the directory; on failure nothing else would delete it.
        if (result is Either.Left) directory.deleteRecursively()
        result
    }

    override suspend fun writeBackup(location: String, packs: List<StickerPack>, files: PackFileSource): Either<TransferError, Unit> =
        onIo { opener.openOutput(location).use { backup.write(packs, files, it) } }

    /** Each restore gets its own directory; leftovers of an earlier crash are removed first. */
    private fun newWorkDirectory(): File {
        workDirectory.deleteRecursively()
        if (!workDirectory.mkdirs()) throw IOException("Cannot create $workDirectory")
        // File.toPath needs API 26; createTempFile gives a unique name that becomes the directory.
        val directory = File.createTempFile("restore", "", workDirectory)
        if (!directory.delete() || !directory.mkdir()) throw IOException("Cannot create $directory")
        return directory
    }

    private suspend fun <T> onIo(block: suspend () -> Either<TransferError, T>): Either<TransferError, T> =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (failure: IOException) {
                TransferError.Io(failure.message).left()
            } catch (failure: SecurityException) {
                TransferError.Io(failure.message).left()
            }
        }

    private fun String.stem(): String = substringBeforeLast('.', this)
}
