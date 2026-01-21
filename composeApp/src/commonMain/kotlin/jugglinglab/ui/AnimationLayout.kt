//
// AnimationLayout.kt
//
// Class to hold the selection view data structures. These are used by
// AnimationView for rendering, and also by AnimationController to interpret
// mouse events.
//
// All coordinates here are physical pixel coordinates, as distinct from logical
// pixel coordinates. Physical coordinates are logical coordinates multiplied by
// a display dependent density factor (e.g., 2.0 on an Apple Retina display)
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.PatternAnimationState
import jugglinglab.jml.JmlEvent
import jugglinglab.jml.JmlPosition
import jugglinglab.renderer.ComposeRenderer
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.distance
import jugglinglab.util.JuggleExceptionInternal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class AnimationLayout(
    val state: PatternAnimationState,
    val width: Int,
    val height: Int,
    val renderer1: ComposeRenderer,
    val renderer2: ComposeRenderer
) {
    // Event editing -----------------------------------------------------------

    // List of events visible on screen
    var visibleEvents: List<JmlEvent> = listOf()

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

    // Whether to show the grid
    var showGrid: Boolean = false

    init {
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

    @Throws(JuggleExceptionInternal::class)
    private fun createEventView(activeEvent: JmlEvent, activeEventPrimary: JmlEvent) {
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
                val ren = if (i == 0) renderer1 else renderer2
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

    private fun createHandpathView(activeEvent: JmlEvent) {
        val rendererCount = if (state.prefs.stereo) 2 else 1
        val numHandpathPoints =
            ceil((handpathEndTime - handpathStartTime) / HANDPATH_POINT_SEP_TIME).toInt() + 1
        handpathPoints = Array(rendererCount) { Array(numHandpathPoints) { DoubleArray(2) } }
        handpathHold = BooleanArray(numHandpathPoints)

        for (i in 0..<rendererCount) {
            val ren = if (i == 0) renderer1 else renderer2
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

    private fun createPositionView(activePosition: JmlPosition) {
        posPoints = Array(2) { Array(POS_CONTROL_POINTS.size) { DoubleArray(2) } }
        showGrid = (state.cameraAngle[1] < Math.toRadians(GRID_SHOW_DEG))

        for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
            val ren = if (i == 0) renderer1 else renderer2
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

    companion object {
        fun anglediff(delta: Double): Double {
            var delta = delta
            while (delta > Math.PI) delta -= 2 * Math.PI
            while (delta <= -Math.PI) delta += 2 * Math.PI
            return abs(delta)
        }

        const val STEREO_SEPARATION_RADIANS: Double = 0.1

        const val EVENT_BOX_HW_CM: Double = 5.0
        const val UNSELECTED_BOX_HW_CM: Double = 2.0
        const val XZ_CONTROL_SHOW_DEG: Double = 60.0
        const val Y_CONTROL_SHOW_DEG: Double = 30.0
        const val HANDPATH_POINT_SEP_TIME: Double = 0.005
        const val POSITION_BOX_HW_CM: Double = 10.0
        const val POSITION_BOX_Z_OFFSET_CM: Double = 0.0
        const val XY_GRID_SPACING_CM: Double = 20.0
        const val GRID_SHOW_DEG: Double = 70.0
        const val ANGLE_CONTROL_SHOW_DEG: Double = 70.0
        const val XY_CONTROL_SHOW_DEG: Double = 70.0
        const val Z_CONTROL_SHOW_DEG: Double = 30.0

        val EVENT_CONTROL_POINTS: List<DoubleArray> = listOf(
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

        val UNSELECTED_EVENT_POINTS: List<DoubleArray> = listOf(
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(0.0, 0.0, 0.0),
        )

        val POS_CONTROL_POINTS: List<DoubleArray> = listOf(
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

        fun getActiveEvent(state: PatternAnimationState): Pair<JmlEvent, JmlEvent>? {
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

        fun getActivePosition(state: PatternAnimationState): JmlPosition? {
            for (pos in state.pattern.positions) {
                if (pos.jlHashCode == state.selectedItemHashCode) {
                    return pos
                }
            }
            return null
        }
    }
}
