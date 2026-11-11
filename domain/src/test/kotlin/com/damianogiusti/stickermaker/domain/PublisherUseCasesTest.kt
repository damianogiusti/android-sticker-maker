package com.damianogiusti.stickermaker.domain

import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.repository.PublisherPreferences
import com.damianogiusti.stickermaker.domain.usecase.GetLastPublisherUseCase
import com.damianogiusti.stickermaker.domain.usecase.RememberPublisherUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

class FakePublisherPreferences(var stored: String? = null) : PublisherPreferences {
    override suspend fun getLastPublisher(): String? = stored
    override suspend fun setLastPublisher(publisher: String) {
        stored = publisher
    }
}

class PublisherUseCasesTest {
    @Test
    fun `get returns empty string when nothing is stored`() = runTest {
        assertEquals("", GetLastPublisherUseCase(FakePublisherPreferences())())
    }

    @Test
    fun `remember stores the trimmed publisher and get returns it`() = runTest {
        val preferences = FakePublisherPreferences()
        RememberPublisherUseCase(preferences)("  Damiano ")
        assertEquals("Damiano", GetLastPublisherUseCase(preferences)())
    }

    @Test
    fun `remember ignores a blank publisher`() = runTest {
        val preferences = FakePublisherPreferences("Old")
        RememberPublisherUseCase(preferences)("   ")
        assertEquals("Old", preferences.stored)
    }
}
