package com.damianogiusti.stickermaker.domain.model

enum class ImageFormat { WEBP, PNG, UNKNOWN }

data class StickerFileFacts(
    val format: ImageFormat,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isAnimated: Boolean,
)
