//
// LadderDiagramView.kt
//
// This is a Composable UI displaying the ladder diagram that can
// accompany the main juggler animation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.ui.mobile.LocalWalkthroughCoordinator
import org.jugglinglab.util.jlToStringRounded
import org.jugglinglab.util.jlGetStringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jugglinglab.ui.common.LadderDiagramLayout.Companion.BORDER_TOP_DP

@Composable
fun LadderDiagramView(
    state: PatternAnimationState,
    modifier: Modifier = Modifier,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    onPress: (Offset, Boolean, Boolean, Offset) -> Boolean = { _, _, _, _ -> false },
    onDrag: (Offset) -> Unit = {},
    onRelease: () -> Unit = {},
    onLayoutUpdate: (LadderDiagramLayout?) -> Unit = {},
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    zoom: Float = 1f,
    onZoomChange: (Float) -> Unit = {},
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    onError: (Throwable) -> Unit
) {
    val coordinator = LocalWalkthroughCoordinator.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val density = LocalDensity.current.density

        val backgroundColor = colorScheme.background
        val messageColor = colorScheme.onBackground
        val symmetryColor = Color.LightGray
        val handsColor = colorScheme.onBackground
        val pathColor = colorScheme.onBackground
        val positionColor = colorScheme.onBackground
        val eventColor = colorScheme.onBackground
        val selectionColor = Constants.HIGHLIGHT_COLOR
        val trackerColor = Color.Red

        var localZoom by remember { mutableFloatStateOf(zoom) }
        val previousZoom = remember { mutableFloatStateOf(zoom) }
        if (zoom != previousZoom.floatValue) {
            localZoom = zoom
            previousZoom.floatValue = zoom
        }
        val coroutineScope = rememberCoroutineScope()
        var targetScroll by remember { mutableStateOf<Int?>(null) }

        // layout contains drawing information for on-screen elements
        val layout = remember(state.pattern, widthPx, heightPx, density, localZoom) {
            val canvasHeightPx = (heightPx * localZoom).toInt()
            val newLayout = if (state.pattern.numberOfJugglers > LadderDiagramLayout.MAX_JUGGLERS ||
                state.pattern.numberOfPaths > LadderDiagramLayout.MAX_PATHS
            ) {
                null
            } else {
                try {
                    LadderDiagramLayout(state.pattern, widthPx, canvasHeightPx, density)
                } catch (e: Throwable) {
                    onError(e)
                    null
                }
            }
            // pass updated layout to containing panel for mouse handling
            onLayoutUpdate(newLayout)
            newLayout
        }

        if (layout == null) {
            val text = if (state.pattern.numberOfJugglers > LadderDiagramLayout.MAX_JUGGLERS) {
                jlGetStringResource(
                    Res.string.gui_too_many_jugglers,
                    LadderDiagramLayout.MAX_JUGGLERS
                )
            } else if (state.pattern.numberOfPaths > LadderDiagramLayout.MAX_PATHS) {
                jlGetStringResource(
                    Res.string.gui_too_many_paths,
                    LadderDiagramLayout.MAX_PATHS
                )
            } else {
                "Ladder unavailable"
            }
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(color = messageColor, fontSize = 13.sp)
            )
            val textSize = textLayoutResult.size

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = backgroundColor)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = (widthPx - textSize.width) / 2f,
                        y = (heightPx - textSize.height) / 2f
                    )
                )
            }
            return@BoxWithConstraints
        }

        val canvasHeightDp = with(LocalDensity.current) { (heightPx * localZoom).toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var isZooming = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }

                            if (event.type == PointerEventType.Scroll) {
                                // desktop zoom handling
                                val delta = event.changes.first().scrollDelta
                                // delta.y is positive for scrolling down (zoom in), negative for up (zoom out)
                                var zoomChange = 1f + 0.05f * abs(delta.y)
                                if (delta.y < 0) {
                                    zoomChange = 1 / zoomChange
                                }
                                val oldZoom = localZoom
                                val newZoom = (localZoom * zoomChange).coerceIn(1f, 10f)
                                localZoom = newZoom
                                onZoomChange(newZoom)
                                val actualZoomChange = newZoom / oldZoom
                                if (actualZoomChange != 1f) {
                                    val yScreen = event.changes.first().position.y
                                    val yCanvas = yScreen + scrollState.value

                                    val borderTop = (BORDER_TOP_DP * density).toInt()
                                    val oldCanvasHeight = (heightPx * oldZoom).toInt()
                                    val newCanvasHeight = (heightPx * newZoom).toInt()
                                    val oldScrollableHeight = max(1, oldCanvasHeight - 2 * borderTop)
                                    val newScrollableHeight = max(1, newCanvasHeight - 2 * borderTop)

                                    val yCanvasNew = ((yCanvas - borderTop).toDouble() * newScrollableHeight / oldScrollableHeight).roundToInt() + borderTop

                                    val target = (yCanvasNew - yScreen).roundToInt()
                                    targetScroll = target
                                    coroutineScope.launch { scrollState.scrollTo(target) }
                                }
                                event.changes.first().consume()
                            } else {
                                // mobile zoom handling
                                if (pressed.size >= 2) {
                                    isZooming = true
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        val oldZoom = localZoom
                                        val newZoom = (localZoom * zoomChange).coerceIn(1f, 10f)
                                        localZoom = newZoom
                                        onZoomChange(newZoom)
                                        val actualZoomChange = newZoom / oldZoom
                                        if (actualZoomChange != 1f) {
                                            val yScreen = event.calculateCentroid().y
                                            val yCanvas = yScreen + scrollState.value

                                            val borderTop = (BORDER_TOP_DP * density).toInt()
                                            val oldCanvasHeight = (heightPx * oldZoom).toInt()
                                            val newCanvasHeight = (heightPx * newZoom).toInt()
                                            val oldScrollableHeight = max(1, oldCanvasHeight - 2 * borderTop)
                                            val newScrollableHeight = max(1, newCanvasHeight - 2 * borderTop)

                                            val yCanvasNew = ((yCanvas - borderTop).toDouble() * newScrollableHeight / oldScrollableHeight).roundToInt() + borderTop

                                            val target = (yCanvasNew - yScreen).roundToInt()
                                            targetScroll = target
                                            coroutineScope.launch { scrollState.scrollTo(target) }
                                        }
                                    }
                                } else if (pressed.size == 1) {
                                    if (isZooming) {
                                        // prevent scrolling at end of pinch to zoom
                                        event.changes.forEach { it.consume() }
                                    }
                                } else {
                                    if (isZooming && localZoom < 1.1f) {
                                        localZoom = 1f
                                        onZoomChange(1f)
                                    }
                                    isZooming = false
                                }
                            }
                        }
                    }
                }
                .let { if (localZoom > 1f) it.verticalScroll(scrollState) else it }
        ) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(canvasHeightDp)
                    .onSizeChanged {
                        targetScroll?.let { target ->
                            coroutineScope.launch {
                                scrollState.scrollTo(target)
                            }
                            targetScroll = null
                        }
                    }
                    .onGloballyPositioned { coords ->
                        if (coordinator == null || coordinator.walkthroughStep == 0) return@onGloballyPositioned

                        val rootBounds = coords.boundsInRoot()
                        coordinator.reportElement("ladder_center", rootBounds)

                        layout.let { lay ->
                            val transItem = lay.eventItems.filter { it.type == LadderItem.TYPE_TRANSITION }.minByOrNull { it.yLow }
                            if (transItem != null) {
                                coordinator.reportElement(
                                    "ladder_transition",
                                    androidx.compose.ui.geometry.Rect(
                                        left = rootBounds.left + transItem.xLow,
                                        top = rootBounds.top + transItem.yLow,
                                        right = rootBounds.left + transItem.xHigh,
                                        bottom = rootBounds.top + transItem.yHigh
                                    )
                                )
                                val evItem = transItem.transEventItem
                                if (evItem != null) {
                                    coordinator.reportElement(
                                        "ladder_event",
                                        androidx.compose.ui.geometry.Rect(
                                            left = rootBounds.left + evItem.xLow,
                                            top = rootBounds.top + evItem.yLow,
                                            right = rootBounds.left + evItem.xHigh,
                                            bottom = rootBounds.top + evItem.yHigh
                                        )
                                    )
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // handle long press for touch (Android/iOS)
                        detectTapGestures(
                            onLongPress = { offset ->
                                val screenOffset = Offset(offset.x, offset.y - scrollState.value)
                                onPress(offset, true, false, screenOffset)  // treat long press as popup trigger
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        // other touch events are handled as left mouse clicks
                        awaitPointerEventScope {
                            var isEventHandled = false
                            var downPos = Offset.Zero
                            val touchSlop = viewConfiguration.touchSlop

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val offset = change.position
                                val isPopup = event.buttons.isSecondaryPressed ||
                                        ((event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) && event.buttons.isPrimaryPressed)

                                if (event.changes.size > 1) {
                                    if (isEventHandled) {
                                        onRelease()
                                        isEventHandled = false
                                    }
                                    // Consume movement during pinch to abort long-press and prevent scrolling
                                    event.changes.forEach {
                                        if (it.pressed && it.positionChanged()) {
                                            it.consume()
                                        }
                                    }
                                } else if (change.changedToDown()) {
                                    downPos = offset
                                    val screenOffset = Offset(offset.x, offset.y - scrollState.value)
                                    isEventHandled = onPress(offset, isPopup, localZoom > 1f, screenOffset)
                                    if (change.type == PointerType.Mouse) {
                                        change.consume()
                                    }
                                } else if (change.pressed && change.positionChanged()) {
                                    if (isEventHandled) {
                                        onDrag(offset)
                                        if ((offset - downPos).getDistance() > touchSlop) {
                                            change.consume()
                                        }
                                    } else if (localZoom > 1f && change.type == PointerType.Mouse) {
                                        val deltaY = change.positionChange().y
                                        scrollState.dispatchRawDelta(-deltaY)
                                        change.consume()
                                    }
                                } else if (change.changedToUp()) {
                                    if (isEventHandled) {
                                        onRelease()
                                        change.consume()
                                    }
                                    isEventHandled = false
                                }
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height

                val strokeStandard = Stroke(layout.lineWidth)
                val strokeSelected = Stroke(layout.lineWidth * 2)
                val dashEffect =
                    PathEffect.dashPathEffect(floatArrayOf(layout.dashOn, layout.dashOff), 0f)

                // 1. Background
                drawRect(color = backgroundColor)

                // 2. Symmetries
                drawLine(
                    symmetryColor,
                    Offset(0f, layout.borderTop.toFloat()),
                    Offset(width, layout.borderTop.toFloat()),
                    strokeWidth = layout.lineWidth
                )
                drawLine(
                    symmetryColor,
                    Offset(0f, height - layout.borderTop),
                    Offset(width, height - layout.borderTop),
                    strokeWidth = layout.lineWidth
                )

                if (layout.hasSwitchSymmetry) {
                    val lx = layout.leftX.toFloat()
                    val rx = width - lx
                    val midY = height - layout.borderTop / 2
                    val qY = height - layout.borderTop * 3 / 4
                    val qY2 = height - layout.borderTop / 4
                    val dx = 2 * (qY2 - midY)

                    drawLine(
                        symmetryColor,
                        Offset(lx, midY),
                        Offset(rx, midY),
                        strokeWidth = layout.lineWidth
                    )
                    drawLine(
                        symmetryColor,
                        Offset(lx, midY),
                        Offset(lx + dx, qY),
                        strokeWidth = layout.lineWidth
                    )
                    drawLine(
                        symmetryColor,
                        Offset(lx, midY),
                        Offset(lx + dx, qY2),
                        strokeWidth = layout.lineWidth
                    )
                    drawLine(
                        symmetryColor,
                        Offset(rx, midY),
                        Offset(rx - dx, qY),
                        strokeWidth = layout.lineWidth
                    )
                    drawLine(
                        symmetryColor,
                        Offset(rx, midY),
                        Offset(rx - dx, qY2),
                        strokeWidth = layout.lineWidth
                    )
                }
                if (layout.hasSwitchDelaySymmetry) {
                    drawLine(
                        symmetryColor,
                        Offset(0f, height / 2),
                        Offset(width, height / 2),
                        strokeWidth = layout.lineWidth
                    )
                }

                // 3. Hands
                for (j in 0..<layout.pattern.numberOfJugglers) {
                    val lx = (layout.leftX + j * layout.jugglerDeltaX).toFloat()
                    val rx = (layout.rightX + j * layout.jugglerDeltaX).toFloat()
                    val top = layout.borderTop.toFloat()
                    val bottom = height - layout.borderTop

                    drawLine(
                        handsColor,
                        Offset(lx, top),
                        Offset(lx, bottom),
                        strokeWidth = layout.lineWidth * 2
                    )
                    drawLine(
                        handsColor,
                        Offset(rx, top),
                        Offset(rx, bottom),
                        strokeWidth = layout.lineWidth * 2
                    )
                }

                // 4. Paths
                for (item in layout.pathItems) {
                    if (item.type == LadderItem.TYPE_CROSS || item.type == LadderItem.TYPE_HOLD) {
                        clipRect(
                            top = layout.borderTop.toFloat(),
                            bottom = height - layout.borderTop
                        ) {
                            drawLine(
                                pathColor,
                                Offset(item.xStart.toFloat(), item.yStart.toFloat()),
                                Offset(item.xEnd.toFloat(), item.yEnd.toFloat()),
                                strokeWidth = layout.lineWidth
                            )
                        }
                    } else if (item.type == LadderItem.TYPE_PASS) {
                        clipRect(
                            top = layout.borderTop.toFloat(),
                            bottom = height - layout.borderTop
                        ) {
                            drawLine(
                                pathColor,
                                Offset(item.xStart.toFloat(), item.yStart.toFloat()),
                                Offset(item.xEnd.toFloat(), item.yEnd.toFloat()),
                                pathEffect = dashEffect,
                                strokeWidth = layout.lineWidth
                            )
                        }
                    } else if (item.type == LadderItem.TYPE_SELF) {
                        if (item.yStart <= (height.toInt() - layout.borderTop) && item.yEnd >= layout.borderTop) {
                            val j = item.endEvent.juggler - 1
                            val lx = (layout.leftX + j * layout.jugglerDeltaX).toFloat()
                            val rx = (layout.rightX + j * layout.jugglerDeltaX).toFloat()
                            val top = max(layout.borderTop.toFloat(), item.yStart.toFloat())
                            val bottom = min(height - layout.borderTop, item.yEnd.toFloat())

                            clipRect(left = lx, top = top, right = rx, bottom = bottom) {
                                drawCircle(
                                    pathColor,
                                    radius = item.radius.toFloat(),
                                    center = Offset(item.xCenter.toFloat(), item.yCenter.toFloat()),
                                    style = strokeStandard
                                )
                            }
                        }
                    }
                }

                val activeItemHash = state.selectedItemHashCode

                // 5. Positions
                for (item in layout.positionItems) {
                    if (item.yLow >= layout.borderTop || item.yHigh <= height + layout.borderTop) {
                        val topLeft = Offset(item.xLow.toFloat(), item.yLow.toFloat())
                        val rectSize =
                            Size(
                                (item.xHigh - item.xLow).toFloat(),
                                (item.yHigh - item.yLow).toFloat()
                            )

                        drawRect(backgroundColor, topLeft, rectSize)
                        drawRect(positionColor, topLeft, rectSize, style = strokeStandard)

                        if (item.jlHashCode == activeItemHash) {
                            drawRect(
                                selectionColor,
                                topLeft.minus(Offset(1f, 1f)),
                                Size(rectSize.width + 2, rectSize.height + 2),
                                style = strokeSelected
                            )
                        }
                    }
                }

                // 6. Events and transitions
                try {
                    for (item in layout.eventItems) {
                        val topLeft = Offset(item.xLow.toFloat(), item.yLow.toFloat())
                        val rectSize =
                            Size(
                                (item.xHigh - item.xLow).toFloat(),
                                (item.yHigh - item.yLow).toFloat()
                            )

                        if (item.type == LadderItem.TYPE_EVENT) {
                            drawOval(eventColor, topLeft, rectSize)

                            if (item.jlHashCode == activeItemHash) {
                                drawRect(
                                    selectionColor,
                                    topLeft.minus(Offset(1f, 1f)),
                                    Size(rectSize.width + 2, rectSize.height + 2),
                                    style = strokeSelected
                                )
                            }
                        } else {
                            if (item.yLow >= layout.borderTop || item.yHigh <= height + layout.borderTop) {
                                val tr = item.event.transitions[item.transNum]
                                val propnum = state.propForPath[tr.path - 1]
                                val propColor = state.pattern.getProp(propnum).getEditorColor()

                                drawOval(propColor, topLeft, rectSize)
                                drawOval(eventColor, topLeft, rectSize, style = strokeStandard)

                                if (item.jlHashCode == activeItemHash) {
                                    drawRect(
                                        selectionColor,
                                        topLeft.minus(Offset(1f, 1f)),
                                        Size(rectSize.width + 2, rectSize.height + 2),
                                        style = strokeSelected
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    onError(e)
                }

                // 7. Tracker
                val trackerY = layout.timeToY(state.time)
                drawLine(
                    trackerColor,
                    Offset(0f, trackerY.toFloat()),
                    Offset(width, trackerY.toFloat()),
                    strokeWidth = layout.lineWidth
                )

                if (state.isPaused) {
                    val text = jlToStringRounded(state.time, 2) + " s"
                    val textLayoutResult = textMeasurer.measure(
                        text = text,
                        style = TextStyle(color = trackerColor, fontSize = 13.sp)
                    )
                    val textSize = textLayoutResult.size
                    val padding = 3.dp.toPx()

                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x = (width - textSize.width) / 2f,
                            y = trackerY.toFloat() - textSize.height - padding
                        )
                    )
                }
            }
        }
    }
}
