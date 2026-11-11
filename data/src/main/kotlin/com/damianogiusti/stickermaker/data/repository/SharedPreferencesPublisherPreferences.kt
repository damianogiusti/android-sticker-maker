package com.damianogiusti.stickermaker.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.damianogiusti.stickermaker.domain.repository.PublisherPreferences

class SharedPreferencesPublisherPreferences(
    private val preferences: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) : PublisherPreferences {

    override suspend fun getLastPublisher(): String? = withContext(ioDispatcher) {
        preferences.getString(KEY_LAST_PUBLISHER, null)
    }

    override suspend fun setLastPublisher(publisher: String) = withContext(ioDispatcher) {
        preferences.edit().putString(KEY_LAST_PUBLISHER, publisher).apply()
    }

    private companion object {
        const val KEY_LAST_PUBLISHER = "last_publisher"
    }
}
