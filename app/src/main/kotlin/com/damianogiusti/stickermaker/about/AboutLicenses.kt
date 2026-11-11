package com.damianogiusti.stickermaker.about

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

internal class FontAsset(val fontName: String, val assetFile: String)

/** What the app ships, by name. Static on purpose: it changes only when a dependency does. */
internal object AboutLicenses {
    const val OFL_NAME = "SIL Open Font License 1.1"
    private const val APACHE = "Apache License 2.0"

    // The texts live in imaging/src/main/assets/licenses/.
    val fonts = listOf(
        FontAsset("Alfa Slab One", "OFL-alfaslabone.txt"),
        FontAsset("Anton", "OFL-anton.txt"),
        FontAsset("Comic Neue", "OFL-comicneue.txt"),
        FontAsset("Cutive Mono", "OFL-cutivemono.txt"),
        FontAsset("DynaPuff", "OFL-dynapuff.txt"),
        FontAsset("Freckle Face", "OFL-freckleface.txt"),
        FontAsset("Fredoka", "OFL-fredoka.txt"),
        FontAsset("Nunito", "OFL-nunito.txt"),
        FontAsset("Sedgwick Ave Display", "OFL-sedgwickavedisplay.txt"),
    )

    val libraries = listOf(
        LibraryLicense("Kotlin and kotlinx (coroutines, serialization)", APACHE),
        LibraryLicense("AndroidX (Core, Lifecycle, Activity, Navigation)", APACHE),
        LibraryLicense("Jetpack Compose and Material 3", APACHE),
        LibraryLicense("Koin", APACHE),
        LibraryLicense("Arrow", APACHE),
        LibraryLicense("LiteRT (TensorFlow Lite)", APACHE),
    )
}

class AssetLicenseTextSource(
    private val assets: AssetManager,
    private val ioDispatcher: CoroutineDispatcher,
) : LicenseTextSource {

    override suspend fun load(fileName: String): String? = withContext(ioDispatcher) {
        try {
            assets.open("licenses/$fileName").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (failure: IOException) {
            null
        }
    }
}
