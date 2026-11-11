package com.damianogiusti.stickermaker.data

import app.cash.turbine.test
import arrow.core.Either
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.data.repository.DatabaseKeyboardHistoryRepository
import com.damianogiusti.stickermaker.domain.error.KeyboardHistoryError
import com.damianogiusti.stickermaker.domain.model.KeyboardHistoryEntry
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DatabaseKeyboardHistoryRepositoryTest {
    private val database = createInMemoryDatabase().also { db ->
        db.stickerPackQueries.insertPack("p1", "Cats", "me", 0L)
    }
    private val packId = StickerPackId("p1")

    private fun entries(result: Either<KeyboardHistoryError, List<KeyboardHistoryEntry>>) =
        (result as Either.Right).value

    @Test
    fun `most recent first and deduped`() = runTest {
        val repo = DatabaseKeyboardHistoryRepository(database, StandardTestDispatcher(testScheduler))
        repo.record(KeyboardHistoryEntry(packId, "a.webp", 1))
        repo.record(KeyboardHistoryEntry(packId, "b.webp", 2))
        repo.record(KeyboardHistoryEntry(packId, "a.webp", 3))

        repo.observeHistory().test {
            assertEquals(
                listOf(KeyboardHistoryEntry(packId, "a.webp", 3), KeyboardHistoryEntry(packId, "b.webp", 2)),
                entries(awaitItem()),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history is capped at 100 entries keeping the newest`() = runTest {
        val repo = DatabaseKeyboardHistoryRepository(database, StandardTestDispatcher(testScheduler))
        repeat(105) { repo.record(KeyboardHistoryEntry(packId, "$it.webp", it.toLong())) }

        repo.observeHistory().test {
            val history = entries(awaitItem())
            assertEquals(100, history.size)
            assertEquals("104.webp", history.first().fileName)
            assertEquals("5.webp", history.last().fileName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting the pack removes its history`() = runTest {
        val repo = DatabaseKeyboardHistoryRepository(database, StandardTestDispatcher(testScheduler))
        repo.record(KeyboardHistoryEntry(packId, "a.webp", 1))
        database.stickerPackQueries.deletePack("p1")

        repo.observeHistory().test {
            assertEquals(emptyList(), entries(awaitItem()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recording for an unknown pack is a typed error`() = runTest {
        val repo = DatabaseKeyboardHistoryRepository(database, StandardTestDispatcher(testScheduler))
        val result = repo.record(KeyboardHistoryEntry(StickerPackId("nope"), "a.webp", 1))
        assertIs<KeyboardHistoryError.Database>((result as Either.Left).value)
    }
}
