package com.damianogiusti.stickermaker.feature.editor.text

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.damianogiusti.stickermaker.feature.editor.R
import com.damianogiusti.stickermaker.feature.editor.SaveFailure
import com.damianogiusti.stickermaker.feature.editor.SaveStatus
import com.damianogiusti.stickermaker.feature.editor.TextEditing
import com.damianogiusti.stickermaker.feature.editor.TextState
import com.damianogiusti.stickermaker.imaging.asBitmap
import com.damianogiusti.stickermaker.imaging.outline.OutlineMath
import com.damianogiusti.stickermaker.imaging.text.StickerFont
import com.damianogiusti.stickermaker.imaging.text.StickerPalette
import com.damianogiusti.stickermaker.imaging.text.StickerTextItem
import com.damianogiusti.stickermaker.imaging.text.TextDrawStyle
import com.damianogiusti.stickermaker.imaging.text.TextItemGeometry
import com.damianogiusti.stickermaker.imaging.text.TextItemPainter

class TextEditorActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onSaveErrorDismissed: () -> Unit,
    val onAddText: () -> Unit,
    val onEditingTextChanged: (String) -> Unit,
    val onEditingConfirmed: () -> Unit,
    val onEditingDismissed: () -> Unit,
    val onItemTapped: (Long) -> Unit,
    val onCanvasTapped: () -> Unit,
    val onItemTransformed: (id: Long, dx: Float, dy: Float, zoom: Float, rotationDelta: Float) -> Unit,
    val onItemReleased: (id: Long, isOverDeleteZone: Boolean) -> Unit,
    val onStyleToggled: () -> Unit,
    val onFontToggled: () -> Unit,
    val onColorSelected: (Int) -> Unit,
    val onOutlineClicked: () -> Unit,
    val onOutlineDialogDismissed: () -> Unit,
    val onOutlineConfirmed: (widthPermille: Int, colorIndex: Int) -> Unit,
    val onOutlineErrorDismissed: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorContent(state: TextState, painter: TextItemPainter, actions: TextEditorActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack, enabled = state.save != SaveStatus.Saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                    }
                },
                actions = {
                    TextButton(onClick = actions.onSave, enabled = state.canSave && !state.isBusy) {
                        Text(stringResource(R.string.text_save))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                    StickerCanvas(state, painter, actions, Modifier.size(side))
                }
                TextControls(state, actions)
            }
            if (state.isBusy) BusyOverlay(isSaving = state.save == SaveStatus.Saving)
        }
    }
    state.editing?.let { TextEditingDialog(it, actions) }
    if (state.isOutlineDialogVisible) OutlineDialog(state, actions)
    (state.save as? SaveStatus.Failed)?.let { SaveFailedDialog(it.reason, actions) }
    if (state.outlineFailed) {
        AlertDialog(
            onDismissRequest = actions.onOutlineErrorDismissed,
            text = { Text(stringResource(R.string.cut_message_outline_failed)) },
            confirmButton = { TextButton(onClick = actions.onOutlineErrorDismissed) { Text(stringResource(R.string.editor_ok)) } },
        )
    }
}

