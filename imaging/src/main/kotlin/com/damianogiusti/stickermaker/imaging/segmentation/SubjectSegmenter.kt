package com.damianogiusti.stickermaker.imaging.segmentation

import arrow.core.Either
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.SegmentationError

interface SubjectSegmenter {
    /** Per-pixel alpha (same size as [image]) of the subjects found. Left(NoSubjectFound) if none. */
    suspend fun segment(image: EditorImage): Either<SegmentationError, AlphaMask>
}
