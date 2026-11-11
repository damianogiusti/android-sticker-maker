package com.damianogiusti.stickermaker.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object About

/** [imageUri] is the picture chosen on Home or shared from another app. */
@Serializable
data class Editor(val imageUri: String)
