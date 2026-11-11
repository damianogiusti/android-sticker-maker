package com.damianogiusti.stickermaker.provider

import java.io.File
import java.io.FileNotFoundException

/**
 * The URIs of the WhatsApp sticker contract, parsed from decoded path segments. Pure Kotlin so it is
 * unit-testable on the JVM (`UriMatcher` is not).
 */
sealed interface ProviderRoute {
    data object Metadata : ProviderRoute
    data class MetadataItem(val packId: String) : ProviderRoute
    data class Stickers(val packId: String) : ProviderRoute
    data class Asset(val packId: String, val fileName: String) : ProviderRoute

    /** MIME type for `getType`; [authority] is ours. */
    fun mimeType(authority: String): String = when (this) {
        Metadata -> "vnd.android.cursor.dir/vnd.$authority.metadata"
        is MetadataItem -> "vnd.android.cursor.item/vnd.$authority.metadata"
        is Stickers -> "vnd.android.cursor.dir/vnd.$authority.stickers"
        is Asset -> if (fileName.endsWith(".png", ignoreCase = true)) "image/png" else "image/webp"
    }

    companion object {
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val STICKERS_ASSET = "stickers_asset"

        fun parse(pathSegments: List<String>): ProviderRoute? = when {
            pathSegments == listOf(METADATA) -> Metadata
            pathSegments.size == 2 && pathSegments[0] == METADATA -> MetadataItem(pathSegments[1])
            pathSegments.size == 2 && pathSegments[0] == STICKERS -> Stickers(pathSegments[1])
            pathSegments.size == 3 && pathSegments[0] == STICKERS_ASSET -> Asset(pathSegments[1], pathSegments[2])
            else -> null
        }
    }
}

/** Decides which `stickers_asset/<id>/<file>` requests may reach the file system. */
object AssetRequestSanitizer {
    private val ALLOWED_EXTENSIONS = listOf(".webp", ".png")

    /** True for a plain `.webp` / `.png` name inside a plain pack directory name: no separators, no `..`. */
    fun isAllowed(packId: String, fileName: String): Boolean =
        isPlainName(packId) && isPlainName(fileName) && ALLOWED_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }

    private fun isPlainName(name: String): Boolean =
        name.isNotEmpty() && name != "." && !name.contains("..") && !name.contains('/') && !name.contains('\\') &&
            !name.contains('\u0000')
}

/**
 * The file behind a `stickers_asset/<id>/<file>` request. Pure apart from [fileOf] so a JVM test can
 * run it against a temp directory; the provider only wraps the result in a file descriptor.
 *
 * @throws FileNotFoundException when the request is rejected by [AssetRequestSanitizer] or the file is missing.
 */
fun resolveAssetFile(route: ProviderRoute.Asset, fileOf: (packId: String, fileName: String) -> File): File {
    if (!AssetRequestSanitizer.isAllowed(route.packId, route.fileName)) {
        throw FileNotFoundException("Rejected asset request: ${route.packId}/${route.fileName}")
    }
    val file = fileOf(route.packId, route.fileName)
    if (!file.isFile) throw FileNotFoundException("No such sticker file: ${route.packId}/${route.fileName}")
    return file
}
