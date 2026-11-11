package com.damianogiusti.stickermaker.feature.gallery

import arrow.core.Either

private const val EXTERNAL_STORAGE_PRIMARY = "primary:"

/** The WhatsApp app a sticker folder belongs to. */
enum class WhatsAppSource(val packageName: String, val stickersFolderPath: String) {
    WHATSAPP("com.whatsapp", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"),
    WHATSAPP_BUSINESS(
        "com.whatsapp.w4b",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers",
    ),
    ;

    /** Document id of the folder on the primary volume, for `com.android.externalstorage.documents`. */
    val documentId: String get() = EXTERNAL_STORAGE_PRIMARY + stickersFolderPath
}

internal const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

sealed interface GalleryError {
    /** The folder is gone or the persisted permission was revoked. */
    data object TreeUnavailable : GalleryError
    data class Io(val message: String?) : GalleryError
}

/** A .webp file found in a WhatsApp sticker folder. Listing it costs no file read: see [AnimationDetector]. */
data class WhatsAppStickerFile(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/** Tells whether a sticker is an animated WebP by reading only its first bytes, once per file. */
interface AnimationDetector {
    suspend fun isAnimated(file: WhatsAppStickerFile): Boolean
}

/** Remembers the SAF tree the user granted for each WhatsApp app. */
interface TreeGrantStore {
    fun treeUri(source: WhatsAppSource): String?

    /** Persists the permission on the system side and remembers the URI. */
    fun save(source: WhatsAppSource, treeUri: String): Either<GalleryError, Unit>
}

/** Lists the .webp files of a granted tree (the folder itself and its direct sub-folders). */
interface WhatsAppFolderReader {
    suspend fun list(treeUri: String): Either<GalleryError, List<WhatsAppStickerFile>>
}

/** Copies a sticker of the user's own packs to the cache and returns its URI. */
interface OwnStickerCopier {
    suspend fun copyToCache(packId: String, fileName: String): Either<GalleryError, String>
}

/** Finds the on-disk file of a pack asset, for thumbnails. */
fun interface GalleryFileLocator {
    fun locate(packId: String, fileName: String): java.io.File
}
