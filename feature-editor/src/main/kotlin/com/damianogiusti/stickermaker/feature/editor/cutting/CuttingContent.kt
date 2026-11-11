package com.damianogiusti.stickermaker.feature.editor.cutting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.damianogiusti.stickermaker.feature.editor.CutTool
import com.damianogiusti.stickermaker.feature.editor.CutViewport
import com.damianogiusti.stickermaker.feature.editor.CuttingBusy
import com.damianogiusti.stickermaker.feature.editor.CuttingMessage
import com.damianogiusti.stickermaker.feature.editor.CuttingState
import com.damianogiusti.stickermaker.feature.editor.GestureRules
import com.damianogiusti.stickermaker.feature.editor.R
import com.damianogiusti.stickermaker.imaging.asBitmap
import com.damianogiusti.stickermaker.imaging.cut.CutPoint
import com.damianogiusti.stickermaker.imaging.cut.CutSelection
import com.damianogiusti.stickermaker.imaging.cut.SelectionPaths
import kotlin.math.max
import kotlin.math.min

class CuttingActions(
    val onBack: () -> Unit,
    val onToolSelected: (CutTool) -> Unit,
    val onFreehandFinished: (List<CutPoint>) -> Unit,
    val onShapeFinished: (CutSelection) -> Unit,
    val onPolygonTapped: (CutPoint, Float) -> Unit,
    val onPolygonUndo: () -> Unit,
    val onSelectAll: () -> Unit,
    val onRotate: () -> Unit,
    val onMagic: () -> Unit,
    val onViewportChanged: (CutViewport) -> Unit,
    val onMessageDismissed: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuttingContent(state: CuttingState, actions: CuttingActions) {
    var isDrawing by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cut_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            CutCanvas(state = state, actions = actions, onDrawingChanged = { isDrawing = it })
            // The tool bar floats over the canvas, so hiding it while drawing does not resize the canvas.
            if (!isDrawing) {
                CuttingToolbar(state, actions, Modifier.align(Alignment.BottomCenter))
            }
            if (state.isBusy) BusyOverlay(state.busy)
        }
    }
    state.message?.let { MessageDialog(it, actions.onMessageDismissed) }
}

@Composable
private fun CuttingToolbar(state: CuttingState, actions: CuttingActions, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolChip(CutTool.FREEHAND, R.string.cut_tool_freehand, state, actions)
            ToolChip(CutTool.SQUARE, R.string.cut_tool_square, state, actions)
            ToolChip(CutTool.CIRCLE, R.string.cut_tool_circle, state, actions)
            ToolChip(CutTool.POLYGON, R.string.cut_tool_polygon, state, actions)
            if (state.tool == CutTool.POLYGON) {
                AssistChip(
                    onClick = actions.onPolygonUndo,
                    enabled = state.canUndo,
                    label = { Text(stringResource(R.string.cut_action_undo)) },
                )
            }
            AssistChip(onClick = actions.onSelectAll, label = { Text(stringResource(R.string.cut_action_select_all)) })
            AssistChip(onClick = actions.onRotate, label = { Text(stringResource(R.string.cut_action_rotate)) })
            AssistChip(onClick = actions.onMagic, label = { Text(stringResource(R.string.cut_action_magic)) })
        }
    }
}

@Composable
private fun ToolChip(tool: CutTool, labelRes: Int, state: CuttingState, actions: CuttingActions) {
    FilterChip(
        selected = state.tool == tool,
        onClick = { actions.onToolSelected(tool) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun BusyOverlay(busy: CuttingBusy) {
    val label = when (busy) {
        CuttingBusy.MAGIC -> R.string.cut_busy_magic
        CuttingBusy.ROTATING -> R.string.cut_busy_rotating
        CuttingBusy.CUTTING, CuttingBusy.NONE -> R.string.cut_busy_cutting
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallows touches so the user cannot draw while the work runs.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(label), color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MessageDialog(message: CuttingMessage, onDismiss: () -> Unit) {
    val title: Int?
    val body: Int
    when (message) {
        CuttingMessage.NO_SUBJECT_FOUND -> { title = R.string.cut_message_no_subject_title; body = R.string.cut_message_no_subject_body }
        CuttingMessage.MAGIC_FAILED -> { title = null; body = R.string.cut_message_magic_failed }
        CuttingMessage.CUT_FAILED -> { title = null; body = R.string.cut_message_cut_failed }
        CuttingMessage.EMPTY_SELECTION -> { title = null; body = R.string.cut_message_empty_selection }
        CuttingMessage.ROTATE_FAILED -> { title = null; body = R.string.cut_message_rotate_failed }
        CuttingMessage.OUTLINE_FAILED -> { title = null; body = R.string.cut_message_outline_failed }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(stringResource(it)) } },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_ok)) } },
    )
}

