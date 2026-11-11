package com.damianogiusti.stickermaker.feature.keyboard

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Finds the on-disk file of a pack asset (`filesDir/packs/<id>/<file>`). */
internal fun interface StickerFileLocator {
    fun locate(packId: String, fileName: String): File
}

/** Android adapter: picks the MIME type, exposes the file through our FileProvider and commits it. */
internal class StickerSender(
    private val context: Context,
    private val locator: StickerFileLocator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val authority = "${context.packageName}.keyboard.fileprovider"

    /** Decoding and file work run on [ioDispatcher]; the commit itself runs on the caller (main) thread. */
    suspend fun send(inputConnection: InputConnection?, editorInfo: EditorInfo?, sticker: KeyboardSticker): SendOutcome {
        if (inputConnection == null || editorInfo == null) return SendOutcome.FAILED
        val mimeType = selectStickerMimeType(EditorInfoCompat.getContentMimeTypes(editorInfo).toList())
            ?: return SendOutcome.NOT_ACCEPTED
        val uri = withContext(ioDispatcher) { prepareUri(sticker, mimeType) } ?: return SendOutcome.FAILED
        val content = InputContentInfoCompat(uri, ClipDescription("Sticker", arrayOf(mimeType.value)), null)
        val plan = CommitContentPolicy.planFor(Build.VERSION.SDK_INT)
        plan.manualGrantFlags?.let { context.grantUriPermission(editorInfo.packageName, uri, it) }
        val committed = InputConnectionCompat.commitContent(inputConnection, editorInfo, content, plan.commitFlags, null)
        return CommitContentPolicy.outcomeOf(committed)
    }

    private fun prepareUri(sticker: KeyboardSticker, mimeType: StickerMimeType): Uri? = try {
        uriFor(sticker, mimeType)
    } catch (error: IOException) {
        Log.w(TAG, "cannot prepare ${sticker.fileName}", error)
        null
    } catch (error: IllegalArgumentException) {
        Log.w(TAG, "cannot expose ${sticker.fileName}", error)
        null
    }

    private fun uriFor(sticker: KeyboardSticker, mimeType: StickerMimeType): Uri? {
        val source = locator.locate(sticker.packId, sticker.fileName)
        if (!source.isFile) return null
        val file = if (mimeType == StickerMimeType.PNG) convertToPng(sticker, source) ?: return null else source
        return FileProvider.getUriForFile(context, authority, file)
    }

    private fun convertToPng(sticker: KeyboardSticker, source: File): File? {
        val directory = File(context.cacheDir, CACHE_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val target = File(directory, "${sticker.packId}_${sticker.fileName.substringBeforeLast('.')}_${sticker.version}.png")
        if (target.isFile) return target
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return null
        try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return target
    }

    private companion object {
        const val TAG = "StickerSender"
        const val CACHE_DIRECTORY = "keyboard"
    }
}
