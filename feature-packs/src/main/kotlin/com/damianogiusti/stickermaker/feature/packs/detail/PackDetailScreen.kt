package com.damianogiusti.stickermaker.feature.packs.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.damianogiusti.stickermaker.feature.packs.transfer.messageRes
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.model.StickerSource
import com.damianogiusti.stickermaker.domain.model.StickerTarget
import com.damianogiusti.stickermaker.feature.packs.PackImageView
import com.damianogiusti.stickermaker.feature.packs.R
import com.damianogiusti.stickermaker.feature.packs.form.PackFormDialog
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress

private const val GRID_COLUMNS = 4

/** Bottom sheets and dialogs are UI-only state: they do not outlive the screen and carry no logic. */
private sealed interface Overlay {
    data class Sources(val target: StickerTarget) : Overlay
    data class StickerActions(val fileName: String) : Overlay
    data object Rename : Overlay
    data object ConfirmDeletePack : Overlay
    data class ConfirmDeleteSticker(val fileName: String) : Overlay
}

@Composable
fun PackDetailScreen(
    packId: String,
    onBack: () -> Unit,
    onCreateSticker: (packId: String, source: StickerSource, target: StickerTarget) -> Unit,
    onEditEmojis: (packId: String, fileName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PackDetailViewModel = koinViewModel { parametersOf(packId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = stringResource(R.string.detail_action_failed)
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onAddToWhatsAppResult(result.toAddToWhatsAppResult())
    }
    val messageTexts = PackDetailMessageTexts.current()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshWhatsAppStatus() }
    LaunchedEffect(viewModel.deletedEvents) { viewModel.deletedEvents.collect { onBack() } }
    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(messageTexts.textOf(it)) }
    }
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.transfer_share_chooser)
    LaunchedEffect(viewModel.shareEvents) {
        viewModel.shareEvents.collect { share ->
            try {
                context.startActivity(shareIntent(context, share, shareChooserTitle))
            } catch (_: ActivityNotFoundException) {
                snackbarHostState.showSnackbar(actionFailed)
            }
        }
    }
    LaunchedEffect(viewModel.launchEvents) {
        viewModel.launchEvents.collect { launch ->
            try {
                addLauncher.launch(launch.request.toIntent(launch.app))
            } catch (_: ActivityNotFoundException) {
                // WhatsApp was removed or disabled between the check and the launch.
                snackbarHostState.showSnackbar(actionFailed)
            }
        }
    }


    PackDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTrayClicked = { overlay = Overlay.Sources(StickerTarget.Tray) },
        onEmptySlotClicked = { overlay = Overlay.Sources(StickerTarget.NewSticker) },
        onStickerClicked = { overlay = Overlay.StickerActions(it) },
        onRename = { overlay = Overlay.Rename },
        onDeletePack = { overlay = Overlay.ConfirmDeletePack },
        onKeyboardAvailabilityChange = viewModel::setAvailableInKeyboard,
        onAddToWhatsApp = viewModel::addToWhatsApp,
        onIssueClicked = { fileName -> onEditEmojis(packId, fileName) },
        onShareWastickers = viewModel::sharePack,
        modifier = modifier,
    )

    (state as? PackDetailUiState.Content)?.pack?.addDialog?.let { dialog ->
        AddToWhatsAppDialogHost(dialog, viewModel::chooseWhatsAppApp, viewModel::dismissAddDialog)
    }

    OverlayHost(
        overlay = overlay,
        packId = packId,
        onDismiss = { overlay = null },
        onShowOverlay = { overlay = it },
        onSourceSelected = { source, target ->
            overlay = null
            onCreateSticker(packId, source, target)
        },
        onEditEmojis = { fileName ->
            overlay = null
            onEditEmojis(packId, fileName)
        },
        onConfirmDeletePack = {
            overlay = null
            viewModel.deletePack()
        },
        onConfirmDeleteSticker = { fileName ->
            overlay = null
            viewModel.deleteSticker(fileName)
        },
    )
}

