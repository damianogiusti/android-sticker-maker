package com.damianogiusti.stickermaker.provider

import androidx.core.content.FileProvider

/**
 * Own subclass so this provider has a distinct component name: another module also declares an
 * androidx FileProvider, and two entries with the same name would clash in the manifest merger.
 */
class ShareFileProvider : FileProvider()
