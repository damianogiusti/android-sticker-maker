package com.damianogiusti.stickermaker.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.damianogiusti.stickermaker.imaging.CutError
import com.damianogiusti.stickermaker.imaging.EditorImage
import com.damianogiusti.stickermaker.imaging.ExportError
import com.damianogiusti.stickermaker.imaging.SegmentationError
import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import com.damianogiusti.stickermaker.imaging.cut.CutSelection
import com.damianogiusti.stickermaker.imaging.cut.ImageCutter
import com.damianogiusti.stickermaker.imaging.outline.OutlineRenderer
import com.damianogiusti.stickermaker.imaging.outline.OutlineSettings
import com.damianogiusti.stickermaker.imaging.segmentation.SubjectSegmenter
import com.damianogiusti.stickermaker.imaging.source.SourceImageLoader
import com.damianogiusti.stickermaker.imaging.store.EditorImageStore
import com.damianogiusti.stickermaker.imaging.store.ImageSlot
import com.damianogiusti.stickermaker.imaging.source.SourceUri
import com.damianogiusti.stickermaker.imaging.text.StickerPalette
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem
import com.damianogiusti.stickermaker.imaging.text.TextItemGeometry
import com.damianogiusti.stickermaker.ui.coroutines.SingleEventFlow

private const val TAG = "EditorViewModel"
private const val CANVAS_CENTER = 512f
private const val KEY_STEP = "editor.step"
private const val KEY_TOOL = "editor.tool"
private const val KEY_TEXT = "editor.text"