@Composable
private fun OverlayHost(
    overlay: Overlay?,
    packId: String,
    onDismiss: () -> Unit,
    onShowOverlay: (Overlay) -> Unit,
    onSourceSelected: (StickerSource, StickerTarget) -> Unit,
    onEditEmojis: (String) -> Unit,
    onConfirmDeletePack: () -> Unit,
    onConfirmDeleteSticker: (String) -> Unit,
) {
    when (overlay) {
        null -> Unit
        is Overlay.Sources -> SourcesSheet(onDismiss) { source -> onSourceSelected(source, overlay.target) }
        is Overlay.StickerActions -> StickerActionsSheet(
            onDismiss = onDismiss,
            onEditEmojis = { onEditEmojis(overlay.fileName) },
            onReplace = { onShowOverlay(Overlay.Sources(StickerTarget.ReplaceSticker(overlay.fileName))) },
            onDelete = { onShowOverlay(Overlay.ConfirmDeleteSticker(overlay.fileName)) },
        )
        Overlay.Rename -> PackFormDialog(packId = packId, onDismiss = onDismiss, onSaved = { onDismiss() })
        Overlay.ConfirmDeletePack -> ConfirmDialog(
            title = R.string.delete_pack_title,
            message = R.string.delete_pack_message,
            onConfirm = onConfirmDeletePack,
            onDismiss = onDismiss,
        )
        is Overlay.ConfirmDeleteSticker -> ConfirmDialog(
            title = R.string.delete_sticker_title,
            message = R.string.delete_sticker_message,
            onConfirm = { onConfirmDeleteSticker(overlay.fileName) },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PackDetailContent(
    state: PackDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTrayClicked: () -> Unit,
    onEmptySlotClicked: () -> Unit,
    onStickerClicked: (String) -> Unit,
    onRename: () -> Unit,
    onDeletePack: () -> Unit,
    onKeyboardAvailabilityChange: (Boolean) -> Unit,
    onAddToWhatsApp: () -> Unit,
    onIssueClicked: (fileName: String) -> Unit,
    onShareWastickers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text((state as? PackDetailUiState.Content)?.pack?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state is PackDetailUiState.Content) DetailOverflowMenu(onRename, onDeletePack)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PackDetailUiState.Loading -> CenteredProgress()
                PackDetailUiState.NotFound -> CenteredMessage(
                    title = stringResource(R.string.detail_not_found),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )
                PackDetailUiState.Error -> CenteredMessage(
                    title = stringResource(R.string.detail_error_title),
                    body = stringResource(R.string.detail_error_body),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )
                is PackDetailUiState.Content -> PackGrid(
                    pack = state.pack,
                    onTrayClicked = onTrayClicked,
                    onEmptySlotClicked = onEmptySlotClicked,
                    onStickerClicked = onStickerClicked,
                    onKeyboardAvailabilityChange = onKeyboardAvailabilityChange,
                    onAddToWhatsApp = onAddToWhatsApp,
                    onIssueClicked = onIssueClicked,
                    onShareWastickers = onShareWastickers,
                )
            }
        }
    }
}

@Composable
private fun DetailOverflowMenu(onRename: () -> Unit, onDeletePack: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { isExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
    }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            onClick = {
                isExpanded = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete_pack)) },
            onClick = {
                isExpanded = false
                onDeletePack()
            },
        )
    }
}

