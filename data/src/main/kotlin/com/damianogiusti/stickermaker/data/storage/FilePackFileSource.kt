package com.damianogiusti.stickermaker.data.storage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import java.io.InputStream

class FilePackFileSource(
    private val storage: PackFileStorage,
    private val ioDispatcher: CoroutineDispatcher,
) : PackFileSource {

    override suspend fun read(packId: StickerPackId, fileName: String): Either<StickerPackError, ByteArray> =
        withContext(ioDispatcher) {
            try {
                val file = storage.fileOf(packId.value, fileName)
                if (file.isFile) file.readBytes().right() else StickerPackError.StickerNotFound(packId, fileName).left()
            } catch (failure: java.io.IOException) {
                StickerPackError.Storage(failure.message).left()
            } catch (failure: IllegalArgumentException) {
                StickerPackError.Storage(failure.message).left()
            }
        }

    override suspend fun open(packId: StickerPackId, fileName: String): Either<StickerPackError, InputStream> =
        withContext(ioDispatcher) {
            try {
                val file = storage.fileOf(packId.value, fileName)
                if (file.isFile) file.inputStream().right() else StickerPackError.StickerNotFound(packId, fileName).left()
            } catch (failure: java.io.IOException) {
                StickerPackError.Storage(failure.message).left()
            } catch (failure: IllegalArgumentException) {
                StickerPackError.Storage(failure.message).left()
            }
        }
}
