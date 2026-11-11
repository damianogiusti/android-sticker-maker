package com.damianogiusti.stickermaker.feature.keyboard

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Explains how to turn the sticker keyboard on and shows whether it already is. */
@Composable
fun KeyboardSetupScreen(onBack: () -> Unit) {
    KeyboardSetupScreen(onBack = onBack, viewModel = koinViewModel<KeyboardSetupViewModel>())
}

@Composable
internal fun KeyboardSetupScreen(onBack: () -> Unit, viewModel: KeyboardSetupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val context = LocalContext.current
    KeyboardSetupContent(
        state = state,
        onBack = onBack,
        onOpenSettings = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
        onShowPicker = { context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardSetupContent(
    state: KeyboardSetupUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowPicker: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keyboard_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.keyboard_setup_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.keyboard_setup_intro), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (state.isEnabled) R.string.keyboard_setup_status_enabled else R.string.keyboard_setup_status_disabled,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.keyboard_setup_step1_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.keyboard_setup_step1_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.keyboard_setup_step1_action)) }
            Text(stringResource(R.string.keyboard_setup_step2_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.keyboard_setup_step2_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onShowPicker) { Text(stringResource(R.string.keyboard_setup_step2_action)) }
        }
    }
}
