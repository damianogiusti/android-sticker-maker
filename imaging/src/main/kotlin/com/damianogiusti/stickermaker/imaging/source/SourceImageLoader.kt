package com.damianogiusti.stickermaker.imaging.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.annotation.TargetApi
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.SourceError
import com.damianogiusti.stickermaker.imaging.asEditorImage
import com.damianogiusti.stickermaker.imaging.geometry.ImageGeometry

/** A `content:` or `file:` URI as a string, so callers above the imaging layer stay Android-free. */
@JvmInline
value class SourceUri(val value: String)

const val WORKING_IMAGE_MAX_LONG_SIDE = 2000

interface SourceImageLoader {
    /** Decodes the image upright (EXIF applied) with its long side at most [WORKING_IMAGE_MAX_LONG_SIDE]. */
    suspend fun load(uri: SourceUri): Either<SourceError, EditorImage>
}

class AndroidSourceImageLoader(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : SourceImageLoader {

    override suspend fun load(uri: SourceUri): Either<SourceError, EditorImage> = withContext(dispatcher) {
        Either.catch {
            val parsed = Uri.parse(uri.value)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(parsed) else decodeWithFactory(parsed)
            bitmap.asEditorImage()
        }.mapLeft { SourceError.Unreadable(it.message) }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies the EXIF orientation by itself.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val target = ImageGeometry.limitLongSide(info.size.width, info.size.height, WORKING_IMAGE_MAX_LONG_SIDE)
            decoder.setTargetSize(target.width, target.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun decodeWithFactory(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val target = ImageGeometry.limitLongSide(bounds.outWidth, bounds.outHeight, WORKING_IMAGE_MAX_LONG_SIDE)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, target.width)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = requireNotNull(resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }) {
            "Unsupported image"
        }
        val orientation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = orientationMatrix(orientation)
        val factor = minOf(1f, WORKING_IMAGE_MAX_LONG_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        matrix.postScale(factor, factor)
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    private fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
}