/** Zoom/pan image with the cut gestures on top. One finger draws (or pans in polygon mode), two fingers zoom and pan. */
@Composable
private fun CutCanvas(state: CuttingState, actions: CuttingActions, onDrawingChanged: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val imageBitmap = remember(state.image) { state.image.asBitmap().asImageBitmap() }
    val imageWidth = state.image.width
    val imageHeight = state.image.height

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(state.image) { mutableFloatStateOf(0f) }
    var offset by remember(state.image) { mutableStateOf(Offset.Zero) }
    var fitScale by remember(state.image) { mutableFloatStateOf(1f) }
    val lasso = remember { mutableStateListOf<CutPoint>() }
    var boxStart by remember { mutableStateOf<CutPoint?>(null) }
    var boxEnd by remember { mutableStateOf<CutPoint?>(null) }
    var finger by remember { mutableStateOf<Offset?>(null) }

    val currentState by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)

    LaunchedEffect(canvasSize, state.image) {
        if (canvasSize == IntSize.Zero || scale > 0f) return@LaunchedEffect
        fitScale = min(canvasSize.width.toFloat() / imageWidth, canvasSize.height.toFloat() / imageHeight)
        val viewport = state.viewport
        scale = viewport?.scale ?: fitScale
        val centerX = viewport?.centerX ?: (imageWidth / 2f)
        val centerY = viewport?.centerY ?: (imageHeight / 2f)
        offset = Offset(canvasSize.width / 2f - centerX * scale, canvasSize.height / 2f - centerY * scale)
    }

    val densityDpi = density.density * 160f
    val closeRadiusPx = with(density) { (POLYGON_BULLET_DP * POLYGON_CLOSE_FACTOR).dp.toPx() }

    fun toImage(screen: Offset) = CutPoint((screen.x - offset.x) / scale, (screen.y - offset.y) / scale)

    fun clearDraft() {
        lasso.clear()
        boxStart = null
        boxEnd = null
        finger = null
        onDrawingChanged(false)
    }

    fun reportViewport() {
        if (scale <= 0f) return
        currentActions.onViewportChanged(
            CutViewport(
                scale = scale,
                centerX = (canvasSize.width / 2f - offset.x) / scale,
                centerY = (canvasSize.height / 2f - offset.y) / scale,
            ),
        )
    }

    fun applyTransform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(fitScale * MIN_ZOOM_FACTOR, max(fitScale * MAX_ZOOM_FACTOR, MAX_SCALE_FLOOR))
        offset = centroid - (centroid - offset) * (newScale / scale) + pan
        scale = newScale
    }

    fun finishDraft(tool: CutTool) {
        val strokeInImage = GestureRules.strokeWidthPx(densityDpi) / scale
        when (tool) {
            CutTool.FREEHAND -> if (GestureRules.isLassoAccepted(lasso)) currentActions.onFreehandFinished(lasso.toList())
            CutTool.SQUARE, CutTool.CIRCLE -> {
                val start = boxStart
                val end = boxEnd
                val box = if (start != null && end != null) GestureRules.normalizedBox(start, end, strokeInImage) else null
                if (box != null) {
                    currentActions.onShapeFinished(if (tool == CutTool.SQUARE) box.toRectangle() else box.toOval())
                }
            }
            CutTool.POLYGON -> Unit
        }
    }

    val strokePx = with(density) { PREVIEW_STROKE_DP.dp.toPx() }
    val loupeRadiusPx = with(density) { LOUPE_RADIUS_DP.dp.toPx() }
    val loupeMarginPx = with(density) { LOUPE_MARGIN_DP.dp.toPx() }
    val bulletPx = with(density) { POLYGON_BULLET_DP.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { canvasSize = it }
            .pointerInput(state.tool, state.image) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (scale <= 0f) return@awaitEachGesture
                    val tool = currentState.tool
                    val canDraw = !currentState.isBusy
                    var isMultiTouch = false
                    var hasDragged = false
                    var travelled = 0f

                    if (canDraw && tool != CutTool.POLYGON) {
                        val point = toImage(down.position)
                        finger = down.position
                        if (tool == CutTool.FREEHAND) lasso.add(point) else { boxStart = point; boxEnd = point }
                        onDrawingChanged(true)
                    }
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            if (!isMultiTouch) {
                                isMultiTouch = true
                                clearDraft()
                            }
                            applyTransform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                            event.changes.forEach { it.consume() }
                        } else if (!isMultiTouch) {
                            val change = event.changes.firstOrNull() ?: break
                            if (tool == CutTool.POLYGON) {
                                travelled += change.positionChange().getDistance()
                                if (travelled > viewConfiguration.touchSlop) {
                                    hasDragged = true
                                    offset += change.positionChange()
                                }
                            } else if (canDraw) {
                                val point = toImage(change.position)
                                finger = change.position
                                if (tool == CutTool.FREEHAND) {
                                    val minStep = GestureRules.lassoMinStepInImagePx(densityDpi, scale)
                                    if (GestureRules.shouldAddLassoPoint(lasso.lastOrNull(), point, minStep)) lasso.add(point)
                                } else {
                                    boxEnd = point
                                }
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    if (!isMultiTouch && canDraw) {
                        if (tool == CutTool.POLYGON) {
                            if (!hasDragged) {
                                currentActions.onPolygonTapped(toImage(down.position), closeRadiusPx / scale)
                            }
                        } else {
                            finishDraft(tool)
                        }
                    }
                    clearDraft()
                    if (isMultiTouch || hasDragged) reportViewport()
                }
            },
    ) {
        if (scale <= 0f) return@Canvas

        fun scene(scope: DrawScope) = with(scope) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                drawImage(imageBitmap, filterQuality = FilterQuality.Medium)
                val width = strokePx / scale
                val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_PX / scale, DASH_OFF_PX / scale))
                val previewPath = previewPath(currentState, lasso, boxStart, boxEnd, imageWidth, imageHeight)
                if (previewPath != null) {
                    drawPath(previewPath, Color.White, style = Stroke(width = width * WHITE_STROKE_FACTOR))
                    drawPath(previewPath, primary, style = Stroke(width = width, pathEffect = dash))
                }
                if (currentState.tool == CutTool.POLYGON) {
                    val vertices = currentState.polygonVertices
                    vertices.forEachIndexed { index, vertex ->
                        val radius = bulletPx / scale / 2f
                        drawCircle(Color.White, radius * 1.4f, Offset(vertex.x, vertex.y))
                        drawCircle(if (index == 0) primary else primary.copy(alpha = 0.8f), radius, Offset(vertex.x, vertex.y))
                    }
                    vertices.firstOrNull()?.takeIf { vertices.size >= GestureRules.MIN_POLYGON_VERTICES }?.let { first ->
                        drawCircle(primary, bulletPx * POLYGON_CLOSE_FACTOR / scale, Offset(first.x, first.y), style = Stroke(width))
                    }
                }
            }
        }

        scene(this)
        finger?.let { touch ->
            val onRight = touch.x < size.width / 2f
            val center = Offset(
                x = if (onRight) size.width - loupeMarginPx - loupeRadiusPx else loupeMarginPx + loupeRadiusPx,
                y = loupeMarginPx + loupeRadiusPx,
            )
            val loupe = Path().apply { addOval(Rect(center, loupeRadiusPx)) }
            clipPath(loupe) {
                drawRect(Color.Black)
                withTransform({
                    translate(center.x - touch.x * LOUPE_ZOOM, center.y - touch.y * LOUPE_ZOOM)
                    scale(LOUPE_ZOOM, LOUPE_ZOOM, Offset.Zero)
                }) { scene(this) }
            }
            drawCircle(Color.White, loupeRadiusPx, center, style = Stroke(strokePx))
            drawCircle(primary, strokePx, center)
        }
    }
}

