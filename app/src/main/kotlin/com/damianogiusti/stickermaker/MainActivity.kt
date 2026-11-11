package com.damianogiusti.stickermaker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.damianogiusti.stickermaker.navigation.StickerMakerNavHost
import com.damianogiusti.stickermaker.share.IncomingIntent
import com.damianogiusti.stickermaker.share.IncomingShareViewModel
import com.damianogiusti.stickermaker.share.SharedImageIntent
import com.damianogiusti.stickermaker.ui.theme.StickerMakerTheme

class MainActivity : ComponentActivity() {
    private val incomingShare: IncomingShareViewModel by viewModel()

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

    /** Thin adapter: reads the intent into plain values, the rule lives in [SharedImageIntent]. */
    private fun handleIncoming(intent: Intent) {
        val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val uri = SharedImageIntent.imageUriOf(
            IncomingIntent(
                action = intent.action,
                mimeType = intent.type,
                streamUri = streamUri?.toString(),
                isLaunchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
                isConsumed = intent.getBooleanExtra(EXTRA_CONSUMED, false),
            ),
        ) ?: return
        intent.putExtra(EXTRA_CONSUMED, true)
        incomingShare.onImageShared(uri)
    }

    private companion object {
        const val EXTRA_CONSUMED = "com.damianogiusti.stickermaker.extra.SHARE_CONSUMED"
    }
}
