//
// AnimationView.kt
//
// Composable juggling animation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("LocalVariableName")

package jugglinglab.ui

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.PatternAnimationState
import jugglinglab.renderer.Renderer
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.Coordinate
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
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
    onZoom: (Float) -> Unit = {},
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    isAntiAlias: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // two renderers for stereo support
    val renderer1 = remember { Renderer() }
    val renderer2 = remember { Renderer() }
    renderer1.isAntiAlias = isAntiAlias
    renderer2.isAntiAlias = isAntiAlias

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        val backgroundColor = Color.White
        val messageColor = Color.Black

        if (state.message.isNotEmpty()) {
            val textLayoutResult = textMeasurer.measure(
                text = state.message,
                style = TextStyle(color = messageColor, fontSize = 13.sp)
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
                        if (state.isPaused) return@withFrameNanos
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
                            } else if (event.type == PointerEventType.Scroll) {
                                val delta = change.scrollDelta
                                // scrollDelta.y is positive for scrolling down (zoom out), negative for up (zoom in) usually
                                // We pass the y delta to the callback
                                onZoom(delta.y)
                                change.consume()
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
            drawRect(color = backgroundColor)

            val width = size.width.toInt()
            val height = size.height.toInt()

            if (state.prefs.stereo) {
                val w = width / 2
                withTransform({ translate(left = 0f, top = 0f) }) {
                    clipRect(left = 0f, top = 0f, right = w.toFloat(), bottom = height.toFloat()) {
                        if (layout.showGrid) {
                            drawGrid(renderer1, this, width = w, height = height)
                        }
                        renderer1.drawFrame(
                            state.time,
                            state.propForPath,
                            state.prefs.hideJugglers,
                            this
                        )
                        drawEventOverlays(layout, 0, this)
                        drawPositionOverlays(state, layout, 0, renderer1, this, textMeasurer)
                        if (state.showAxes) {
                            renderer1.drawAxes(textMeasurer, this)
                        }
                    }
                }

                withTransform({ translate(left = w.toFloat(), top = 0f) }) {
                    clipRect(left = 0f, top = 0f, right = w.toFloat(), bottom = height.toFloat()) {
                        if (layout.showGrid) {
                            drawGrid(renderer2, this, width = w, height = height)
                        }
                        renderer2.drawFrame(
                            state.time,
                            state.propForPath,
                            state.prefs.hideJugglers,
                            this
                        )
                        drawEventOverlays(layout, 1, this)
                        drawPositionOverlays(state, layout, 1, renderer2, this, textMeasurer)
                        if (state.showAxes) {
                            renderer2.drawAxes(textMeasurer, this)
                        }
                    }
                }
            } else {
                if (layout.showGrid) {
                    drawGrid(renderer1, this)
                }
                renderer1.drawFrame(
                    state.time,
                    state.propForPath,
                    state.prefs.hideJugglers,
                    this
                )
                drawEventOverlays(layout, 0, this)
                drawPositionOverlays(state, layout, 0, renderer1, this, textMeasurer)
                if (state.showAxes) {
                    renderer1.drawAxes(textMeasurer, this)
                }
            }
        }
    }
}

