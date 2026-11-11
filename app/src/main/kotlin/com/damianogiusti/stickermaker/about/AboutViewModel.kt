package com.damianogiusti.stickermaker.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import com.damianogiusti.stickermaker.ui.logging.ErrorLogger

/** Reads the bundled license text called [fileName], or null when it cannot be read. */
fun interface LicenseTextSource {
    suspend fun load(fileName: String): String?
}

/** [fullText] is the whole license text, shown on demand; null when it could not be read. */
data class FontLicense(val fontName: String, val licenseName: String, val fullText: String?)

data class LibraryLicense(val libraryName: String, val licenseName: String)

sealed interface AboutUiState {
    data object Loading : AboutUiState
    data class Content(
        val versionName: String,
        val fonts: List<FontLicense>,
        val libraries: List<LibraryLicense>,
    ) : AboutUiState
}

class AboutViewModel(
    private val versionName: String,
    private val licenseTexts: LicenseTextSource,
    private val errorLogger: ErrorLogger,
) : ViewModel() {

    val state: StateFlow<AboutUiState> = flow { emit(buildContent()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AboutUiState.Loading)

    private suspend fun buildContent(): AboutUiState.Content {
        val fonts = AboutLicenses.fonts.map { font ->
            val text = licenseTexts.load(font.assetFile)
            // The list still shows the font and its license name; only the full text is missing.
            if (text == null) errorLogger.log(TAG, "Could not read license text ${font.assetFile}")
            FontLicense(font.fontName, AboutLicenses.OFL_NAME, text)
        }
        return AboutUiState.Content(versionName, fonts, AboutLicenses.libraries)
    }

    private companion object {
        const val TAG = "AboutViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
