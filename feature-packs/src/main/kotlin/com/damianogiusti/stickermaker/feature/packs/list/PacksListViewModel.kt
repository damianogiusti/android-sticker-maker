package com.damianogiusti.stickermaker.feature.packs.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import arrow.core.Either
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.usecase.CreateBackupUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetPacksInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.ImportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.RestoreBackupUseCase
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferKind
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferUiState
import com.damianogiusti.stickermaker.ui.coroutines.SingleEventFlow
import com.damianogiusti.stickermaker.ui.logging.ErrorLogger
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPacksUseCase
import com.damianogiusti.stickermaker.feature.packs.PackFileResolver
import com.damianogiusti.stickermaker.feature.packs.PackImage

data class PackListItem(
    val id: String,
    val name: String,
    val publisher: String,
    val stickerCount: Int,
    val tray: PackImage?,
    val isInWhatsApp: Boolean = false,
)

/** The list part of the screen. */
sealed interface PacksListBody {
    data object Loading : PacksListBody
    data object Empty : PacksListBody
    data object Error : PacksListBody
    data class Content(val packs: List<PackListItem>) : PacksListBody
}

/** One state for the whole screen: the list, and a transfer (import, backup, restore) running on top of it. */
data class PacksListUiState(
    val body: PacksListBody = PacksListBody.Loading,
    val transfer: TransferUiState = TransferUiState.Idle,
)

class PacksListViewModel(
    observePacks: ObserveStickerPacksUseCase,
    private val fileResolver: PackFileResolver,
    private val getPacksInWhatsApp: GetPacksInWhatsAppUseCase,
    private val importPack: ImportStickerPackUseCase,
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val errorLogger: ErrorLogger,
) : ViewModel() {

    private val transfer = MutableStateFlow<TransferUiState>(TransferUiState.Idle)

    private val _importedPackEvents = SingleEventFlow<String>()

    /** Fired with the id of a pack that was just imported, so the screen can open it. */
    val importedPackEvents = _importedPackEvents.asSharedFlow()

    private val packsInWhatsApp = MutableStateFlow<Set<StickerPackId>>(emptySet())

    val state: StateFlow<PacksListUiState> = combine(observePacks(), packsInWhatsApp, transfer) { result, inWhatsApp, transferState ->
        val body = result.fold(
            ifLeft = { PacksListBody.Error },
            ifRight = { packs ->
                if (packs.isEmpty()) {
                    PacksListBody.Empty
                } else {
                    PacksListBody.Content(packs.map { toItem(it, isInWhatsApp = it.id in inWhatsApp) })
                }
            },
        )
        PacksListUiState(body, transferState)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PacksListUiState())

    /** Asks WhatsApp which packs it has. On failure the last known badges stay: there is nothing to act on. */
    fun refreshWhatsAppStatus() {
        viewModelScope.launch {
            getPacksInWhatsApp().fold(
                // TODO(ux): surface? There is no UI state for "WhatsApp could not be asked".
                ifLeft = { errorLogger.log(TAG, "Could not ask WhatsApp which packs it has: $it") },
                ifRight = { packsInWhatsApp.value = it },
            )
        }
    }

    fun importFrom(location: String) = runTransfer(TransferKind.IMPORT) {
        importPack(location).map { pack ->
            _importedPackEvents.tryEmit(pack.id.value)
            0
        }
    }

    fun backupTo(location: String) = runTransfer(TransferKind.BACKUP) { createBackup(location).map { it.packCount } }

    fun restoreFrom(location: String) = runTransfer(TransferKind.RESTORE) { restoreBackup(location).map { it.packCount } }

    fun dismissTransferResult() {
        if (transfer.value !is TransferUiState.InProgress) transfer.value = TransferUiState.Idle
    }

    /** One transfer at a time: a second request while one runs is ignored. */
    private fun runTransfer(kind: TransferKind, action: suspend () -> Either<TransferError, Int>) {
        if (transfer.value is TransferUiState.InProgress) return
        transfer.value = TransferUiState.InProgress(kind)
        viewModelScope.launch {
            transfer.value = action().fold(
                ifLeft = { TransferUiState.Failed(kind, it) },
                // An import opens the new pack instead of showing a result.
                ifRight = { count -> if (kind == TransferKind.IMPORT) TransferUiState.Idle else TransferUiState.Succeeded(kind, count) },
            )
        }
    }

    private fun toItem(pack: StickerPack, isInWhatsApp: Boolean) = PackListItem(
        id = pack.id.value,
        name = pack.name,
        publisher = pack.publisher,
        stickerCount = pack.stickers.size,
        tray = pack.trayFileName?.let { PackImage(fileResolver.resolve(pack.id.value, it), pack.imageDataVersion) },
        isInWhatsApp = isInWhatsApp,
    )

    private companion object {
        const val TAG = "PacksListViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
