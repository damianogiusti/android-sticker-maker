package com.damianogiusti.stickermaker.domain

import com.damianogiusti.stickermaker.domain.model.ImageFormat
import com.damianogiusti.stickermaker.domain.model.StickerFileFacts
import com.damianogiusti.stickermaker.domain.validation.StickerPackValidator
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StickerPackValidatorTest {
    private val validator = StickerPackValidator()

    private fun validPack() = aPack(stickers = (0 until 3).map { aSticker(it) })
    private fun validFacts() =
        (0 until 3).associate { "$it.webp" to validStickerFacts() } + ("tray.png" to validTrayFacts())

    @Test
    fun `valid pack has no issues`() {
        assertEquals(emptyList(), validator.validate(validPack(), validFacts()))
    }

    @Test
    fun `fewer than 3 stickers is reported`() {
        val pack = aPack(stickers = listOf(aSticker(0), aSticker(1)))
        assertContains(validator.validate(pack, validFacts()), ValidationIssue.TooFewStickers(2))
    }

    @Test
    fun `more than 30 stickers is reported`() {
        val pack = aPack(stickers = (0 until 31).map { aSticker(it) })
        val facts = (0 until 31).associate { "$it.webp" to validStickerFacts() } + ("tray.png" to validTrayFacts())
        assertContains(validator.validate(pack, facts), ValidationIssue.TooManyStickers(31))
    }

    @Test
    fun `missing tray is reported`() {
        val pack = validPack().copy(trayFileName = null)
        assertContains(validator.validate(pack, validFacts()), ValidationIssue.MissingTray)
    }

    @Test
    fun `pack id rules`() {
        listOf("", "a..b", "a/b", "x".repeat(129)).forEach { id ->
            val issues = validator.validate(validPack().copy(id = com.damianogiusti.stickermaker.domain.model.StickerPackId(id)), validFacts())
            assertContains(issues, ValidationIssue.InvalidPackId, "id '$id'")
        }
        assertEquals(emptyList(), validator.validate(validPack().copy(id = com.damianogiusti.stickermaker.domain.model.StickerPackId("My pack-1.0,x's")), validFacts()))
    }

    @Test
    fun `name and publisher lengths`() {
        val issues = validator.validate(validPack().copy(name = "", publisher = "p".repeat(129)), validFacts())
        assertContains(issues, ValidationIssue.InvalidName)
        assertContains(issues, ValidationIssue.InvalidPublisher)
        assertEquals(
            emptyList(),
            validator.validate(validPack().copy(name = "n".repeat(128), publisher = "p"), validFacts()),
        )
    }

    @Test
    fun `sticker without emoji or with more than 3 is reported`() {
        val pack = aPack(
            stickers = listOf(aSticker(0, emptyList()), aSticker(1, listOf("a", "b", "c", "d")), aSticker(2)),
        )
        val issues = validator.validate(pack, validFacts())
        assertContains(issues, ValidationIssue.MissingEmoji("0.webp"))
        assertContains(issues, ValidationIssue.TooManyEmojis("1.webp"))
    }

    @Test
    fun `sticker file rules`() {
        val facts = validFacts() + mapOf(
            "0.webp" to StickerFileFacts(ImageFormat.WEBP, 256, 512, 50_000, false),
            "1.webp" to StickerFileFacts(ImageFormat.WEBP, 512, 512, 100L * 1024 + 1, false),
            "2.webp" to StickerFileFacts(ImageFormat.WEBP, 512, 512, 10, true),
        )
        val issues = validator.validate(validPack(), facts)
        assertContains(issues, ValidationIssue.StickerWrongSize("0.webp", 256, 512))
        assertContains(issues, ValidationIssue.StickerTooLarge("1.webp", 100L * 1024 + 1))
        assertContains(issues, ValidationIssue.StickerAnimated("2.webp"))
    }

    @Test
    fun `png sticker is not webp`() {
        val facts = validFacts() + ("0.webp" to StickerFileFacts(ImageFormat.PNG, 512, 512, 10, false))
        assertContains(validator.validate(validPack(), facts), ValidationIssue.StickerNotWebp("0.webp"))
    }

    @Test
    fun `tray file rules`() {
        val facts = validFacts() + ("tray.png" to StickerFileFacts(ImageFormat.WEBP, 100, 96, 50L * 1024 + 1, false))
        val issues = validator.validate(validPack(), facts)
        assertContains(issues, ValidationIssue.TrayNotPng("tray.png"))
        assertContains(issues, ValidationIssue.TrayWrongSize("tray.png", 100, 96))
        assertContains(issues, ValidationIssue.TrayTooLarge("tray.png", 50L * 1024 + 1))
    }

    @Test
    fun `missing file facts are reported`() {
        val issues = validator.validate(validPack(), validFacts() - "1.webp")
        assertContains(issues, ValidationIssue.FileFactsMissing("1.webp"))
    }
}
