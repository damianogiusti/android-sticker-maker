package com.damianogiusti.stickermaker.domain.error

import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue

sealed interface StickerPackError {
    data class NotFound(val id: StickerPackId) : StickerPackError
    data class StickerNotFound(val packId: StickerPackId, val fileName: String) : StickerPackError
    data object InvalidName : StickerPackError
    data object InvalidPublisher : StickerPackError
    data object TooManyStickers : StickerPackError
    data object TooManyEmojis : StickerPackError
    data class Storage(val message: String?) : StickerPackError
    data class Database(val message: String?) : StickerPackError
}

sealed interface KeyboardHistoryError {
    data class Database(val message: String?) : KeyboardHistoryError
}

sealed interface StickerFileInspectionError {
    data class FileNotFound(val fileName: String) : StickerFileInspectionError
    data class Unreadable(val fileName: String, val message: String?) : StickerFileInspectionError
}

/** Failure of the whole "validate a pack" action (not a validation finding). */
sealed interface ValidatePackError {
    data class Pack(val error: StickerPackError) : ValidatePackError
    data class Inspection(val error: StickerFileInspectionError) : ValidatePackError
}

/** Asking one WhatsApp app whether a pack is added failed (provider missing, denied, bad column). */
sealed interface WhitelistError {
    data class Unavailable(val packageName: String, val message: String?) : WhitelistError
}

/** Failure of the "prepare add to WhatsApp" action. */
sealed interface PrepareAddToWhatsAppError {
    /** The pack changed since the UI last validated it; the issues explain why it cannot be sent. */
    data class NotReady(val issues: List<ValidationIssue>) :
        PrepareAddToWhatsAppError

    data class Validation(val error: ValidatePackError) : PrepareAddToWhatsAppError
    data class Pack(val error: StickerPackError) : PrepareAddToWhatsAppError
}

/** Failure of importing, exporting, backing up or restoring packs. */
sealed interface TransferError {
    data object NotAZip : TransferError

    /** A zip entry would land outside the target directory (zip-slip) or has an unusable name. */
    data class UnsafeEntry(val name: String) : TransferError

    /** The archive is bigger than the allowed total, or holds too many entries. */
    data object TooLarge : TransferError
    data object TooManyStickers : TransferError
    data object NoStickers : TransferError
    data class NotAWebp(val fileName: String) : TransferError
    data class AnimatedNotSupported(val fileName: String) : TransferError
    data class WrongStickerSize(val fileName: String, val width: Int, val height: Int) : TransferError

    /** The zip is fine but it is not a backup made by this app (no manifest, bad JSON, missing file). */
    data class InvalidBackup(val message: String?) : TransferError
    data class UnsupportedBackupVersion(val version: Int) : TransferError
    data class Io(val message: String?) : TransferError
    data class Pack(val error: StickerPackError) : TransferError
}
