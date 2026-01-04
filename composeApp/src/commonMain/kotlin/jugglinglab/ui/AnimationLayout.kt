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

import jugglinglab.core.PatternAnimationState
import jugglinglab.jml.JMLEvent
import jugglinglab.jml.JMLPosition
import jugglinglab.renderer.ComposeRenderer
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

    private fun calculateBoundingBox(): Pair<Coordinate, Coordinate> {
        var patternMax: Coordinate? = null
        var patternMin: Coordinate? = null
        for (i in 1..state.pattern.numberOfPaths) {
            patternMax = Coordinate.max(patternMax, state.pattern.layout.getPathMax(i))
            patternMin = Coordinate.min(patternMin, state.pattern.layout.getPathMin(i))
        }

        var propMax: Coordinate? = null
        var propMin: Coordinate? = null
        for (i in 1..state.pattern.numberOfProps) {
            propMax = Coordinate.max(propMax, state.pattern.getProp(i).getMax())
            propMin = Coordinate.min(propMin, state.pattern.getProp(i).getMin())
        }

        if (patternMax != null && patternMin != null) {
            patternMax = Coordinate.add(patternMax, propMax)
            patternMin = Coordinate.add(patternMin, propMin)
        }

        val safeMax = patternMax ?: Coordinate(100.0, 100.0, 100.0)
        val safeMin = patternMin ?: Coordinate(-100.0, -100.0, -100.0)

        safeMax.z = max(safeMax.z, 180.0)
        safeMin.x = min(safeMin.x, -50.0)
        safeMax.x = max(safeMax.x, 50.0)

        return Pair(safeMin, safeMax)
    }

    companion object {
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
