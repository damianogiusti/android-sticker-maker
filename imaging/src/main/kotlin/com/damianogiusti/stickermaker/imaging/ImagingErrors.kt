package com.damianogiusti.stickermaker.imaging

sealed interface SourceError {
    data class Unreadable(val message: String?) : SourceError
}

sealed interface CutError {
    /** The selection covers no visible pixel. */
    data object EmptySelection : CutError
    data class Failed(val message: String?) : CutError
}

sealed interface SegmentationError {
    /** The model found none of the supported classes in the image. */
    data object NoSubjectFound : SegmentationError
    data class Failed(val message: String?) : SegmentationError
}

sealed interface OutlineError {
    data class Failed(val message: String?) : OutlineError
}

sealed interface ExportError {
    data object EmptyComposite : ExportError
    data class StickerTooLarge(val lastSizeBytes: Long, val limitBytes: Long) : ExportError
    data class TrayTooLarge(val sizeBytes: Long, val limitBytes: Long) : ExportError
    data class EncodeFailed(val message: String?) : ExportError
}

sealed interface ImageStoreError {
    /** Nothing saved in that slot. */
    data object Missing : ImageStoreError
    data class Failed(val message: String?) : ImageStoreError
}
