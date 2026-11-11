package com.damianogiusti.stickermaker.feature.packs.emoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.feature.packs.R
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress

@Composable
fun EmojiPickerScreen(
    packId: String,
    fileName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmojiPickerViewModel = koinViewModel { parametersOf(packId, fileName) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.savedEvents) { viewModel.savedEvents.collect { onClose() } }

    EmojiPickerContent(
        state = state,
        onEmojiPicked = viewModel::onEmojiPicked,
        onEmojiRemoved = viewModel::onEmojiRemoved,
        onSave = viewModel::save,
        onClose = onClose,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerContent(
    state: EmojiPickerUiState,
    onEmojiPicked: (String) -> Unit,
    onEmojiRemoved: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emoji_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = state is EmojiPickerUiState.Content && state.canSave,
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                EmojiPickerUiState.Loading -> CenteredProgress()
                EmojiPickerUiState.Error -> CenteredMessage(
                    title = stringResource(R.string.emoji_error_title),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onClose,
                )
                is EmojiPickerUiState.Content -> PickerBody(state, onEmojiPicked, onEmojiRemoved)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PickerBody(
    state: EmojiPickerUiState.Content,
    onEmojiPicked: (String) -> Unit,
    onEmojiRemoved: (String) -> Unit,
) {
    Text(
        stringResource(R.string.emoji_selected_count, state.selected.size, StickerPackLimits.MAX_EMOJIS_PER_STICKER),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.selected.forEach { emoji ->
            InputChip(
                selected = true,
                onClick = { onEmojiRemoved(emoji) },
                label = { Text(emoji, style = MaterialTheme.typography.titleMedium) },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.emoji_remove))
                },
            )
        }
    }
    if (state.hasSaveFailed) {
        Text(
            stringResource(R.string.emoji_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
        factory = { context ->
            EmojiPickerView(context).apply {
                setOnEmojiPickedListener { item -> onEmojiPicked(item.emoji) }
            }
        },
        update = { view -> view.setOnEmojiPickedListener { item -> onEmojiPicked(item.emoji) } },
    )
}