private fun drawEventOverlays(
    layout: AnimationLayout,
    viewIndex: Int,
    scope: DrawScope
): Unit = with(scope) {
    val handpathColor = Color.LightGray
    val eventColor = Color.Green

    val strokeWidth1 = 1.dp.toPx()
    val strokeWidth1_25 = 1.25.dp.toPx()
    val dotSize4 = 4.dp.toPx()
    val dotOffset2 = dotSize4 / 2
    val dashOn = 5.7.dp.toPx()
    val dashOff = 5.dp.toPx()

    // Draw Hand Paths
    if (viewIndex < layout.handpathPoints.size) {
        val points = layout.handpathPoints[viewIndex]
        val holds = layout.handpathIsHold

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

            drawPath(
                pathSolid,
                handpathColor,
                style = Stroke(width = strokeWidth1_25)
            )
            drawPath(
                pathDashed,
                handpathColor,
                style = Stroke(
                    width = strokeWidth1_25,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff))
                )
            )
        }
    }

    // Draw Events
    val eventPoints = layout.eventPoints

    for (evIndex in eventPoints.indices) {
        if (viewIndex < eventPoints[evIndex].size) {
            val points = eventPoints[evIndex][viewIndex]
            val isSelected = (evIndex == 0)  // first event is always the active one

            val p0 = Offset(points[0][0].toFloat(), points[0][1].toFloat())
            val p1 = Offset(points[1][0].toFloat(), points[1][1].toFloat())
            val p2 = Offset(points[2][0].toFloat(), points[2][1].toFloat())
            val p3 = Offset(points[3][0].toFloat(), points[3][1].toFloat())
            val p4 = Offset(points[4][0].toFloat(), points[4][1].toFloat())

            // center dot
            drawOval(
                color = eventColor,
                topLeft = Offset(p4.x - dotOffset2, p4.y - dotOffset2),
                size = Size(dotSize4, dotSize4)
            )

            if (isSelected) {
                if (layout.showXzDragControl) {
                    // selected event box
                    drawLine(eventColor, p0, p1, strokeWidth = strokeWidth1_25)
                    drawLine(eventColor, p1, p2, strokeWidth = strokeWidth1_25)
                    drawLine(eventColor, p2, p3, strokeWidth = strokeWidth1_25)
                    drawLine(eventColor, p3, p0, strokeWidth = strokeWidth1_25)
                }

                if (layout.showYDragControl) {
                    // y-axis control pointing forward/backward
                    drawLine(
                        eventColor,
                        Offset(points[5][0].toFloat(), points[5][1].toFloat()),
                        Offset(points[6][0].toFloat(), points[6][1].toFloat()),
                        strokeWidth = strokeWidth1
                    )
                    drawLine(
                        eventColor,
                        Offset(points[5][0].toFloat(), points[5][1].toFloat()),
                        Offset(points[7][0].toFloat(), points[7][1].toFloat()),
                        strokeWidth = strokeWidth1
                    )
                    drawLine(
                        eventColor,
                        Offset(points[5][0].toFloat(), points[5][1].toFloat()),
                        Offset(points[8][0].toFloat(), points[8][1].toFloat()),
                        strokeWidth = strokeWidth1
                    )
                    drawLine(
                        eventColor,
                        Offset(points[6][0].toFloat(), points[6][1].toFloat()),
                        Offset(points[9][0].toFloat(), points[9][1].toFloat()),
                        strokeWidth = strokeWidth1
                    )
                    drawLine(
                        eventColor,
                        Offset(points[6][0].toFloat(), points[6][1].toFloat()),
                        Offset(points[10][0].toFloat(), points[10][1].toFloat()),
                        strokeWidth = strokeWidth1
                    )
                }
            } else {
                // unselected event box
                drawLine(eventColor, p0, p1, strokeWidth = strokeWidth1_25)
                drawLine(eventColor, p1, p2, strokeWidth = strokeWidth1_25)
                drawLine(eventColor, p2, p3, strokeWidth = strokeWidth1_25)
                drawLine(eventColor, p3, p0, strokeWidth = strokeWidth1_25)
            }
        }
    }
}

