package com.damianogiusti.stickermaker.domain.validation

sealed interface ValidationIssue {
    data object InvalidPackId : ValidationIssue
    data object InvalidName : ValidationIssue
    data object InvalidPublisher : ValidationIssue
    data object MissingTray : ValidationIssue
    data class TooFewStickers(val count: Int) : ValidationIssue
    data class TooManyStickers(val count: Int) : ValidationIssue
    data class MissingEmoji(val fileName: String) : ValidationIssue
    data class TooManyEmojis(val fileName: String) : ValidationIssue
    data class FileFactsMissing(val fileName: String) : ValidationIssue
    data class StickerNotWebp(val fileName: String) : ValidationIssue
    data class StickerAnimated(val fileName: String) : ValidationIssue
    data class StickerWrongSize(val fileName: String, val width: Int, val height: Int) : ValidationIssue
    data class StickerTooLarge(val fileName: String, val sizeBytes: Long) : ValidationIssue
    data class TrayNotPng(val fileName: String) : ValidationIssue
    data class TrayWrongSize(val fileName: String, val width: Int, val height: Int) : ValidationIssue
    data class TrayTooLarge(val fileName: String, val sizeBytes: Long) : ValidationIssue
}
