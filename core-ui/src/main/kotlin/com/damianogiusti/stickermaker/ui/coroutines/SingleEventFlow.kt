package com.damianogiusti.stickermaker.ui.coroutines

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Fire-once events (replay 0): a collector that is not subscribed misses them.
 * Expose it as `asSharedFlow()`; do not use a Channel.
 */
@Suppress("FunctionName")
fun <T> SingleEventFlow(): MutableSharedFlow<T> = MutableSharedFlow(
    replay = 0,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
