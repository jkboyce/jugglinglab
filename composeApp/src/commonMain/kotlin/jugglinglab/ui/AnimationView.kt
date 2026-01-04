//
// AnimationView.kt
//
// Composable juggling animation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.PatternAnimationState
import jugglinglab.renderer.ComposeRenderer
import jugglinglab.util.Coordinate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity

@Composable
fun AnimationView(
    state: PatternAnimationState,
    layout: AnimationLayout?,
    onFrame: (Double) -> Unit // Callback with current animation time for sound syncing
) {
    val density = LocalDensity.current.density
    
    // We maintain two renderers for stereo support
    val renderer1 = remember { ComposeRenderer() }
    val renderer2 = remember { ComposeRenderer() }

    // Update renderers when pattern changes
    SideEffect {
        try {
            val pattern = state.pattern
            val sg = (state.prefs.showGround == AnimationPrefs.GROUND_ON ||
                    (state.prefs.showGround == AnimationPrefs.GROUND_AUTO && pattern.isBouncePattern))
            
            renderer1.setPattern(pattern)
            renderer1.setGround(sg)
            renderer1.zoomLevel = state.zoom

            if (state.prefs.stereo) {
                renderer2.setPattern(pattern)
                renderer2.setGround(sg)
                renderer2.zoomLevel = state.zoom
            }

            // Sync camera angles
            val ca = doubleArrayOf(state.cameraAngle[0], state.cameraAngle[1])
            // Normalize angle
            while (ca[0] < 0) ca[0] += 2 * Math.PI
            while (ca[0] >= 2 * Math.PI) ca[0] -= 2 * Math.PI

            if (state.prefs.stereo) {
                val separation = 0.10 // STEREO_SEPARATION_RADIANS
                renderer1.cameraAngle = doubleArrayOf(ca[0] - separation / 2, ca[1])
                renderer2.cameraAngle = doubleArrayOf(ca[0] + separation / 2, ca[1])
            } else {
                renderer1.cameraAngle = ca
            }
        } catch (e: Exception) {
            // jlHandleFatalException not available in commonMain, log to console or ignore
            println("Error in AnimationView SideEffect: ${e.message}")
        }
    }

    // Animation Loop
    LaunchedEffect(state.isPaused) {
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
                    
                    // Update state time
                    var newTime = state.time + deltaSimSecs
                    
                    // Loop logic
                    if (newTime >= state.pattern.loopEndTime) {
                         val overflow = newTime - state.pattern.loopEndTime
                         newTime = state.pattern.loopStartTime + (overflow % loopDuration)
                         
                         // Advance props
                         state.advancePropForPath()
                    }
                    
                    state.update(time = newTime)
                    onFrame(newTime)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.White)
            
            val width = size.width.toInt()
            val height = size.height.toInt()
            val borderPixels = state.prefs.borderPixels

            val (minC, maxC) = calculateBoundingBox(state)
            
            if (state.prefs.stereo) {
                val w = width / 2
                renderer1.initDisplay(w, height, borderPixels, maxC, minC)
                renderer2.initDisplay(w, height, borderPixels, maxC, minC)
                
                withTransform({ translate(left = 0f, top = 0f) }) {
                   renderer1.drawFrame(state.time, state.propForPath, state.prefs.hideJugglers, this)
                   drawOverlays(layout, 0, this, density)
                   drawPositions(layout, 0, this, density)
                }
                
                withTransform({ translate(left = w.toFloat(), top = 0f) }) {
                   renderer2.drawFrame(state.time, state.propForPath, state.prefs.hideJugglers, this)
                   drawOverlays(layout, 1, this, density)
                   drawPositions(layout, 1, this, density)
                }
            } else {
                renderer1.initDisplay(width, height, borderPixels, maxC, minC)
                renderer1.drawFrame(state.time, state.propForPath, state.prefs.hideJugglers, this)
                drawOverlays(layout, 0, this, density)
                drawPositions(layout, 0, this, density)
            }
        }
    }
}

