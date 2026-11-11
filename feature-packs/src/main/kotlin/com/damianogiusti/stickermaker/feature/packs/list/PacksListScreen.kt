package com.damianogiusti.stickermaker.feature.packs.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferErrorDialog
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferKind
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferProgressDialog
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.stickermaker.feature.packs.PackImageView
import com.damianogiusti.stickermaker.feature.packs.R
import com.damianogiusti.stickermaker.feature.packs.form.PackFormDialog
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress

/**
 * Import, backup and restore use the system file picker (SAF) and are handled here. Keyboard setup
 * and About land in later work packages: while their callback is null the screen says "coming soon".
 */
@Composable
fun PacksListScreen(
    onOpenPack: (packId: String) -> Unit,
    modifier: Modifier = Modifier,
    onKeyboardSetup: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    viewModel: PacksListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    var isCreating by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFrom(it.toString()) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
        uri?.let { viewModel.backupTo(it.toString()) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreFrom(it.toString()) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshWhatsAppStatus() }
    LaunchedEffect(viewModel.importedPackEvents) { viewModel.importedPackEvents.collect(onOpenPack) }

    fun orComingSoon(action: (() -> Unit)?): () -> Unit = action ?: {
        scope.launch { snackbarHostState.showSnackbar(comingSoon) }
    }

    PacksListContent(
        state = state.body,
        snackbarHostState = snackbarHostState,
        onNewPack = { isCreating = true },
        onOpenPack = onOpenPack,
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        onBackup = { backupLauncher.launch(backupFileName()) },
        onRestore = { restoreLauncher.launch(arrayOf(ZIP_MIME)) },
        onKeyboardSetup = orComingSoon(onKeyboardSetup),
        onAbout = orComingSoon(onAbout),
        modifier = modifier,
    )

    TransferDialogs(state.transfer, onDismiss = viewModel::dismissTransferResult)

    if (isCreating) {
        PackFormDialog(
            packId = null,
            onDismiss = { isCreating = false },
            onSaved = { packId ->
                isCreating = false
                onOpenPack(packId)
            },
        )
    }
}

private const val ZIP_MIME = "application/zip"

private fun backupFileName(): String =
    "stickermaker-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".zip"

@Composable
private fun TransferDialogs(state: TransferUiState, onDismiss: () -> Unit) {
    when (state) {
        TransferUiState.Idle -> Unit
        is TransferUiState.InProgress -> TransferProgressDialog(state.kind)
        is TransferUiState.Failed -> TransferErrorDialog(state.error, onDismiss)
        is TransferUiState.Succeeded -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(
                        if (state.kind == TransferKind.BACKUP) R.string.transfer_backup_done_title else R.string.transfer_restore_done_title,
                    ),
                )
            },
            text = { Text(pluralStringResource(R.plurals.transfer_pack_count, state.packCount, state.packCount)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_ok)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PacksListContent(
    state: PacksListBody,
    snackbarHostState: SnackbarHostState,
    onNewPack: () -> Unit,
    onOpenPack: (String) -> Unit,
    onImport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onKeyboardSetup: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.packs_title)) },
                actions = {
                    PacksOverflowMenu(onImport, onBackup, onRestore, onKeyboardSetup, onAbout)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewPack) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.packs_new_pack),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PacksListBody.Loading -> CenteredProgress()
                PacksListBody.Empty -> CenteredMessage(
                    title = stringResource(R.string.packs_empty_title),
                    body = stringResource(R.string.packs_empty_body),
                )
                PacksListBody.Error -> CenteredMessage(
                    title = stringResource(R.string.packs_error_title),
                    body = stringResource(R.string.packs_error_body),
                )
                is PacksListBody.Content -> PacksList(state.packs, onOpenPack)
            }
        }
    }
}

@Composable
private fun PacksOverflowMenu(
    onImport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onKeyboardSetup: () -> Unit,
    onAbout: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { isExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
    }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        MenuEntry(R.string.menu_import, onImport) { isExpanded = false }
        MenuEntry(R.string.menu_backup, onBackup) { isExpanded = false }
        MenuEntry(R.string.menu_restore, onRestore) { isExpanded = false }
        MenuEntry(R.string.menu_keyboard_setup, onKeyboardSetup) { isExpanded = false }
        MenuEntry(R.string.menu_about, onAbout) { isExpanded = false }
    }
}

@Composable
private fun MenuEntry(labelRes: Int, onClick: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

@Composable
private fun PacksList(packs: List<PackListItem>, onOpenPack: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(packs, key = { it.id }) { pack ->
            ListItem(
                modifier = Modifier.clickable { onOpenPack(pack.id) },
                leadingContent = { TrayThumbnail(pack) },
                headlineContent = { Text(pack.name) },
                supportingContent = {
                    Column {
                        Text(pack.publisher)
                        if (pack.isInWhatsApp) InWhatsAppBadge()
                        Text(
                            pluralStringResource(R.plurals.sticker_count, pack.stickerCount, pack.stickerCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun TrayThumbnail(pack: PackListItem) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (pack.tray != null) {
            PackImageView(pack.tray, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
        }
    }
}

@Composable
private fun InWhatsAppBadge() {
    Text(
        stringResource(R.string.badge_in_whatsapp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
