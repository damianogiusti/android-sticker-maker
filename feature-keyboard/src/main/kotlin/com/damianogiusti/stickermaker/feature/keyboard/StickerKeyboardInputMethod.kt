package com.damianogiusti.stickermaker.feature.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import org.koin.android.ext.android.inject
import com.damianogiusti.stickermaker.ui.theme.StickerMakerTheme

/** Thin Android adapter: hosts the Compose UI and forwards taps to [StickerSender]. */
class StickerKeyboardInputMethod : InputMethodService() {

    private val viewTreeOwner = ImeViewTreeOwner()
    private val locator: StickerFileLocator by inject()
    private val sender: StickerSender by lazy { StickerSender(this, locator) }

    override fun onCreate() {
        super.onCreate()
        viewTreeOwner.create()
        window?.window?.decorView?.let(viewTreeOwner::attachTo)
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        viewTreeOwner.attachTo(view)
        view.setContent {
            StickerMakerTheme {
                KeyboardScreen(
                    locator = locator,
                    onSend = { sticker -> sender.send(currentInputConnection, currentInputEditorInfo, sticker) },
                    onSwitchKeyboard = ::switchKeyboard,
                )
            }
        }
        return view
    }

    override fun onWindowShown() {
        super.onWindowShown()
        viewTreeOwner.show()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        viewTreeOwner.show()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        viewTreeOwner.hide()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        viewTreeOwner.hide()
    }

    override fun onDestroy() {
        viewTreeOwner.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun switchKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
            return
        }
        val token = window?.window?.attributes?.token ?: return
        getSystemService(InputMethodManager::class.java)?.switchToLastInputMethod(token)
    }
}
