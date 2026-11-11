package com.damianogiusti.stickermaker.imaging.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.SegmentationError
import com.damianogiusti.stickermaker.imaging.asBitmap

/**
 * DeepLab v3 MobileNetV2 (PASCAL VOC) on CPU, 2 threads, through the plain TFLite runtime.
 * Input and output shapes are read from the tensors, so another export of the model also works.
 */
class DeepLabSubjectSegmenter(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : SubjectSegmenter {

    private val lock = Mutex()
    private var interpreter: Interpreter? = null

    override suspend fun segment(image: EditorImage): Either<SegmentationError, AlphaMask> = withContext(dispatcher) {
        lock.withLock {
            Either.catch { either { runModel(image.asBitmap()) } }.fold(
                ifLeft = { Either.Left(SegmentationError.Failed(it.message)) },
                ifRight = { it },
            )
        }
    }

    private fun arrow.core.raise.Raise<SegmentationError>.runModel(bitmap: Bitmap): AlphaMask {
        val model = obtainInterpreter()
        val inputShape = model.getInputTensor(0).shape()
        ensure(inputShape.size == 4 && inputShape[3] == CHANNELS) {
            SegmentationError.Failed("unexpected input shape ${inputShape.toList()}")
        }
        val inputHeight = inputShape[1]
        val inputWidth = inputShape[2]
        val letterbox = Letterbox.fit(bitmap.width, bitmap.height, inputWidth, inputHeight)

        val output = model.getOutputTensor(0)
        val outputBuffer = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
        model.run(buildInput(bitmap, letterbox, inputWidth, inputHeight), outputBuffer)
        outputBuffer.rewind()

        val foreground = decodeForeground(outputBuffer, output.shape(), output.dataType(), inputWidth * inputHeight)
        ensure(foreground != null) { SegmentationError.Failed("unexpected output shape ${output.shape().toList()}") }
        ensure(hasEnoughForeground(foreground, inputWidth, letterbox)) { SegmentationError.NoSubjectFound }

        val alpha = MaskUpscaler.toImageAlpha(foreground, inputWidth, inputHeight, letterbox, bitmap.width, bitmap.height)
        ensure(!alpha.isEmpty) { SegmentationError.NoSubjectFound }
        return alpha
    }

    private fun obtainInterpreter(): Interpreter = interpreter ?: createInterpreter().also { interpreter = it }

    private fun createInterpreter(): Interpreter {
        val options = Interpreter.Options().setNumThreads(THREADS)
        return Interpreter(mapModel(), options)
    }

    private fun mapModel(): MappedByteBuffer = context.assets.openFd(MODEL_ASSET).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
    }

    /** Aspect-fit letterbox on mid-gray, RGB float `(c / 127.5) - 1`. */
    private fun buildInput(bitmap: Bitmap, letterbox: LetterboxRect, inputWidth: Int, inputHeight: Int): ByteBuffer {
        val canvasBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(GRAY, GRAY, GRAY))
        canvas.drawBitmap(
            bitmap,
            null,
            Rect(letterbox.left, letterbox.top, letterbox.left + letterbox.width, letterbox.top + letterbox.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val pixels = IntArray(inputWidth * inputHeight)
        canvasBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        canvasBitmap.recycle()
        val buffer = ByteBuffer.allocateDirect(pixels.size * CHANNELS * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            buffer.putFloat(normalize((pixel shr 16) and 0xFF))
            buffer.putFloat(normalize((pixel shr 8) and 0xFF))
            buffer.putFloat(normalize(pixel and 0xFF))
        }
        buffer.rewind()
        return buffer
    }

    private fun normalize(channel: Int): Float = channel / HALF_RANGE - 1f

    /** `[1,H,W,C]` scores or `[1,H,W]` class ids. Null if the shape is not one of those. */
    private fun decodeForeground(buffer: ByteBuffer, shape: IntArray, type: DataType, pixels: Int): FloatArray? {
        val hasScores = shape.size == 4 && shape[3] > 1
        val hasIds = shape.size == 3 || (shape.size == 4 && shape[3] == 1)
        return when {
            hasScores && type == DataType.FLOAT32 -> {
                val logits = FloatArray(pixels * shape[3])
                buffer.asFloatBuffer().get(logits)
                ClassMask.fromLogits(logits, shape[3])
            }
            hasIds -> ClassMask.fromIndices(readIds(buffer, type, pixels) ?: return null)
            else -> null
        }
    }

    private fun readIds(buffer: ByteBuffer, type: DataType, pixels: Int): IntArray? = when (type) {
        DataType.INT32 -> IntArray(pixels).also { buffer.asIntBuffer().get(it) }
        DataType.INT64 -> LongArray(pixels).also { buffer.asLongBuffer().get(it) }.let { ids -> IntArray(pixels) { ids[it].toInt() } }
        DataType.UINT8 -> IntArray(pixels) { buffer.get(it).toInt() and 0xFF }
        DataType.FLOAT32 -> FloatArray(pixels).also { buffer.asFloatBuffer().get(it) }.let { ids -> IntArray(pixels) { ids[it].toInt() } }
        else -> null
    }

    /** A handful of stray pixels is noise, not a subject. */
    private fun hasEnoughForeground(foreground: FloatArray, inputWidth: Int, letterbox: LetterboxRect): Boolean {
        var count = 0
        for (y in letterbox.top until letterbox.top + letterbox.height) {
            for (x in letterbox.left until letterbox.left + letterbox.width) {
                if (foreground[y * inputWidth + x] > 0f) count++
            }
        }
        return count >= letterbox.width * letterbox.height * MIN_FOREGROUND_FRACTION
    }

    private companion object {
        const val MODEL_ASSET = "deeplabv3_257_mv_gpu.tflite"
        const val THREADS = 2
        const val CHANNELS = 3
        const val GRAY = 127
        const val HALF_RANGE = 127.5f
        const val MIN_FOREGROUND_FRACTION = 0.001f
    }
}
