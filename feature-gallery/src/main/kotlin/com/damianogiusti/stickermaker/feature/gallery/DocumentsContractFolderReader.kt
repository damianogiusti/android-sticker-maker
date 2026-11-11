package com.damianogiusti.stickermaker.feature.gallery

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

internal class DocumentsContractFolderReader(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher,
) : WhatsAppFolderReader {

    private class Entry(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val lastModified: Long,
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val isWebp: Boolean get() = name.endsWith(".webp", ignoreCase = true)
    }

    /** One children query per folder (the root and its direct sub-folders); no file is opened. */
    override suspend fun list(treeUri: String): Either<GalleryError, List<WhatsAppStickerFile>> =
        withContext(dispatcher) {
            try {
                val tree = Uri.parse(treeUri)
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                val rootEntries = children(tree, rootId)
                val nested = rootEntries.filter { it.isDirectory }.flatMap { children(tree, it.documentId) }
                (rootEntries + nested)
                    .filter { it.isWebp }
                    .map { toStickerFile(tree, it) }
                    .right()
            } catch (_: SecurityException) {
                GalleryError.TreeUnavailable.left()
            } catch (_: IllegalArgumentException) {
                GalleryError.TreeUnavailable.left()
            } catch (error: IOException) {
                GalleryError.Io(error.message).left()
            }
        }

    private fun children(tree: Uri, parentId: String): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("No cursor for $childrenUri")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(Entry(it.getString(0), it.getString(1).orEmpty(), it.getString(2).orEmpty(), it.getLong(3), it.getLong(4)))
                }
            }
        }
    }

    private fun toStickerFile(tree: Uri, entry: Entry) = WhatsAppStickerFile(
        uriString = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId).toString(),
        name = entry.name,
        sizeBytes = entry.sizeBytes,
        lastModified = entry.lastModified,
    )
}
