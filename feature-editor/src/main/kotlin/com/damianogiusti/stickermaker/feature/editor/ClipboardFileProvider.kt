package com.damianogiusti.stickermaker.feature.editor

import androidx.core.content.FileProvider

/** Serves the sticker copied to the clipboard. Own subclass so the provider name is unique in the merged manifest. */
class ClipboardFileProvider : FileProvider()
