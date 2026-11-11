package com.damianogiusti.stickermaker.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.damianogiusti.stickermaker.about.AboutScreen
import com.damianogiusti.stickermaker.domain.model.StickerSource
import com.damianogiusti.stickermaker.domain.model.StickerTarget
import com.damianogiusti.stickermaker.feature.editor.EditorScreen
import com.damianogiusti.stickermaker.feature.editor.EditorSource
import com.damianogiusti.stickermaker.feature.gallery.StickerGalleryScreen
import com.damianogiusti.stickermaker.feature.keyboard.KeyboardSetupScreen
import com.damianogiusti.stickermaker.feature.packs.detail.PackDetailScreen
import com.damianogiusti.stickermaker.feature.packs.emoji.EmojiPickerScreen
import com.damianogiusti.stickermaker.feature.packs.list.PacksListScreen
import com.damianogiusti.stickermaker.imports.IncomingImportHost

@Composable
fun StickerMakerNavHost() {
    val navController = rememberNavController()
    IncomingImportHost(onOpenPack = { navController.navigate(PackDetail(it)) })
    NavHost(navController = navController, startDestination = PacksList) {
        composable<PacksList> {
            PacksListScreen(
                onOpenPack = { navController.navigate(PackDetail(it)) },
                onKeyboardSetup = { navController.navigate(KeyboardSetup) },
                onAbout = { navController.navigate(About) },
            )
        }
        composable<About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable<KeyboardSetup> {
            KeyboardSetupScreen(onBack = { navController.popBackStack() })
        }
        composable<StickerGallery> { entry ->
            val route = entry.toRoute<StickerGallery>()
            StickerGalleryScreen(
                onImagePicked = { uri ->
                    navController.navigate(route.toEditor(uri)) {
                        popUpTo<StickerGallery> { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PackDetail> { entry ->
            val route = entry.toRoute<PackDetail>()
            PackDetailScreen(
                packId = route.packId,
                onBack = { navController.popBackStack() },
                onCreateSticker = { packId, source, target ->
                    navController.navigate(stickerCreationRoute(packId, source, target))
                },
                onEditEmojis = { packId, fileName -> navController.navigate(EmojiPicker(packId, fileName)) },
            )
        }
        composable<EmojiPicker> { entry ->
            val route = entry.toRoute<EmojiPicker>()
            EmojiPickerScreen(
                packId = route.packId,
                fileName = route.fileName,
                onClose = { navController.popBackStack() },
            )
        }
        composable<Editor> { entry ->
            val route = entry.toRoute<Editor>()
            EditorScreen(
                source = route.toEditorSource(),
                onClose = { navController.popBackStack() },
            )
        }
    }
}

/** The Sticker Gallery source picks its image first; every other source goes straight to the editor. */
private fun stickerCreationRoute(packId: String, source: StickerSource, target: StickerTarget): Any {
    val editor = editorRoute(packId, source, target)
    return if (source == StickerSource.WHATSAPP_STICKERS) {
        StickerGallery(packId, editor.targetKind, editor.replaceFileName)
    } else {
        editor
    }
}

private fun StickerGallery.toEditor(imageUri: String): Editor =
    Editor(packId, StickerSource.WHATSAPP_STICKERS, targetKind, replaceFileName, imageUri)

// Interim mapping until the pack screens are removed: every source opens the Photo Picker.
private fun Editor.toEditorSource(): EditorSource =
    imageUri?.let { EditorSource.ImageUri(Uri.parse(it)) } ?: EditorSource.PhotoPicker

private fun editorRoute(packId: String, source: StickerSource, target: StickerTarget): Editor = when (target) {
    StickerTarget.NewSticker -> Editor(packId, source, EditorTargetKind.NEW)
    is StickerTarget.ReplaceSticker -> Editor(packId, source, EditorTargetKind.REPLACE, target.fileName)
    StickerTarget.Tray -> Editor(packId, source, EditorTargetKind.TRAY)
}
