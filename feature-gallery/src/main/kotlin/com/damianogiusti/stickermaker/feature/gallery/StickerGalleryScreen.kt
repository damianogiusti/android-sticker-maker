package com.damianogiusti.stickermaker.feature.gallery

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress
import java.io.File

private const val GRID_COLUMNS = 3
private const val ANIMATED_ALPHA = 0.4f

/** [onImagePicked] receives a `content://` URI (WhatsApp folder) or a `file://` URI (copy in the cache). */
@Composable
fun StickerGalleryScreen(
    onImagePicked: (uriString: String) -> Unit,
    onBack: () -> Unit,
) {
    StickerGalleryScreen(onImagePicked, onBack, koinViewModel<GalleryViewModel>(), koinInject<GalleryFileLocator>())
}

@Composable
internal fun StickerGalleryScreen(
    onImagePicked: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GalleryViewModel,
    locator: GalleryFileLocator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.pickedImages) { viewModel.pickedImages.collect(onImagePicked) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onTreeGranted(uri.toString())
    }
    StickerGalleryContent(
        state = state,
        locator = locator,
        onBack = onBack,
        onTabSelected = viewModel::selectTab,
        onSourceSelected = viewModel::selectSource,
        onChooseFolder = { folderPicker.launch(initialFolderUri(state.source)) },
        onRetry = viewModel::reload,
        onWhatsAppStickerClick = viewModel::onWhatsAppStickerPicked,
        onWhatsAppStickerVisible = viewModel::onWhatsAppStickerVisible,
        onOwnStickerClick = viewModel::onOwnStickerPicked,
        onMessageShown = viewModel::dismissMessage,
    )
}

// A document URI: DocumentsUI on Android 17 ignores a bare tree URI here and opens the root.
private fun initialFolderUri(source: WhatsAppSource): Uri =
    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, source.documentId)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerGalleryContent(
    state: GalleryUiState,
    locator: GalleryFileLocator,
    onBack: () -> Unit,
    onTabSelected: (GalleryTab) -> Unit,
    onSourceSelected: (WhatsAppSource) -> Unit,
    onChooseFolder: () -> Unit,
    onRetry: () -> Unit,
    onWhatsAppStickerClick: (WhatsAppStickerFile) -> Unit,
    onWhatsAppStickerVisible: (WhatsAppStickerFile) -> Unit,
    onOwnStickerClick: (OwnSticker) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let {
        stringResource(
            if (it == GalleryMessage.GRANT_FAILED) R.string.gallery_message_grant_failed else R.string.gallery_message_copy_failed,
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.gallery_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == GalleryTab.WHATSAPP,
                    onClick = { onTabSelected(GalleryTab.WHATSAPP) },
                    text = { Text(stringResource(R.string.gallery_tab_whatsapp)) },
                )
                Tab(
                    selected = state.tab == GalleryTab.MY_STICKERS,
                    onClick = { onTabSelected(GalleryTab.MY_STICKERS) },
                    text = { Text(stringResource(R.string.gallery_tab_mine)) },
                )
            }
            when (state.tab) {
                GalleryTab.WHATSAPP -> WhatsAppTab(state, onSourceSelected, onChooseFolder, onRetry, onWhatsAppStickerClick, onWhatsAppStickerVisible)
                GalleryTab.MY_STICKERS -> MyStickersTab(state.myStickers, locator, onOwnStickerClick)
            }
        }
    }
}

@Composable
private fun WhatsAppTab(
    state: GalleryUiState,
    onSourceSelected: (WhatsAppSource) -> Unit,
    onChooseFolder: () -> Unit,
    onRetry: () -> Unit,
    onStickerClick: (WhatsAppStickerFile) -> Unit,
    onStickerVisible: (WhatsAppStickerFile) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WhatsAppSource.entries.forEach { source ->
                FilterChip(
                    selected = state.source == source,
                    onClick = { onSourceSelected(source) },
                    label = {
                        Text(
                            stringResource(
                                if (source == WhatsAppSource.WHATSAPP) R.string.gallery_source_whatsapp else R.string.gallery_source_business,
                            ),
                        )
                    },
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (val whatsApp = state.whatsApp) {
                WhatsAppUiState.Loading -> CenteredProgress()
                WhatsAppUiState.PermissionNeeded -> CenteredMessage(
                    title = stringResource(R.string.gallery_permission_title),
                    body = stringResource(R.string.gallery_permission_body),
                    actionLabel = stringResource(R.string.gallery_permission_action),
                    onAction = onChooseFolder,
                )
                WhatsAppUiState.Empty -> CenteredMessage(
                    title = stringResource(R.string.gallery_whatsapp_empty_title),
                    body = stringResource(R.string.gallery_whatsapp_empty_body),
                    actionLabel = stringResource(R.string.gallery_change_folder),
                    onAction = onChooseFolder,
                )
                WhatsAppUiState.Error -> CenteredMessage(
                    title = stringResource(R.string.gallery_whatsapp_error),
                    actionLabel = stringResource(R.string.gallery_retry),
                    onAction = onRetry,
                )
                is WhatsAppUiState.Content -> WhatsAppGrid(whatsApp, onChooseFolder, onStickerClick, onStickerVisible)
            }
        }
    }
}

@Composable
private fun WhatsAppGrid(
    content: WhatsAppUiState.Content,
    onChooseFolder: () -> Unit,
    onStickerClick: (WhatsAppStickerFile) -> Unit,
    onStickerVisible: (WhatsAppStickerFile) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (content.hasAnimated) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.gallery_animated_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(content.stickers, key = { it.uriString }) { sticker ->
            // Composed only when it scrolls into view, so only visible items are inspected.
            LaunchedEffect(sticker.uriString) { onStickerVisible(sticker) }
            val isAnimated = content.isAnimated(sticker)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .alpha(if (isAnimated) ANIMATED_ALPHA else 1f)
                    .clickable(enabled = !isAnimated) { onStickerClick(sticker) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                StickerImage(data = sticker.uriString, cacheKey = sticker.uriString, modifier = Modifier.fillMaxSize())
                if (isAnimated) {
                    Text(stringResource(R.string.gallery_animated_badge), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.gallery_change_folder)) }
        }
    }
}

@Composable
private fun MyStickersTab(
    state: MyStickersUiState,
    locator: GalleryFileLocator,
    onStickerClick: (OwnSticker) -> Unit,
) {
    when (state) {
        MyStickersUiState.Loading -> CenteredProgress()
        MyStickersUiState.Empty -> CenteredMessage(
            title = stringResource(R.string.gallery_mine_empty_title),
            body = stringResource(R.string.gallery_mine_empty_body),
        )
        MyStickersUiState.Error -> CenteredMessage(title = stringResource(R.string.gallery_mine_error))
        is MyStickersUiState.Content -> LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.packId}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(group.packName, style = MaterialTheme.typography.titleSmall)
                }
                items(group.stickers, key = { "${it.packId}/${it.fileName}" }) { sticker ->
                    val file: File = locator.locate(sticker.packId, sticker.fileName)
                    StickerImage(
                        data = file,
                        cacheKey = "${file.absolutePath}#${sticker.version}",
                        modifier = Modifier.aspectRatio(1f).clickable { onStickerClick(sticker) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerImage(data: Any, cacheKey: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(data, cacheKey) {
        ImageRequest.Builder(context).data(data).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).build()
    }
    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.gallery_sticker_description),
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
