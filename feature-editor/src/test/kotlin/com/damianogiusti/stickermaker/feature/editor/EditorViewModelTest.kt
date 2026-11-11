package com.damianogiusti.stickermaker.feature.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.Clock
import com.damianogiusti.stickermaker.domain.usecase.AddStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.ReplaceStickerUseCase
import com.damianogiusti.stickermaker.domain.usecase.SetStickerPackTrayUseCase
import com.damianogiusti.stickermaker.imaging.CutError
import com.damianogiusti.stickermaker.imaging.ExportError
import com.damianogiusti.stickermaker.imaging.OutlineError
import com.damianogiusti.stickermaker.imaging.SegmentationError
import com.damianogiusti.stickermaker.imaging.SourceError
import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import com.damianogiusti.stickermaker.imaging.cut.CutSelection
import com.damianogiusti.stickermaker.imaging.source.SourceUri
import com.damianogiusti.stickermaker.imaging.store.ImageSlot
import com.damianogiusti.stickermaker.imaging.text.StickerFont
import com.damianogiusti.stickermaker.imaging.text.TextDrawStyle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val loader = FakeSourceImageLoader()
    private val cutter = FakeImageCutter()
    private val segmenter = FakeSegmenter()
    private val outline = FakeOutlineRenderer()
    private val preferences = FakeOutlinePreferences()
    private val exporter = FakeStickerExporter()
    private val repository = FakeStickerPackRepository()
    private val store = FakeImageStore()
    private val logger = FakeEditorLogger()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(target: EditorTarget = EditorTarget.NewSticker, handle: SavedStateHandle = savedState) = EditorViewModel(
        packId = StickerPackId("42"),
        target = target,
        sourceLoader = loader,
        cutter = cutter,
        segmenter = segmenter,
        outlineRenderer = outline,
        preferences = preferences,
        saveSticker = SaveStickerUseCase(
            exporter, AddStickerUseCase(repository), ReplaceStickerUseCase(repository),
            SetStickerPackTrayUseCase(repository), Clock { 7L },
        ),
        imageStore = store,
        savedState = handle,
        logger = logger,
    )

    private fun EditorViewModel.cutting(): CuttingState = assertIs<EditorUiState.Cutting>(state.value).state
    private fun EditorViewModel.text(): TextState = assertIs<EditorUiState.Text>(state.value).state

    private fun EditorViewModel.openCutting(): EditorViewModel = apply { onImagePicked(SourceUri("content://image")) }

    private fun EditorViewModel.openTextWithOneItem(): EditorViewModel = apply {
        onTextSourceSelected()
        onEditingTextChanged("Hello")
        onEditingConfirmed()
    }

    // region source

    @Test
    fun `starts waiting for the picker`() {
        assertEquals(EditorUiState.WaitingForPicker, viewModel().state.value)
    }

    @Test
    fun `a picked image opens the cutting screen`() {
        val vm = viewModel().openCutting()

        assertEquals(CutTool.FREEHAND, vm.cutting().tool)
        assertEquals(listOf(SourceUri("content://image")), loader.loaded)
    }

    @Test
    fun `an unreadable image shows the failure and retry loads it again`() {
        loader.result = SourceError.Unreadable("boom").left()
        val vm = viewModel().openCutting()
        assertEquals(EditorUiState.Failed(EditorFailure.IMAGE_UNREADABLE), vm.state.value)

        loader.result = arrow.core.Either.Right(fakeBitmap())
        vm.onRetryLoad()

        assertIs<EditorUiState.Cutting>(vm.state.value)
        assertEquals(2, loader.loaded.size)
    }

    @Test
    fun `a cancelled picker emits Cancelled`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onPickerCancelled()
            assertEquals(EditorEvent.Cancelled, awaitItem())
        }
    }

    @Test
    fun `a missing picker app shows a failure`() {
        val vm = viewModel().apply { onPickerUnavailable() }
        assertEquals(EditorUiState.Failed(EditorFailure.NO_PICKER_AVAILABLE), vm.state.value)
    }

    // endregion

    // region cutting

    @Test
    fun `finishing a freehand gesture cuts and opens the text screen with the default outline`() {
        val vm = viewModel().openCutting()
        val points = List(8) { CutPoint(it.toFloat(), it.toFloat()) }

        vm.onFreehandFinished(points)

        assertEquals(listOf<CutSelection>(CutSelection.Lasso(points)), cutter.selections)
        val text = vm.text()
        assertTrue(text.hasImage)
        assertFalse(text.isSelectAll)
        assertEquals(10, text.outline.widthPermille)
        assertEquals(10, outline.rendered.single().widthPermille)
    }

    @Test
    fun `select all uses the select all outline width`() {
        val vm = viewModel().openCutting()

        vm.onSelectAllClicked()

        assertEquals(listOf<CutSelection>(CutSelection.WholeImage), cutter.selections)
        assertTrue(vm.text().isSelectAll)
        assertEquals(0, vm.text().outline.widthPermille)
    }

    @Test
    fun `an empty selection shows a message and stays in cutting`() {
        cutter.cutResult = CutError.EmptySelection.left()
        val vm = viewModel().openCutting()

        vm.onShapeFinished(CutSelection.Rectangle(0f, 0f, 5f, 5f))

        assertEquals(CuttingMessage.EMPTY_SELECTION, vm.cutting().message)
        assertEquals(CuttingBusy.NONE, vm.cutting().busy)
        vm.onMessageDismissed()
        assertNull(vm.cutting().message)
    }

    @Test
    fun `polygon taps add vertices and undo removes the last one`() {
        val vm = viewModel().openCutting()
        vm.onToolSelected(CutTool.POLYGON)

        vm.onPolygonTapped(CutPoint(0f, 0f), 10f)
        vm.onPolygonTapped(CutPoint(100f, 0f), 10f)
        assertEquals(2, vm.cutting().polygonVertices.size)
        assertTrue(vm.cutting().canUndo)

        vm.onPolygonUndo()
        assertEquals(listOf(CutPoint(0f, 0f)), vm.cutting().polygonVertices)
        vm.onPolygonUndo()
        assertFalse(vm.cutting().canUndo)
    }

    @Test
    fun `tapping near the first vertex closes the polygon and cuts it`() {
        val vm = viewModel().openCutting()
        vm.onToolSelected(CutTool.POLYGON)
        val triangle = listOf(CutPoint(0f, 0f), CutPoint(100f, 0f), CutPoint(50f, 80f))
        triangle.forEach { vm.onPolygonTapped(it, 10f) }

        vm.onPolygonTapped(CutPoint(3f, 3f), 10f)

        assertEquals(listOf<CutSelection>(CutSelection.Polygon(triangle)), cutter.selections)
        assertIs<EditorUiState.Text>(vm.state.value)
    }

    @Test
    fun `polygon taps are ignored with another tool`() {
        val vm = viewModel().openCutting()

        vm.onPolygonTapped(CutPoint(1f, 1f), 10f)

        assertTrue(vm.cutting().polygonVertices.isEmpty())
    }

    @Test
    fun `rotate swaps the image and resets the viewport`() {
        val vm = viewModel().openCutting()
        vm.onViewportChanged(CutViewport(2f, 10f, 10f))
        val rotated = fakeBitmap()
        cutter.rotateResult = arrow.core.Either.Right(rotated)

        vm.onRotateClicked()

        assertTrue(vm.cutting().image === rotated)
        assertNull(vm.cutting().viewport)
        assertEquals(CuttingBusy.NONE, vm.cutting().busy)
    }

    @Test
    fun `magic with a subject cuts by mask and opens the text screen`() {
        val vm = viewModel().openCutting()

        vm.onMagicClicked()

        assertEquals(1, cutter.maskCuts.size)
        assertIs<EditorUiState.Text>(vm.state.value)
    }

    @Test
    fun `magic without a subject reports no subject found`() {
        segmenter.result = SegmentationError.NoSubjectFound.left()
        val vm = viewModel().openCutting()

        vm.onMagicClicked()

        assertEquals(CuttingMessage.NO_SUBJECT_FOUND, vm.cutting().message)
        assertFalse(vm.cutting().isBusy)
    }

    @Test
    fun `magic failure is reported as a failure`() {
        segmenter.result = SegmentationError.Failed("model").left()
        val vm = viewModel().openCutting()

        vm.onMagicClicked()

        assertEquals(CuttingMessage.MAGIC_FAILED, vm.cutting().message)
    }

    @Test
    fun `an outline failure keeps the user in cutting`() {
        outline.result = OutlineError.Failed("oom").left()
        val vm = viewModel().openCutting()

        vm.onSelectAllClicked()

        assertEquals(CuttingMessage.OUTLINE_FAILED, vm.cutting().message)
    }

    @Test
    fun `back from the text screen restores tool, polygon and viewport`() {
        val vm = viewModel().openCutting()
        vm.onToolSelected(CutTool.CIRCLE)
        vm.onViewportChanged(CutViewport(3f, 40f, 50f))
        vm.onSelectAllClicked()

        vm.onBackPressed()

        val cutting = vm.cutting()
        assertEquals(CutTool.CIRCLE, cutting.tool)
        assertEquals(CutViewport(3f, 40f, 50f), cutting.viewport)
        assertEquals(CuttingBusy.NONE, cutting.busy)
    }

    @Test
    fun `back from the cutting screen cancels`() = runTest {
        val vm = viewModel().openCutting()
        vm.events.test {
            vm.onBackPressed()
            assertEquals(EditorEvent.Cancelled, awaitItem())
        }
    }

    // endregion

    // region text

    @Test
    fun `text source opens the text screen with the add dialog and no image`() {
        val vm = viewModel().apply { onTextSourceSelected() }

        val text = vm.text()
        assertFalse(text.hasImage)
        assertEquals(TextEditing(null, ""), text.editing)
    }

    @Test
    fun `confirming a text adds a selected item at the canvas center`() {
        val vm = viewModel().openTextWithOneItem()

        val text = vm.text()
        val item = text.items.single()
        assertEquals("Hello", item.text)
        assertEquals(item.id, text.selectedId)
        assertEquals(512f, item.centerX)
        assertNull(text.editing)
    }

    @Test
    fun `a blank text adds nothing`() {
        val vm = viewModel().apply {
            onTextSourceSelected()
            onEditingTextChanged("   ")
            onEditingConfirmed()
        }

        assertTrue(vm.text().items.isEmpty())
        assertNull(vm.text().editing)
    }

    @Test
    fun `tapping the selected text edits it and confirming changes its text`() {
        val vm = viewModel().openTextWithOneItem()
        val id = vm.text().items.single().id

        vm.onItemTapped(id)
        assertEquals(TextEditing(id, "Hello"), vm.text().editing)
        vm.onEditingTextChanged("Ciao")
        vm.onEditingConfirmed()

        assertEquals("Ciao", vm.text().items.single().text)
    }

    @Test
    fun `transform moves, scales within 0_5 and 10, and rotates`() {
        val vm = viewModel().openTextWithOneItem()
        val id = vm.text().items.single().id

        vm.onItemTransformed(id, dx = 10f, dy = -20f, zoom = 100f, rotationDelta = 15f)
        var item = vm.text().items.single()
        assertEquals(522f, item.centerX)
        assertEquals(492f, item.centerY)
        assertEquals(10f, item.scale)
        assertEquals(15f, item.rotationDegrees)

        vm.onItemTransformed(id, 0f, 0f, 0.001f, 0f)
        item = vm.text().items.single()
        assertEquals(0.5f, item.scale)
    }

    @Test
    fun `releasing over the delete zone removes the item`() {
        val vm = viewModel().openTextWithOneItem()
        val id = vm.text().items.single().id

        vm.onItemReleased(id, isOverDeleteZone = false)
        assertEquals(1, vm.text().items.size)
        vm.onItemReleased(id, isOverDeleteZone = true)

        assertTrue(vm.text().items.isEmpty())
        assertNull(vm.text().selectedId)
    }

    @Test
    fun `style font and color toggles change the selected item and the defaults`() {
        val vm = viewModel().openTextWithOneItem()

        vm.onStyleToggled()
        vm.onFontToggled()
        vm.onColorSelected(4)

        val item = vm.text().items.single()
        assertEquals(TextDrawStyle.WITH_STROKE, item.style)
        assertEquals(StickerFont.ALFA_SLAB_ONE, item.font)
        assertEquals(4, item.colorIndex)
        assertEquals(TextDrawStyle.WITH_STROKE, vm.text().style)
    }

    @Test
    fun `toggles without a selection only change the defaults used by the next text`() {
        val vm = viewModel().openTextWithOneItem()
        vm.onCanvasTapped()
        vm.onStyleToggled()
        vm.onColorSelected(2)

        vm.onAddTextClicked()
        vm.onEditingTextChanged("Next")
        vm.onEditingConfirmed()

        val (first, second) = vm.text().items
        assertEquals(TextDrawStyle.WITH_BACKGROUND, first.style)
        assertEquals(TextDrawStyle.WITH_STROKE, second.style)
        assertEquals(2, second.colorIndex)
    }

    @Test
    fun `back on a text only sticker cancels`() = runTest {
        val vm = viewModel().openTextWithOneItem()
        vm.events.test {
            vm.onBackPressed()
            assertEquals(EditorEvent.Cancelled, awaitItem())
        }
    }

    // endregion

    // region outline

    @Test
    fun `confirming the outline re-renders, persists and closes the dialog`() {
        val vm = viewModel().openCutting().apply { onFreehandFinished(List(6) { CutPoint(it.toFloat(), 0f) }) }
        val rendered = fakeBitmap()
        outline.result = arrow.core.Either.Right(rendered)
        vm.onOutlineClicked()
        assertTrue(vm.text().isOutlineDialogVisible)

        vm.onOutlineConfirmed(widthPermille = 25, colorIndex = 1)

        val text = vm.text()
        assertFalse(text.isOutlineDialogVisible)
        assertFalse(text.isOutlineRendering)
        assertEquals(25, text.outline.widthPermille)
        assertEquals(1, text.outlineColorIndex)
        assertTrue(text.outlinedCutout === rendered)
        assertEquals(listOf(Triple(false, 25, 1)), preferences.writes)
    }

    @Test
    fun `stored outline defaults are used for the next sticker`() {
        preferences.defaults = OutlineDefaults(widthPermille = 33, widthPermilleSelectAll = 5, colorIndex = 6)
        val vm = viewModel().openCutting()

        vm.onShapeFinished(CutSelection.Oval(0f, 0f, 10f, 10f))

        assertEquals(33, vm.text().outline.widthPermille)
        assertEquals(6, vm.text().outlineColorIndex)
    }

    @Test
    fun `an outline failure keeps the previous outline and flags the error`() {
        val vm = viewModel().openCutting().apply { onSelectAllClicked() }
        val before = vm.text().outlinedCutout
        outline.result = OutlineError.Failed("oom").left()

        vm.onOutlineConfirmed(30, 2)

        assertTrue(vm.text().outlineFailed)
        assertTrue(vm.text().outlinedCutout === before)
        assertTrue(preferences.writes.isEmpty())
    }

    // endregion

    // region save

    @Test
    fun `save stores the sticker and emits Done`() = runTest {
        val vm = viewModel().openCutting().apply { onSelectAllClicked() }
        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditorEvent.Done, awaitItem())
        }
        assertEquals("7.webp", repository.added.single().fileName)
        assertEquals(1, exporter.compositions.size)
        assertNotNull(exporter.compositions.single().outlinedCutout)
    }

    @Test
    fun `save of a replace target replaces the file`() {
        val vm = viewModel(EditorTarget.ReplaceSticker("old.webp")).openTextWithOneItem()

        vm.onSaveClicked()

        assertEquals("old.webp", repository.replaced.single().first)
    }

    @Test
    fun `save of the tray target stores tray png`() {
        val vm = viewModel(EditorTarget.Tray).openTextWithOneItem()

        vm.onSaveClicked()

        assertEquals("tray.png", repository.trays.single().fileName)
    }

    @Test
    fun `a file that is too large shows an error and can be dismissed`() {
        exporter.stickerResult = ExportError.StickerTooLarge(300_000, 102_400).left()
        val vm = viewModel().openTextWithOneItem()

        vm.onSaveClicked()

        assertEquals(SaveStatus.Failed(SaveFailure.TOO_LARGE), vm.text().save)
        vm.onSaveErrorDismissed()
        assertEquals(SaveStatus.Idle, vm.text().save)
    }

    @Test
    fun `a full pack shows the too many stickers error`() {
        repository.stickerCount = 30
        val vm = viewModel().openTextWithOneItem()

        vm.onSaveClicked()

        assertEquals(SaveStatus.Failed(SaveFailure.TOO_MANY_STICKERS), vm.text().save)
    }

    @Test
    fun `a storage error shows the storage failure and retry saves again`() {
        repository.failure = StickerPackError.Storage("disk")
        val vm = viewModel().openTextWithOneItem()
        vm.onSaveClicked()
        assertEquals(SaveStatus.Failed(SaveFailure.STORAGE), vm.text().save)

        repository.failure = null
        vm.onSaveClicked()

        assertEquals(1, repository.added.size)
    }

    @Test
    fun `saving with nothing on the canvas reports nothing to save`() {
        val vm = viewModel().apply { onTextSourceSelected(); onEditingDismissed() }

        vm.onSaveClicked()

        assertEquals(SaveStatus.Failed(SaveFailure.NOTHING_TO_SAVE), vm.text().save)
        assertTrue(repository.added.isEmpty())
    }

    // endregion

    // region process death

    @Test
    fun `a picked image is saved as the working image`() {
        val image = fakeBitmap()
        loader.result = arrow.core.Either.Right(image)

        viewModel().openCutting()

        assertEquals(image, store.images[ImageSlot.WORKING])
    }

    @Test
    fun `recreation in the cutting step restores the working image and the tool`() {
        val image = fakeBitmap()
        loader.result = arrow.core.Either.Right(image)
        viewModel().openCutting().onToolSelected(CutTool.CIRCLE)

        val restored = viewModel()

        assertEquals(image, restored.cutting().image)
        assertEquals(CutTool.CIRCLE, restored.cutting().tool)
        assertEquals(1, loader.loaded.size)
    }

    @Test
    fun `recreation in the text step restores the cutout, the outline and the texts`() {
        val first = viewModel().openCutting()
        first.onSelectAllClicked()
        first.onAddTextClicked()
        first.onEditingTextChanged("Hello")
        first.onEditingConfirmed()
        first.onOutlineConfirmed(widthPermille = 20, colorIndex = 2)
        first.onItemTransformed(1L, dx = 10f, dy = 0f, zoom = 1f, rotationDelta = 0f)
        val saved = first.text()

        val restored = viewModel().text()

        assertEquals(store.images[ImageSlot.CUTOUT], restored.cutout)
        assertTrue(restored.isSelectAll)
        assertEquals(saved.items, restored.items)
        assertEquals(saved.selectedId, restored.selectedId)
        assertEquals(20, restored.outline.widthPermille)
        assertEquals(2, restored.outlineColorIndex)
        assertNotNull(restored.outlinedCutout)
        assertEquals(20, outline.rendered.last().widthPermille)
    }

    @Test
    fun `restored texts do not reuse an existing id`() {
        val first = viewModel().openTextWithOneItem()
        val restored = viewModel()

        restored.onAddTextClicked()
        restored.onEditingTextChanged("World")
        restored.onEditingConfirmed()

        assertEquals(listOf(1L, 2L), restored.text().items.map { it.id })
        assertEquals(1, first.text().items.size)
    }

    @Test
    fun `a text only sticker is restored without images`() {
        viewModel().openTextWithOneItem()

        val restored = viewModel().text()

        assertFalse(restored.hasImage)
        assertEquals("Hello", restored.items.single().text)
    }

    @Test
    fun `a missing working image shows the lost work failure instead of a spinner`() {
        viewModel().openCutting()
        store.images.clear()

        val restored = viewModel()

        assertEquals(EditorUiState.Failed(EditorFailure.WORK_LOST), restored.state.value)
        assertEquals(1, logger.warnings.size)
    }

    @Test
    fun `an unreadable cutout shows the lost work failure`() {
        viewModel().openCutting().onSelectAllClicked()
        store.failLoads = true

        assertEquals(EditorUiState.Failed(EditorFailure.WORK_LOST), viewModel().state.value)
    }

    @Test
    fun `a broken text snapshot shows the lost work failure`() {
        viewModel().openTextWithOneItem()
        savedState["editor.text"] = "{not json"

        assertEquals(EditorUiState.Failed(EditorFailure.WORK_LOST), viewModel().state.value)
    }

    @Test
    fun `picking again after a lost work forgets it and asks for the picker`() = runTest {
        viewModel().openCutting()
        store.images.clear()
        val restored = viewModel()

        restored.events.test {
            restored.onPickAgainClicked()
            assertEquals(EditorEvent.LaunchPicker, awaitItem())
        }
        assertEquals(EditorUiState.WaitingForPicker, restored.state.value)
        assertEquals(EditorUiState.WaitingForPicker, viewModel().state.value)
    }

    @Test
    fun `back from a restored text step reloads the working image`() {
        viewModel().openCutting().onSelectAllClicked()
        val restored = viewModel()

        restored.onBackPressed()

        assertIs<EditorUiState.Cutting>(restored.state.value)
    }

    @Test
    fun `leaving the editor clears the saved work`() = runTest {
        val vm = viewModel().openCutting()

        vm.events.test {
            vm.onBackPressed()
            assertEquals(EditorEvent.Cancelled, awaitItem())
        }

        assertTrue(store.images.isEmpty())
        assertEquals(EditorUiState.WaitingForPicker, viewModel().state.value)
    }

    // endregion
}
