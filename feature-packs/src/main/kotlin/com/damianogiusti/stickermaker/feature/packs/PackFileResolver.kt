package com.damianogiusti.stickermaker.feature.packs

import java.io.File

/** Gives the UI the on-disk file of a pack asset. Implemented in :app on top of the data layer. */
fun interface PackFileResolver {
    fun resolve(packId: String, fileName: String): File
}

/** A pack image to show. [version] is part of the cache key because file names can be reused (tray.png). */
data class PackImage(val file: File, val version: Int)
