package com.damianogiusti.stickermaker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.damianogiusti.stickermaker.imports.IncomingImportViewModel
import com.damianogiusti.stickermaker.navigation.StickerMakerNavHost
import com.damianogiusti.stickermaker.ui.theme.StickerMakerTheme

class MainActivity : ComponentActivity() {
    private val incomingImport: IncomingImportViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the intent is the same one: it was already handled.
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            StickerMakerTheme {
                StickerMakerNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the marked intent as the current one, so a later recreation sees it as consumed.
        setIntent(intent)
        handleIncoming(intent)
    }

    /**
     * Thin adapter: finds the file URI in a VIEW / SEND intent and hands it to the ViewModel. The same
     * intent is delivered again when the task is brought back from Recents; importing it twice would
     * duplicate the pack or fail on an expired URI grant, so such intents are ignored.
     */
    private fun handleIncoming(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (intent.getBooleanExtra(EXTRA_CONSUMED, false)) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let {
            intent.putExtra(EXTRA_CONSUMED, true)
            incomingImport.importFrom(it.toString())
        }
    }

    private companion object {
        const val EXTRA_CONSUMED = "com.damianogiusti.stickermaker.extra.IMPORT_CONSUMED"
    }
}
