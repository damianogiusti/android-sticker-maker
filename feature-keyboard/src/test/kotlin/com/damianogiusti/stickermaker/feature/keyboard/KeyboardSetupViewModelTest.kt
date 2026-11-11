package com.damianogiusti.stickermaker.feature.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardSetupViewModelTest {

    private class FakeEnabledQuery(var enabled: Boolean) : KeyboardEnabledQuery {
        override fun isEnabled() = enabled
    }

    @Test
    fun `starts with the current system status`() {
        val viewModel = KeyboardSetupViewModel(FakeEnabledQuery(enabled = true))

        assertEquals(KeyboardSetupUiState(isEnabled = true), viewModel.state.value)
    }

    @Test
    fun `refresh picks up a change made in the settings`() {
        val query = FakeEnabledQuery(enabled = false)
        val viewModel = KeyboardSetupViewModel(query)

        query.enabled = true
        viewModel.refresh()

        assertEquals(KeyboardSetupUiState(isEnabled = true), viewModel.state.value)
    }
}
