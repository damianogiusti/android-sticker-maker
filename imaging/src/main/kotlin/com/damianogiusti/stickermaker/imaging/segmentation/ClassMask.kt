package com.damianogiusti.stickermaker.imaging.segmentation

/** Turns model output into a 0/1 foreground mask. Pure, so it is unit-tested on the JVM. */
object ClassMask {
    private const val CAR = 7
    private const val CAT = 8
    private const val COW = 10
    private const val DOG = 12
    private const val HORSE = 13
    private const val MOTORBIKE = 14
    private const val PERSON = 15
    private const val POTTED_PLANT = 16

    /** PASCAL VOC classes that count as a subject. Everything else is background. */
    val keptClasses: Set<Int> = setOf(CAR, CAT, COW, DOG, HORSE, MOTORBIKE, PERSON, POTTED_PLANT)

    fun isKept(classIndex: Int): Boolean = classIndex in keptClasses

    /** [indices] holds one class index per pixel (an `[1,H,W]` argmax output). */
    fun fromIndices(indices: IntArray): FloatArray =
        FloatArray(indices.size) { if (isKept(indices[it])) 1f else 0f }

    /** [logits] holds [classCount] scores per pixel (an `[1,H,W,C]` output). The argmax picks the class. */
    fun fromLogits(logits: FloatArray, classCount: Int): FloatArray {
        require(classCount > 0 && logits.size % classCount == 0) { "logits do not match $classCount classes" }
        val pixels = logits.size / classCount
        return FloatArray(pixels) { pixel ->
            val base = pixel * classCount
            var best = 0
            for (candidate in 1 until classCount) {
                if (logits[base + candidate] > logits[base + best]) best = candidate
            }
            if (isKept(best)) 1f else 0f
        }
    }
}
