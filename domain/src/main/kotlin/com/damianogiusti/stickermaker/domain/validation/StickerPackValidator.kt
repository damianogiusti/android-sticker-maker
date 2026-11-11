package com.damianogiusti.stickermaker.domain.validation

import com.damianogiusti.stickermaker.domain.model.ImageFormat
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits

/** Pure: file facts are supplied by the caller, keyed by file name. */
class StickerPackValidator {

    fun validate(pack: StickerPack, facts: Map<String, StickerFileFacts>): List<ValidationIssue> =
        validateMetadata(pack) + validateTray(pack, facts) + validateStickers(pack, facts)

    private fun validateMetadata(pack: StickerPack): List<ValidationIssue> = buildList {
        if (!isValidPackId(pack.id.value)) add(ValidationIssue.InvalidPackId)
        if (!isValidText(pack.name)) add(ValidationIssue.InvalidName)
        if (!isValidText(pack.publisher)) add(ValidationIssue.InvalidPublisher)
    }

    private fun validateTray(pack: StickerPack, facts: Map<String, StickerFileFacts>): List<ValidationIssue> {
        val trayFileName = pack.trayFileName ?: return listOf(ValidationIssue.MissingTray)
        val trayFacts = facts[trayFileName] ?: return listOf(ValidationIssue.FileFactsMissing(trayFileName))
        return buildList {
            if (trayFacts.format != ImageFormat.PNG) add(ValidationIssue.TrayNotPng(trayFileName))
            if (trayFacts.width != StickerPackLimits.TRAY_SIDE_PX || trayFacts.height != StickerPackLimits.TRAY_SIDE_PX) {
                add(ValidationIssue.TrayWrongSize(trayFileName, trayFacts.width, trayFacts.height))
            }
            if (trayFacts.sizeBytes > StickerPackLimits.TRAY_MAX_BYTES) {
                add(ValidationIssue.TrayTooLarge(trayFileName, trayFacts.sizeBytes))
            }
        }
    }

    private fun validateStickers(pack: StickerPack, facts: Map<String, StickerFileFacts>): List<ValidationIssue> {
        val count = pack.stickers.size
        val countIssues = when {
            count < StickerPackLimits.MIN_STICKERS_TO_SEND -> listOf(ValidationIssue.TooFewStickers(count))
            count > StickerPackLimits.MAX_STICKERS -> listOf(ValidationIssue.TooManyStickers(count))
            else -> emptyList()
        }
        return countIssues + pack.stickers.flatMap { validateSticker(it, facts[it.fileName]) }
    }

    private fun validateSticker(sticker: Sticker, facts: StickerFileFacts?): List<ValidationIssue> = buildList {
        addAll(validateEmojis(sticker))
        if (facts == null) {
            add(ValidationIssue.FileFactsMissing(sticker.fileName))
            return@buildList
        }
        if (facts.format != ImageFormat.WEBP) add(ValidationIssue.StickerNotWebp(sticker.fileName))
        if (facts.isAnimated) add(ValidationIssue.StickerAnimated(sticker.fileName))
        if (facts.width != StickerPackLimits.STICKER_SIDE_PX || facts.height != StickerPackLimits.STICKER_SIDE_PX) {
            add(ValidationIssue.StickerWrongSize(sticker.fileName, facts.width, facts.height))
        }
        if (facts.sizeBytes > StickerPackLimits.STICKER_MAX_BYTES) {
            add(ValidationIssue.StickerTooLarge(sticker.fileName, facts.sizeBytes))
        }
    }

    private fun validateEmojis(sticker: Sticker): List<ValidationIssue> = when {
        sticker.emojis.isEmpty() -> listOf(ValidationIssue.MissingEmoji(sticker.fileName))
        sticker.emojis.size > StickerPackLimits.MAX_EMOJIS_PER_STICKER ->
            listOf(ValidationIssue.TooManyEmojis(sticker.fileName))
        else -> emptyList()
    }

    private fun isValidText(text: String): Boolean = text.length in 1..StickerPackLimits.MAX_TEXT_LENGTH

    private fun isValidPackId(id: String): Boolean =
        isValidText(id) && PACK_ID_PATTERN.matches(id) && !id.contains("..")

    private companion object {
        val PACK_ID_PATTERN = Regex("[\\w\\-.,'\\s]+")
    }
}