private fun DrawScope.drawOverlays(layout: AnimationLayout?, viewIndex: Int, scope: DrawScope, density: Float) {
    if (layout == null) return
    
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
                val p1 = Offset(points[i][0].toFloat() * density, points[i][1].toFloat() * density)
                val p2 = Offset(points[i+1][0].toFloat() * density, points[i+1][1].toFloat() * density)
                
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
            
            scope.drawPath(pathSolid, Color.LightGray, style = Stroke(width = 2f))
            scope.drawPath(pathDashed, Color.LightGray, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))))
        }
    }
    
    // Draw Events
    val eventPoints = layout.eventPoints
    
    for (evIndex in eventPoints.indices) {
        if (viewIndex < eventPoints[evIndex].size) {
            val points = eventPoints[evIndex][viewIndex]
            val isSelected = (evIndex == 0) // First event is always the active one in the list
            
            // Center dot
            val center = points[4] // Index 4 is center
            val cx = center[0].toFloat() * density
            val cy = center[1].toFloat() * density
            
            scope.drawOval(
                color = Color.Green,
                topLeft = Offset(cx - 2.5f, cy - 2.5f),
                size = Size(5f, 5f)
            )
            
            if (isSelected) {
                if (layout.showXzDragControl) {
                    val p0 = Offset(points[0][0].toFloat() * density, points[0][1].toFloat() * density)
                    val p1 = Offset(points[1][0].toFloat() * density, points[1][1].toFloat() * density)
                    val p2 = Offset(points[2][0].toFloat() * density, points[2][1].toFloat() * density)
                    val p3 = Offset(points[3][0].toFloat() * density, points[3][1].toFloat() * density)
                    scope.drawLine(Color.Green, p0, p1, strokeWidth = 2f)
                    scope.drawLine(Color.Green, p1, p2, strokeWidth = 2f)
                    scope.drawLine(Color.Green, p2, p3, strokeWidth = 2f)
                    scope.drawLine(Color.Green, p3, p0, strokeWidth = 2f)
                }
                
                if (layout.showYDragControl) {
                     scope.drawLine(Color.Green, 
                         Offset(points[5][0].toFloat() * density, points[5][1].toFloat() * density),
                         Offset(points[6][0].toFloat() * density, points[6][1].toFloat() * density),
                         strokeWidth = 2f
                     )
                }
            } else {
                // Draw unselected event box
                val p0 = Offset(points[0][0].toFloat() * density, points[0][1].toFloat() * density)
                val p1 = Offset(points[1][0].toFloat() * density, points[1][1].toFloat() * density)
                val p2 = Offset(points[2][0].toFloat() * density, points[2][1].toFloat() * density)
                val p3 = Offset(points[3][0].toFloat() * density, points[3][1].toFloat() * density)
                scope.drawLine(Color.Green, p0, p1, strokeWidth = 2f)
                scope.drawLine(Color.Green, p1, p2, strokeWidth = 2f)
                scope.drawLine(Color.Green, p2, p3, strokeWidth = 2f)
                scope.drawLine(Color.Green, p3, p0, strokeWidth = 2f)
            }
        }
    }
    
}

private fun DrawScope.drawPositions(layout: AnimationLayout?, viewIndex: Int, scope: DrawScope, density: Float) {
    if (layout == null) return
    val posPoints = layout.posPoints

    // Check if we have data for this viewIndex
    if (viewIndex < posPoints.size) {
        val points = posPoints[viewIndex]
        if (points.isEmpty()) return
        
        // Center dot (index 4)
        val center = points[4]
        val cx = center[0].toFloat() * density
        val cy = center[1].toFloat() * density
        
        scope.drawOval(
            color = Color.Green,
            topLeft = Offset(cx - 2.5f, cy - 2.5f),
            size = Size(5f, 5f)
        )
        
        // Draw Box 0-3
        // 0-1, 1-2, 2-3, 3-0
        val p0 = Offset(points[0][0].toFloat() * density, points[0][1].toFloat() * density)
        val p1 = Offset(points[1][0].toFloat() * density, points[1][1].toFloat() * density)
        val p2 = Offset(points[2][0].toFloat() * density, points[2][1].toFloat() * density)
        val p3 = Offset(points[3][0].toFloat() * density, points[3][1].toFloat() * density)
        
        scope.drawLine(Color.Green, p0, p1, strokeWidth = 2f)
        scope.drawLine(Color.Green, p1, p2, strokeWidth = 2f)
        scope.drawLine(Color.Green, p2, p3, strokeWidth = 2f)
        scope.drawLine(Color.Green, p3, p0, strokeWidth = 2f)
        
        // Angle Control (handle at 5)
        if (layout.showAngleDragControl) {
            val p5 = Offset(points[5][0].toFloat() * density, points[5][1].toFloat() * density)
            val p4 = Offset(points[4][0].toFloat() * density, points[4][1].toFloat() * density)
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
            val p6 = Offset(points[6][0].toFloat() * density, points[6][1].toFloat() * density)
            val p4 = Offset(points[4][0].toFloat() * density, points[4][1].toFloat() * density)
            
            scope.drawLine(Color.Green, p4, p6, strokeWidth = 1f)
            
            // Arrow heads
            val p7 = Offset(points[7][0].toFloat() * density, points[7][1].toFloat() * density)
            val p8 = Offset(points[8][0].toFloat() * density, points[8][1].toFloat() * density)
            
            scope.drawLine(Color.Green, p7, p6, strokeWidth = 1f)
            scope.drawLine(Color.Green, p8, p6, strokeWidth = 1f)
        }
    }
}

private fun calculateBoundingBox(state: PatternAnimationState): Pair<Coordinate, Coordinate> {
    val pattern = state.pattern
    
    var patternMax: Coordinate? = null
    var patternMin: Coordinate? = null
    for (i in 1..pattern.numberOfPaths) {
         patternMax = Coordinate.max(patternMax, pattern.layout.getPathMax(i))
         patternMin = Coordinate.min(patternMin, pattern.layout.getPathMin(i))
    }
    
    var propMax: Coordinate? = null
    var propMin: Coordinate? = null
    for (i in 1..pattern.numberOfProps) {
        propMax = Coordinate.max(propMax, pattern.getProp(i).getMax())
        propMin = Coordinate.min(propMin, pattern.getProp(i).getMin())
    }
    
    if (patternMax != null && patternMin != null) {
        patternMax = Coordinate.add(patternMax, propMax)
        patternMin = Coordinate.add(patternMin, propMin)
    }
    
    val safeMax = patternMax ?: Coordinate(100.0, 100.0, 100.0)
    val safeMin = patternMin ?: Coordinate(-100.0, -100.0, -100.0)
    
    safeMax.z = kotlin.math.max(safeMax.z, 180.0)
    safeMin.x = kotlin.math.min(safeMin.x, -50.0)
    safeMax.x = kotlin.math.max(safeMax.x, 50.0)
    
    return Pair(safeMin, safeMax)
}
