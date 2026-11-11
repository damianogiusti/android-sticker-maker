package com.damianogiusti.stickermaker.data.inspect

import android.graphics.BitmapFactory
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import com.damianogiusti.stickermaker.domain.error.StickerFileInspectionError
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.StickerFileInspector

/** Android adapter: dimensions via a bounds-only BitmapFactory decode, the rest from the file bytes. */
class AndroidStickerFileInspector(
    private val storage: PackFileStorage,
    private val ioDispatcher: CoroutineDispatcher,
    private val headerParser: ImageHeaderParser,
) : StickerFileInspector {

    override suspend fun inspect(
        packId: StickerPackId,
        fileName: String,
    ): Either<StickerFileInspectionError, StickerFileFacts> = withContext(ioDispatcher) {
        Either.catch {
            val file = storage.fileOf(packId.value, fileName)
            if (!file.isFile) return@catch StickerFileInspectionError.FileNotFound(fileName).left()
            val bytes = file.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@catch StickerFileInspectionError.Unreadable(fileName, "Cannot decode image bounds").left()
            }
            StickerFileFacts(
                format = headerParser.formatOf(bytes),
                width = bounds.outWidth,
                height = bounds.outHeight,
                sizeBytes = bytes.size.toLong(),
                isAnimated = headerParser.isAnimatedWebp(bytes),
            ).right()
        }.fold(
            ifLeft = { StickerFileInspectionError.Unreadable(fileName, it.message).left() },
            ifRight = { it },
        )
    }
}
