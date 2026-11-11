package com.damianogiusti.stickermaker.feature.keyboard

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether our keyboard is enabled in the system settings. */
internal fun interface KeyboardEnabledQuery {
    fun isEnabled(): Boolean
}

internal class InputMethodManagerKeyboardEnabledQuery(private val context: Context) : KeyboardEnabledQuery {
    override fun isEnabled(): Boolean {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
        val serviceName = StickerKeyboardInputMethod::class.java.name
        return manager.enabledInputMethodList.any {
            it.packageName == context.packageName && it.serviceInfo.name == serviceName
        }
    }
}

internal data class KeyboardSetupUiState(val isEnabled: Boolean)

internal class KeyboardSetupViewModel(private val enabledQuery: KeyboardEnabledQuery) : ViewModel() {

    private val _state = MutableStateFlow(KeyboardSetupUiState(enabledQuery.isEnabled()))
    val state: StateFlow<KeyboardSetupUiState> = _state.asStateFlow()

    /** Called when the screen resumes: the user may have just toggled the keyboard in the settings. */
    fun refresh() {
        _state.value = KeyboardSetupUiState(enabledQuery.isEnabled())
    }
}