@Composable
private fun PackGrid(
    pack: PackDetailContent,
    onTrayClicked: () -> Unit,
    onEmptySlotClicked: () -> Unit,
    onStickerClicked: (String) -> Unit,
    onKeyboardAvailabilityChange: (Boolean) -> Unit,
    onAddToWhatsApp: () -> Unit,
    onIssueClicked: (fileName: String) -> Unit,
    onShareWastickers: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth { TrayHeader(pack, onTrayClicked) }
        fullWidth { KeyboardSwitchRow(pack.isAvailableInKeyboard, onKeyboardAvailabilityChange) }
        if (pack.isInWhatsApp) fullWidth { InWhatsAppStatus() }
        fullWidth { ValidationSection(pack.validation, onIssueClicked) }
        fullWidth {
            ActionButtons(
                canAdd = pack.validation is PackValidation.Valid,
                isInWhatsApp = pack.isInWhatsApp,
                isPreparingShare = pack.isPreparingShare,
                onAddToWhatsApp = onAddToWhatsApp,
                onShareWastickers = onShareWastickers,
            )
        }
        fullWidth {
            Text(
                stringResource(R.string.detail_stickers_header, pack.stickerCount, StickerPackLimits.MAX_STICKERS),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        itemsIndexed(pack.slots, key = { index, slot -> (slot as? StickerSlot.Filled)?.fileName ?: "empty-$index" }) { _, slot ->
            when (slot) {
                is StickerSlot.Filled -> FilledSlot(slot, onClick = { onStickerClicked(slot.fileName) })
                StickerSlot.Empty -> EmptySlot(onClick = onEmptySlotClicked)
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun TrayHeader(pack: PackDetailContent, onTrayClicked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(SlotShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onTrayClicked),
            contentAlignment = Alignment.Center,
        ) {
            if (pack.tray != null) {
                PackImageView(pack.tray, stringResource(R.string.detail_tray), Modifier.fillMaxSize().padding(8.dp))
            } else {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.detail_tray_add))
            }
        }
        Column {
            Text(pack.publisher, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.detail_tray),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyboardSwitchRow(isAvailable: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.detail_available_in_keyboard), modifier = Modifier.weight(1f))
        Switch(checked = isAvailable, onCheckedChange = onChange)
    }
}

@Composable
private fun ValidationSection(validation: PackValidation, onIssueClicked: (fileName: String) -> Unit) {
    when (validation) {
        PackValidation.Valid -> Text(
            stringResource(R.string.validation_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        PackValidation.Unavailable -> Text(
            stringResource(R.string.validation_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        is PackValidation.Invalid -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.validation_title), style = MaterialTheme.typography.titleSmall)
            validation.issues.forEach { issue -> IssueRow(issue, onIssueClicked) }
        }
    }
}

/** Emoji issues are actionable: a tap opens the emoji picker for that sticker. */
@Composable
private fun IssueRow(issue: PackIssue, onIssueClicked: (fileName: String) -> Unit) {
    val emojiFileName = issue.emojiFileName
    val text = stringResource(R.string.validation_bullet, issue.text())
    if (emojiFileName == null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.issue_action_choose_emojis)) { onIssueClicked(emojiFileName) },
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(
            stringResource(R.string.issue_action_choose_emojis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InWhatsAppStatus() {
    Text(
        stringResource(R.string.whatsapp_added),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ActionButtons(
    canAdd: Boolean,
    isInWhatsApp: Boolean,
    isPreparingShare: Boolean,
    onAddToWhatsApp: () -> Unit,
    onShareWastickers: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onAddToWhatsApp, enabled = canAdd, modifier = Modifier.weight(1f)) {
            Text(
                stringResource(if (isInWhatsApp) R.string.action_update_in_whatsapp else R.string.action_add_to_whatsapp),
            )
        }
        OutlinedButton(onClick = onShareWastickers, enabled = !isPreparingShare, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (isPreparingShare) R.string.transfer_share_preparing else R.string.action_share))
        }
    }
}

private val SlotShape = RoundedCornerShape(12.dp)

@Composable
private fun FilledSlot(slot: StickerSlot.Filled, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        shape = SlotShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            PackImageView(slot.image, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
            if (slot.emojis.isNotEmpty()) {
                Text(slot.emojis.joinToString(""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        shape = SlotShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.detail_slot_add),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(onDismiss: () -> Unit, onSourceSelected: (StickerSource) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            SheetEntry(R.string.source_camera) { onSourceSelected(StickerSource.CAMERA) }
            SheetEntry(R.string.source_gallery) { onSourceSelected(StickerSource.GALLERY) }
            SheetEntry(R.string.source_files) { onSourceSelected(StickerSource.FILES) }
            SheetEntry(R.string.source_text) { onSourceSelected(StickerSource.TEXT) }
            SheetEntry(R.string.source_whatsapp_stickers) { onSourceSelected(StickerSource.WHATSAPP_STICKERS) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerActionsSheet(
    onDismiss: () -> Unit,
    onEditEmojis: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            SheetEntry(R.string.sticker_edit_emojis, onEditEmojis)
            SheetEntry(R.string.sticker_replace, onReplace)
            SheetEntry(R.string.sticker_delete, onDelete)
        }
    }
}

@Composable
private fun SheetEntry(labelRes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ConfirmDialog(title: Int, message: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AddToWhatsAppDialogHost(
    dialog: AddToWhatsAppDialog,
    onAppChosen: (WhatsAppApp) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        AddToWhatsAppDialog.Hidden -> Unit
        AddToWhatsAppDialog.NoWhatsAppInstalled -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.whatsapp_missing_title)) },
            text = { Text(stringResource(R.string.whatsapp_missing_message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
        is AddToWhatsAppDialog.ChooseApp -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.whatsapp_choose_title)) },
            text = {
                Column {
                    dialog.apps.forEach { app ->
                        ListItem(
                            headlineContent = { Text(stringResource(app.labelRes())) },
                            modifier = Modifier.clickable { onAppChosen(app) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Authority declared in the app manifest: `${applicationId}.share.fileprovider`. */
private fun shareIntent(context: Context, share: ShareFile, chooserTitle: String): Intent {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share.fileprovider", File(share.location))
    return ShareCompat.IntentBuilder(context)
        .setType(WASTICKERS_MIME)
        .setStream(uri)
        .setChooserTitle(chooserTitle)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private const val WASTICKERS_MIME = "application/x-wastickers"

private fun WhatsAppApp.labelRes(): Int = when (this) {
    WhatsAppApp.WHATSAPP -> R.string.whatsapp_app_whatsapp
    WhatsAppApp.BUSINESS -> R.string.whatsapp_app_business
}

/** Resolved in composition so the collector (a coroutine) can turn a message into text. */
private class PackDetailMessageTexts(
    private val actionFailed: String,
    private val packNotReady: String,
    private val added: String,
    private val alreadyAdded: String,
    private val checkWhatsApp: String,
    private val rejectedGeneric: String,
    private val rejectedWith: (String) -> String,
    private val text: (Int) -> String,
) {
    fun textOf(message: PackDetailMessage): String = when (message) {
        PackDetailMessage.ActionFailed -> actionFailed
        PackDetailMessage.PackNotReady -> packNotReady
        PackDetailMessage.AddedToWhatsApp -> added
        PackDetailMessage.AlreadyInWhatsApp -> alreadyAdded
        PackDetailMessage.CheckWhatsApp -> checkWhatsApp
        is PackDetailMessage.ShareFailed -> text(message.error.messageRes())
        is PackDetailMessage.WhatsAppRejected -> message.detail?.let(rejectedWith) ?: rejectedGeneric
    }

    companion object {
        @Composable
        fun current(): PackDetailMessageTexts {
            val context = LocalContext.current
            val rejectedFormat = stringResource(R.string.whatsapp_rejected_detail, "%s")
            return PackDetailMessageTexts(
                actionFailed = stringResource(R.string.detail_action_failed),
                packNotReady = stringResource(R.string.detail_pack_not_ready),
                added = stringResource(R.string.whatsapp_added_message),
                alreadyAdded = stringResource(R.string.whatsapp_already_added_message),
                checkWhatsApp = stringResource(R.string.whatsapp_check_message),
                rejectedGeneric = stringResource(R.string.whatsapp_rejected),
                rejectedWith = { detail -> rejectedFormat.replace("%s", detail) },
                text = context::getString,
            )
        }
    }
}
