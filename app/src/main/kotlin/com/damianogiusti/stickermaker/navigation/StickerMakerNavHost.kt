package com.damianogiusti.stickermaker.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.damianogiusti.stickermaker.about.AboutScreen
import com.damianogiusti.stickermaker.feature.editor.EditorScreen
import com.damianogiusti.stickermaker.feature.editor.EditorSource
import com.damianogiusti.stickermaker.home.HomeScreen
import com.damianogiusti.stickermaker.share.IncomingShareHost

@Composable
fun StickerMakerNavHost() {
    val navController = rememberNavController()
    // Back from an editor opened by a share lands on Home: the share replaces whatever was open above it.
    IncomingShareHost(
        onOpenEditor = { uri ->
            navController.navigate(Editor(uri)) { popUpTo<Home>() }
        },
    )
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onPhotoChosen = { uri -> navController.navigate(Editor(uri)) },
                onAbout = { navController.navigate(About) },
            )
        }
        composable<About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable<Editor> { entry ->
            val route = entry.toRoute<Editor>()
            EditorScreen(
                source = EditorSource.ImageUri(Uri.parse(route.imageUri)),
                onClose = { navController.popBackStack() },
            )
        }
    }
}
