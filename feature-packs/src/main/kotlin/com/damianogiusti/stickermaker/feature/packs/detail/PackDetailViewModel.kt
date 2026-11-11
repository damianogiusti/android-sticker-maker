package com.damianogiusti.stickermaker.feature.packs.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.PrepareAddToWhatsAppError
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppPlan
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppRequest
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppResult
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.StickerPackLimits
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.usecase.CheckPackInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.PrepareAddToWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.usecase.DeleteStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ExportStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.DeleteStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.ObserveStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetKeyboardAvailabilityUseCase
import com.damianogiusti.stickermaker.domain.usecase.ValidateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue
import com.damianogiusti.stickermaker.feature.packs.PackFileResolver
import com.damianogiusti.stickermaker.feature.packs.PackImage
import com.damianogiusti.stickermaker.ui.coroutines.SingleEventFlow
import com.damianogiusti.stickermaker.ui.logging.ErrorLogger

sealed interface StickerSlot {
    data class Filled(val fileName: String, val image: PackImage, val emojis: List<String>) : StickerSlot
    data object Empty : StickerSlot
}

/** [stickerNumber] is the 1-based slot of the sticker the issue is about, null for pack-level issues. */
data class PackIssue(val issue: ValidationIssue, val stickerNumber: Int?) {
    /** The sticker whose emojis the user can fix from this issue, null when the issue is not about emojis. */
    val emojiFileName: String?
        get() = when (issue) {
            is ValidationIssue.MissingEmoji -> issue.fileName
            is ValidationIssue.TooManyEmojis -> issue.fileName
            else -> null
        }
}

/** Whether the pack can be sent to WhatsApp. [Unavailable] = the check itself failed (not a finding). */
sealed interface PackValidation {
    data object Valid : PackValidation
    data class Invalid(val issues: List<PackIssue>) : PackValidation
    data object Unavailable : PackValidation
}

data class PackDetailContent(
    val packId: String,
    val name: String,
    val publisher: String,
    val tray: PackImage?,
    val slots: List<StickerSlot>,
    val isAvailableInKeyboard: Boolean,
    val validation: PackValidation,
    val isInWhatsApp: Boolean = false,
    val addDialog: AddToWhatsAppDialog = AddToWhatsAppDialog.Hidden,
    /** True while the `.wastickers` file is being written. */
    val isPreparingShare: Boolean = false,
) {
    val stickerCount: Int get() = slots.count { it is StickerSlot.Filled }
}

/** Dialogs of the add flow; they belong to the state because the flow has logic (which app, none installed). */
sealed interface AddToWhatsAppDialog {
    data object Hidden : AddToWhatsAppDialog
    data object NoWhatsAppInstalled : AddToWhatsAppDialog
    data class ChooseApp(val request: AddToWhatsAppRequest, val apps: List<WhatsAppApp>) : AddToWhatsAppDialog
}

/** Fired once: the screen shares the `.wastickers` file at [location] (a path in the share cache). */
data class ShareFile(val location: String, val fileName: String)

/** Fired once: the screen launches WhatsApp for [request] in [app]. */
data class AddToWhatsAppLaunch(val request: AddToWhatsAppRequest, val app: WhatsAppApp)

sealed interface PackDetailUiState {
    data object Loading : PackDetailUiState
    data object NotFound : PackDetailUiState
    data object Error : PackDetailUiState
    data class Content(val pack: PackDetailContent) : PackDetailUiState
}

sealed interface PackDetailMessage {
    data object ActionFailed : PackDetailMessage
    data object PackNotReady : PackDetailMessage
    data object AddedToWhatsApp : PackDetailMessage
    data object AlreadyInWhatsApp : PackDetailMessage

    /** WhatsApp closed with OK but no answer: neither "added" nor "cancelled" is true. */
    data object CheckWhatsApp : PackDetailMessage
    data class ShareFailed(val error: TransferError) : PackDetailMessage

    /** [detail] is the text WhatsApp gave, or null for the generic message. */
    data class WhatsAppRejected(val detail: String?) : PackDetailMessage
}

