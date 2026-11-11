package com.damianogiusti.stickermaker.ui.coroutines

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SingleEventFlowTest {
    @Test
    fun `subscriber receives an event emitted after subscribing`() = runTest {
        val events = SingleEventFlow<Int>()
        events.test {
            events.tryEmit(1)
            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun `late subscriber does not get past events`() = runTest {
        val events = SingleEventFlow<Int>()
        events.tryEmit(1)
        events.test { expectNoEvents() }
    }
}