class EditorViewModel(
    private val sourceLoader: SourceImageLoader,
    private val cutter: ImageCutter,
    private val segmenter: SubjectSegmenter,
    private val outlineRenderer: OutlineRenderer,
    private val preferences: OutlinePreferences,
    private val copySticker: CopyStickerUseCase,
    private val imageStore: EditorImageStore,
    private val savedState: SavedStateHandle,
    private val logger: EditorLogger,
) : ViewModel() {

    private val _state = MutableStateFlow<EditorUiState>(EditorUiState.WaitingForPicker)
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = SingleEventFlow<EditorEvent>()
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    /** Kept while the text screen is open, so Back restores tool, zoom and polygon. */
    private var cuttingState: CuttingState? = null
    private var lastSource: SourceUri? = null
    private var nextItemId = 1L

    init {
        restoreAfterProcessDeath()
    }

    // region source

    fun onImagePicked(uri: SourceUri) {
        lastSource = uri
        _state.value = EditorUiState.LoadingImage
        viewModelScope.launch {
            sourceLoader.load(uri).fold(
                ifLeft = {
                    logger.warn(TAG, "load failed: $it")
                    _state.value = EditorUiState.Failed(EditorFailure.IMAGE_UNREADABLE)
                },
                ifRight = { image ->
                    persistImage(ImageSlot.WORKING, image)
                    showCutting(CuttingState(image))
                },
            )
        }
    }

    fun onRetryLoad() {
        lastSource?.let(::onImagePicked)
    }

    fun onPickerCancelled() = finish(EditorEvent.Cancelled)

    /** After a failed restore: forget the lost work and open the source picker again. */
    fun onPickAgainClicked() {
        forgetSavedWork()
        _state.value = EditorUiState.WaitingForPicker
        _events.tryEmit(EditorEvent.LaunchPicker)
    }

    fun onPickerUnavailable() {
        _state.value = EditorUiState.Failed(EditorFailure.NO_PICKER_AVAILABLE)
    }

    fun onBackPressed() {
        val current = _state.value
        if (current is EditorUiState.Text) {
            if (current.state.copy == CopyStatus.Copying) return
            val cutting = cuttingState
            if (cutting != null && current.state.hasImage) {
                showCutting(cutting)
                return
            }
            if (current.state.hasImage) {
                restoreCuttingFromFile()
                return
            }
        }
        finish(EditorEvent.Cancelled)
    }

    // endregion

    // region cutting

    fun onToolSelected(tool: CutTool) {
        savedState[KEY_TOOL] = tool.name
        updateCutting { it.copy(tool = tool, polygonVertices = emptyList()) }
    }

    fun onViewportChanged(viewport: CutViewport) = updateCutting { it.copy(viewport = viewport) }

    fun onMessageDismissed() = updateCutting { it.copy(message = null) }

    fun onFreehandFinished(points: List<CutPoint>) = finishSelection(CutSelection.Lasso(points))

    fun onShapeFinished(selection: CutSelection) = finishSelection(selection)

    fun onSelectAllClicked() = finishSelection(CutSelection.WholeImage)

    /** A tap on the image in polygon mode: closes the polygon near the first vertex, else adds a vertex. */
    fun onPolygonTapped(point: CutPoint, closeRadiusInImagePx: Float) {
        val current = currentCutting() ?: return
        if (current.isBusy || current.tool != CutTool.POLYGON) return
        if (GestureRules.isClosingTap(current.polygonVertices, point, closeRadiusInImagePx)) {
            val vertices = current.polygonVertices
            updateCutting { it.copy(polygonVertices = emptyList()) }
            finishSelection(CutSelection.Polygon(vertices))
        } else {
            updateCutting { it.copy(polygonVertices = it.polygonVertices + point) }
        }
    }

    fun onPolygonUndo() = updateCutting { it.copy(polygonVertices = it.polygonVertices.dropLast(1)) }

    fun onRotateClicked() {
        val current = currentCutting() ?: return
        if (current.isBusy) return
        updateCutting { it.copy(busy = CuttingBusy.ROTATING) }
        viewModelScope.launch {
            cutter.rotateClockwise(current.image).fold(
                ifLeft = { failCutting(CuttingMessage.ROTATE_FAILED, it) },
                ifRight = { rotated ->
                    persistImage(ImageSlot.WORKING, rotated)
                    updateCutting {
                        it.copy(image = rotated, viewport = null, polygonVertices = emptyList(), busy = CuttingBusy.NONE)
                    }
                },
            )
        }
    }

    fun onMagicClicked() {
        val current = currentCutting() ?: return
        if (current.isBusy) return
        updateCutting { it.copy(busy = CuttingBusy.MAGIC, polygonVertices = emptyList()) }
        viewModelScope.launch {
            segmenter.segment(current.image).fold(
                ifLeft = { error ->
                    logger.warn(TAG, "segmentation failed: $error")
                    val message = if (error is SegmentationError.NoSubjectFound) {
                        CuttingMessage.NO_SUBJECT_FOUND
                    } else {
                        CuttingMessage.MAGIC_FAILED
                    }
                    updateCutting { it.copy(busy = CuttingBusy.NONE, message = message) }
                },
                ifRight = { mask ->
                    cutter.cut(current.image, mask).fold(
                        ifLeft = { failCutting(cutMessage(it), it) },
                        ifRight = { cutout -> openTextStage(cutout, isSelectAll = false) },
                    )
                },
            )
        }
    }

    private fun finishSelection(selection: CutSelection) {
        val current = currentCutting() ?: return
        if (current.isBusy) return
        updateCutting { it.copy(busy = CuttingBusy.CUTTING) }
        viewModelScope.launch {
            cutter.cut(current.image, selection).fold(
                ifLeft = { failCutting(cutMessage(it), it) },
                ifRight = { cutout -> openTextStage(cutout, isSelectAll = selection == CutSelection.WholeImage) },
            )
        }
    }

    private fun cutMessage(error: CutError) =
        if (error is CutError.EmptySelection) CuttingMessage.EMPTY_SELECTION else CuttingMessage.CUT_FAILED

    private fun failCutting(message: CuttingMessage, error: Any) {
        logger.warn(TAG, "cutting step failed: $error")
        updateCutting { it.copy(busy = CuttingBusy.NONE, message = message) }
    }

    private suspend fun openTextStage(cutout: EditorImage, isSelectAll: Boolean) {
        val defaults = preferences.read()
        val width = if (isSelectAll) defaults.widthPermilleSelectAll else defaults.widthPermille
        val settings = OutlineSettings(width, StickerPalette.pairs[defaults.colorIndex].color)
        outlineRenderer.render(cutout, settings).fold(
            ifLeft = { failCutting(CuttingMessage.OUTLINE_FAILED, it) },
            ifRight = { outlined ->
                cuttingState = currentCutting()?.copy(busy = CuttingBusy.NONE)
                persistImage(ImageSlot.CUTOUT, cutout)
                enterText(newTextState(cutout, outlined, isSelectAll))
            },
        )
    }

    private fun showCutting(cutting: CuttingState) {
        cuttingState = cutting
        savedState[KEY_STEP] = EditorStep.CUTTING.name
        _state.value = EditorUiState.Cutting(cutting)
    }

    private fun enterText(text: TextState) {
        _state.value = EditorUiState.Text(text)
        savedState[KEY_STEP] = EditorStep.TEXT.name
        savedState[KEY_TEXT] = TextSnapshot.of(text).encode()
    }

    private fun currentCutting(): CuttingState? = (_state.value as? EditorUiState.Cutting)?.state

    private fun updateCutting(transform: (CuttingState) -> CuttingState) {
        val current = _state.value as? EditorUiState.Cutting ?: return
        val updated = transform(current.state)
        cuttingState = updated
        _state.value = EditorUiState.Cutting(updated)
    }

    // endregion

    // region text and outline

    private fun newTextState(cutout: EditorImage?, outlined: EditorImage?, isSelectAll: Boolean): TextState {
        val defaults = preferences.read()
        return TextState(
            cutout = cutout,
            outlinedCutout = outlined,
            isSelectAll = isSelectAll,
            outline = OutlineSettings(
                if (isSelectAll) defaults.widthPermilleSelectAll else defaults.widthPermille,
                StickerPalette.pairs[defaults.colorIndex].color,
            ),
            outlineColorIndex = defaults.colorIndex,
        )
    }

    fun onAddTextClicked() = updateText { it.copy(editing = TextEditing(itemId = null, text = "")) }

    fun onEditingTextChanged(text: String) = updateText { state ->
        state.copy(editing = state.editing?.copy(text = text))
    }

    fun onEditingDismissed() = updateText { it.copy(editing = null) }

    fun onEditingConfirmed() = updateText { state ->
        val editing = state.editing ?: return@updateText state
        val text = editing.text.trim()
        when {
            text.isEmpty() -> state.copy(editing = null)
            editing.itemId == null -> addItem(state.copy(editing = null), text)
            else -> state.copy(
                editing = null,
                items = state.items.map { if (it.id == editing.itemId) it.copy(text = text) else it },
            )
        }
    }

    private fun addItem(state: TextState, text: String): TextState {
        val item = StickerTextItem(
            id = nextItemId++,
            text = text,
            style = state.style,
            font = state.font,
            colorIndex = state.colorIndex,
            centerX = CANVAS_CENTER,
            centerY = CANVAS_CENTER,
            scale = 1f,
            rotationDegrees = 0f,
        )
        return state.copy(items = state.items + item, selectedId = item.id)
    }

    /** A tap selects a text; a tap on the selected text edits it. */
    fun onItemTapped(id: Long) = updateText { state ->
        val item = state.items.firstOrNull { it.id == id } ?: return@updateText state
        if (state.selectedId == id) {
            state.copy(editing = TextEditing(id, item.text))
        } else {
            state.copy(selectedId = id, style = item.style, font = item.font, colorIndex = item.colorIndex)
        }
    }

    fun onCanvasTapped() = updateText { it.copy(selectedId = null) }

    /** [dx]/[dy] in canvas pixels, [zoom] a factor, [rotationDelta] in degrees. */
    fun onItemTransformed(id: Long, dx: Float, dy: Float, zoom: Float, rotationDelta: Float) = updateText { state ->
        val grabbed = state.items.firstOrNull { it.id == id } ?: return@updateText state
        val selection = if (state.selectedId == id) {
            state
        } else {
            state.copy(style = grabbed.style, font = grabbed.font, colorIndex = grabbed.colorIndex)
        }
        selection.copy(
            selectedId = id,
            items = selection.items.map { item ->
                if (item.id != id) return@map item
                item.copy(
                    centerX = item.centerX + dx,
                    centerY = item.centerY + dy,
                    scale = TextItemGeometry.clampScale(item.scale * zoom),
                    rotationDegrees = item.rotationDegrees + rotationDelta,
                )
            },
        )
    }

    fun onItemReleased(id: Long, isOverDeleteZone: Boolean) {
        if (!isOverDeleteZone) return
        updateText { state ->
            state.copy(items = state.items.filterNot { it.id == id }, selectedId = null)
        }
    }

    fun onStyleToggled() = updateText { state ->
        val next = (state.selected?.style ?: state.style).next()
        state.copy(style = next, items = state.items.map { if (it.id == state.selectedId) it.copy(style = next) else it })
    }

    fun onFontToggled() = updateText { state ->
        val next = (state.selected?.font ?: state.font).next()
        state.copy(font = next, items = state.items.map { if (it.id == state.selectedId) it.copy(font = next) else it })
    }

    fun onColorSelected(index: Int) = updateText { state ->
        state.copy(
            colorIndex = index,
            items = state.items.map { if (it.id == state.selectedId) it.copy(colorIndex = index) else it },
        )
    }

    fun onOutlineClicked() = updateText { it.copy(isOutlineDialogVisible = true) }

    fun onOutlineDialogDismissed() = updateText { it.copy(isOutlineDialogVisible = false) }

    fun onOutlineConfirmed(widthPermille: Int, colorIndex: Int) {
        val current = currentText() ?: return
        val cutout = current.cutout ?: return
        val settings = OutlineSettings(widthPermille, StickerPalette.pairs[colorIndex].color)
        updateText { it.copy(isOutlineDialogVisible = false, isOutlineRendering = true, outlineFailed = false) }
        viewModelScope.launch {
            outlineRenderer.render(cutout, settings).fold(
                ifLeft = {
                    logger.warn(TAG, "outline failed: $it")
                    updateText { state -> state.copy(isOutlineRendering = false, outlineFailed = true) }
                },
                ifRight = { outlined ->
                    preferences.write(current.isSelectAll, widthPermille, colorIndex)
                    updateText { state ->
                        state.copy(
                            outlinedCutout = outlined,
                            outline = settings,
                            outlineColorIndex = colorIndex,
                            isOutlineRendering = false,
                        )
                    }
                },
            )
        }
    }

    fun onOutlineErrorDismissed() = updateText { it.copy(outlineFailed = false) }

    // endregion

    // region copy

    fun onCopyClicked() {
        val current = currentText() ?: return
        if (current.isBusy) return
        if (!current.canCopy) {
            updateText { it.copy(copy = CopyStatus.Failed(CopyFailure.NOTHING_TO_COPY)) }
            return
        }
        updateText { it.copy(copy = CopyStatus.Copying) }
        viewModelScope.launch {
            copySticker(current.outlinedCutout, current.items).fold(
                ifLeft = { error ->
                    logger.warn(TAG, "copy failed: $error")
                    updateText { it.copy(copy = CopyStatus.Failed(failureOf(error))) }
                },
                ifRight = {
                    updateText { it.copy(copy = CopyStatus.Idle) }
                    _events.tryEmit(EditorEvent.Copied)
                },
            )
        }
    }

    fun onCopyErrorDismissed() = updateText { it.copy(copy = CopyStatus.Idle) }

    private fun failureOf(error: CopyStickerError): CopyFailure = when (error) {
        is CopyStickerError.Export -> when (error.error) {
            is ExportError.StickerTooLarge -> CopyFailure.TOO_LARGE
            ExportError.EmptyComposite -> CopyFailure.NOTHING_TO_COPY
            is ExportError.EncodeFailed -> CopyFailure.STORAGE
        }
        is CopyStickerError.Clipboard -> CopyFailure.STORAGE
    }

    private fun currentText(): TextState? = (_state.value as? EditorUiState.Text)?.state

    private fun updateText(transform: (TextState) -> TextState) {
        val current = _state.value as? EditorUiState.Text ?: return
        val updated = transform(current.state)
        _state.value = EditorUiState.Text(updated)
        savedState[KEY_TEXT] = TextSnapshot.of(updated).encode()
    }

    // endregion

    // region process death

    private fun persistImage(slot: ImageSlot, image: EditorImage) {
        viewModelScope.launch {
            imageStore.save(slot, image).onLeft { logger.warn(TAG, "cannot save ${slot.fileName}: $it") }
        }
    }

    private fun restoreAfterProcessDeath() {
        val step = EditorStep.entries.firstOrNull { it.name == savedState.get<String>(KEY_STEP) } ?: return
        _state.value = EditorUiState.LoadingImage
        when (step) {
            EditorStep.CUTTING -> restoreCuttingFromFile()
            EditorStep.TEXT -> restoreText()
        }
    }

    private fun restoreCuttingFromFile() {
        _state.value = EditorUiState.LoadingImage
        viewModelScope.launch {
            imageStore.load(ImageSlot.WORKING).fold(
                ifLeft = { onRestoreFailed("working image: $it") },
                ifRight = { image ->
                    val tool = CutTool.entries.firstOrNull { it.name == savedState.get<String>(KEY_TOOL) }
                    showCutting(CuttingState(image, tool = tool ?: CutTool.FREEHAND))
                },
            )
        }
    }

    private fun restoreText() {
        val snapshot = TextSnapshot.decode(savedState.get<String>(KEY_TEXT))
        if (snapshot == null) {
            onRestoreFailed("text snapshot is missing")
            return
        }
        viewModelScope.launch {
            if (!snapshot.hasImage) {
                showRestoredText(snapshot, cutout = null, outlined = null)
                return@launch
            }
            imageStore.load(ImageSlot.CUTOUT).fold(
                ifLeft = { onRestoreFailed("cutout: $it") },
                ifRight = { cutout ->
                    val settings = outlineSettingsOf(snapshot)
                    outlineRenderer.render(cutout, settings).fold(
                        ifLeft = { onRestoreFailed("outline: $it") },
                        ifRight = { outlined -> showRestoredText(snapshot, cutout, outlined) },
                    )
                },
            )
        }
    }

    private fun outlineSettingsOf(snapshot: TextSnapshot): OutlineSettings {
        val color = StickerPalette.pairs.getOrNull(snapshot.outlineColorIndex) ?: StickerPalette.pairs.first()
        return OutlineSettings(snapshot.outlineWidthPermille, color.color)
    }

    private fun showRestoredText(snapshot: TextSnapshot, cutout: EditorImage?, outlined: EditorImage?) {
        val items = snapshot.items.map(TextItemSnapshot::toItem)
        nextItemId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        _state.value = EditorUiState.Text(
            TextState(
                cutout = cutout,
                outlinedCutout = outlined,
                isSelectAll = snapshot.isSelectAll,
                outline = outlineSettingsOf(snapshot),
                outlineColorIndex = snapshot.outlineColorIndex,
                items = items,
                selectedId = snapshot.selectedId?.takeIf { id -> items.any { it.id == id } },
                style = snapshot.style,
                font = snapshot.font,
                colorIndex = snapshot.colorIndex,
            ),
        )
    }

    private fun onRestoreFailed(reason: String) {
        logger.warn(TAG, "restore failed, $reason")
        forgetSavedWork()
        _state.value = EditorUiState.Failed(EditorFailure.WORK_LOST)
    }

    private fun forgetSavedWork() {
        cuttingState = null
        savedState.remove<String>(KEY_STEP)
        savedState.remove<String>(KEY_TOOL)
        savedState.remove<String>(KEY_TEXT)
        viewModelScope.launch { imageStore.clear() }
    }

    /** Leaving the editor: the saved work is no longer needed. */
    private fun finish(event: EditorEvent) {
        savedState.remove<String>(KEY_STEP)
        viewModelScope.launch {
            imageStore.clear()
            _events.tryEmit(event)
        }
    }

    // endregion
}
