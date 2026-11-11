package com.damianogiusti.stickermaker.feature.gallery

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import arrow.core.Either
import arrow.core.left
import arrow.core.right

internal class SharedPreferencesTreeGrantStore(
    private val contentResolver: ContentResolver,
    private val preferences: SharedPreferences,
) : TreeGrantStore {

    override fun treeUri(source: WhatsAppSource): String? = preferences.getString(key(source), null)

    override fun save(source: WhatsAppSource, treeUri: String): Either<GalleryError, Unit> =
        try {
            contentResolver.takePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            preferences.edit().putString(key(source), treeUri).apply()
            Unit.right()
        } catch (error: SecurityException) {
            GalleryError.Io(error.message).left()
        }

    private fun key(source: WhatsAppSource) = "tree_uri_${source.name}"
}