/** The 1024x1024 sticker canvas drawn at screen size. Gestures move, pinch and rotate the text under the finger. */
@Composable
private fun StickerCanvas(state: TextState, painter: TextItemPainter, actions: TextEditorActions, modifier: Modifier) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val currentState by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var isOverDeleteZone by remember { mutableStateOf(false) }
    val deleteZonePx = with(density) { DELETE_ZONE_DP.dp.toPx() }
    val zoneScale by animateFloatAsState(if (isOverDeleteZone) DELETE_ZONE_HOVER_SCALE else 1f, label = "deleteZoneScale")

    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val unit = size.width / CANVAS_SIDE
                        val items = currentState.items
                        val start = down.position / unit
                        val hit = items.lastOrNull { TextItemGeometry.contains(it, painter.measure(it), start.x, start.y) }
                        var hasMoved = false
                        var travelled = 0f
                        var lastPosition = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            lastPosition = change.position
                            if (hit != null) {
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                travelled += pan.getDistance()
                                val isMultiTouch = event.changes.count { it.pressed } > 1
                                if (hasMoved || isMultiTouch || travelled > viewConfiguration.touchSlop) {
                                    hasMoved = true
                                    draggingId = hit.id
                                    currentActions.onItemTransformed(hit.id, pan.x / unit, pan.y / unit, zoom, rotation)
                                    isOverDeleteZone = isInDeleteZone(lastPosition, size.width.toFloat(), size.height.toFloat(), deleteZonePx)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        when {
                            hit == null -> currentActions.onCanvasTapped()
                            !hasMoved -> currentActions.onItemTapped(hit.id)
                            else -> currentActions.onItemReleased(
                                hit.id,
                                isInDeleteZone(lastPosition, size.width.toFloat(), size.height.toFloat(), deleteZonePx),
                            )
                        }
                        draggingId = null
                        isOverDeleteZone = false
                    }
                },
        ) {
            drawCheckerboard(CHECKER_TILE_DP.dp.toPx())
            val unit = size.width / CANVAS_SIDE
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(unit, unit)
                state.outlinedCutout?.let { native.drawBitmap(it.asBitmap(), 0f, 0f, bitmapPaint) }
                state.items.forEach { painter.draw(native, it) }
                native.restore()
            }
            state.selected?.let { item ->
                val box = painter.measure(item)
                val width = box.width * item.scale * unit
                val height = box.height * item.scale * unit
                withTransform({
                    translate(item.centerX * unit, item.centerY * unit)
                    rotate(item.rotationDegrees, Offset.Zero)
                }) {
                    drawRect(
                        color = primary,
                        topLeft = Offset(-width / 2f, -height / 2f),
                        size = Size(width, height),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))),
                    )
                }
            }
        }
        if (state.items.isEmpty() && !state.hasImage) {
            Text(
                stringResource(R.string.text_empty_hint),
                modifier = Modifier.align(Alignment.Center),
                color = Color.DarkGray,
            )
        }
        if (draggingId != null) {
            val deleteDescription = stringResource(R.string.text_delete_zone_cd)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .scale(zoneScale)
                    .size(DELETE_ZONE_DP.dp)
                    .background(if (isOverDeleteZone) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.55f), CircleShape)
                    .semantics { contentDescription = deleteDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        }
    }
}

private fun isInDeleteZone(position: Offset, width: Float, height: Float, zonePx: Float): Boolean =
    position.y > height - zonePx * DELETE_ZONE_HIT_FACTOR && abs(position.x - width / 2f) < zonePx * DELETE_ZONE_HIT_FACTOR

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard(tile: Float) {
    val light = Color(0xFFD9D9D9)
    val dark = Color(0xFFBDBDBD)
    drawRect(light)
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 2 == 1) drawRect(dark, Offset(x, y), Size(tile, tile))
            x += tile
            column++
        }
        y += tile
        row++
    }
}

@Composable
private fun TextControls(state: TextState, actions: TextEditorActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = actions.onAddText) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.text_add), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = actions.onStyleToggled) {
                Text(stringResource(R.string.text_style_label, styleName(state.style)))
            }
            OutlinedButton(onClick = actions.onFontToggled) {
                Text(stringResource(R.string.text_font_label, fontName(state.font)))
            }
            if (state.hasImage) {
                OutlinedButton(onClick = actions.onOutlineClicked) { Text(stringResource(R.string.outline_button)) }
            }
        }
        ColorSwatches(selectedIndex = state.colorIndex, onSelected = actions.onColorSelected)
    }
}

@Composable
private fun styleName(style: TextDrawStyle): String = stringResource(
    when (style) {
        TextDrawStyle.WITH_BACKGROUND -> R.string.text_style_with_background
        TextDrawStyle.WITH_STROKE -> R.string.text_style_with_stroke
        TextDrawStyle.WITHOUT_BACKGROUND -> R.string.text_style_without_background
    },
)

@Composable
private fun fontName(font: StickerFont): String =
    if (font == StickerFont.SYSTEM) stringResource(R.string.text_font_system) else font.displayName