private fun drawPositionOverlays(
    state: PatternAnimationState,
    layout: AnimationLayout,
    viewIndex: Int,
    renderer: Renderer,
    scope: DrawScope,
    textMeasurer: TextMeasurer
): Unit = with(scope) {
    val posPoints = layout.posPoints
    val posColor = Color.Green
    val posTextColor = Color.Black

    val strokeWidth1 = 1.dp.toPx()
    val strokeWidth1_25 = 1.25.dp.toPx()
    val dotSize5 = 5.dp.toPx()
    val dotOffset2_5 = dotSize5 / 2
    val dotSize7 = 7.dp.toPx()
    val dotOffset3_5 = dotSize7 / 2
    val dotSize10 = 10.dp.toPx()
    val dotOffset5 = dotSize10 / 2

    // Check if we have data for this viewIndex
    if (viewIndex < posPoints.size) {
        val points = posPoints[viewIndex]
        if (points.isEmpty()) return

        // center
        val p4 = Offset(points[4][0].toFloat(), points[4][1].toFloat())

        // center dot
        drawOval(
            color = posColor,
            topLeft = Offset(p4.x - dotOffset2_5, p4.y - dotOffset2_5),
            size = Size(dotSize5, dotSize5)
        )

        if (layout.showXyDragControl || state.draggingPosition) {
            // edges of xy plane control
            val p0 = Offset(points[0][0].toFloat(), points[0][1].toFloat())
            val p1 = Offset(points[1][0].toFloat(), points[1][1].toFloat())
            val p2 = Offset(points[2][0].toFloat(), points[2][1].toFloat())
            val p3 = Offset(points[3][0].toFloat(), points[3][1].toFloat())
            drawLine(posColor, p0, p1, strokeWidth = strokeWidth1_25)
            drawLine(posColor, p1, p2, strokeWidth = strokeWidth1_25)
            drawLine(posColor, p2, p3, strokeWidth = strokeWidth1_25)
            drawLine(posColor, p3, p0, strokeWidth = strokeWidth1_25)
        }

        if (layout.showZDragControl && (!state.draggingPosition || state.draggingPositionZ)) {
            // z-axis control pointing upward (handle end at 6, arrow head at 7,8)
            val p6 = Offset(points[6][0].toFloat(), points[6][1].toFloat())
            val p7 = Offset(points[7][0].toFloat(), points[7][1].toFloat())
            val p8 = Offset(points[8][0].toFloat(), points[8][1].toFloat())
            drawLine(posColor, p4, p6, strokeWidth = strokeWidth1)
            drawLine(posColor, p6, p7, strokeWidth = strokeWidth1)
            drawLine(posColor, p6, p8, strokeWidth = strokeWidth1)
        }

        if (layout.showAngleDragControl && (!state.draggingPosition || state.draggingPositionAngle)) {
            // angle-changing control pointing backward (center at 4, handle at 5)
            val p5 = Offset(points[5][0].toFloat(), points[5][1].toFloat())
            drawLine(posColor, p4, p5, strokeWidth = strokeWidth1)
            drawOval(
                color = posColor,
                topLeft = Offset(p5.x - dotOffset5, p5.y - dotOffset5),
                size = Size(dotSize10, dotSize10)
            )
        }

        if (state.draggingPositionAngle) {
            // sighting line during angle rotation
            val p9 = Offset(points[9][0].toFloat(), points[9][1].toFloat())
            val p10 = Offset(points[10][0].toFloat(), points[10][1].toFloat())
            drawLine(posColor, p9, p10, strokeWidth = strokeWidth1)
        }

        if (!state.draggingPositionAngle) {
            if (state.draggingPositionZ || layout.showGrid) {
                // line dropping down to projection on ground (z = 0)
                val c = layout.visiblePosition!!.coordinate
                val z = c.z
                c.z = 0.0
                val xyProjection = renderer.getXY(c)
                drawLine(
                    posColor,
                    p4,
                    Offset(xyProjection[0].toFloat(), xyProjection[1].toFloat()),
                    strokeWidth = strokeWidth1
                )
                drawOval(
                    color = posColor,
                    topLeft = Offset(xyProjection[0].toFloat() - dotOffset3_5, xyProjection[1].toFloat() - dotOffset3_5),
                    size = Size(dotSize7, dotSize7)
                )

                if (state.draggingPositionZ) {
                    // z-label on the line
                    val textLayoutResult = textMeasurer.measure(
                        text = "z = " + jlToStringRounded(z, 1) + " cm",
                        style = TextStyle(color = posTextColor, fontSize = 13.sp)
                    )
                    val y = max(
                        max(points[0][1], points[1][1]),
                        max(points[2][1], points[3][1])
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x = xyProjection[0].toFloat() + 5.dp.toPx(),
                            y = y.toFloat() + 32.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

// In position editing mode, draw an xy grid at ground level (z = 0).

private fun drawGrid(
    renderer: Renderer,
    scope: DrawScope,
    width: Int = scope.size.width.toInt(),
    height: Int = scope.size.height.toInt()
): Unit = with(scope) {
    val gridColor = Color.LightGray
    val strokeWidthAxes = 2.dp.toPx()
    val strokeWidthGrid = 0.75.dp.toPx()

    // Figure out pixel deltas for 1cm vectors along x and y axes
    val center = renderer.getXY(Coordinate(0.0, 0.0, 0.0))
    val dx = renderer.getXY(Coordinate(100.0, 0.0, 0.0))
    val dy = renderer.getXY(Coordinate(0.0, 100.0, 0.0))
    val axisX = doubleArrayOf(
        AnimationLayout.XY_GRID_SPACING_CM * ((dx[0] - center[0]).toDouble() / 100.0),
        AnimationLayout.XY_GRID_SPACING_CM * ((dx[1] - center[1]).toDouble() / 100.0)
    )
    val axisY = doubleArrayOf(
        AnimationLayout.XY_GRID_SPACING_CM * ((dy[0] - center[0]).toDouble() / 100.0),
        AnimationLayout.XY_GRID_SPACING_CM * ((dy[1] - center[1]).toDouble() / 100.0)
    )

    // Find which grid intersections are visible on screen by solving for the
    // grid coordinates at the four corners.
    val det = axisX[0] * axisY[1] - axisX[1] * axisY[0]
    var mMin = 0
    var mMax = 0
    var nMin = 0
    var nMax = 0
    for (j in 0..3) {
        val a = ((if (j % 2 == 0) 0 else width) - center[0]).toDouble()
        val b = ((if (j < 2) 0 else height) - center[1]).toDouble()
        val m = (axisY[1] * a - axisY[0] * b) / det
        val n = (-axisX[1] * a + axisX[0] * b) / det
        val mInt = floor(m).toInt()
        val nInt = floor(n).toInt()
        mMin = if (j == 0) mInt else min(mMin, mInt)
        mMax = if (j == 0) mInt + 1 else max(mMax, mInt + 1)
        nMin = if (j == 0) nInt else min(nMin, nInt)
        nMax = if (j == 0) nInt + 1 else max(nMax, nInt + 1)
    }

    for (j in mMin..mMax) {
        val x1 = (center[0] + j * axisX[0] + nMin * axisY[0]).toFloat()
        val y1 = (center[1] + j * axisX[1] + nMin * axisY[1]).toFloat()
        val x2 = (center[0] + j * axisX[0] + nMax * axisY[0]).toFloat()
        val y2 = (center[1] + j * axisX[1] + nMax * axisY[1]).toFloat()
        drawLine(gridColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = if (j == 0) strokeWidthAxes else strokeWidthGrid)
    }
    for (j in nMin..nMax) {
        val x1 = (center[0] + mMin * axisX[0] + j * axisY[0]).toFloat()
        val y1 = (center[1] + mMin * axisX[1] + j * axisY[1]).toFloat()
        val x2 = (center[0] + mMax * axisX[0] + j * axisY[0]).toFloat()
        val y2 = (center[1] + mMax * axisX[1] + j * axisY[1]).toFloat()
        drawLine(gridColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = if (j == 0) strokeWidthAxes else strokeWidthGrid)
    }
}
