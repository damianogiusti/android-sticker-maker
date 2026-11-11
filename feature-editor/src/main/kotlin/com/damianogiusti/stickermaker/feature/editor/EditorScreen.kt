package com.damianogiusti.stickermaker.feature.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.damianogiusti.stickermaker.feature.editor.cutting.CuttingActions
import com.damianogiusti.stickermaker.feature.editor.cutting.CuttingContent
import com.damianogiusti.stickermaker.feature.editor.text.TextEditorActions
import com.damianogiusti.stickermaker.feature.editor.text.TextEditorContent
import com.damianogiusti.stickermaker.imaging.source.SourceUri
import com.damianogiusti.stickermaker.imaging.text.TextItemPainter
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress
import java.io.File

/**
 * The whole editor: opens the picker for [source], then the cutting screen and the text screen,
 * and saves into the pack according to [target]. Needs `editorModule` in Koin.
 */
@Composable
fun EditorScreen(
    packId: String,
    source: EditorSource,
    target: EditorTarget,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel: EditorViewModel = koinViewModel(parameters = { parametersOf(packId, target) })
    val painter: TextItemPainter = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Done -> onDone()
                EditorEvent.Cancelled -> onCancel()
                EditorEvent.LaunchPicker -> Unit
            }
        }
    }
    BackHandler(onBack = viewModel::onBackPressed)

    SourceLauncher(source = source, viewModel = viewModel)

    EditorContent(
        state = state,
        painter = painter,
        cuttingActions = remember(viewModel) { cuttingActionsOf(viewModel) },
        textActions = remember(viewModel) { textActionsOf(viewModel) },
        onRetryLoad = viewModel::onRetryLoad,
        onPickAgain = viewModel::onPickAgainClicked,
        onClose = onCancel,
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
    onSave = viewModel::onSaveClicked,
    onSaveErrorDismissed = viewModel::onSaveErrorDismissed,
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
    val context = LocalContext.current
    val onResult: (Uri?) -> Unit = { uri ->
        if (uri == null) viewModel.onPickerCancelled() else viewModel.onImagePicked(SourceUri(uri.toString()))
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onResult)
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onResult)
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) viewModel.onImagePicked(SourceUri(uri)) else viewModel.onPickerCancelled()
    }

    val launchSource: () -> Unit = {
        try {
            when (source) {
                EditorSource.Camera -> {
                    val uri = newCameraUri(context)
                    cameraUri = uri.toString()
                    camera.launch(uri)
                }
                EditorSource.Gallery ->
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                EditorSource.Files -> documentPicker.launch(arrayOf(IMAGE_MIME_TYPES))
                EditorSource.Text -> viewModel.onTextSourceSelected()
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

/** A fresh file in the cache, shared with the camera app through [EditorFileProvider]. */
private fun newCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
    val file = File(directory, CAMERA_FILE_NAME).also { it.delete() }
    return FileProvider.getUriForFile(context, "${context.packageName}.editor.fileprovider", file)
}

@Composable
private fun EditorContent(
    state: EditorUiState,
    painter: TextItemPainter,
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
        is EditorUiState.Text -> TextEditorContent(state.state, painter, textActions)
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

private const val IMAGE_MIME_TYPES = "image/*"
private const val CAMERA_DIRECTORY = "editor"
private const val CAMERA_FILE_NAME = "camera.jpg"
