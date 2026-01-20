//
// LadderDiagramView.kt
//
// This is a Composable UI displaying the ladder diagram that can
// accompany the main juggler animation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.PatternAnimationState
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.jlGetStringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.max
import kotlin.math.min

@Composable
fun LadderDiagramView(
    state: PatternAnimationState,
    onPress: (Offset, Boolean) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onRelease: () -> Unit = {},
    onLayoutUpdate: (LadderDiagramLayout?) -> Unit = {},
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val density = LocalDensity.current.density

        val backgroundColor = Color.White
        val messageColor = Color.Black
        val symmetryColor = Color.LightGray
        val handsColor = Color.Black
        val pathColor = Color.Black
        val positionColor = Color.Black
        val eventColor = Color.Black
        val selectionColor = Color.Green
        val trackerColor = Color.Red

        // layout contains drawing information for on-screen elements
        val layout = remember(state.pattern, widthPx, heightPx, density) {
            if (state.pattern.numberOfJugglers > LadderDiagramLayout.MAX_JUGGLERS)
                null
            else
                LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
        }

        LaunchedEffect(layout) {
            // pass layout to containing panel for mouse handling
            onLayoutUpdate(layout)
        }

        if (layout == null) {
            val text = jlGetStringResource(Res.string.gui_too_many_jugglers, LadderDiagramLayout.MAX_JUGGLERS)
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

        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // handle long press for touch (Android/iOS)
                    detectTapGestures(
                        onLongPress = { offset ->
                            onPress(offset, true)  // treat long press as popup trigger
                        }
                    )
                }
                .pointerInput(Unit) {
                    // other touch events are handled as left mouse clicks
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            val offset = change.position
                            val isPopup = event.buttons.isSecondaryPressed

                            if (change.changedToDown()) {
                                onPress(offset, isPopup)
                                change.consume()
                            } else if (change.pressed && change.positionChanged()) {
                                onDrag(offset)
                                change.consume()
                            } else if (change.changedToUp()) {
                                onRelease()
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

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

                drawLine(symmetryColor, Offset(lx, midY), Offset(rx, midY), strokeWidth = layout.lineWidth)
                drawLine(symmetryColor, Offset(lx, midY), Offset(lx * 2, qY), strokeWidth = layout.lineWidth)
                drawLine(symmetryColor, Offset(lx, midY), Offset(lx * 2, qY2), strokeWidth = layout.lineWidth)
                drawLine(symmetryColor, Offset(rx, midY), Offset(width - lx * 2, qY), strokeWidth = layout.lineWidth)
                drawLine(symmetryColor, Offset(rx, midY), Offset(width - lx * 2, qY2), strokeWidth = layout.lineWidth)
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

                drawLine(handsColor, Offset(lx, top), Offset(lx, bottom), strokeWidth = layout.lineWidth * 2)
                drawLine(handsColor, Offset(rx, top), Offset(rx, bottom), strokeWidth = layout.lineWidth * 2)
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
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(layout.dashOn, layout.dashOff), 0f),
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
                                style = Stroke(layout.lineWidth)
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
                    val rectSize = Size((item.xHigh - item.xLow).toFloat(), (item.yHigh - item.yLow).toFloat())

                    drawRect(backgroundColor, topLeft, rectSize)
                    drawRect(positionColor, topLeft, rectSize, style = Stroke(layout.lineWidth))

                    if (item.jlHashCode == activeItemHash) {
                        drawRect(
                            selectionColor,
                            topLeft.minus(Offset(1f, 1f)),
                            Size(rectSize.width + 2, rectSize.height + 2),
                            style = Stroke(layout.lineWidth * 2)
                        )
                    }
                }
            }

            // 6. Events and transitions
            for (item in layout.eventItems) {
                val topLeft = Offset(item.xLow.toFloat(), item.yLow.toFloat())
                val rectSize = Size((item.xHigh - item.xLow).toFloat(), (item.yHigh - item.yLow).toFloat())

                if (item.type == LadderItem.TYPE_EVENT) {
                    drawOval(eventColor, topLeft, rectSize)

                    if (item.jlHashCode == activeItemHash) {
                        drawRect(
                            selectionColor,
                            topLeft.minus(Offset(1f, 1f)),
                            Size(rectSize.width + 2, rectSize.height + 2),
                            style = Stroke(layout.lineWidth * 2)
                        )
                    }
                } else {
                    if (item.yLow >= layout.borderTop || item.yHigh <= height + layout.borderTop) {
                        val tr = item.event.transitions[item.transNum]
                        val propnum = state.propForPath[tr.path - 1]
                        val propColor = state.pattern.getProp(propnum).getEditorColor()

                        drawOval(propColor, topLeft, rectSize)
                        drawOval(eventColor, topLeft, rectSize, style = Stroke(layout.lineWidth))

                        if (item.jlHashCode == activeItemHash) {
                            drawRect(
                                selectionColor,
                                topLeft.minus(Offset(1f, 1f)),
                                Size(rectSize.width + 2, rectSize.height + 2),
                                style = Stroke(layout.lineWidth * 2)
                            )
                        }
                    }
                }
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
