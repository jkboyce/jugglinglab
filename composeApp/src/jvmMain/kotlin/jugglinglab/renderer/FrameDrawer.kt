//
// FrameDrawer.kt
//
// This class draws individual frames of juggling. It is independent of JPanel
// or other GUI elements so that it can be used in headless mode, e.g., as when
// creating an animated GIF from the command line.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.renderer

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.Constants
import jugglinglab.core.PatternAnimationState
import jugglinglab.jml.JmlEvent
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.add
import jugglinglab.util.Coordinate.Companion.max
import jugglinglab.util.Coordinate.Companion.min
import jugglinglab.util.JuggleExceptionInternal
import java.awt.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

class FrameDrawer(
    val state: PatternAnimationState
) {
    var ren1: Renderer = Renderer2D()
    var ren2: Renderer = Renderer2D()
    private var overallMax: Coordinate? = null
    private var overallMin: Coordinate? = null

    var simIntervalSecs: Double = 0.0
        private set
    var realIntervalMillis: Long = 0
        private set

    init {
        // sync the renderers to the initial pattern
        changeAnimatorPattern()
        changeAnimatorCameraAngle()
    }

    // Propagate a change in pattern to update the renderers and
    // other data structures. Rescale the drawing so that the pattern
    // and key parts of the juggler are visible.

    fun changeAnimatorPattern() {
        val pattern = state.pattern
        val sg = (state.prefs.showGround == AnimationPrefs.GROUND_ON ||
            (state.prefs.showGround == AnimationPrefs.GROUND_AUTO && pattern.isBouncePattern))
        ren1.setPattern(pattern)
        ren1.setGround(sg)
        if (state.prefs.stereo) {
            ren2.setPattern(pattern)
            ren2.setGround(sg)
        }

        if (state.fitToFrame) {
            findMaxMin()
            syncRenderersToSize()
        }

        // figure out timing constants; this in effect adjusts fps to get an integer
        // number of frames in one repetition of the pattern
        val numFrames = (0.5 + (pattern.loopEndTime - pattern.loopStartTime) * state.prefs.slowdown * state.prefs.fps).toInt()
        simIntervalSecs = (pattern.loopEndTime - pattern.loopStartTime) / numFrames
        realIntervalMillis = (1000.0 * simIntervalSecs * state.prefs.slowdown).toLong()
    }

    // Propagate a change in camera angle to the renderers.

    fun changeAnimatorCameraAngle() {
        val ca = doubleArrayOf(state.cameraAngle[0], state.cameraAngle[1])
        while (ca[0] < 0) {
            ca[0] += 2 * Math.PI
        }
        while (ca[0] >= 2 * Math.PI) {
            ca[0] -= 2 * Math.PI
        }

        if (state.prefs.stereo) {
            ren1.cameraAngle = doubleArrayOf(ca[0] - STEREO_SEPARATION_RADIANS / 2, ca[1])
            ren2.cameraAngle = doubleArrayOf(ca[0] + STEREO_SEPARATION_RADIANS / 2, ca[1])
        } else {
            ren1.cameraAngle = ca
        }
    }

    // Draw the frame of juggling

    @Suppress("UnnecessaryVariable")
    @Throws(JuggleExceptionInternal::class)
    fun drawFrame(simTime: Double, g: Graphics, drawAxes: Boolean, drawBackground: Boolean) {
        if (drawBackground) {
            drawBackground(g)
        }

        if (state.prefs.stereo) {
            ren1.drawFrame(
                simTime,
                state.propForPath,
                state.prefs.hideJugglers,
                g.create(0, 0, state.prefs.width / 2, state.prefs.height)
            )
            ren2.drawFrame(
                simTime,
                state.propForPath,
                state.prefs.hideJugglers,
                g.create(state.prefs.width / 2, 0, state.prefs.width / 2, state.prefs.height)
            )
        } else {
            ren1.drawFrame(simTime, state.propForPath, state.prefs.hideJugglers, g)
        }

        if (drawAxes) {
            if (g is Graphics2D) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                val ren = if (i == 0) ren1 else ren2
                val ca = ren.cameraAngle
                val theta = ca[0]
                val phi = ca[1]

                val xya = 30.0
                val xyb = xya * cos(phi)
                val zlen = xya * sin(phi)
                val cx = 38 + i * (state.prefs.width / 2)
                val cy = 45
                val xx = cx + (0.5 - xya * cos(theta)).toInt()
                val xy = cy + (0.5 + xyb * sin(theta)).toInt()
                val yx = cx + (0.5 + xya * sin(theta)).toInt()
                val yy = cy + (0.5 + xyb * cos(theta)).toInt()
                val zx = cx
                val zy = cy - (0.5 + zlen).toInt()

                g.color = Color.green
                g.drawLine(cx, cy, xx, xy)
                g.drawLine(cx, cy, yx, yy)
                g.drawLine(cx, cy, zx, zy)
                g.fillOval(xx - 2, xy - 2, 5, 5)
                g.fillOval(yx - 2, yy - 2, 5, 5)
                g.fillOval(zx - 2, zy - 2, 5, 5)
                g.drawString("x", xx - 2, xy - 4)
                g.drawString("y", yx - 2, yy - 4)
                g.drawString("z", zx - 2, zy - 4)
            }
        }
    }

    fun drawBackground(g: Graphics) {
        g.color = ren1.getBackground()
        g.fillRect(0, 0, state.prefs.width, state.prefs.height)
    }

    // Find the overall bounding box of the juggler and pattern, in real-space
    // (centimeters) global coordinates. Output is the variables `overallmin`
    // and `overallmax`, which determine a bounding box.

    private fun findMaxMin() {
        val pattern = state.pattern

        // Step 1: Work out a bounding box that contains all paths through space
        // for the pattern, including the props
        var patternmax: Coordinate? = null
        var patternmin: Coordinate? = null
        for (i in 1..pattern.numberOfPaths) {
            patternmax = max(patternmax, pattern.layout.getPathMax(i))
            patternmin = min(patternmin, pattern.layout.getPathMin(i))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from FrameDrawer.findMaxMin():")
                println("Path max $i = " + pattern.layout.getPathMax(i))
                println("Path min $i = " + pattern.layout.getPathMin(i))
            }
        }

        var propmax: Coordinate? = null
        var propmin: Coordinate? = null
        for (i in 1..pattern.numberOfProps) {
            propmax = max(propmax, pattern.getProp(i).getMax())
            propmin = min(propmin, pattern.getProp(i).getMin())
        }

        // Make sure props are entirely visible along all paths. In principle
        // not all props go on all paths so this could be done more carefully.
        if (patternmax != null && patternmin != null) {
            patternmax = add(patternmax, propmax)
            patternmin = add(patternmin, propmin)
        }

        // Step 2: Work out a bounding box that contains the hands at all times,
        // factoring in the physical extent of the hands.
        var handmax: Coordinate? = null
        var handmin: Coordinate? = null
        for (i in 1..pattern.numberOfJugglers) {
            handmax = max(handmax, pattern.layout.getHandMax(i, JmlEvent.LEFT_HAND))
            handmin = min(handmin, pattern.layout.getHandMin(i, JmlEvent.LEFT_HAND))
            handmax = max(handmax, pattern.layout.getHandMax(i, JmlEvent.RIGHT_HAND))
            handmin = min(handmin, pattern.layout.getHandMin(i, JmlEvent.RIGHT_HAND))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from FrameDrawer.findMaxMin():")
                println("Hand max $i left = " + pattern.layout.getHandMax(i, JmlEvent.LEFT_HAND))
                println("Hand min $i left = " + pattern.layout.getHandMin(i, JmlEvent.LEFT_HAND))
                println("Hand max $i right = " + pattern.layout.getHandMax(i, JmlEvent.RIGHT_HAND))
                println("Hand min $i right = " + pattern.layout.getHandMin(i, JmlEvent.RIGHT_HAND))
            }
        }

        // The renderer's hand window is in local coordinates. We don't know
        // the juggler's rotation angle when `handmax` and `handmin` are
        // achieved. So we create a bounding box that contains the hand
        // regardless of rotation angle.
        val hwmax = ren1.handWindowMax
        val hwmin = ren1.handWindowMin
        hwmax.x = max(
            max(abs(hwmax.x), abs(hwmin.x)),
            max(abs(hwmax.y), abs(hwmin.y))
        )
        hwmin.x = -hwmax.x
        hwmax.y = hwmax.x
        hwmin.y = hwmin.x

        // make sure hands are entirely visible
        handmax = add(handmax, hwmax)
        handmin = add(handmin, hwmin)

        // Step 3: Find a bounding box that contains the juggler's body
        // at all times, incorporating any juggler movements as well as the
        // physical extent of the juggler's body.
        val jwmax = ren1.jugglerWindowMax
        val jwmin = ren1.jugglerWindowMin

        // Step 4: Combine the pattern, hand, and juggler bounding boxes into
        // an overall bounding box.
        overallMax = max(patternmax, max(handmax, jwmax))
        overallMin = min(patternmin, min(handmin, jwmin))

        if (Constants.DEBUG_LAYOUT) {
            println("Data from FrameDrawer.findMaxMin():")
            println("Hand max = $handmax")
            println("Hand min = $handmin")
            println("Prop max = $propmax")
            println("Prop min = $propmin")
            println("Pattern max = $patternmax")
            println("Pattern min = $patternmin")
            println("Juggler max = $jwmax")
            println("Juggler min = $jwmin")
            println("Overall max = $overallMax")
            println("Overall min = $overallMin")
        }
    }

    // Pass the global bounding box, and the viewport pixel size, to the
    // renderer so it can calculate a zoom factor.

    private fun syncRenderersToSize() {
        val d = Dimension(state.prefs.width, state.prefs.height)
        if (state.prefs.stereo) {
            d.width /= 2
            ren1.initDisplay(d, state.prefs.borderPixels, overallMax!!, overallMin!!)
            ren2.initDisplay(d, state.prefs.borderPixels, overallMax!!, overallMin!!)
        } else {
            ren1.initDisplay(d, state.prefs.borderPixels, overallMax!!, overallMin!!)
        }
    }

    companion object {
        private const val STEREO_SEPARATION_RADIANS: Double = 0.10
    }
}
