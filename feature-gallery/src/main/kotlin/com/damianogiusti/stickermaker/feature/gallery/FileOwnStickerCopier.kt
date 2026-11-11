package com.damianogiusti.stickermaker.feature.gallery

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Copies a pack sticker to `cacheDir/gallery/` and returns a `file://` URI, so the editor never touches the pack. */
internal class FileOwnStickerCopier(
    private val locator: GalleryFileLocator,
    private val cacheDirectory: File,
    private val dispatcher: CoroutineDispatcher,
) : OwnStickerCopier {

    override suspend fun copyToCache(packId: String, fileName: String): Either<GalleryError, String> =
        withContext(dispatcher) {
            try {
                val source = locator.locate(packId, fileName)
                if (!source.isFile) throw IOException("Missing $source")
                val directory = File(cacheDirectory, "gallery")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
                val target = File(directory, "${packId}_$fileName")
                source.copyTo(target, overwrite = true)
                target.toURI().toString().right()
            } catch (error: IOException) {
                GalleryError.Io(error.message).left()
            }
        }
}
