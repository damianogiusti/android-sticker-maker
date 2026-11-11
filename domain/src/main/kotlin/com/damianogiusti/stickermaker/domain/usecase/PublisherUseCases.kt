package com.damianogiusti.stickermaker.domain.usecase

import com.damianogiusti.stickermaker.domain.repository.PublisherPreferences

class GetLastPublisherUseCase(private val preferences: PublisherPreferences) {
    suspend operator fun invoke(): String = preferences.getLastPublisher().orEmpty()
}

class RememberPublisherUseCase(private val preferences: PublisherPreferences) {
    suspend operator fun invoke(publisher: String) {
        val clean = publisher.trim()
        if (clean.isNotEmpty()) preferences.setLastPublisher(clean)
    }
}
