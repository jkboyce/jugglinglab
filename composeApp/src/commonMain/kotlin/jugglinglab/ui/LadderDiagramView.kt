//
// LadderDiagramView.kt
//
// This is a Composable UI displaying the ladder diagram that can
// accompany the main juggler animation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.PatternAnimationState
import jugglinglab.util.jlToStringRounded
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*

@Composable
fun LadderDiagramView(
    layout: LadderLayout,
    state: PatternAnimationState,
    onPress: (Offset, Boolean) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackerY = layout.timeToY(state.time)
    val activeItemHash = state.selectedItemHashCode
    val propForPath = state.propForPath
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.first()
                val offset = change.position
                // Check if right mouse button is pressed (secondary button)
                // Note: isSecondaryPressed might need careful check across platforms
                val isPopup = (event.buttons.isSecondaryPressed)

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
    }) {
        val width = size.width
        val height = size.height

        // 1. Background
        drawRect(color = Color.White)

        // 2. Symmetries
        val symmetryColor = Color.LightGray
        drawLine(symmetryColor, Offset(0f, layout.borderTop.toFloat()), Offset(width, layout.borderTop.toFloat()), strokeWidth = layout.lineWidth)
        drawLine(symmetryColor, Offset(0f, height - layout.borderTop), Offset(width, height - layout.borderTop), strokeWidth = layout.lineWidth)

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
            drawLine(symmetryColor, Offset(0f, height / 2), Offset(width, height / 2), strokeWidth = layout.lineWidth)
        }

        // 3. Hands
        val handsColor = Color.Black
        for (j in 0..<layout.pattern.numberOfJugglers) {
            for (i in -1..1) {
                val lx = (layout.leftX + i + j * layout.jugglerDeltaX).toFloat()
                val rx = (layout.rightX + i + j * layout.jugglerDeltaX).toFloat()
                val top = layout.borderTop.toFloat()
                val bottom = height - layout.borderTop

                drawLine(handsColor, Offset(lx, top), Offset(lx, bottom), strokeWidth = layout.lineWidth)
                drawLine(handsColor, Offset(rx, top), Offset(rx, bottom), strokeWidth = layout.lineWidth)
            }
        }

        // 4. Paths
        for (item in layout.pathItems) {
            val pathColor = Color.Black

            if (item.type == LadderItem.TYPE_CROSS || item.type == LadderItem.TYPE_HOLD) {
                clipRect(
                    left = (layout.leftX + (item.startEvent.juggler - 1) * layout.jugglerDeltaX).toFloat() - layout.lineWidth,
                    top = layout.borderTop.toFloat(),
                    right = (layout.rightX + (item.startEvent.juggler - 1) * layout.jugglerDeltaX).toFloat() + layout.lineWidth,
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
                    left = layout.leftX.toFloat(),
                    top = layout.borderTop.toFloat(),
                    right = width - layout.leftX,
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
                if (item.yEnd >= layout.borderTop) {
                    clipRect(
                        left = (layout.leftX + (item.startEvent.juggler - 1) * layout.jugglerDeltaX).toFloat(),
                        top = layout.borderTop.toFloat(),
                        right = (layout.rightX + (item.startEvent.juggler - 1) * layout.jugglerDeltaX).toFloat(),
                        bottom = height - layout.borderTop
                    ) {
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

        // 5. Positions
        for (item in layout.positionItems) {
            if (item.yLow >= layout.borderTop || item.yHigh <= height + layout.borderTop) {
                val rectSize = Size((item.xHigh - item.xLow).toFloat(), (item.yHigh - item.yLow).toFloat())
                val topLeft = Offset(item.xLow.toFloat(), item.yLow.toFloat())

                drawRect(Color.White, topLeft, rectSize)
                drawRect(Color.Black, topLeft, rectSize, style = Stroke(layout.lineWidth))

                if (item.jlHashCode == activeItemHash) {
                    drawRect(Color.Green, topLeft.minus(Offset(1f, 1f)), Size(rectSize.width + 2, rectSize.height + 2), style = Stroke(layout.lineWidth))
                }
            }
        }

        // 6. Events
        for (item in layout.eventItems) {
            val topLeft = Offset(item.xLow.toFloat(), item.yLow.toFloat())
            val size = Size((item.xHigh - item.xLow).toFloat(), (item.yHigh - item.yLow).toFloat())

            if (item.type == LadderItem.TYPE_EVENT) {
                drawOval(Color.Black, topLeft, size)
                if (item.jlHashCode == activeItemHash) {
                    drawRect(Color.Green, topLeft.minus(Offset(1f, 1f)), Size(size.width + 2, size.height + 2), style = Stroke(layout.lineWidth))
                }
            } else {
                if (item.yLow >= layout.borderTop || item.yHigh <= height + layout.borderTop) {
                    val tr = item.event.transitions[item.transNum]
                    val propnum = propForPath[tr.path - 1]
                    val propColor = state.pattern.getProp(propnum).getEditorColor()

                    drawOval(propColor, topLeft, size)
                    drawOval(Color.Black, topLeft, size, style = Stroke(layout.lineWidth))

                    if (item.jlHashCode == activeItemHash) {
                        drawRect(Color.Green, topLeft.minus(Offset(1f, 1f)), Size(size.width + 2, size.height + 2), style = Stroke(layout.lineWidth))
                    }
                }
            }
        }

        // 7. Tracker
        drawLine(Color.Red, Offset(0f, trackerY.toFloat()), Offset(width, trackerY.toFloat()), strokeWidth = layout.lineWidth)

        if (state.isPaused) {
            val text = jlToStringRounded(state.time, 2) + " s"
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(color = Color.Red, fontSize = 13.sp)
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
