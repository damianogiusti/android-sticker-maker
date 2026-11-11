package com.damianogiusti.stickermaker.feature.gallery

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AnimationDetector"

/** Reads [WEBP_HEADER_BYTES] bytes of a document and caches the answer by URI and modification time. */
internal class HeaderAnimationDetector(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher,
) : AnimationDetector {

    private val cache = ConcurrentHashMap<String, Boolean>()

    override suspend fun isAnimated(file: WhatsAppStickerFile): Boolean {
        val key = "${file.uriString}#${file.lastModified}"
        cache[key]?.let { return it }
        return withContext(dispatcher) {
            val animated = readHeader(file)?.let(WebpHeader::isAnimated) ?: return@withContext false
            cache[key] = animated
            animated
        }
    }

    /** An unreadable file is reported as not animated and not cached, so a later look can succeed. */
    private fun readHeader(file: WhatsAppStickerFile): ByteArray? = try {
        contentResolver.openInputStream(Uri.parse(file.uriString))?.use { input ->
            val buffer = ByteArray(WEBP_HEADER_BYTES)
            buffer.copyOf(input.read(buffer).coerceAtLeast(0))
        }
    } catch (error: IOException) {
        Log.w(TAG, "cannot read the header of ${file.name}", error)
        null
    } catch (error: SecurityException) {
        Log.w(TAG, "no access to ${file.name}", error)
        null
    }
}