/** The outline shown while drawing, in image coordinates. */
private fun previewPath(
    state: CuttingState,
    lasso: List<CutPoint>,
    boxStart: CutPoint?,
    boxEnd: CutPoint?,
    imageWidth: Int,
    imageHeight: Int,
): Path? {
    val selection: CutSelection? = when (state.tool) {
        CutTool.FREEHAND -> lasso.takeIf { it.size >= 2 }?.let { CutSelection.Lasso(it.toList()) }
        CutTool.SQUARE, CutTool.CIRCLE -> {
            if (boxStart == null || boxEnd == null) {
                null
            } else {
                val left = min(boxStart.x, boxEnd.x)
                val top = min(boxStart.y, boxEnd.y)
                val right = max(boxStart.x, boxEnd.x)
                val bottom = max(boxStart.y, boxEnd.y)
                if (state.tool == CutTool.SQUARE) CutSelection.Rectangle(left, top, right, bottom) else CutSelection.Oval(left, top, right, bottom)
            }
        }
        CutTool.POLYGON -> state.polygonVertices.takeIf { it.size >= 2 }?.let { vertices ->
            // Open polyline while drawing: the polygon closes on the last tap.
            return Path().apply {
                moveTo(vertices.first().x, vertices.first().y)
                vertices.drop(1).forEach { lineTo(it.x, it.y) }
            }
        }
    }
    return selection?.let { SelectionPaths.toPath(it, imageWidth, imageHeight).asComposePath() }
}

private const val MIN_ZOOM_FACTOR = 0.5f
private const val MAX_ZOOM_FACTOR = 10f
private const val MAX_SCALE_FLOOR = 8f
private const val PREVIEW_STROKE_DP = 2f
private const val WHITE_STROKE_FACTOR = 1.4f
private const val DASH_ON_PX = 10f
private const val DASH_OFF_PX = 20f
private const val POLYGON_BULLET_DP = 8f
private const val POLYGON_CLOSE_FACTOR = 1.5f
private const val LOUPE_RADIUS_DP = 56f
private const val LOUPE_MARGIN_DP = 12f
private const val LOUPE_ZOOM = 1.5f
