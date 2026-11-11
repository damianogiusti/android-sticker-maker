package com.damianogiusti.stickermaker.feature.packs.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.feature.packs.R
import java.util.UUID

/** New pack when [packId] is null, rename otherwise. */
@Composable
fun PackFormDialog(
    packId: String?,
    onDismiss: () -> Unit,
    onSaved: (packId: String) -> Unit,
) {
    // One ViewModel per opening: the dialog is not a destination, so it would otherwise keep stale text.
    val instanceKey = remember { UUID.randomUUID().toString() }
    val viewModel: PackFormViewModel = koinViewModel(key = instanceKey) { parametersOf(*listOfNotNull(packId).toTypedArray()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.savedEvents) { viewModel.savedEvents.collect(onSaved) }

    PackFormContent(
        isNewPack = packId == null,
        state = state,
        onNameChange = viewModel::onNameChange,
        onPublisherChange = viewModel::onPublisherChange,
        onSave = viewModel::save,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun PackFormContent(
    isNewPack: Boolean,
    state: PackFormUiState,
    onNameChange: (String) -> Unit,
    onPublisherChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNewPack) R.string.pack_form_title_new else R.string.pack_form_title_rename))
        },
        text = {
            when (state.phase) {
                FormPhase.LOADING -> Text(stringResource(R.string.loading))
                FormPhase.LOAD_FAILED -> Text(stringResource(R.string.pack_form_load_failed))
                FormPhase.EDITING, FormPhase.SAVING -> FormFields(state, onNameChange, onPublisherChange, onSave)
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.canSave) {
                Text(stringResource(if (isNewPack) R.string.action_create else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FormFields(
    state: PackFormUiState,
    onNameChange: (String) -> Unit,
    onPublisherChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column {
        FormTextField(
            value = state.name,
            label = stringResource(R.string.pack_form_name),
            error = state.nameError,
            imeAction = ImeAction.Next,
            onValueChange = onNameChange,
            onDone = {},
        )
        Spacer(Modifier.height(12.dp))
        FormTextField(
            value = state.publisher,
            label = stringResource(R.string.pack_form_publisher),
            error = state.publisherError,
            imeAction = ImeAction.Done,
            onValueChange = onPublisherChange,
            onDone = onSave,
        )
        if (state.hasSaveFailed) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pack_form_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    label: String,
    error: FieldError?,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            Text(
                when (error) {
                    FieldError.EMPTY -> stringResource(R.string.pack_form_error_empty)
                    FieldError.TOO_LONG -> stringResource(R.string.pack_form_error_too_long, StickerPackLimits.MAX_TEXT_LENGTH)
                    null -> "${value.trim().length}/${StickerPackLimits.MAX_TEXT_LENGTH}"
                },
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}
