package com.damianogiusti.stickermaker.imaging.text

import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import arrow.core.Either
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the bundled OFL fonts from `assets/fonts` once. Variable fonts get a fixed weight on API 26+.
 * Called from the main thread (UI) and from `Dispatchers.Default` (export), so the cache is concurrent.
 * A font that fails to load is logged and replaced by [Typeface.DEFAULT].
 */
class FontTypefaces(private val assets: AssetManager) {
    private val cache = ConcurrentHashMap<StickerFont, Typeface>()

    fun typefaceOf(font: StickerFont): Typeface = cache.computeIfAbsent(font, ::loadOrDefault)

    private fun loadOrDefault(font: StickerFont): Typeface {
        val file = fileOf(font) ?: return Typeface.DEFAULT
        return Either.catch { load(file, weightOf(font)) }.fold(
            ifLeft = { error ->
                Log.w(TAG, "cannot load font $file, using the default typeface", error)
                Typeface.DEFAULT
            },
            ifRight = { it },
        )
    }

    private fun load(file: String, weight: Int?): Typeface {
        val path = "fonts/$file"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = Typeface.Builder(assets, path)
            if (weight != null) builder.setFontVariationSettings("'wght' $weight")
            return requireNotNull(builder.build()) { "cannot load $path" }
        }
        return Typeface.createFromAsset(assets, path)
    }

    private fun fileOf(font: StickerFont): String? = when (font) {
        StickerFont.ANTON -> "Anton-Regular.ttf"
        StickerFont.ALFA_SLAB_ONE -> "AlfaSlabOne-Regular.ttf"
        StickerFont.DYNAPUFF_SEMIBOLD -> "DynaPuff-Variable.ttf"
        StickerFont.CUTIVE_MONO -> "CutiveMono-Regular.ttf"
        StickerFont.FREDOKA_ONE -> "Fredoka-Variable.ttf"
        StickerFont.FRECKLE_FACE -> "FreckleFace-Regular.ttf"
        StickerFont.SEDGWICK_AVE_DISPLAY -> "SedgwickAveDisplay-Regular.ttf"
        StickerFont.NUNITO -> "Nunito-Variable.ttf"
        StickerFont.COMIC_NEUE -> "ComicNeue-Bold.ttf"
        StickerFont.SYSTEM -> null
    }

    private fun weightOf(font: StickerFont): Int? = when (font) {
        StickerFont.DYNAPUFF_SEMIBOLD -> WEIGHT_SEMIBOLD
        StickerFont.FREDOKA_ONE -> WEIGHT_SEMIBOLD
        StickerFont.NUNITO -> WEIGHT_REGULAR
        else -> null
    }

    private companion object {
        const val TAG = "FontTypefaces"
        const val WEIGHT_SEMIBOLD = 600
        const val WEIGHT_REGULAR = 400
    }
}
