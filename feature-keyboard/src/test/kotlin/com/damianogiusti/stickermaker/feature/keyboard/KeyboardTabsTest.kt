package com.damianogiusti.stickermaker.feature.keyboard

import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KeyboardTabsTest {

    @Test
    fun `history comes first then packs most recently added first`() {
        val packs = listOf(aPack("1", keyboardAddedAt = 10), aPack("2", keyboardAddedAt = 30), aPack("3", keyboardAddedAt = 20))

        val tabs = buildKeyboardTabs(packs, emptyList())

        assertIs<KeyboardTab.History>(tabs.first())
        assertEquals(listOf("2", "3", "1"), tabs.drop(1).map { (it as KeyboardTab.Pack).packId })
    }

    @Test
    fun `packs not available in the keyboard have no tab`() {
        val packs = listOf(aPack("1", keyboardAddedAt = 10), aPack("2", keyboardAddedAt = null))

        val tabs = buildKeyboardTabs(packs, emptyList())

        assertEquals(2, tabs.size)
    }

    @Test
    fun `history keeps its order and drops stickers that no longer exist`() {
        val packs = listOf(aPack("1", keyboardAddedAt = 10, stickerCount = 2))
        val history = listOf(
            KeyboardHistoryEntry(StickerPackId("1"), "1.webp", 3),
            KeyboardHistoryEntry(StickerPackId("gone"), "0.webp", 2),
            KeyboardHistoryEntry(StickerPackId("1"), "9.webp", 1),
            KeyboardHistoryEntry(StickerPackId("1"), "0.webp", 0),
        )

        val historyTab = buildKeyboardTabs(packs, history).first()

        assertEquals(listOf("1.webp", "0.webp"), historyTab.stickers.map { it.fileName })
    }

    @Test
    fun `history can show a sticker of a pack removed from the keyboard`() {
        val packs = listOf(aPack("1", keyboardAddedAt = null, isAvailable = false), aPack("2", keyboardAddedAt = 5))
        val history = listOf(KeyboardHistoryEntry(StickerPackId("1"), "0.webp", 1))

        val tabs = buildKeyboardTabs(packs, history)

        assertEquals(listOf("0.webp"), tabs.first().stickers.map { it.fileName })
    }
}
