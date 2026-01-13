//
// AnimationLayout.kt
//
// Class to hold the selection view data structures. These are calculated by
// AnimationPanel and passed to AnimationView for rendering, and also used by
// AnimationPanel to interpret mouse events.
//
// All coordinates here are logical coordinates, as distinct from pixel
// coordinates, which are logical coordinates multiplied by a display-
// dependent density factor (e.g., 2.0 on an Apple Retina display)
//
// Java AWT returns mouse coordinates in logical coordinates. AnimationView
// applies the density factor to get pixel coordinates for drawing.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.Constants
import jugglinglab.core.PatternAnimationState
import jugglinglab.jml.HandLink
import jugglinglab.jml.JMLEvent
import jugglinglab.jml.JMLPosition
import jugglinglab.renderer.ComposeRenderer
import jugglinglab.renderer.Juggler
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.distance
import jugglinglab.util.JuggleExceptionInternal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class AnimationLayout(
    val state: PatternAnimationState,
    val width: Int,
    val height: Int
) {
    // Renderers for translating into logical screen coordinates
    private val calcRenderer1 = ComposeRenderer()
    private val calcRenderer2 = ComposeRenderer()

    // Coordinates (box_min, box_max) of corners of bounding box around juggler/
    // pattern, in global juggler coordinates (cm)
    val boundingBox = calculateBoundingBox()

    // Event editing -----------------------------------------------------------

    // List of events visible on screen
    var visibleEvents: List<JMLEvent> = listOf()

    // Screen (logical) coordinates of visual representations for events.
    // [event index][stereo view 0/1][control point index][x/y]
    var eventPoints = Array(0) { Array(0) { Array(0) { DoubleArray(0) } } }

    // Screen coordinates for hand paths.
    // [stereo view 0/1][point index][x/y]
    var handpathPoints = Array(0) { Array(0) { DoubleArray(0) } }
    var handpathStartTime: Double = 0.0
    var handpathEndTime: Double = 0.0

    // Whether each hand path segment is a "hold" (solid line) or not (dashed line).
    var handpathHold: BooleanArray = BooleanArray(0)

    // Visibility flags for drag controls
    var showXzDragControl: Boolean = false
    var showYDragControl: Boolean = false

    // Position editing --------------------------------------------------------

    // Screen coordinates for position editing controls.
    // [stereo view 0/1][control point index][x/y]
    var posPoints = Array(0) { Array(0) { DoubleArray(0) } }

    // Visibility flags for drag controls
    var showXyDragControl: Boolean = false
    var showZDragControl: Boolean = false
    var showAngleDragControl: Boolean = false

    init {
        if (Constants.DEBUG_LAYOUT) {
            println("AnimationLayout.init() number $layoutCount")
            ++layoutCount
        }
        initRenderers()

        val activeEventImage = getActiveEvent(state)
        if (activeEventImage != null) {
            createEventView(activeEventImage.first, activeEventImage.second)
        } else {
            val activePosition = getActivePosition(state)
            if (activePosition != null) {
                createPositionView(activePosition)
            }
        }
    }

    private fun initRenderers() {
        if (width > 0 && height > 0) {
            val (overallMin, overallMax) = boundingBox

            if (state.prefs.stereo) {
                calcRenderer1.initDisplay(
                    width / 2,
                    height,
                    state.prefs.borderPixels,
                    overallMax,
                    overallMin
                )
                calcRenderer2.initDisplay(
                    width / 2,
                    height,
                    state.prefs.borderPixels,
                    overallMax,
                    overallMin
                )
            } else {
                calcRenderer1.initDisplay(
                    width,
                    height,
                    state.prefs.borderPixels,
                    overallMax,
                    overallMin
                )
            }
        }

        calcRenderer1.setPattern(state.pattern)
        calcRenderer1.zoomLevel = state.zoom

        val ca = state.cameraAngle.toDoubleArray()
        if (state.prefs.stereo) {
            calcRenderer2.setPattern(state.pattern)
            calcRenderer2.zoomLevel = state.zoom
            val separation = STEREO_SEPARATION_RADIANS
            calcRenderer1.cameraAngle = doubleArrayOf(ca[0] - separation / 2, ca[1])
            calcRenderer2.cameraAngle = doubleArrayOf(ca[0] + separation / 2, ca[1])
        } else {
            calcRenderer1.cameraAngle = ca
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun createEventView(activeEvent: JMLEvent, activeEventPrimary: JMLEvent) {
        handpathStartTime = activeEvent.t
        handpathEndTime = activeEvent.t

        val index = state.pattern.allEvents.indexOfFirst { it.event == activeEvent }
        if (index == -1) {
            throw JuggleExceptionInternal("Error 1 in AP.createEventView()")
        }

        visibleEvents = buildList {
            add(activeEvent)
            for (image in state.pattern.allEvents.subList(index + 1, state.pattern.allEvents.size)
                .filter { it.event.hand == activeEvent.hand && it.event.juggler == activeEvent.juggler }) {
                handpathEndTime = max(handpathEndTime, image.event.t)
                if (image.primary != activeEventPrimary) add(image.event) else break
                if (image.event.hasThrowOrCatch) break
            }
            for (image in state.pattern.allEvents.subList(0, index).asReversed()
                .filter { it.event.hand == activeEvent.hand && it.event.juggler == activeEvent.juggler }) {
                handpathStartTime = min(handpathStartTime, image.event.t)
                if (image.primary != activeEventPrimary) add(image.event) else break
                if (image.event.hasThrowOrCatch) break
            }
        }

        val rendererCount = if (state.prefs.stereo) 2 else 1
        eventPoints = Array(visibleEvents.size) {
            Array(rendererCount) {
                Array(EVENT_CONTROL_POINTS.size) {
                    DoubleArray(2)
                }
            }
        }

        for ((evNum, ev2) in visibleEvents.withIndex()) {
            for (i in 0..<rendererCount) {
                val ren =
                    if (i == 0) calcRenderer1 else calcRenderer2 // Use local renderers
                val c = state.pattern.layout.getGlobalCoordinate(ev2)
                val c2 = ren.getScreenTranslatedCoordinate(c, 1, 0)
                val dl = 1.0 / distance(c, c2)

                val ca = ren.cameraAngle
                val theta = ca[0] + Math.toRadians(state.pattern.layout.getJugglerAngle(ev2.juggler, ev2.t))
                val phi = ca[1]

                val dlc = dl * cos(phi)
                val dls = dl * sin(phi)
                val dxx = -dl * cos(theta)
                val dxy = dlc * sin(theta)
                val dyx = dl * sin(theta)
                val dyy = dlc * cos(theta)
                val dzx = 0.0
                val dzy = -dls

                val center = ren.getXY(c)
                val targetPoints =
                    if (ev2 == activeEvent) EVENT_CONTROL_POINTS else UNSELECTED_EVENT_POINTS

                for (j in targetPoints.indices) {
                    eventPoints[evNum][i][j][0] =
                        center[0].toDouble() + dxx * targetPoints[j][0] + dyx * targetPoints[j][1] + dzx * targetPoints[j][2]
                    eventPoints[evNum][i][j][1] =
                        center[1].toDouble() + dxy * targetPoints[j][0] + dyy * targetPoints[j][1] + dzy * targetPoints[j][2]
                }

                if (ev2 == activeEvent) {
                    showXzDragControl =
                        (anglediff(phi - Math.PI / 2) < Math.toRadians(XZ_CONTROL_SHOW_DEG) &&
                            (anglediff(theta) < Math.toRadians(XZ_CONTROL_SHOW_DEG) || anglediff(
                                theta - Math.PI
                            ) < Math.toRadians(XZ_CONTROL_SHOW_DEG)))
                    showYDragControl =
                        !(anglediff(phi - Math.PI / 2) < Math.toRadians(Y_CONTROL_SHOW_DEG) &&
                            (anglediff(theta) < Math.toRadians(Y_CONTROL_SHOW_DEG) || anglediff(
                                theta - Math.PI
                            ) < Math.toRadians(Y_CONTROL_SHOW_DEG)))
                }
            }
        }
        createHandpathView(activeEvent)
    }

    private fun createHandpathView(activeEvent: JMLEvent) {
        val rendererCount = if (state.prefs.stereo) 2 else 1
        val numHandpathPoints =
            ceil((handpathEndTime - handpathStartTime) / HANDPATH_POINT_SEP_TIME).toInt() + 1
        handpathPoints = Array(rendererCount) { Array(numHandpathPoints) { DoubleArray(2) } }
        handpathHold = BooleanArray(numHandpathPoints)

        for (i in 0..<rendererCount) {
            val ren = if (i == 0) calcRenderer1 else calcRenderer2
            val c = Coordinate()
            for (j in 0..<numHandpathPoints) {
                val t = handpathStartTime + j * HANDPATH_POINT_SEP_TIME
                state.pattern.layout.getHandCoordinate(activeEvent.juggler, activeEvent.hand, t, c)
                val point = ren.getXY(c)
                handpathPoints[i][j][0] = point[0].toDouble()
                handpathPoints[i][j][1] = point[1].toDouble()
                handpathHold[j] = state.pattern.layout.isHandHolding(
                    activeEvent.juggler,
                    activeEvent.hand, t + 0.0001)
            }
        }
    }

    private fun createPositionView(activePosition: JMLPosition) {
        posPoints = Array(2) { Array(POS_CONTROL_POINTS.size) { DoubleArray(2) } }
        for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
            val ren = if (i == 0) calcRenderer1 else calcRenderer2
            val c = Coordinate.add(
                activePosition.coordinate,
                Coordinate(0.0, 0.0, POSITION_BOX_Z_OFFSET_CM)
            )
            val c2 = ren.getScreenTranslatedCoordinate(c!!, 1, 0)
            val dl = 1.0 / distance(c, c2)

            val ca = ren.cameraAngle
            val theta = ca[0] + Math.toRadians(activePosition.angle)
            val phi = ca[1]

            val dlc = dl * cos(phi)
            val dls = dl * sin(phi)
            val dxx = -dl * cos(theta)
            val dxy = dlc * sin(theta)
            val dyx = dl * sin(theta)
            val dyy = dlc * cos(theta)
            val dzx = 0.0
            val dzy = -dls

            val center = ren.getXY(c)
            for (j in POS_CONTROL_POINTS.indices) {
                posPoints[i][j][0] =
                    center[0].toDouble() + dxx * POS_CONTROL_POINTS[j][0] + dyx * POS_CONTROL_POINTS[j][1] + dzx * POS_CONTROL_POINTS[j][2]
                posPoints[i][j][1] =
                    center[1].toDouble() + dxy * POS_CONTROL_POINTS[j][0] + dyy * POS_CONTROL_POINTS[j][1] + dzy * POS_CONTROL_POINTS[j][2]
            }

            showAngleDragControl =
                (anglediff(phi - Math.PI / 2) > Math.toRadians(90 - ANGLE_CONTROL_SHOW_DEG))
            showXyDragControl =
                (anglediff(phi - Math.PI / 2) > Math.toRadians(90 - XY_CONTROL_SHOW_DEG))
            showZDragControl =
                (anglediff(phi - Math.PI / 2) < Math.toRadians(90 - Z_CONTROL_SHOW_DEG))
        }
    }

    val handWindowMax: Coordinate
        get() = Coordinate(Juggler.HAND_OUT, 0.0, 1.0)

    val handWindowMin: Coordinate
        get() = Coordinate(-Juggler.HAND_IN, 0.0, -1.0)

    val jugglerWindowMax: Coordinate
        get() {
            var max = state.pattern.layout.getJugglerMax(1)
            for (i in 2..state.pattern.numberOfJugglers) {
                max = Coordinate.max(max, state.pattern.layout.getJugglerMax(i))
            }

            max = Coordinate.add(
                max,
                Coordinate(
                    Juggler.SHOULDER_HW,
                    Juggler.SHOULDER_HW,  // Juggler.HEAD_HW,
                    Juggler.SHOULDER_H + Juggler.NECK_H + Juggler.HEAD_H
                )
            )
            return max!!
        }

    val jugglerWindowMin: Coordinate
        get() {
            var min = state.pattern.layout.getJugglerMin(1)
            for (i in 2..state.pattern.numberOfJugglers) {
                min = Coordinate.min(min, state.pattern.layout.getJugglerMin(i))
            }

            min = Coordinate.add(
                min,
                Coordinate(-Juggler.SHOULDER_HW, -Juggler.SHOULDER_HW, 0.0)
            )
            return min!!
        }

    private fun calculateBoundingBox(): Pair<Coordinate, Coordinate> {
        val pattern = state.pattern

        // Step 1: Work out a bounding box that contains all paths through space
        // for the pattern, including the props
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

        // Make sure props are entirely visible along all paths. In principle
        // not all props go on all paths so this could be done more carefully.
        if (patternMax != null && patternMin != null) {
            patternMax = Coordinate.add(patternMax, propMax)
            patternMin = Coordinate.add(patternMin, propMin)
        }

        // Step 2: Work out a bounding box that contains the hands at all times,
        // factoring in the physical extent of the hands.
        var handMax: Coordinate? = null
        var handMin: Coordinate? = null
        for (i in 1..pattern.numberOfJugglers) {
            handMax = Coordinate.max(handMax, pattern.layout.getHandMax(i, HandLink.LEFT_HAND))
            handMin = Coordinate.min(handMin, pattern.layout.getHandMin(i, HandLink.LEFT_HAND))
            handMax = Coordinate.max(handMax, pattern.layout.getHandMax(i, HandLink.RIGHT_HAND))
            handMin = Coordinate.min(handMin, pattern.layout.getHandMin(i, HandLink.RIGHT_HAND))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from AnimationLayout.findMaxMin():")
                println("Hand max $i left = " + pattern.layout.getHandMax(i, HandLink.LEFT_HAND))
                println("Hand min $i left = " + pattern.layout.getHandMin(i, HandLink.LEFT_HAND))
                println("Hand max $i right = " + pattern.layout.getHandMax(i, HandLink.RIGHT_HAND))
                println("Hand min $i right = " + pattern.layout.getHandMin(i, HandLink.RIGHT_HAND))
            }
        }

        // The renderer's hand window is in local coordinates. We don't know
        // the juggler's rotation angle where `handMax` and `handMin` are
        // achieved. So we create a bounding box that contains the hand
        // regardless of rotation angle.
        handWindowMax.x = max(
            max(abs(handWindowMax.x), abs(handWindowMin.x)),
            max(abs(handWindowMax.y), abs(handWindowMin.y))
        )
        handWindowMin.x = -handWindowMax.x
        handWindowMax.y = handWindowMax.x
        handWindowMin.y = handWindowMin.x

        // make sure hands are entirely visible
        handMax = Coordinate.add(handMax, handWindowMax)
        handMin = Coordinate.add(handMin, handWindowMin)

        // Step 3: Combine the pattern, hand, and juggler bounding boxes into an
        // overall bounding box.
        val overallMax = Coordinate.max(patternMax, Coordinate.max(handMax, jugglerWindowMax))
        val overallMin = Coordinate.min(patternMin, Coordinate.min(handMin, jugglerWindowMin))

        return Pair(overallMin!!, overallMax!!)
    }

    companion object {
        private var layoutCount = 0

        fun anglediff(delta: Double): Double {
            var delta = delta
            while (delta > Math.PI) delta -= 2 * Math.PI
            while (delta <= -Math.PI) delta += 2 * Math.PI
            return abs(delta)
        }

        const val STEREO_SEPARATION_RADIANS: Double = 0.1

        private const val EVENT_BOX_HW_CM: Double = 5.0
        private const val UNSELECTED_BOX_HW_CM: Double = 2.0
        private const val XZ_CONTROL_SHOW_DEG: Double = 60.0
        private const val Y_CONTROL_SHOW_DEG: Double = 30.0
        private const val HANDPATH_POINT_SEP_TIME: Double = 0.005
        private const val POSITION_BOX_HW_CM: Double = 10.0
        private const val POSITION_BOX_Z_OFFSET_CM: Double = 0.0
        private const val XY_GRID_SPACING_CM: Double = 20.0
        private const val GRID_SHOW_DEG: Double = 70.0
        private const val ANGLE_CONTROL_SHOW_DEG: Double = 70.0
        private const val XY_CONTROL_SHOW_DEG: Double = 70.0
        private const val Z_CONTROL_SHOW_DEG: Double = 30.0

        private val EVENT_CONTROL_POINTS: List<DoubleArray> = listOf(
            doubleArrayOf(-EVENT_BOX_HW_CM, 0.0, -EVENT_BOX_HW_CM),
            doubleArrayOf(-EVENT_BOX_HW_CM, 0.0, EVENT_BOX_HW_CM),
            doubleArrayOf(EVENT_BOX_HW_CM, 0.0, EVENT_BOX_HW_CM),
            doubleArrayOf(EVENT_BOX_HW_CM, 0.0, -EVENT_BOX_HW_CM),
            doubleArrayOf(0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 10.0, 0.0),
            doubleArrayOf(0.0, -10.0, 0.0),
            doubleArrayOf(0.0, 7.0, 2.0),
            doubleArrayOf(0.0, 7.0, -2.0),
            doubleArrayOf(0.0, -7.0, 2.0),
            doubleArrayOf(0.0, -7.0, -2.0),
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0),
        )

        private val UNSELECTED_EVENT_POINTS: List<DoubleArray> = listOf(
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(0.0, 0.0, 0.0),
        )

        private val POS_CONTROL_POINTS: List<DoubleArray> = listOf(
            doubleArrayOf(-POSITION_BOX_HW_CM, -POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(-POSITION_BOX_HW_CM, POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(POSITION_BOX_HW_CM, POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(POSITION_BOX_HW_CM, -POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0),
            doubleArrayOf(0.0, -20.0, 0.0),
            doubleArrayOf(0.0, 0.0, 20.0),
            doubleArrayOf(2.0, 0.0, 17.0),
            doubleArrayOf(-2.0, 0.0, 17.0),
            doubleArrayOf(0.0, -250.0, 0.0),
            doubleArrayOf(0.0, 250.0, 0.0),
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0),
        )

        fun getActiveEvent(state: PatternAnimationState): Pair<JMLEvent, JMLEvent>? {
            for ((ev, evPrimary) in state.pattern.loopEvents) {
                if (ev.jlHashCode == state.selectedItemHashCode) {
                    return Pair(ev, evPrimary)
                } else if (ev.transitions.withIndex().any { (transNum, _) ->
                        val trHash = ev.jlHashCode + 23 + transNum * 27
                        trHash == state.selectedItemHashCode
                    }) {
                    return Pair(ev, evPrimary)
                }
            }
            return null
        }

        fun getActivePosition(state: PatternAnimationState): JMLPosition? {
            for (pos in state.pattern.positions) {
                if (pos.jlHashCode == state.selectedItemHashCode) {
                    return pos
                }
            }
            return null
        }
    }
}