class PackDetailViewModel(
    private val packId: String,
    observePack: ObserveStickerPackUseCase,
    private val validatePack: ValidateStickerPackUseCase,
    private val deletePack: DeleteStickerPackUseCase,
    private val deleteSticker: DeleteStickerUseCase,
    private val setKeyboardAvailability: SetKeyboardAvailabilityUseCase,
    private val fileResolver: PackFileResolver,
    private val prepareAddToWhatsApp: PrepareAddToWhatsAppUseCase,
    private val checkPackInWhatsApp: CheckPackInWhatsAppUseCase,
    private val exportPack: ExportStickerPackUseCase,
    private val errorLogger: ErrorLogger,
) : ViewModel() {

    private val isInWhatsApp = MutableStateFlow(false)
    private val addDialog = MutableStateFlow<AddToWhatsAppDialog>(AddToWhatsAppDialog.Hidden)
    private val isPreparingShare = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val packState = observePack(StickerPackId(packId))
        .mapLatest { result ->
            result.fold(
                ifLeft = { error ->
                    if (error is StickerPackError.NotFound) PackDetailUiState.NotFound else PackDetailUiState.Error
                },
                ifRight = { pack -> PackDetailUiState.Content(toContent(pack, validate(pack.id))) },
            )
        }

    val state: StateFlow<PackDetailUiState> = combine(packState, isInWhatsApp, addDialog, isPreparingShare) { pack, inWhatsApp, dialog, preparing ->
        if (pack is PackDetailUiState.Content) {
            PackDetailUiState.Content(pack.pack.copy(isInWhatsApp = inWhatsApp, addDialog = dialog, isPreparingShare = preparing))
        } else {
            pack
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PackDetailUiState.Loading)

    private val _launchEvents = SingleEventFlow<AddToWhatsAppLaunch>()
    val launchEvents = _launchEvents.asSharedFlow()

    private val _shareEvents = SingleEventFlow<ShareFile>()
    val shareEvents = _shareEvents.asSharedFlow()

    private val _deletedEvents = SingleEventFlow<Unit>()
    val deletedEvents = _deletedEvents.asSharedFlow()

    private val _messages = SingleEventFlow<PackDetailMessage>()
    val messages = _messages.asSharedFlow()

    /** Writes the pack to a `.wastickers` file; the screen then opens the share sheet. */
    fun sharePack() {
        if (isPreparingShare.value) return
        isPreparingShare.value = true
        viewModelScope.launch {
            exportPack(StickerPackId(packId)).fold(
                ifLeft = { _messages.tryEmit(PackDetailMessage.ShareFailed(it)) },
                ifRight = { _shareEvents.tryEmit(ShareFile(it.location, it.fileName)) },
            )
            isPreparingShare.value = false
        }
    }

    fun deletePack() {
        viewModelScope.launch {
            deletePack(StickerPackId(packId)).fold(
                ifLeft = { _messages.tryEmit(PackDetailMessage.ActionFailed) },
                ifRight = { _deletedEvents.tryEmit(Unit) },
            )
        }
    }

    fun deleteSticker(fileName: String) {
        viewModelScope.launch {
            deleteSticker(StickerPackId(packId), fileName).onLeft { _messages.tryEmit(PackDetailMessage.ActionFailed) }
        }
    }

    fun setAvailableInKeyboard(isAvailable: Boolean) {
        viewModelScope.launch {
            setKeyboardAvailability(StickerPackId(packId), isAvailable)
                .onLeft { _messages.tryEmit(PackDetailMessage.ActionFailed) }
        }
    }

    /** Asks WhatsApp whether it has the pack. On failure the last known answer stays: nothing to tell the user. */
    fun refreshWhatsAppStatus() {
        viewModelScope.launch {
            checkPackInWhatsApp(StickerPackId(packId)).fold(
                // TODO(ux): surface? There is no UI state for "WhatsApp could not be asked".
                ifLeft = { errorLogger.log(TAG, "Could not ask WhatsApp about pack $packId: $it") },
                ifRight = { isInWhatsApp.value = it },
            )
        }
    }

    fun addToWhatsApp() {
        viewModelScope.launch {
            prepareAddToWhatsApp(StickerPackId(packId)).fold(
                ifLeft = { error -> _messages.tryEmit(error.toMessage()) },
                ifRight = ::handlePlan,
            )
        }
    }

    fun chooseWhatsAppApp(app: WhatsAppApp) {
        val dialog = addDialog.value as? AddToWhatsAppDialog.ChooseApp ?: return
        addDialog.value = AddToWhatsAppDialog.Hidden
        _launchEvents.tryEmit(AddToWhatsAppLaunch(dialog.request, app))
    }

    fun dismissAddDialog() {
        addDialog.update { AddToWhatsAppDialog.Hidden }
    }

    fun onAddToWhatsAppResult(result: AddToWhatsAppResult) {
        when (result) {
            AddToWhatsAppResult.Added -> onAdded(PackDetailMessage.AddedToWhatsApp)
            AddToWhatsAppResult.AlreadyAdded -> onAdded(PackDetailMessage.AlreadyInWhatsApp)
            is AddToWhatsAppResult.Rejected -> _messages.tryEmit(PackDetailMessage.WhatsAppRejected(result.message))
            AddToWhatsAppResult.Unconfirmed -> onAdded(PackDetailMessage.CheckWhatsApp)
            AddToWhatsAppResult.Cancelled -> Unit
        }
    }

    private fun onAdded(message: PackDetailMessage) {
        _messages.tryEmit(message)
        refreshWhatsAppStatus()
    }

    private fun handlePlan(plan: AddToWhatsAppPlan) {
        when (plan) {
            is AddToWhatsAppPlan.Launch -> _launchEvents.tryEmit(AddToWhatsAppLaunch(plan.request, plan.app))
            is AddToWhatsAppPlan.ChooseApp -> addDialog.value = AddToWhatsAppDialog.ChooseApp(plan.request, plan.apps)
            AddToWhatsAppPlan.NoWhatsAppInstalled -> addDialog.value = AddToWhatsAppDialog.NoWhatsAppInstalled
        }
    }

    private fun PrepareAddToWhatsAppError.toMessage(): PackDetailMessage = when (this) {
        is PrepareAddToWhatsAppError.NotReady -> PackDetailMessage.PackNotReady
        is PrepareAddToWhatsAppError.Validation, is PrepareAddToWhatsAppError.Pack -> PackDetailMessage.ActionFailed
    }

    /** Null when the check itself failed. */
    private suspend fun validate(id: StickerPackId): List<ValidationIssue>? =
        validatePack(id).fold(
            // The screen shows this as PackValidation.Unavailable; the log keeps the cause.
            ifLeft = { error ->
                errorLogger.log(TAG, "Could not validate pack ${id.value}: $error")
                null
            },
            ifRight = { it },
        )

    private fun toContent(pack: StickerPack, issues: List<ValidationIssue>?): PackDetailContent {
        val numbers = pack.stickers.sortedBy { it.position }.withIndex().associate { it.value.fileName to it.index + 1 }
        val validation = when {
            issues == null -> PackValidation.Unavailable
            issues.isEmpty() -> PackValidation.Valid
            else -> PackValidation.Invalid(issues.map { PackIssue(it, it.fileNameOrNull()?.let(numbers::get)) })
        }
        val filled = pack.stickers.sortedBy { it.position }.map { sticker ->
            StickerSlot.Filled(
                fileName = sticker.fileName,
                image = PackImage(fileResolver.resolve(pack.id.value, sticker.fileName), pack.imageDataVersion),
                emojis = sticker.emojis,
            )
        }
        return PackDetailContent(
            packId = pack.id.value,
            name = pack.name,
            publisher = pack.publisher,
            tray = pack.trayFileName?.let { PackImage(fileResolver.resolve(pack.id.value, it), pack.imageDataVersion) },
            slots = filled + List((StickerPackLimits.MAX_STICKERS - filled.size).coerceAtLeast(0)) { StickerSlot.Empty },
            isAvailableInKeyboard = pack.isAvailableInKeyboard,
            validation = validation,
        )
    }

    private companion object {
        const val TAG = "PackDetailViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun ValidationIssue.fileNameOrNull(): String? = when (this) {
    is ValidationIssue.MissingEmoji -> fileName
    is ValidationIssue.TooManyEmojis -> fileName
    is ValidationIssue.FileFactsMissing -> fileName
    is ValidationIssue.StickerNotWebp -> fileName
    is ValidationIssue.StickerAnimated -> fileName
    is ValidationIssue.StickerWrongSize -> fileName
    is ValidationIssue.StickerTooLarge -> fileName
    ValidationIssue.InvalidPackId, ValidationIssue.InvalidName, ValidationIssue.InvalidPublisher,
    ValidationIssue.MissingTray, is ValidationIssue.TooFewStickers, is ValidationIssue.TooManyStickers,
    is ValidationIssue.TrayNotPng, is ValidationIssue.TrayWrongSize, is ValidationIssue.TrayTooLarge -> null
}
