package com.damianogiusti.stickermaker.feature.editor

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.stickermaker.feature.editor.cutting.CuttingActions
import com.damianogiusti.stickermaker.feature.editor.cutting.CuttingContent
import com.damianogiusti.stickermaker.feature.editor.text.TextEditorActions
import com.damianogiusti.stickermaker.feature.editor.text.TextEditorContent
import com.damianogiusti.stickermaker.imaging.source.SourceUri
import com.damianogiusti.stickermaker.imaging.text.TextItemPainter
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress

/**
 * The whole editor: opens the Photo Picker for [EditorSource.PhotoPicker] (or loads the given image),
 * then the cutting screen and the text screen, where Copy puts the sticker on the clipboard.
 * [onClose] runs when the user leaves the editor. Needs `editorModule` in Koin.
 */
@Composable
fun EditorScreen(
    source: EditorSource,
    onClose: () -> Unit,
) {
    val viewModel: EditorViewModel = koinViewModel()
    val painter: TextItemPainter = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.editor_copied)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Copied -> {
                    // A second Copy replaces the first message instead of queueing behind it.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    launch { snackbarHostState.showSnackbar(copiedMessage) }
                }
                EditorEvent.Cancelled -> onClose()
                EditorEvent.LaunchPicker -> Unit
            }
        }
    }
    BackHandler(onBack = viewModel::onBackPressed)

    SourceLauncher(source = source, viewModel = viewModel)

    EditorContent(
        state = state,
        painter = painter,
        snackbarHostState = snackbarHostState,
        cuttingActions = remember(viewModel) { cuttingActionsOf(viewModel) },
        textActions = remember(viewModel) { textActionsOf(viewModel) },
        onRetryLoad = viewModel::onRetryLoad,
        onPickAgain = viewModel::onPickAgainClicked,
        onClose = onClose,
    )
}

private fun cuttingActionsOf(viewModel: EditorViewModel) = CuttingActions(
    onBack = viewModel::onBackPressed,
    onToolSelected = viewModel::onToolSelected,
    onFreehandFinished = viewModel::onFreehandFinished,
    onShapeFinished = viewModel::onShapeFinished,
    onPolygonTapped = viewModel::onPolygonTapped,
    onPolygonUndo = viewModel::onPolygonUndo,
    onSelectAll = viewModel::onSelectAllClicked,
    onRotate = viewModel::onRotateClicked,
    onMagic = viewModel::onMagicClicked,
    onViewportChanged = viewModel::onViewportChanged,
    onMessageDismissed = viewModel::onMessageDismissed,
)

private fun textActionsOf(viewModel: EditorViewModel) = TextEditorActions(
    onBack = viewModel::onBackPressed,
    onCopy = viewModel::onCopyClicked,
    onCopyErrorDismissed = viewModel::onCopyErrorDismissed,
    onAddText = viewModel::onAddTextClicked,
    onEditingTextChanged = viewModel::onEditingTextChanged,
    onEditingConfirmed = viewModel::onEditingConfirmed,
    onEditingDismissed = viewModel::onEditingDismissed,
    onItemTapped = viewModel::onItemTapped,
    onCanvasTapped = viewModel::onCanvasTapped,
    onItemTransformed = viewModel::onItemTransformed,
    onItemReleased = viewModel::onItemReleased,
    onStyleToggled = viewModel::onStyleToggled,
    onFontToggled = viewModel::onFontToggled,
    onColorSelected = viewModel::onColorSelected,
    onOutlineClicked = viewModel::onOutlineClicked,
    onOutlineDialogDismissed = viewModel::onOutlineDialogDismissed,
    onOutlineConfirmed = viewModel::onOutlineConfirmed,
    onOutlineErrorDismissed = viewModel::onOutlineErrorDismissed,
)

@Composable
private fun SourceLauncher(source: EditorSource, viewModel: EditorViewModel) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) viewModel.onPickerCancelled() else viewModel.onImagePicked(SourceUri(uri.toString()))
    }

    val launchSource: () -> Unit = {
        try {
            when (source) {
                EditorSource.PhotoPicker ->
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                is EditorSource.ImageUri -> viewModel.onImagePicked(SourceUri(source.uri.toString()))
            }
        } catch (_: ActivityNotFoundException) {
            viewModel.onPickerUnavailable()
        }
    }
    val currentLaunch by rememberUpdatedState(launchSource)

    // Saved: after recreation the ViewModel restores the work itself, the picker must not open again.
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(source) {
        if (launched) return@LaunchedEffect
        launched = true
        currentLaunch()
    }
    // After a failed restore the user chose to start again.
    LaunchedEffect(viewModel) {
        viewModel.events.filterIsInstance<EditorEvent.LaunchPicker>().collect { currentLaunch() }
    }
}

@Composable
private fun EditorContent(
    state: EditorUiState,
    painter: TextItemPainter,
    snackbarHostState: SnackbarHostState,
    cuttingActions: CuttingActions,
    textActions: TextEditorActions,
    onRetryLoad: () -> Unit,
    onPickAgain: () -> Unit,
    onClose: () -> Unit,
) {
    when (state) {
        EditorUiState.WaitingForPicker -> CenteredProgress()
        EditorUiState.LoadingImage -> CenteredProgress()
        is EditorUiState.Failed -> FailureContent(state.reason, onRetry = onRetryLoad, onPickAgain = onPickAgain, onClose = onClose)
        is EditorUiState.Cutting -> CuttingContent(state.state, cuttingActions)
        is EditorUiState.Text -> TextEditorContent(state.state, painter, snackbarHostState, textActions)
    }
}

@Composable
private fun FailureContent(reason: EditorFailure, onRetry: () -> Unit, onPickAgain: () -> Unit, onClose: () -> Unit) {
    when (reason) {
        EditorFailure.IMAGE_UNREADABLE -> CenteredMessage(
            title = stringResource(R.string.editor_failure_image_title),
            body = stringResource(R.string.editor_failure_image_body),
            actionLabel = stringResource(R.string.editor_try_again),
            onAction = onRetry,
        )
        EditorFailure.WORK_LOST -> CenteredMessage(
            title = stringResource(R.string.editor_failure_lost_title),
            body = stringResource(R.string.editor_failure_lost_body),
            actionLabel = stringResource(R.string.editor_pick_again),
            onAction = onPickAgain,
        )
        EditorFailure.NO_PICKER_AVAILABLE -> CenteredMessage(
            title = stringResource(R.string.editor_failure_picker_title),
            body = stringResource(R.string.editor_failure_picker_body),
            actionLabel = stringResource(R.string.editor_close),
            onAction = onClose,
        )
    }
}
