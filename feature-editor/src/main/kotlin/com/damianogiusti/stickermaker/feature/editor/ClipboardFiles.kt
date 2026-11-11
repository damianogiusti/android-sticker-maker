package com.damianogiusti.stickermaker.feature.editor

import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat

/** File naming and rotation rules of the clipboard folder, kept free of Android so they can be tested. */
object ClipboardFiles {
    fun fileName(timestampMillis: Long, format: StickerImageFormat): String =
        "$timestampMillis.${format.fileExtension}"

    /** Everything except [current] is stale once the new clip is set; only the latest sticker is kept. */
    fun staleFileNames(existing: List<String>, current: String): List<String> =
        existing.filter { it != current }
}
