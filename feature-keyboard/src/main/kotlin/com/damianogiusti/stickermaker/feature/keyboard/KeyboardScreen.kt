package com.damianogiusti.stickermaker.feature.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.stickermaker.ui.components.CenteredMessage
import com.damianogiusti.stickermaker.ui.components.CenteredProgress

private val KeyboardHeight = 300.dp
private const val GRID_COLUMNS = 4

@Composable
internal fun KeyboardScreen(
    locator: StickerFileLocator,
    onSend: suspend (KeyboardSticker) -> SendOutcome,
    onSwitchKeyboard: () -> Unit,
    viewModel: KeyboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The scope of the IME view tree: a pending send is dropped with the keyboard.
    val scope = rememberCoroutineScope()
    KeyboardContent(
        state = state,
        locator = locator,
        onStickerClick = { sticker -> scope.launch { viewModel.onSendOutcome(sticker, onSend(sticker)) } },
        onDismissNotice = viewModel::dismissNotice,
        onSwitchKeyboard = onSwitchKeyboard,
    )
}

@Composable
internal fun KeyboardContent(
    state: KeyboardUiState,
    locator: StickerFileLocator,
    onStickerClick: (KeyboardSticker) -> Unit,
    onDismissNotice: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth().height(KeyboardHeight), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (state) {
                    KeyboardUiState.Loading -> CenteredProgress()
                    KeyboardUiState.NoPacks -> CenteredMessage(
                        title = stringResource(R.string.keyboard_empty_title),
                        body = stringResource(R.string.keyboard_empty_body),
                    )
                    KeyboardUiState.Error -> CenteredMessage(title = stringResource(R.string.keyboard_error))
                    is KeyboardUiState.Content -> TabsContent(state, locator, onStickerClick, onDismissNotice)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onSwitchKeyboard) { Text(stringResource(R.string.keyboard_switch)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabsContent(
    state: KeyboardUiState.Content,
    locator: StickerFileLocator,
    onStickerClick: (KeyboardSticker) -> Unit,
    onDismissNotice: () -> Unit,
) {
    val pagerState = rememberPagerState { state.tabs.size }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
            state.tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { TabTitle(tab, locator) },
                )
            }
        }
        state.notice?.let { notice ->
            Text(
                text = stringResource(
                    if (notice == KeyboardNotice.NOT_ACCEPTED) R.string.keyboard_not_accepted else R.string.keyboard_send_failed,
                ),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(onClick = onDismissNotice)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            StickerGrid(state.tabs[page], locator, onStickerClick)
        }
    }
}

@Composable
private fun TabTitle(tab: KeyboardTab, locator: StickerFileLocator) {
    when (tab) {
        is KeyboardTab.History -> Text(stringResource(R.string.keyboard_tab_history))
        is KeyboardTab.Pack -> {
            val tray = tab.trayFileName
            if (tray == null) {
                Text(tab.name, maxLines = 1)
            } else {
                StickerImage(
                    file = locator.locate(tab.packId, tray),
                    version = tab.version,
                    contentDescription = tab.name,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun StickerGrid(tab: KeyboardTab, locator: StickerFileLocator, onStickerClick: (KeyboardSticker) -> Unit) {
    if (tab.stickers.isEmpty()) {
        CenteredMessage(title = stringResource(R.string.keyboard_history_empty))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tab.stickers, key = { "${it.packId}/${it.fileName}" }) { sticker ->
            StickerImage(
                file = locator.locate(sticker.packId, sticker.fileName),
                version = sticker.version,
                contentDescription = stringResource(R.string.keyboard_sticker_description),
                modifier = Modifier.aspectRatio(1f).clickable { onStickerClick(sticker) },
            )
        }
    }
}

@Composable
private fun StickerImage(file: java.io.File, version: Int, contentDescription: String?, modifier: Modifier) {
    val context = LocalContext.current
    val request = androidx.compose.runtime.remember(file, version) {
        val cacheKey = "${file.absolutePath}#$version"
        ImageRequest.Builder(context).data(file).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
    )
}
