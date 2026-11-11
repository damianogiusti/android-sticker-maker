package com.damianogiusti.stickermaker.feature.editor

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.damianogiusti.stickermaker.domain.clipboard.ClipboardError
import com.damianogiusti.stickermaker.domain.clipboard.EncodedSticker
import com.damianogiusti.stickermaker.domain.clipboard.StickerClipboard
import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import com.damianogiusti.stickermaker.imaging.CutError
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.ImageStoreError
import com.damianogiusti.stickermaker.imaging.store.EditorImageStore
import com.damianogiusti.stickermaker.imaging.store.ImageSlot
import com.damianogiusti.stickermaker.imaging.ExportError
import com.damianogiusti.stickermaker.imaging.OutlineError
import com.damianogiusti.stickermaker.imaging.SegmentationError
import com.damianogiusti.stickermaker.imaging.SourceError
import com.damianogiusti.stickermaker.imaging.cut.CutSelection
import com.damianogiusti.stickermaker.imaging.cut.ImageCutter
import com.damianogiusti.stickermaker.imaging.export.StickerComposition
import com.damianogiusti.stickermaker.imaging.export.StickerExporter
import com.damianogiusti.stickermaker.imaging.outline.OutlineRenderer
import com.damianogiusti.stickermaker.imaging.outline.OutlineSettings
import com.damianogiusti.stickermaker.imaging.segmentation.AlphaMask
import com.damianogiusti.stickermaker.imaging.segmentation.SubjectSegmenter
import com.damianogiusti.stickermaker.imaging.source.SourceImageLoader
import com.damianogiusti.stickermaker.imaging.source.SourceUri

/** An image handle for the JVM tests: only its identity and size matter. */
data class FakeImage(val id: Int = nextId++, override val width: Int = 1, override val height: Int = 1) : EditorImage {
    private companion object {
        var nextId = 0
    }
}

fun fakeBitmap(): EditorImage = FakeImage()

class FakeEditorLogger : EditorLogger {
    val warnings = mutableListOf<String>()
    override fun warn(tag: String, message: String) {
        warnings += "$tag: $message"
    }
}

class FakeImageStore : EditorImageStore {
    val images = mutableMapOf<ImageSlot, EditorImage>()
    var clears = 0
    var failLoads = false

    override suspend fun save(slot: ImageSlot, image: EditorImage): Either<ImageStoreError, Unit> {
        images[slot] = image
        return Unit.right()
    }

    override suspend fun load(slot: ImageSlot): Either<ImageStoreError, EditorImage> {
        if (failLoads) return ImageStoreError.Failed("boom").left()
        return images[slot]?.right() ?: ImageStoreError.Missing.left()
    }

    override suspend fun clear() {
        clears++
        images.clear()
    }
}

class FakeSourceImageLoader(var result: Either<SourceError, EditorImage> = fakeBitmap().right()) : SourceImageLoader {
    val loaded = mutableListOf<SourceUri>()
    override suspend fun load(uri: SourceUri): Either<SourceError, EditorImage> {
        loaded += uri
        return result
    }
}

class FakeImageCutter : ImageCutter {
    var cutResult: Either<CutError, EditorImage> = fakeBitmap().right()
    var rotateResult: Either<CutError, EditorImage> = fakeBitmap().right()
    val selections = mutableListOf<CutSelection>()
    val maskCuts = mutableListOf<AlphaMask>()

    override suspend fun rotateClockwise(source: EditorImage): Either<CutError, EditorImage> = rotateResult

    override suspend fun cut(source: EditorImage, selection: CutSelection): Either<CutError, EditorImage> {
        selections += selection
        return cutResult
    }

    override suspend fun cut(source: EditorImage, mask: AlphaMask): Either<CutError, EditorImage> {
        maskCuts += mask
        return cutResult
    }
}

class FakeSegmenter(
    var result: Either<SegmentationError, AlphaMask> = AlphaMask(1, 1, byteArrayOf(-1)).right(),
) : SubjectSegmenter {
    override suspend fun segment(image: EditorImage): Either<SegmentationError, AlphaMask> = result
}

class FakeOutlineRenderer : OutlineRenderer {
    var result: Either<OutlineError, EditorImage> = fakeBitmap().right()
    val rendered = mutableListOf<OutlineSettings>()
    override suspend fun render(cutout: EditorImage, settings: OutlineSettings): Either<OutlineError, EditorImage> {
        rendered += settings
        return result
    }
}

class FakeOutlinePreferences(var defaults: OutlineDefaults = OutlineDefaults.Initial) : OutlinePreferences {
    val writes = mutableListOf<Triple<Boolean, Int, Int>>()
    override fun read(): OutlineDefaults = defaults
    override fun write(isSelectAll: Boolean, widthPermille: Int, colorIndex: Int) {
        writes += Triple(isSelectAll, widthPermille, colorIndex)
    }
}

class FakeStickerExporter : StickerExporter {
    var result: Either<ExportError, ByteArray> = byteArrayOf(1, 2, 3).right()
    val compositions = mutableListOf<StickerComposition>()
    val formats = mutableListOf<StickerImageFormat>()

    override suspend fun exportSticker(
        composition: StickerComposition,
        format: StickerImageFormat,
    ): Either<ExportError, ByteArray> {
        compositions += composition
        formats += format
        return result
    }
}

class FakeStickerClipboard : StickerClipboard {
    var failure: ClipboardError? = null
    val copied = mutableListOf<EncodedSticker>()

    override suspend fun copy(sticker: EncodedSticker): Either<ClipboardError, Unit> =
        failure?.left() ?: Unit.right().also { copied += sticker }
}