/** The 7 color pairs: the circle is the swatch color, the letter shows the text color that goes with it. */
@Composable
private fun ColorSwatches(selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StickerPalette.pairs.forEachIndexed { index, pair ->
            val description = stringResource(R.string.text_color_cd, index + 1)
            val border = if (index == selectedIndex) {
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
            Box(
                Modifier
                    .size(SWATCH_DP.dp)
                    .background(Color(pair.color), CircleShape)
                    .border(border, CircleShape)
                    .clickable { onSelected(index) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text("A", color = Color(pair.textColor), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BusyOverlay(isSaving: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(
                stringResource(if (isSaving) R.string.save_progress else R.string.outline_rendering),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TextEditingDialog(editing: TextEditing, actions: TextEditorActions) {
    AlertDialog(
        onDismissRequest = actions.onEditingDismissed,
        title = {
            Text(stringResource(if (editing.itemId == null) R.string.text_dialog_new_title else R.string.text_dialog_edit_title))
        },
        text = {
            OutlinedTextField(
                value = editing.text,
                onValueChange = actions.onEditingTextChanged,
                label = { Text(stringResource(R.string.text_dialog_hint)) },
                maxLines = MAX_TEXT_LINES,
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onEditingConfirmed, enabled = editing.text.isNotBlank()) {
                Text(stringResource(R.string.editor_ok))
            }
        },
        dismissButton = { TextButton(onClick = actions.onEditingDismissed) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

@Composable
private fun OutlineDialog(state: TextState, actions: TextEditorActions) {
    var width by remember { mutableFloatStateOf(state.outline.widthPermille.toFloat()) }
    var colorIndex by remember { mutableStateOf(state.outlineColorIndex) }
    AlertDialog(
        onDismissRequest = actions.onOutlineDialogDismissed,
        title = { Text(stringResource(R.string.outline_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.outline_dialog_color), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(selectedIndex = colorIndex, onSelected = { colorIndex = it })
                Text(
                    stringResource(R.string.outline_dialog_width, width.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = width,
                    onValueChange = { width = it },
                    valueRange = 0f..OutlineMath.MAX_WIDTH_PERMILLE.toFloat(),
                    steps = OutlineMath.MAX_WIDTH_PERMILLE - 1,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onOutlineConfirmed(width.roundToInt(), colorIndex) }) {
                Text(stringResource(R.string.outline_dialog_apply))
            }
        },
        dismissButton = { TextButton(onClick = actions.onOutlineDialogDismissed) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

@Composable
private fun SaveFailedDialog(reason: SaveFailure, actions: TextEditorActions) {
    val message = when (reason) {
        SaveFailure.TOO_LARGE -> R.string.save_failed_too_large
        SaveFailure.TOO_MANY_STICKERS -> R.string.save_failed_too_many
        SaveFailure.NOTHING_TO_SAVE -> R.string.save_failed_nothing
        SaveFailure.STORAGE -> R.string.save_failed_storage
    }
    val canRetry = reason == SaveFailure.TOO_LARGE || reason == SaveFailure.STORAGE
    AlertDialog(
        onDismissRequest = actions.onSaveErrorDismissed,
        title = { Text(stringResource(R.string.save_failed_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            if (canRetry) {
                TextButton(onClick = actions.onSave) { Text(stringResource(R.string.editor_try_again)) }
            } else {
                TextButton(onClick = actions.onSaveErrorDismissed) { Text(stringResource(R.string.editor_ok)) }
            }
        },
        dismissButton = if (canRetry) {
            { TextButton(onClick = actions.onSaveErrorDismissed) { Text(stringResource(R.string.editor_cancel)) } }
        } else {
            null
        },
    )
}

private const val CANVAS_SIDE = 1024f
private const val DELETE_ZONE_DP = 72f
private const val DELETE_ZONE_HOVER_SCALE = 1.4f
private const val DELETE_ZONE_HIT_FACTOR = 1.2f
private const val CHECKER_TILE_DP = 16f
private const val SWATCH_DP = 36f
private const val MAX_TEXT_LINES = 4
