package com.damianogiusti.stickermaker.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.damianogiusti.stickermaker.data.storage.PackFileStorage
import com.damianogiusti.stickermaker.di.ensureKoinStarted
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.usecase.GetStickerPackUseCase
import com.damianogiusti.stickermaker.domain.usecase.ListStickerPacksUseCase
import java.io.FileNotFoundException

/**
 * Thin Adapter for the WhatsApp sticker contract: parses the URI, asks a use case, copies rows.
 *
 * A ContentProvider is a framework entry point that Android creates, so it cannot receive constructor
 * arguments; [KoinComponent] is the accepted exception to "no service locator". Dependencies are
 * resolved lazily, after [ensureKoinStarted] has run in `onCreate`.
 */
class StickerContentProvider : ContentProvider(), KoinComponent {

    private val listPacks: ListStickerPacksUseCase by inject()
    private val getPack: GetStickerPackUseCase by inject()
    private val storage: PackFileStorage by inject()
    private val authority: StickerAuthority by inject()

    override fun onCreate(): Boolean {
        // Runs before Application.onCreate; Koin must be up before the first query. Dependencies stay lazy.
        ensureKoinStarted(requireNotNull(context) { "Provider created without a context" })
        return true
    }

    override fun getType(uri: Uri): String? = ProviderRoute.parse(uri.pathSegments)?.mimeType(authority.value)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val data = when (val route = ProviderRoute.parse(uri.pathSegments)) {
            ProviderRoute.Metadata -> StickerCursorMapper.metadata(packs())
            is ProviderRoute.MetadataItem -> StickerCursorMapper.metadata(listOfNotNull(pack(route.packId)))
            is ProviderRoute.Stickers -> pack(route.packId)?.let(StickerCursorMapper::stickers)
                ?: CursorData(StickerCursorMapper.STICKER_COLUMNS, emptyList())
            is ProviderRoute.Asset, null -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        return MatrixCursor(data.columns.toTypedArray(), data.rows.size).apply { data.rows.forEach(::addRow) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val route = ProviderRoute.parse(uri.pathSegments) as? ProviderRoute.Asset
            ?: throw FileNotFoundException("Unknown URI: $uri")
        if (mode != "r") throw SecurityException("Read-only provider")
        val file = resolveAssetFile(route, storage::fileOf)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor =
        AssetFileDescriptor(openFile(uri, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH)

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Not supported")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("Not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("Not supported")

    // The ContentProvider API is synchronous and runs on a binder thread, so there is nothing to suspend.
    private fun packs() = runBlocking { listPacks() }.fold(
        ifLeft = { error ->
            Log.w(TAG, "Could not list packs: $error")
            emptyList()
        },
        ifRight = { it },
    )

    private fun pack(id: String) = runBlocking { getPack(StickerPackId(id)) }.fold(
        ifLeft = { error ->
            Log.w(TAG, "Could not read pack $id: $error")
            null
        },
        ifRight = { it },
    )

    private companion object {
        const val TAG = "StickerContentProvider"
    }
}
