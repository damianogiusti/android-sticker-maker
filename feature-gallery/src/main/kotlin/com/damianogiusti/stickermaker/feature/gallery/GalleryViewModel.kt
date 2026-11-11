package com.damianogiusti.stickermaker.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.ui.coroutines.SingleEventFlow

enum class GalleryTab { WHATSAPP, MY_STICKERS }

enum class GalleryMessage { GRANT_FAILED, COPY_FAILED }

sealed interface WhatsAppUiState {
    data object Loading : WhatsAppUiState
    data object PermissionNeeded : WhatsAppUiState
    data object Empty : WhatsAppUiState
    data object Error : WhatsAppUiState
    /** [animatedByUri] only has the stickers inspected so far: detection is lazy, per visible item. */
    data class Content(
        val stickers: List<WhatsAppStickerFile>,
        val animatedByUri: Map<String, Boolean> = emptyMap(),
    ) : WhatsAppUiState {
        val hasAnimated: Boolean get() = animatedByUri.values.any { it }
        fun isAnimated(file: WhatsAppStickerFile): Boolean = animatedByUri[file.uriString] == true
    }
}

data class OwnSticker(val packId: String, val fileName: String, val version: Int)

data class OwnStickerGroup(val packId: String, val packName: String, val stickers: List<OwnSticker>)

sealed interface MyStickersUiState {
    data object Loading : MyStickersUiState
    data object Empty : MyStickersUiState
    data object Error : MyStickersUiState
    data class Content(val groups: List<OwnStickerGroup>) : MyStickersUiState
}

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.WHATSAPP,
    val source: WhatsAppSource = WhatsAppSource.WHATSAPP,
    val whatsApp: WhatsAppUiState = WhatsAppUiState.Loading,
    val myStickers: MyStickersUiState = MyStickersUiState.Loading,
    val message: GalleryMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    observePacks: ObserveStickerPacksUseCase,
    private val grantStore: TreeGrantStore,
    private val folderReader: WhatsAppFolderReader,
    private val ownStickerCopier: OwnStickerCopier,
    private val animationDetector: AnimationDetector,
) : ViewModel() {

    private val tab = MutableStateFlow(GalleryTab.WHATSAPP)
    private val source = MutableStateFlow(WhatsAppSource.WHATSAPP)
    private val reloadCount = MutableStateFlow(0)
    private val message = MutableStateFlow<GalleryMessage?>(null)
    private val animatedByUri = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val inspected = mutableSetOf<String>()

    private val _pickedImages = SingleEventFlow<String>()

    /** URI string of the image the user chose; the host decides what to do with it. */
    val pickedImages: SharedFlow<String> = _pickedImages.asSharedFlow()

    private val whatsApp: Flow<WhatsAppUiState> = combine(source, reloadCount) { current, _ -> current }
        .flatMapLatest { current -> loadWhatsApp(current) }

    private val myStickers: Flow<MyStickersUiState> = observePacks().map { result ->
        result.fold(
            ifLeft = { MyStickersUiState.Error },
            ifRight = { packs ->
                val groups = packs
                    .map { pack ->
                        OwnStickerGroup(
                            packId = pack.id.value,
                            packName = pack.name,
                            stickers = pack.stickers.sortedBy { it.position }
                                .map { OwnSticker(pack.id.value, it.fileName, pack.imageDataVersion) },
                        )
                    }
                    .filter { it.stickers.isNotEmpty() }
                if (groups.isEmpty()) MyStickersUiState.Empty else MyStickersUiState.Content(groups)
            },
        )
    }

    private val whatsAppWithAnimation: Flow<WhatsAppUiState> = combine(whatsApp, animatedByUri) { content, animated ->
        if (content is WhatsAppUiState.Content) content.copy(animatedByUri = animated) else content
    }

    val state: StateFlow<GalleryUiState> = combine(tab, source, whatsAppWithAnimation, myStickers, message) { tab, source, whatsApp, mine, message ->
        GalleryUiState(tab, source, whatsApp, mine, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun selectTab(newTab: GalleryTab) {
        tab.value = newTab
    }

    fun selectSource(newSource: WhatsAppSource) {
        source.value = newSource
    }

    /** The system folder picker returned [treeUri] for the selected WhatsApp app. */
    fun onTreeGranted(treeUri: String) {
        val current = source.value
        grantStore.save(current, treeUri).fold(
            ifLeft = { message.value = GalleryMessage.GRANT_FAILED },
            ifRight = { reloadCount.value += 1 },
        )
    }

    fun reload() {
        reloadCount.value += 1
    }

    /** A grid item came on screen: look at its header once, the answer is cached by the detector. */
    fun onWhatsAppStickerVisible(sticker: WhatsAppStickerFile) {
        if (!inspected.add(sticker.uriString)) return
        viewModelScope.launch {
            val animated = animationDetector.isAnimated(sticker)
            animatedByUri.value = animatedByUri.value + (sticker.uriString to animated)
        }
    }

    fun onWhatsAppStickerPicked(sticker: WhatsAppStickerFile) {
        viewModelScope.launch {
            if (animationDetector.isAnimated(sticker)) return@launch
            _pickedImages.tryEmit(sticker.uriString)
        }
    }

    fun onOwnStickerPicked(sticker: OwnSticker) {
        viewModelScope.launch {
            ownStickerCopier.copyToCache(sticker.packId, sticker.fileName).fold(
                ifLeft = { message.value = GalleryMessage.COPY_FAILED },
                ifRight = { _pickedImages.tryEmit(it) },
            )
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun loadWhatsApp(forSource: WhatsAppSource): Flow<WhatsAppUiState> = flow {
        emit(WhatsAppUiState.Loading)
        val treeUri = grantStore.treeUri(forSource)
        if (treeUri == null) {
            emit(WhatsAppUiState.PermissionNeeded)
            return@flow
        }
        emit(
            folderReader.list(treeUri).fold(
                ifLeft = { error ->
                    when (error) {
                        GalleryError.TreeUnavailable -> WhatsAppUiState.PermissionNeeded
                        is GalleryError.Io -> WhatsAppUiState.Error
                    }
                },
                ifRight = { files ->
                    if (files.isEmpty()) WhatsAppUiState.Empty else WhatsAppUiState.Content(files.sortedByDescending { it.lastModified })
                },
            ),
        )
    }
}
