package com.damianogiusti.stickermaker.feature.editor

import android.content.Context
import com.damianogiusti.stickermaker.imaging.outline.OutlineMath
import com.damianogiusti.stickermaker.imaging.text.StickerPalette

/** Last outline choices. Select all has its own width, as in the original (default 0). */
data class OutlineDefaults(val widthPermille: Int, val widthPermilleSelectAll: Int, val colorIndex: Int) {
    companion object {
        val Initial = OutlineDefaults(
            widthPermille = OutlineMath.DEFAULT_WIDTH_PERMILLE,
            widthPermilleSelectAll = OutlineMath.DEFAULT_WIDTH_PERMILLE_AFTER_SELECT_ALL,
            colorIndex = StickerPalette.DEFAULT_COLOR_INDEX,
        )
    }
}

interface OutlinePreferences {
    fun read(): OutlineDefaults
    fun write(isSelectAll: Boolean, widthPermille: Int, colorIndex: Int)
}

class SharedOutlinePreferences(context: Context) : OutlinePreferences {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): OutlineDefaults = OutlineDefaults(
        widthPermille = preferences.getInt(KEY_WIDTH, OutlineDefaults.Initial.widthPermille),
        widthPermilleSelectAll = preferences.getInt(KEY_WIDTH_SELECT_ALL, OutlineDefaults.Initial.widthPermilleSelectAll),
        colorIndex = preferences.getInt(KEY_COLOR, OutlineDefaults.Initial.colorIndex)
            .coerceIn(StickerPalette.pairs.indices),
    )

    override fun write(isSelectAll: Boolean, widthPermille: Int, colorIndex: Int) {
        preferences.edit()
            .putInt(if (isSelectAll) KEY_WIDTH_SELECT_ALL else KEY_WIDTH, widthPermille)
            .putInt(KEY_COLOR, colorIndex)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "editor_outline"
        const val KEY_WIDTH = "width"
        const val KEY_WIDTH_SELECT_ALL = "width_select_all"
        const val KEY_COLOR = "color"
    }
}
