package com.damianogiusti.stickermaker.imaging.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.ImageStoreError
import com.damianogiusti.stickermaker.imaging.asBitmap
import com.damianogiusti.stickermaker.imaging.asEditorImage

/** The two images the editor keeps on disk so that it survives process death (SPEC section 4). */
enum class ImageSlot(val fileName: String) {
    WORKING("working.png"),
    CUTOUT("cutout.png"),
}

interface EditorImageStore {
    suspend fun save(slot: ImageSlot, image: EditorImage): Either<ImageStoreError, Unit>
    suspend fun load(slot: ImageSlot): Either<ImageStoreError, EditorImage>
    suspend fun clear()
}

/** PNG files in [directory] (`cacheDir/editor`). Writes go through a temp file, so a kill never leaves half an image. */
class FileEditorImageStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher,
) : EditorImageStore {

    private val lock = Mutex()

    override suspend fun save(slot: ImageSlot, image: EditorImage): Either<ImageStoreError, Unit> =
        withContext(dispatcher) {
            lock.withLock {
                Either.catch {
                    directory.mkdirs()
                    val target = File(directory, slot.fileName)
                    val temp = File(directory, "${slot.fileName}.tmp")
                    temp.outputStream().use { check(image.asBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
                    check(temp.renameTo(target)) { "cannot rename ${temp.name}" }
                }.mapLeft { ImageStoreError.Failed(it.message) }
            }
        }

    override suspend fun load(slot: ImageSlot): Either<ImageStoreError, EditorImage> = withContext(dispatcher) {
        lock.withLock {
            val file = File(directory, slot.fileName)
            if (!file.isFile) return@withLock Either.Left(ImageStoreError.Missing)
            Either.catch {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "cannot decode ${file.name}" }.asEditorImage()
            }.mapLeft { ImageStoreError.Failed(it.message) }
        }
    }

    override suspend fun clear() {
        withContext(dispatcher) {
            lock.withLock { ImageSlot.entries.forEach { File(directory, it.fileName).delete() } }
        }
    }

    private companion object {
        const val PNG_QUALITY = 100
    }
}
