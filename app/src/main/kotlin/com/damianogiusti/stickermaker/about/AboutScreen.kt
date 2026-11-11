package com.damianogiusti.stickermaker.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.stickermaker.R
import com.damianogiusti.stickermaker.ui.components.CenteredProgress

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AboutContent(state, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutContent(state: AboutUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            AboutUiState.Loading -> CenteredProgress(Modifier.padding(padding))
            is AboutUiState.Content -> AboutList(state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AboutList(state: AboutUiState.Content, modifier: Modifier = Modifier) {
    // Which license text is open is UI-only state.
    var openFont by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(stringResource(R.string.about_version, state.versionName), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { SectionTitle(R.string.about_licenses_fonts) }
        items(state.fonts, key = { it.fontName }) { font ->
            FontLicenseItem(font, isOpen = openFont == font.fontName) {
                openFont = if (openFont == font.fontName) null else font.fontName
            }
        }
        item {
            SectionTitle(R.string.about_licenses_model)
            Text(stringResource(R.string.about_model_notice), style = MaterialTheme.typography.bodyMedium)
        }
        item { SectionTitle(R.string.about_licenses_libraries) }
        items(state.libraries, key = { it.libraryName }) { library ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(library.libraryName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    library.licenseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun FontLicenseItem(font: FontLicense, isOpen: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .clickable(enabled = font.fullText != null, onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Text(font.fontName, style = MaterialTheme.typography.bodyLarge)
        Text(
            font.licenseName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isOpen && font.fullText != null) {
            Text(font.fullText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
