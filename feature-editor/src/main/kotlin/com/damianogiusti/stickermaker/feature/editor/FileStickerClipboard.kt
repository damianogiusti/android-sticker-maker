package com.damianogiusti.stickermaker.feature.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import com.damianogiusti.stickermaker.domain.clipboard.ClipboardError
import com.damianogiusti.stickermaker.domain.clipboard.EncodedSticker
import com.damianogiusti.stickermaker.domain.clipboard.StickerClipboard

/**
 * Writes the sticker to [directory] and puts its content URI on the clipboard. The folder is exposed by
 * the `${applicationId}.clipboard.fileprovider` FileProvider declared in this module's manifest.
 */
class FileStickerClipboard(
    private val context: Context,
    private val directory: File,
    private val dispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) : StickerClipboard {

    override suspend fun copy(sticker: EncodedSticker): Either<ClipboardError, Unit> = withContext(dispatcher) {
        Either.catch {
            directory.mkdirs()
            val name = ClipboardFiles.fileName(nowMillis(), sticker.format)
            val file = File(directory, name)
            file.writeBytes(sticker.bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.clipboard.fileprovider", file)
            val manager = checkNotNull(context.getSystemService(ClipboardManager::class.java)) { "No clipboard" }
            manager.setPrimaryClip(ClipData.newUri(context.contentResolver, CLIP_LABEL, uri))
            // After the new clip is set, so the previous one stays pasteable if anything above fails.
            ClipboardFiles.staleFileNames(directory.list().orEmpty().toList(), name)
                .forEach { File(directory, it).delete() }
        }.mapLeft { ClipboardError.Failed(it.message) }
    }

    private companion object {
        const val CLIP_LABEL = "Sticker"
    }
}
