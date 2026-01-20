//
// AnimationView.kt
//
// Composable juggling animation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.PatternAnimationState
import jugglinglab.renderer.ComposeRenderer
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.jlHandleFatalException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun AnimationView(
    state: PatternAnimationState,
    onPress: (Offset) -> Unit = {},
    onDrag: (Offset, Float) -> Unit = {_, _ ->},
    onRelease: () -> Unit = {},
    onEnter: () -> Unit = {},
    onExit: () -> Unit = {},
    onLayoutUpdate: (AnimationLayout) -> Unit = {},
    onFrame: (Double) -> Unit = {},
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    modifier: Modifier = Modifier,
) {
    // two renderers for stereo support
    val renderer1 = remember { ComposeRenderer() }
    val renderer2 = remember { ComposeRenderer() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        if (state.message.isNotEmpty()) {
            val textLayoutResult = textMeasurer.measure(
                text = state.message,
                style = TextStyle(color = Color.Black, fontSize = 13.sp)
            )
            val textSize = textLayoutResult.size
            state.update(time = state.pattern.loopStartTime)

            Canvas(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                state.update(message = "", isPaused = false)
                            }
                        )
                    }
            ) {
                drawRect(color = Color.White)
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

        try {
            val showGround = (state.prefs.showGround == AnimationPrefs.GROUND_ON ||
                    (state.prefs.showGround == AnimationPrefs.GROUND_AUTO && state.pattern.isBouncePattern))
            renderer1.setPattern(state.pattern)
            renderer1.setGround(showGround)
            renderer1.zoomLevel = state.zoom
            if (state.prefs.stereo) {
                renderer2.setPattern(state.pattern)
                renderer2.setGround(showGround)
                renderer2.zoomLevel = state.zoom
            }

            if (state.fitToFrame) {
                val borderPixels = state.prefs.borderPixels
                val (overallMin, overallMax) = state.pattern.layout.overallBoundingBox
                if (state.prefs.stereo) {
                    val w = widthPx / 2
                    renderer1.initDisplay(w, heightPx, borderPixels, overallMax, overallMin)
                    renderer2.initDisplay(w, heightPx, borderPixels, overallMax, overallMin)
                } else {
                    renderer1.initDisplay(widthPx, heightPx, borderPixels, overallMax, overallMin)
                }
            }

            val ca = state.cameraAngle.toDoubleArray()
            if (state.prefs.stereo) {
                val separation = AnimationLayout.STEREO_SEPARATION_RADIANS
                renderer1.cameraAngle = doubleArrayOf(ca[0] - separation / 2, ca[1])
                renderer2.cameraAngle = doubleArrayOf(ca[0] + separation / 2, ca[1])
            } else {
                renderer1.cameraAngle = ca
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }

        val layout = remember(
            state.pattern,
            state.prefs.stereo,
            state.prefs.borderPixels,
            state.cameraAngle,
            state.zoom,
            state.selectedItemHashCode,
            state.fitToFrame,
            widthPx,
            heightPx
        ) {
            AnimationLayout(state, widthPx, heightPx, renderer1, renderer2)
        }

        LaunchedEffect(layout) {
            onLayoutUpdate(layout)
        }

        LaunchedEffect(state.isPaused) {
            // Animation loop
            if (!state.isPaused) {
                val startTime = withFrameNanos { it }
                var lastFrameTime = startTime
                val loopDuration = state.pattern.loopEndTime - state.pattern.loopStartTime

                while (true) {
                    withFrameNanos { frameTimeNanos ->
                        val deltaNanos = frameTimeNanos - lastFrameTime
                        lastFrameTime = frameTimeNanos
                        val deltaRealSecs = deltaNanos / 1_000_000_000.0
                        val deltaSimSecs = deltaRealSecs / state.prefs.slowdown
                        var newTime = state.time + deltaSimSecs

                        if (newTime >= state.pattern.loopEndTime) {
                            val overflow = newTime - state.pattern.loopEndTime
                            newTime = state.pattern.loopStartTime + (overflow % loopDuration)
                            state.advancePropForPath()
                        }

                        state.update(time = newTime)
                        onFrame(newTime)
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            val offset = change.position

                            if (event.type == PointerEventType.Enter) {
                                onEnter()
                            } else if (event.type == PointerEventType.Exit) {
                                onExit()
                            } else if (change.changedToDown()) {
                                onPress(offset)
                                change.consume()
                            } else if (change.pressed && change.positionChanged()) {
                                onDrag(offset, density)
                                change.consume()
                            } else if (change.changedToUp()) {
                                onRelease()
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            drawRect(color = Color.White)

            val width = size.width.toInt()
            val height = size.height.toInt()

            if (state.prefs.stereo) {
                val w = width / 2
                withTransform({ translate(left = 0f, top = 0f) }) {
                    clipRect(left = 0f, top = 0f, right = w.toFloat(), bottom = height.toFloat()) {
                        renderer1.drawFrame(
                            state.time,
                            state.propForPath,
                            state.prefs.hideJugglers,
                            this
                        )
                        drawEventOverlays(layout, 0, this)
                        drawPositions(layout, 0, this)
                    }
                }

                withTransform({ translate(left = w.toFloat(), top = 0f) }) {
                    clipRect(left = 0f, top = 0f, right = w.toFloat(), bottom = height.toFloat()) {
                        renderer2.drawFrame(
                            state.time,
                            state.propForPath,
                            state.prefs.hideJugglers,
                            this
                        )
                        drawEventOverlays(layout, 1, this)
                        drawPositions(layout, 1, this)
                    }
                }
            } else {
                renderer1.drawFrame(
                    state.time,
                    state.propForPath,
                    state.prefs.hideJugglers,
                    this
                )
                drawEventOverlays(layout, 0, this)
                drawPositions(layout, 0, this)
            }
        }
    }
}

private fun drawEventOverlays(
    layout: AnimationLayout,
    viewIndex: Int,
    scope: DrawScope
) {
    // Draw Hand Paths
    if (viewIndex < layout.handpathPoints.size) {
        val points = layout.handpathPoints[viewIndex]
        val holds = layout.handpathHold

        if (points.isNotEmpty()) {
            val pathSolid = Path()
            val pathDashed = Path()
            var lastSolid = false
            var lastDashed = false

            for (i in 0 until points.size - 1) {
                val p1 = Offset(points[i][0].toFloat(), points[i][1].toFloat())
                val p2 = Offset(points[i + 1][0].toFloat(), points[i + 1][1].toFloat())

                if (holds[i]) {
                    if (!lastSolid) pathSolid.moveTo(p1.x, p1.y)
                    pathSolid.lineTo(p2.x, p2.y)
                    lastSolid = true
                    lastDashed = false
                } else {
                    if (!lastDashed) pathDashed.moveTo(p1.x, p1.y)
                    pathDashed.lineTo(p2.x, p2.y)
                    lastDashed = true
                    lastSolid = false
                }
            }

            scope.drawPath(
                pathSolid,
                Color.LightGray,
                style = Stroke(width = 2f)
            )
            scope.drawPath(
                pathDashed,
                Color.LightGray,
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
                )
            )
        }
    }

    // Draw Events
    val eventPoints = layout.eventPoints

    for (evIndex in eventPoints.indices) {
        if (viewIndex < eventPoints[evIndex].size) {
            val points = eventPoints[evIndex][viewIndex]
            val isSelected = (evIndex == 0) // First event is always the active one in the list

            // Center dot
            val center = points[4]
            val cx = center[0].toFloat()
            val cy = center[1].toFloat()

            scope.drawOval(
                color = Color.Green,
                topLeft = Offset(cx - 2.5f, cy - 2.5f),
                size = Size(5f, 5f)
            )

            if (isSelected) {
                if (layout.showXzDragControl) {
                    val p0 = Offset(points[0][0].toFloat(), points[0][1].toFloat())
                    val p1 = Offset(points[1][0].toFloat(), points[1][1].toFloat())
                    val p2 = Offset(points[2][0].toFloat(), points[2][1].toFloat())
                    val p3 = Offset(points[3][0].toFloat(), points[3][1].toFloat())
                    scope.drawLine(Color.Green, p0, p1, strokeWidth = 2f)
                    scope.drawLine(Color.Green, p1, p2, strokeWidth = 2f)
                    scope.drawLine(Color.Green, p2, p3, strokeWidth = 2f)
                    scope.drawLine(Color.Green, p3, p0, strokeWidth = 2f)
                }

                if (layout.showYDragControl) {
                    scope.drawLine(
                        Color.Green,
                        Offset(points[5][0].toFloat(), points[5][1].toFloat()),
                        Offset(points[6][0].toFloat(), points[6][1].toFloat()),
                        strokeWidth = 2f
                    )
                }
            } else {
                // Draw unselected event box
                val p0 = Offset(points[0][0].toFloat(), points[0][1].toFloat())
                val p1 = Offset(points[1][0].toFloat(), points[1][1].toFloat())
                val p2 = Offset(points[2][0].toFloat(), points[2][1].toFloat())
                val p3 = Offset(points[3][0].toFloat(), points[3][1].toFloat())
                scope.drawLine(Color.Green, p0, p1, strokeWidth = 2f)
                scope.drawLine(Color.Green, p1, p2, strokeWidth = 2f)
                scope.drawLine(Color.Green, p2, p3, strokeWidth = 2f)
                scope.drawLine(Color.Green, p3, p0, strokeWidth = 2f)
            }
        }
    }
}

private fun drawPositions(
    layout: AnimationLayout,
    viewIndex: Int,
    scope: DrawScope
) {
    val posPoints = layout.posPoints

    // Check if we have data for this viewIndex
    if (viewIndex < posPoints.size) {
        val points = posPoints[viewIndex]
        if (points.isEmpty()) return

        // Center dot (index 4)
        val center = points[4]
        val cx = center[0].toFloat()
        val cy = center[1].toFloat()

        scope.drawOval(
            color = Color.Green,
            topLeft = Offset(cx - 2.5f, cy - 2.5f),
            size = Size(5f, 5f)
        )

        // Draw Box 0-3
        // 0-1, 1-2, 2-3, 3-0
        val p0 = Offset(points[0][0].toFloat(), points[0][1].toFloat())
        val p1 = Offset(points[1][0].toFloat(), points[1][1].toFloat())
        val p2 = Offset(points[2][0].toFloat(), points[2][1].toFloat())
        val p3 = Offset(points[3][0].toFloat(), points[3][1].toFloat())

        scope.drawLine(Color.Green, p0, p1, strokeWidth = 2f)
        scope.drawLine(Color.Green, p1, p2, strokeWidth = 2f)
        scope.drawLine(Color.Green, p2, p3, strokeWidth = 2f)
        scope.drawLine(Color.Green, p3, p0, strokeWidth = 2f)

        // Angle Control (handle at 5)
        if (layout.showAngleDragControl) {
            val p5 = Offset(points[5][0].toFloat(), points[5][1].toFloat())
            val p4 = Offset(points[4][0].toFloat(), points[4][1].toFloat())
            scope.drawLine(Color.Green, p4, p5, strokeWidth = 1f)

            // Handle box/circle at 5
            scope.drawOval(
                color = Color.Green,
                topLeft = Offset(p5.x - 3f, p5.y - 3f),
                size = Size(6f, 6f)
            )
        }

        // Z Drag Control (handle at 6, arrows at 7,8)
        if (layout.showZDragControl) {
            val p6 = Offset(points[6][0].toFloat(), points[6][1].toFloat())
            val p4 = Offset(points[4][0].toFloat(), points[4][1].toFloat())

            scope.drawLine(Color.Green, p4, p6, strokeWidth = 1f)

            // Arrow heads
            val p7 = Offset(points[7][0].toFloat(), points[7][1].toFloat())
            val p8 = Offset(points[8][0].toFloat(), points[8][1].toFloat())

            scope.drawLine(Color.Green, p7, p6, strokeWidth = 1f)
            scope.drawLine(Color.Green, p8, p6, strokeWidth = 1f)
        }
    }
}
