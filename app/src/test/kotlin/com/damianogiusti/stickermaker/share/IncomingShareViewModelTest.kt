package com.damianogiusti.stickermaker.share

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingShareViewModelTest {
    @Test
    fun `a shared image stays pending until the editor is opened`() = runTest {
        val viewModel = IncomingShareViewModel()

        // Shared before anyone collects: the state keeps it.
        viewModel.onImageShared("content://image/1")

        viewModel.state.test {
            assertEquals("content://image/1", awaitItem().pendingImageUri)
            viewModel.onEditorOpened()
            assertNull(awaitItem().pendingImageUri)
        }
    }
}
