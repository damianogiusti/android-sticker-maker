package com.damianogiusti.stickermaker.about

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.damianogiusti.stickermaker.ui.logging.ErrorLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val logged = mutableListOf<String>()
    private val logger = ErrorLogger { tag, message -> logged += "$tag: $message" }

    @BeforeTest
    fun setUpMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDownMain() = Dispatchers.resetMain()

    private fun content(texts: LicenseTextSource) = runTest {
        AboutViewModel("1.2.3", texts, logger).state.test {
            assertEquals(AboutUiState.Loading, awaitItem())
            assertIs<AboutUiState.Content>(awaitItem()).also { result = it }
        }
    }

    private lateinit var result: AboutUiState.Content

    @Test
    fun `shows the version, every font with its text, and the libraries`() {
        content { "text of $it" }

        assertEquals("1.2.3", result.versionName)
        assertEquals(9, result.fonts.size)
        assertTrue(result.fonts.all { it.licenseName.startsWith("SIL Open Font License") })
        assertEquals("text of OFL-anton.txt", result.fonts.first { it.fontName == "Anton" }.fullText)
        assertTrue(result.libraries.any { it.libraryName == "Koin" })
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `an unreadable license text keeps the font in the list and is logged`() {
        content { name -> name.takeUnless { it == "OFL-nunito.txt" }?.let { "ok" } }

        assertNull(result.fonts.first { it.fontName == "Nunito" }.fullText)
        assertEquals(9, result.fonts.size)
        assertEquals(1, logged.size)
        assertTrue(logged.single().contains("OFL-nunito.txt"))
    }
}
