package com.damianogiusti.stickermaker.feature.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommitContentPolicyTest {
    private val grantRead = 1

    @Test
    fun `api 24 grants the permission by hand and passes no flag`() {
        val plan = CommitContentPolicy.planFor(24)

        assertEquals(0, plan.commitFlags)
        assertEquals(grantRead, plan.manualGrantFlags)
    }

    @Test
    fun `api 25 and above let the system grant read access`() {
        listOf(25, 30, 36).forEach { api ->
            val plan = CommitContentPolicy.planFor(api)

            assertEquals(grantRead, plan.commitFlags)
            assertNull(plan.manualGrantFlags)
        }
    }

    @Test
    fun `only an accepted commit is a sent sticker`() {
        assertEquals(SendOutcome.SENT, CommitContentPolicy.outcomeOf(committed = true))
        assertEquals(SendOutcome.FAILED, CommitContentPolicy.outcomeOf(committed = false))
    }
}
