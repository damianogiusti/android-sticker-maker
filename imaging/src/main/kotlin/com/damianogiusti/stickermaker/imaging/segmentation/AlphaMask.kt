package com.damianogiusti.stickermaker.imaging.segmentation

/** One alpha byte (0..255) per pixel of the source image, row-major. */
class AlphaMask(val width: Int, val height: Int, val alpha: ByteArray) {
    init {
        require(alpha.size == width * height) { "mask size does not match $width x $height" }
    }

    val isEmpty: Boolean get() = alpha.all { it.toInt() == 0 }
}
