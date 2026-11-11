package com.damianogiusti.stickermaker.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedImageIntentTest {
    private val share = IncomingIntent(
        action = "android.intent.action.SEND",
        mimeType = "image/jpeg",
        streamUri = "content://media/external/images/1",
        isLaunchedFromHistory = false,
        isConsumed = false,
    )

    @Test
    fun `a shared image with a content uri is accepted`() {
        assertEquals("content://media/external/images/1", SharedImageIntent.imageUriOf(share))
    }

    @Test
    fun `any image subtype is accepted`() {
        assertEquals("content://media/external/images/1", SharedImageIntent.imageUriOf(share.copy(mimeType = "image/*")))
        assertEquals("content://media/external/images/1", SharedImageIntent.imageUriOf(share.copy(mimeType = "image/webp")))
    }

    @Test
    fun `an intent relaunched from history is ignored`() {
        assertNull(SharedImageIntent.imageUriOf(share.copy(isLaunchedFromHistory = true)))
    }

    @Test
    fun `an intent already handled is ignored`() {
        assertNull(SharedImageIntent.imageUriOf(share.copy(isConsumed = true)))
    }

    @Test
    fun `other actions are ignored`() {
        assertNull(SharedImageIntent.imageUriOf(share.copy(action = "android.intent.action.VIEW")))
        assertNull(SharedImageIntent.imageUriOf(share.copy(action = null)))
    }

    @Test
    fun `non image types are ignored`() {
        assertNull(SharedImageIntent.imageUriOf(share.copy(mimeType = "text/plain")))
        assertNull(SharedImageIntent.imageUriOf(share.copy(mimeType = null)))
    }

    @Test
    fun `file uris and missing uris are ignored`() {
        assertNull(SharedImageIntent.imageUriOf(share.copy(streamUri = "file:///sdcard/a.jpg")))
        assertNull(SharedImageIntent.imageUriOf(share.copy(streamUri = null)))
    }
}
