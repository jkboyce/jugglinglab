//
// AnimationEditPanel.kt
//
// This subclass of AnimationPanel is used by Edit view. It adds functionality
// for interacting with on-screen representations of JML events and positions,
// and for interacting with a ladder diagram.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.jml.JMLEvent
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.JMLPosition
import jugglinglab.util.*
import jugglinglab.util.Coordinate.Companion.add
import jugglinglab.util.Coordinate.Companion.distance
import jugglinglab.util.jlHandleFatalException
import java.awt.*
import java.awt.event.*
import java.awt.geom.Path2D
import javax.swing.SwingUtilities
import kotlin.math.*
import androidx.compose.ui.unit.IntSize
import jugglinglab.jml.PatternBuilder
import jugglinglab.util.Coordinate.Companion.sub

class AnimationEditPanel : AnimationPanel(), MouseListener, MouseMotionListener {
    // for when an event is activated/dragged
    private var eventActive: Boolean = false
    private var event: JMLEvent? = null
    private var draggingXz: Boolean = false
    private var draggingY: Boolean = false
    private var showXzDragControl: Boolean = false
    private var showYDragControl: Boolean = false
    private var eventStart: Coordinate? = null
    private var eventPrimaryStart: Coordinate? = null
    private var visibleEvents: ArrayList<JMLEvent>? = null
    private var eventPoints: Array<Array<Array<DoubleArray>>>
    private var handpathPoints: Array<Array<DoubleArray>>
    private var handpathStartTime: Double = 0.0
    private var handpathEndTime: Double = 0.0
    private var handpathHold: BooleanArray = BooleanArray(0)

    // for when a position is activated/dragged
    private var positionActive: Boolean = false
    private var position: JMLPosition? = null
    private var posPoints: Array<Array<DoubleArray>>
    private var draggingXy: Boolean = false
    private var draggingZ: Boolean = false
    private var draggingAngle: Boolean = false
    private var showXyDragControl: Boolean = false
    private var showZDragControl: Boolean = false
    private var showAngleDragControl: Boolean = false
    private var positionStart: Coordinate? = null
    private var startAngle: Double = 0.0

    // for when a position angle is being dragged
    private var deltaAngle: Double = 0.0
    private var startDx: DoubleArray = doubleArrayOf(0.0, 0.0)
    private var startDy: DoubleArray = doubleArrayOf(0.0, 0.0)
    private var startControl: DoubleArray = doubleArrayOf(0.0, 0.0)

    // for when either an event or position is being dragged
    private var dragging: Boolean = false
    private var draggingLeft: Boolean = false // for stereo mode; may not be necessary?
    private var deltax: Int = 0
    private var deltay: Int = 0 // extent of drag action (pixels)

    init {
        eventPoints = Array(0) { Array(1) { Array(0) { DoubleArray(2) } } }
        handpathPoints = Array(1) { Array(0) { DoubleArray(2) } }
        handpathHold = BooleanArray(0)
        posPoints = Array(2) { Array(0) { DoubleArray(2) } }
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //--------------------------------------------------------------------------

    private var lastpress: Long = 0L
    private var lastenter: Long = 1L

    override fun mousePressed(me: MouseEvent) {
        lastpress = me.getWhen()

        // The following (and its equivalent in mouseReleased()) is a hack to
        // swallow a mouseclick when the browser stops reporting enter/exit
        // events because the user has clicked on something else. The system
        // reports simultaneous enter/press events when the user mouses down in
        // the component; we want to not treat this as a click, but just use it
        // to get focus back.
        if (jc.mousePause && lastpress == lastenter) return
        if (!engineAnimating) return
        if (writingGIF) return

        startx = me.getX()
        starty = me.getY()

        if (eventActive) {
            val mx = me.getX()
            val my = me.getY()

            for (i in 0..<(if (jc.stereo) 2 else 1)) {
                val t = i * size.width / 2

                if (showYDragControl) {
                    draggingY =
                        jlIsNearLine(
                            mx - t,
                            my,
                            eventPoints[0][i][5][0].roundToInt(),
                            eventPoints[0][i][5][1].roundToInt(),
                            eventPoints[0][i][6][0].roundToInt(),
                            eventPoints[0][i][6][1].roundToInt(),
                            4
                        )

                    if (draggingY) {
                        dragging = true
                        draggingLeft = (i == 0)
                        deltay = 0
                        deltax = 0
                        eventStart = event!!.localCoordinate
                        eventPrimaryStart = event!!.primary.localCoordinate
                        repaint()
                        return
                    }
                }

                if (showXzDragControl) {
                    for (j in eventPoints.indices) {
                        if (!isInsidePolygon(mx - t, my, eventPoints[j], i, FACE_XZ)) {
                            continue
                        }

                        if (j > 0) {
                            try {
                                activateEvent(pattern!!.getEventImageInLoop(visibleEvents!![j]))
                                for (att in attachments) {
                                    if (att is EditLadderDiagram) {
                                        att.activateEvent(event!!)
                                    }
                                    att.repaintAttachment()
                                }
                            } catch (jei: JuggleExceptionInternal) {
                                jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
                            }
                        }

                        draggingXz = true
                        dragging = true
                        draggingLeft = (i == 0)
                        deltay = 0
                        deltax = 0
                        eventStart = event!!.localCoordinate
                        eventPrimaryStart = event!!.primary.localCoordinate
                        repaint()
                        return
                    }
                }
            }
        }

        if (positionActive) {
            val mx = me.getX()
            val my = me.getY()

            for (i in 0..<(if (jc.stereo) 2 else 1)) {
                val t = i * size.width / 2

                if (showZDragControl) {
                    draggingZ =
                        jlIsNearLine(
                            mx - t,
                            my,
                            posPoints[i][4][0].roundToInt(),
                            posPoints[i][4][1].roundToInt(),
                            posPoints[i][6][0].roundToInt(),
                            posPoints[i][6][1].roundToInt(),
                            4
                        )

                    if (draggingZ) {
                        dragging = true
                        draggingLeft = (i == 0)
                        deltay = 0
                        deltax = 0
                        positionStart = position!!.coordinate
                        repaint()
                        return
                    }
                }

                if (showXyDragControl) {
                    draggingXy = isInsidePolygon(mx - t, my, posPoints, i, FACE_XY)

                    if (draggingXy) {
                        dragging = true
                        draggingLeft = (i == 0)
                        deltay = 0
                        deltax = 0
                        positionStart = position!!.coordinate
                        repaint()
                        return
                    }
                }

                if (showAngleDragControl) {
                    val dmx = mx - t - posPoints[i][5][0].roundToInt()
                    val dmy = my - posPoints[i][5][1].roundToInt()
                    draggingAngle = (dmx * dmx + dmy * dmy < 49.0)

                    if (draggingAngle) {
                        dragging = true
                        draggingLeft = (i == 0)
                        deltay = 0
                        deltax = 0

                        // record pixel coordinates of x and y unit vectors
                        // in juggler's frame, at start of angle drag
                        startDx =
                            doubleArrayOf(
                                posPoints[i][11][0] - posPoints[i][4][0],
                                posPoints[i][11][1] - posPoints[i][4][1]
                            )
                        startDy =
                            doubleArrayOf(
                                posPoints[i][12][0] - posPoints[i][4][0],
                                posPoints[i][12][1] - posPoints[i][4][1]
                            )
                        startControl =
                            doubleArrayOf(
                                posPoints[i][5][0] - posPoints[i][4][0],
                                posPoints[i][5][1] - posPoints[i][4][1]
                            )
                        repaint()
                        return
                    }
                }
            }
        }
    }

    override fun mouseReleased(me: MouseEvent) {
        if (jc.mousePause && lastpress == lastenter) return
        if (writingGIF) return
        if (!engineAnimating && engine != null && engine!!.isAlive) {
            isPaused = !enginePaused
            return
        }

        val mouseMoved = (me.getX() != startx) || (me.getY() != starty)

        if (eventActive && dragging && mouseMoved) {
            draggingY = false
            draggingXz = false

            try {
                for (att in attachments) {
                    if (att is EditLadderDiagram) {
                        // reactivate the event in ladder diagram, since we've
                        // called layoutPattern() and events may have changed
                        event = att.reactivateEvent()
                        att.addToUndoList(pattern!!)
                    }
                }
                animator.initAnimator()
                activateEvent(event)
            } catch (jei: JuggleExceptionInternal) {
                jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
            }
        }

        if (positionActive && dragging && mouseMoved) {
            draggingAngle = false
            draggingZ = false
            draggingXy = false
            deltaAngle = 0.0
            for (att in attachments) {
                if (att is EditLadderDiagram) {
                    att.addToUndoList(pattern!!)
                }
            }
            animator.initAnimator()
            activatePosition(position)
        }

        if (!mouseMoved && !dragging && engine != null && engine!!.isAlive) {
            isPaused = !enginePaused
        }

        draggingCamera = false
        dragging = false
        draggingY = false
        draggingXz = false
        draggingAngle = false
        draggingZ = false
        draggingXy = false
        deltay = 0
        deltax = 0
        deltaAngle = 0.0
        eventPrimaryStart = null
        eventStart = null
        positionStart = null
        repaint()
    }

    override fun mouseClicked(e: MouseEvent?) {}

    override fun mouseEntered(me: MouseEvent) {
        lastenter = me.getWhen()
        if (jc.mousePause && !writingGIF) {
            isPaused = waspaused
        }
        outside = false
        outsideValid = true
    }

    override fun mouseExited(me: MouseEvent?) {
        if (jc.mousePause && !writingGIF) {
            waspaused = isPaused
            isPaused = true
        }
        outside = true
        outsideValid = true
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //--------------------------------------------------------------------------

    override fun mouseDragged(me: MouseEvent) {
        if (!engineAnimating) return
        if (writingGIF) return

        if (dragging) {
            val mx = me.getX()
            val my = me.getY()
            var dolayout = false

            if (draggingAngle) {
                // shift pixel coords of control point by mouse drag
                val dcontrol = doubleArrayOf(
                    startControl[0] + mx - startx,
                    startControl[1] + my - starty
                )

                // re-express control point location in coordinate
                // system of juggler:
                //
                // dcontrol_x = A * start_dx_x + B * start_dy_x;
                // dcontrol_y = A * start_dx_y + B * start_dy_y;
                //
                // then (A, B) are coordinates of shifted control
                // point, in juggler space
                val det = startDx[0] * startDy[1] - startDx[1] * startDy[0]
                val a = (startDy[1] * dcontrol[0] - startDy[0] * dcontrol[1]) / det
                val b = (-startDx[1] * dcontrol[0] + startDx[0] * dcontrol[1]) / det
                deltaAngle = -atan2(-a, -b)

                // snap the angle to the four cardinal directions
                val newAngle = startAngle + deltaAngle
                if (anglediff(newAngle) < SNAPANGLE / 2) {
                    deltaAngle = -startAngle
                } else if (anglediff(newAngle + 0.5 * Math.PI) < SNAPANGLE / 2) {
                    deltaAngle = -startAngle - 0.5 * Math.PI
                } else if (anglediff(newAngle + Math.PI) < SNAPANGLE / 2) {
                    deltaAngle = -startAngle + Math.PI
                } else if (anglediff(newAngle + 1.5 * Math.PI) < SNAPANGLE / 2) {
                    deltaAngle = -startAngle + 0.5 * Math.PI
                }

                var finalAngle = Math.toDegrees(startAngle + deltaAngle)
                while (finalAngle > 360) {
                    finalAngle -= 360.0
                }
                while (finalAngle < 0) {
                    finalAngle += 360.0
                }
                val rec = PatternBuilder.fromJMLPattern(pattern!!)
                val index = rec.positions.indexOf(position!!)
                if (index < 0) throw JuggleExceptionInternal("Error 1 in AEP.mouseDragged()")
                val newPosition = position!!.copy(angle = finalAngle)
                rec.positions[index] = newPosition
                position = newPosition
                restartJuggle(JMLPattern.fromPatternBuilder(rec), null)
            } else {
                deltax = mx - startx
                deltay = my - starty

                // Get updated event/position coordinate based on mouse position.
                // This modifies deltax, deltay based on snapping and projection.
                val cc = currentCoordinate

                if (eventActive) {
                    var deltalc = sub(cc, eventStart)!!
                    deltalc = Coordinate.truncate(deltalc, 1e-7)

                    val newEventCoordinate = add(eventStart, deltalc)!!
                    val newEvent = event!!.copy(
                        x = newEventCoordinate.x,
                        y = newEventCoordinate.y,
                        z = newEventCoordinate.z
                    )

                    val primary = event!!.primary
                    if (event!!.hand != primary.hand) {
                        deltalc.x = -deltalc.x
                    }
                    val newPrimaryCoordinate = add(eventPrimaryStart, deltalc)!!
                    val newPrimary = primary.copy(
                        x = newPrimaryCoordinate.x,
                        y = newPrimaryCoordinate.y,
                        z = newPrimaryCoordinate.z
                    )

                    event = newEvent
                    if (pattern != null) {
                        val record = PatternBuilder.fromJMLPattern(pattern!!)
                        val index = record.events.indexOf(primary)
                        record.events[index] = newPrimary
                        restartJuggle(JMLPattern.fromPatternBuilder(record), null)
                        //dolayout = true
                    }
                }

                if (positionActive) {
                    val rec = PatternBuilder.fromJMLPattern(pattern!!)
                    val index = rec.positions.indexOf(position!!)
                    if (index < 0) throw JuggleExceptionInternal("Error 2 in AEP.mouseDragged()")
                    val newPosition = position!!.copy(x = cc.x, y = cc.y, z = cc.z)
                    rec.positions[index] = newPosition
                    position = newPosition
                    restartJuggle(JMLPattern.fromPatternBuilder(rec), null)
                }
            }

            if (dolayout) {
                try {
                    synchronized(animator.pat!!) {
                        animator.pat!!.setNeedsLayout()
                        animator.pat!!.layout
                    }
                    if (eventActive) {
                        createHandpathView()
                    }
                } catch (je: JuggleException) {
                    // The editing operations here should never put the pattern
                    // into an invalid state, so we shouldn't ever get here
                    jlHandleFatalException(je)
                }
                repaint()
            }
        } else if (!draggingCamera) {
            draggingCamera = true
            lastx = startx
            lasty = starty
            dragcamangle = animator.cameraAngle
        }

        if (draggingCamera) {
            val dx = me.getX() - lastx
            val dy = me.getY() - lasty
            lastx = me.getX()
            lasty = me.getY()
            val ca = dragcamangle
            ca!![0] += dx.toDouble() * 0.02
            ca[1] -= dy.toDouble() * 0.02
            if (ca[1] < Math.toRadians(0.0001)) {
                ca[1] = Math.toRadians(0.0001)
            }
            if (ca[1] > Math.toRadians(179.9999)) {
                ca[1] = Math.toRadians(179.9999)
            }
            while (ca[0] < 0) {
                ca[0] += Math.toRadians(360.0)
            }
            while (ca[0] >= Math.toRadians(360.0)) {
                ca[0] -= Math.toRadians(360.0)
            }

            animator.cameraAngle = snapCamera(ca)
        }

        if (eventActive && draggingCamera) {
            try {
                createEventView()
            } catch (jei: JuggleExceptionInternal) {
                jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
            }
        }

        if (positionActive && (draggingCamera || draggingAngle)) {
            createPositionView()
        }

        if (isPaused) {
            repaint()
        }
    }

    override fun mouseMoved(e: MouseEvent?) {}

    //--------------------------------------------------------------------------
    // AnimationPanel methods
    //--------------------------------------------------------------------------

    override fun initHandlers() {
        addMouseListener(this)
        addMouseMotionListener(this)

        addComponentListener(
            object : ComponentAdapter() {
                var hasResized: Boolean = false

                override fun componentResized(e: ComponentEvent?) {
                    if (!engineAnimating) return
                    if (writingGIF) return

                    animator.dimension = size
                    if (eventActive) {
                        try {
                            createEventView()
                        } catch (jei: JuggleExceptionInternal) {
                            jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
                        }
                    }
                    if (positionActive) {
                        createPositionView()
                    }
                    if (isPaused) {
                        repaint()
                    }

                    // Don't update the preferred animation size if the enclosing
                    // window is maximized
                    val comp = SwingUtilities.getRoot(this@AnimationEditPanel)
                    if (comp is PatternWindow) {
                        if (comp.isWindowMaximized) {
                            return
                        }
                    }

                    if (hasResized) {
                        jc.size = IntSize(size.width, size.height)
                    }
                    hasResized = true
                }
            })
    }

    override fun snapCamera(ca: DoubleArray): DoubleArray {
        val result = DoubleArray(2)
        result[0] = ca[0]
        result[1] = ca[1]

        // vertical snap to equator and north/south poles
        if (result[1] < SNAPANGLE) {
            result[1] = Math.toRadians(0.0001) // avoid gimbal lock
        } else if (anglediff(Math.toRadians(90.0) - result[1]) < SNAPANGLE) {
            result[1] = Math.toRadians(90.0)
        } else if (result[1] > (Math.toRadians(180.0) - SNAPANGLE)) {
            result[1] = Math.toRadians(179.9999)
        }

        var a = 0.0
        var snapHorizontal = true

        if (eventActive) {
            a = -Math.toRadians(animator.pat!!.layout.getJugglerAngle(event!!.juggler, event!!.t))
        } else if (positionActive) {
            // a = -Math.toRadians(anim.pat.getJugglerAngle(position.getJuggler(), position.getT()));
            a = 0.0
        } else if (animator.pat!!.numberOfJugglers == 1) {
            a = -Math.toRadians(animator.pat!!.layout.getJugglerAngle(1, time))
        } else {
            snapHorizontal = false
        }

        if (snapHorizontal) {
            while (a < 0) {
                a += Math.toRadians(360.0)
            }
            while (a >= Math.toRadians(360.0)) {
                a -= Math.toRadians(360.0)
            }

            if (anglediff(a - result[0]) < SNAPANGLE) {
                result[0] = a
            } else if (anglediff(a + 0.5 * Math.PI - result[0]) < SNAPANGLE) {
                result[0] = a + 0.5 * Math.PI
            } else if (anglediff(a + Math.PI - result[0]) < SNAPANGLE) {
                result[0] = a + Math.PI
            } else if (anglediff(a + 1.5 * Math.PI - result[0]) < SNAPANGLE) {
                result[0] = a + 1.5 * Math.PI
            }
        }
        return result
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartJuggle(pat: JMLPattern?, newjc: AnimationPrefs?) {
        super.restartJuggle(pat, newjc)
        if (eventActive) {
            createEventView()
        }
        if (positionActive) {
            createPositionView()
        }
    }

    override var zoomLevel: Double
        get() = super.zoomLevel
        set(z) {
            if (!writingGIF) {
                animator.zoomLevel = z
                try {
                    createEventView()
                } catch (jei: JuggleExceptionInternal) {
                    jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
                }
                createPositionView()
                repaint()
            }
        }

    //--------------------------------------------------------------------------
    // Helper functions related to event editing
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    fun activateEvent(ev: JMLEvent?) {
        deactivatePosition()
        event = ev
        eventActive = true
        createEventView()
    }

    fun deactivateEvent() {
        event = null
        eventActive = false
        draggingY = false
        draggingXz = false
        eventPoints = Array(0) { Array(1) { Array(0) { DoubleArray(2) } } }
        visibleEvents = null
        handpathPoints = Array(1) { Array(0) { DoubleArray(2) } }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun createEventView() {
        if (!eventActive) return

        // determine which events to display on-screen
        visibleEvents = ArrayList()
        visibleEvents!!.add(event!!)
        handpathStartTime = event!!.t
        handpathEndTime = event!!.t

        var ev2 = event!!.previous
        while (ev2 != null) {
            if (ev2.juggler == event!!.juggler && ev2.hand == event!!.hand) {
                handpathStartTime = min(handpathStartTime, ev2.t)

                var newPrimary = true
                for (ev3 in visibleEvents!!) {
                    if (ev3.hasSamePrimaryAs(ev2)) {
                        newPrimary = false
                    }
                }
                if (newPrimary) {
                    visibleEvents!!.add(ev2)
                } else {
                    break
                }
                if (ev2.hasThrowOrCatch) {
                    break
                }
            }
            ev2 = ev2.previous
        }

        ev2 = event!!.next
        while (ev2 != null) {
            if (ev2.juggler == event!!.juggler && ev2.hand == event!!.hand) {
                handpathEndTime = max(handpathEndTime, ev2.t)

                var newPrimary = true
                for (ev3 in visibleEvents!!) {
                    if (ev3.hasSamePrimaryAs(ev2)) {
                        newPrimary = false
                    }
                }
                if (newPrimary) {
                    visibleEvents!!.add(ev2)
                } else {
                    break
                }
                if (ev2.hasThrowOrCatch) {
                    break
                }
            }
            ev2 = ev2.next
        }

        // Determine screen coordinates of visual representations for events.
        // Note the first event in `visible_events` is the selected one.
        val rendererCount = if (jc.stereo) 2 else 1
        eventPoints =
            Array(visibleEvents!!.size) {
                Array(rendererCount) {
                    Array(EVENT_CONTROL_POINTS.size) { DoubleArray(2) }
                }
            }

        var evNum = 0
        for (ev in visibleEvents!!) {
            for (i in 0..<rendererCount) {
                val ren = (if (i == 0) animator.ren1 else animator.ren2)

                // translate by one pixel and see how far it is in juggler space
                val c = ev.globalCoordinate
                    ?: throw JuggleExceptionInternalWithPattern("AEP: No coord on event $ev", pattern)
                val c2 = ren!!.getScreenTranslatedCoordinate(c, 1, 0)
                val dl = 1.0 / distance(c, c2) // pixels/cm

                val ca = ren.cameraAngle
                val theta = ca[0] + Math.toRadians(pattern!!.layout.getJugglerAngle(ev.juggler, ev.t))
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

                if (ev == event) {
                    for (j in EVENT_CONTROL_POINTS.indices) {
                        eventPoints[0][i][j][0] =
                            (center[0].toDouble() +
                                dxx * EVENT_CONTROL_POINTS[j][0] +
                                dyx * EVENT_CONTROL_POINTS[j][1] +
                                dzx * EVENT_CONTROL_POINTS[j][2])
                        eventPoints[0][i][j][1] =
                            (center[1].toDouble() +
                                dxy * EVENT_CONTROL_POINTS[j][0] +
                                dyy * EVENT_CONTROL_POINTS[j][1] +
                                dzy * EVENT_CONTROL_POINTS[j][2])
                    }

                    showXzDragControl =
                        (anglediff(phi - Math.PI / 2) < Math.toRadians(XZ_CONTROL_SHOW_DEG)
                            && (anglediff(theta) < Math.toRadians(XZ_CONTROL_SHOW_DEG)
                            || anglediff(theta - Math.PI) < Math.toRadians(XZ_CONTROL_SHOW_DEG))
                            )
                    showYDragControl =
                        !(anglediff(phi - Math.PI / 2) < Math.toRadians(Y_CONTROL_SHOW_DEG)
                            && (anglediff(theta) < Math.toRadians(Y_CONTROL_SHOW_DEG)
                            || anglediff(theta - Math.PI) < Math.toRadians(Y_CONTROL_SHOW_DEG))
                            )
                } else {
                    for (j in UNSELECTED_EVENT_POINTS.indices) {
                        eventPoints[evNum][i][j][0] =
                            (center[0].toDouble() +
                                dxx * UNSELECTED_EVENT_POINTS[j][0] +
                                dyx * UNSELECTED_EVENT_POINTS[j][1] +
                                dzx * UNSELECTED_EVENT_POINTS[j][2])
                        eventPoints[evNum][i][j][1] =
                            (center[1].toDouble() +
                                dxy * UNSELECTED_EVENT_POINTS[j][0] +
                                dyy * UNSELECTED_EVENT_POINTS[j][1] +
                                dzy * UNSELECTED_EVENT_POINTS[j][2])
                    }
                }
            }
            ++evNum
        }
        createHandpathView()
    }

    @Throws(JuggleExceptionInternal::class)
    private fun createHandpathView() {
        if (!eventActive) return

        val pat = pattern
        val rendererCount = (if (jc.stereo) 2 else 1)
        val numHandpathPoints =
            ceil((handpathEndTime - handpathStartTime) / HANDPATH_POINT_SEP_TIME).toInt() + 1
        handpathPoints =
            Array(rendererCount) { Array(numHandpathPoints) { DoubleArray(2) } }
        handpathHold = BooleanArray(numHandpathPoints)

        for (i in 0..<rendererCount) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)
            val c = Coordinate()

            for (j in 0..<numHandpathPoints) {
                val t: Double = handpathStartTime + j * HANDPATH_POINT_SEP_TIME
                pat!!.layout.getHandCoordinate(event!!.juggler, event!!.hand, t, c)
                val point = ren!!.getXY(c)
                handpathPoints[i][j][0] = point[0].toDouble()
                handpathPoints[i][j][1] = point[1].toDouble()
                handpathHold[j] = pat.layout.isHandHolding(event!!.juggler, event!!.hand, t + 0.0001)
            }
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun drawEvents(g: Graphics) {
        if (!eventActive) return

        val d = size
        var g2 = g

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            // Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            if (jc.stereo) {
                if (i == 0) {
                    g2 = g.create(0, 0, d.width / 2, d.height)
                } else {
                    g2 = g.create(d.width / 2, 0, d.width / 2, d.height)
                }
            }

            if (g2 is Graphics2D) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            // draw hand path
            val numHandpathPoints = handpathPoints[0].size
            val pathSolid = Path2D.Double()
            val pathDashed = Path2D.Double()
            for (j in 0..<numHandpathPoints - 1) {
                val path = (if (handpathHold[j]) pathSolid else pathDashed)

                if (path.getCurrentPoint() == null) {
                    path.moveTo(handpathPoints[i][j][0], handpathPoints[i][j][1])
                    path.lineTo(handpathPoints[i][j + 1][0], handpathPoints[i][j + 1][1])
                } else {
                    path.lineTo(handpathPoints[i][j + 1][0], handpathPoints[i][j + 1][1])
                }
            }

            if (pathDashed.getCurrentPoint() != null) {
                val gdash = g2.create() as Graphics2D
                val dashed: Stroke =
                    BasicStroke(
                        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, floatArrayOf(5f, 3f), 0f
                    )
                gdash.stroke = dashed
                gdash.color = COLOR_HANDPATH
                gdash.draw(pathDashed)
                gdash.dispose()
            }

            if (pathSolid.getCurrentPoint() != null) {
                val gsolid = g2.create() as Graphics2D
                gsolid.color = COLOR_HANDPATH
                gsolid.draw(pathSolid)
                gsolid.dispose()
            }

            // draw event
            g2.color = COLOR_EVENTS

            // dot at center
            g2.fillOval(
                eventPoints[0][i][4][0].roundToInt() + deltax - 2,
                eventPoints[0][i][4][1].roundToInt() + deltay - 2,
                5,
                5
            )

            if (showXzDragControl || dragging) {
                // edges of xz plane control
                drawLine(g2, eventPoints[0], i, 0, 1, true)
                drawLine(g2, eventPoints[0], i, 1, 2, true)
                drawLine(g2, eventPoints[0], i, 2, 3, true)
                drawLine(g2, eventPoints[0], i, 3, 0, true)

                for (j in 1..<eventPoints.size) {
                    drawLine(g2, eventPoints[j], i, 0, 1, false)
                    drawLine(g2, eventPoints[j], i, 1, 2, false)
                    drawLine(g2, eventPoints[j], i, 2, 3, false)
                    drawLine(g2, eventPoints[j], i, 3, 0, false)
                    g2.fillOval(
                        eventPoints[j][i][4][0].roundToInt() - 1,
                        eventPoints[j][i][4][1].roundToInt() - 1,
                        3,
                        3
                    )
                }
            }

            if (showYDragControl && (!dragging || draggingY)) {
                // y-axis control pointing forward/backward
                drawLine(g2, eventPoints[0], i, 5, 6, true)
                drawLine(g2, eventPoints[0], i, 5, 7, true)
                drawLine(g2, eventPoints[0], i, 5, 8, true)
                drawLine(g2, eventPoints[0], i, 6, 9, true)
                drawLine(g2, eventPoints[0], i, 6, 10, true)
            }
        }
    }

    //--------------------------------------------------------------------------
    // Helper functions related to position editing
    //--------------------------------------------------------------------------

    fun activatePosition(pos: JMLPosition?) {
        deactivateEvent()
        position = pos
        positionActive = true
        startAngle = Math.toRadians(position!!.angle)
        createPositionView()
    }

    fun deactivatePosition() {
        position = null
        positionActive = false
        draggingAngle = false
        draggingZ = false
        draggingXy = false
    }

    private fun createPositionView() {
        if (!positionActive) return

        posPoints = Array(2) { Array(POS_CONTROL_POINTS.size) { DoubleArray(2) } }

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)

            // translate by one pixel and see how far it is in juggler space
            val c =
                add(position!!.coordinate, Coordinate(0.0, 0.0, POSITION_BOX_Z_OFFSET_CM))
            val c2 = ren!!.getScreenTranslatedCoordinate(c!!, 1, 0)
            val dl = 1.0 / distance(c, c2) // pixels/cm

            val ca = ren.cameraAngle
            val theta = ca[0] + startAngle + deltaAngle
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
                    (center[0].toDouble() +
                        dxx * POS_CONTROL_POINTS[j][0] +
                        dyx * POS_CONTROL_POINTS[j][1] +
                        dzx * POS_CONTROL_POINTS[j][2])
                posPoints[i][j][1] =
                    (center[1].toDouble() +
                        dxy * POS_CONTROL_POINTS[j][0] +
                        dyy * POS_CONTROL_POINTS[j][1] +
                        dzy * POS_CONTROL_POINTS[j][2])
            }

            showAngleDragControl =
                (anglediff(phi - Math.PI / 2) > Math.toRadians(90 - ANGLE_CONTROL_SHOW_DEG))
            showXyDragControl =
                (anglediff(phi - Math.PI / 2) > Math.toRadians(90 - XY_CONTROL_SHOW_DEG))
            showZDragControl =
                (anglediff(phi - Math.PI / 2) < Math.toRadians(90 - Z_CONTROL_SHOW_DEG))
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun drawPositions(g: Graphics) {
        if (!positionActive) {
            return
        }

        val d = size
        var g2 = g

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            val ren = if (i == 0) animator.ren1 else animator.ren2

            if (jc.stereo) {
                g2 = when (i) {
                    0 -> g.create(0, 0, d.width / 2, d.height)
                    else -> g.create(d.width / 2, 0, d.width / 2, d.height)
                }
            }

            if (g2 is Graphics2D) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON)
            }

            g2.color = COLOR_POSITIONS

            // dot at center
            g2.fillOval(
                posPoints[i][4][0].roundToInt() + deltax - 2,
                posPoints[i][4][1].roundToInt() + deltay - 2,
                5,
                5
            )

            if (showXyDragControl || dragging) {
                // edges of xy plane control
                drawLine(g2, posPoints, i, 0, 1, true)
                drawLine(g2, posPoints, i, 1, 2, true)
                drawLine(g2, posPoints, i, 2, 3, true)
                drawLine(g2, posPoints, i, 3, 0, true)
            }

            if (showZDragControl && (!dragging || draggingZ)) {
                // z-axis control pointing upward
                drawLine(g2, posPoints, i, 4, 6, true)
                drawLine(g2, posPoints, i, 6, 7, true)
                drawLine(g2, posPoints, i, 6, 8, true)
            }

            if (showAngleDragControl && (!dragging || draggingAngle)) {
                // angle-changing control pointing backward
                drawLine(g2, posPoints, i, 4, 5, true)
                g2.fillOval(
                    posPoints[i][5][0].roundToInt() - 4 + deltax,
                    posPoints[i][5][1].roundToInt() - 4 + deltay,
                    10,
                    10
                )
            }

            if (draggingAngle) {
                // sighting line during angle rotation
                drawLine(g2, posPoints, i, 9, 10, true)
            }

            if (!draggingAngle) {
                if (draggingZ || (cameraAngle[1] <= Math.toRadians(GRID_SHOW_DEG))) {
                    // line dropping down to projection on ground (z = 0)
                    val c = currentCoordinate
                    val z = c.z
                    c.z = 0.0
                    val xyProjection = ren!!.getXY(c)
                    g2.drawLine(
                        xyProjection[0],
                        xyProjection[1],
                        posPoints[i][4][0].roundToInt() + deltax,
                        posPoints[i][4][1].roundToInt() + deltay
                    )
                    g2.fillOval(xyProjection[0] - 2, xyProjection[1] - 2, 5, 5)

                    if (draggingZ) {
                        // z-label on the line
                        val y = max(
                            max(posPoints[i][0][1], posPoints[i][1][1]),
                            max(posPoints[i][2][1], posPoints[i][3][1])
                        )
                        val messageY = y.roundToInt() + deltay + 40

                        g2.color = Color.black
                        g2.drawString(
                            "z = " + jlToStringRounded(z, 1) + " cm", xyProjection[0] + 5, messageY
                        )
                    }
                }
            }
        }
    }

    // In position editing mode, draw an xy grid at ground level (z = 0).

    @Suppress("UnnecessaryVariable")
    private fun drawGrid(g: Graphics) {
        if (!positionActive) return

        // need a Graphics2D object for setStroke() below
        if (g !is Graphics2D) return
        var g2: Graphics2D = g

        // only draw grid when looking down from above
        if (cameraAngle[1] > Math.toRadians(GRID_SHOW_DEG)) return

        g2.color = COLOR_GRID
        val d = size

        val width = (if (jc.stereo) d.width / 2 else d.width)

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)

            if (jc.stereo) {
                g2 = if (i == 0) {
                    g2.create(0, 0, d.width / 2, d.height) as Graphics2D
                } else {
                    g2.create(d.width / 2, 0, d.width / 2, d.height) as Graphics2D
                }
            }

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Figure out pixel deltas for 1cm vectors along x and y axes
            val center = ren!!.getXY(Coordinate(0.0, 0.0, 0.0))

            val dx = ren.getXY(Coordinate(100.0, 0.0, 0.0))
            val dy = ren.getXY(Coordinate(0.0, 100.0, 0.0))
            val xaxisSpacing = doubleArrayOf(
                XY_GRID_SPACING_CM * ((dx[0] - center[0]).toDouble() / 100.0),
                XY_GRID_SPACING_CM * ((dx[1] - center[1]).toDouble() / 100.0)
            )
            val yaxisSpacing = doubleArrayOf(
                XY_GRID_SPACING_CM * ((dy[0] - center[0]).toDouble() / 100.0),
                XY_GRID_SPACING_CM * ((dy[1] - center[1]).toDouble() / 100.0)
            )

            val axis1 = xaxisSpacing
            val axis2 = yaxisSpacing

            // Find which grid intersections are visible on screen by solving
            // for the grid coordinates at the four corners.
            val det = axis1[0] * axis2[1] - axis1[1] * axis2[0]
            var mmin = 0
            var mmax = 0
            var nmin = 0
            var nmax = 0
            for (j in 0..3) {
                val a = ((if (j % 2 == 0) 0 else width) - center[0]).toDouble()
                val b = ((if (j < 2) 0 else d.height) - center[1]).toDouble()
                val m = (axis2[1] * a - axis2[0] * b) / det
                val n = (-axis1[1] * a + axis1[0] * b) / det
                val mint = floor(m).toInt()
                val nint = floor(n).toInt()
                mmin = (if (j == 0) mint else min(mmin, mint))
                mmax = (if (j == 0) mint + 1 else max(mmax, mint + 1))
                nmin = (if (j == 0) nint else min(nmin, nint))
                nmax = (if (j == 0) nint + 1 else max(nmax, nint + 1))
            }

            for (j in mmin..mmax) {
                val x1 = (center[0] + j * axis1[0] + nmin * axis2[0]).roundToInt()
                val y1 = (center[1] + j * axis1[1] + nmin * axis2[1]).roundToInt()
                val x2 = (center[0] + j * axis1[0] + nmax * axis2[0]).roundToInt()
                val y2 = (center[1] + j * axis1[1] + nmax * axis2[1]).roundToInt()
                if (j == 0) {
                    g2.stroke = BasicStroke(3f)
                }
                g2.drawLine(x1, y1, x2, y2)
                if (j == 0) {
                    g2.stroke = BasicStroke(1f)
                }
            }
            for (j in nmin..nmax) {
                val x1 = (center[0] + mmin * axis1[0] + j * axis2[0]).roundToInt()
                val y1 = (center[1] + mmin * axis1[1] + j * axis2[1]).roundToInt()
                val x2 = (center[0] + mmax * axis1[0] + j * axis2[0]).roundToInt()
                val y2 = (center[1] + mmax * axis1[1] + j * axis2[1]).roundToInt()
                if (j == 0) {
                    g2.stroke = BasicStroke(3f)
                }
                g2.drawLine(x1, y1, x2, y2)
                if (j == 0) {
                    g2.stroke = BasicStroke(1f)
                }
            }
        }
    }

    private val currentCoordinate: Coordinate
        get() {
            if (eventActive) {
                if (!dragging) {
                    return event!!.localCoordinate
                }

                val c = eventStart!!.copy()

                // screen (pixel) offset of a 1cm offset in each of the cardinal
                // directions in the juggler's coordinate system (i.e., global
                // coordinates rotated by the juggler's angle)
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = (if (jc.stereo) 0.5 else 1.0)

                for (i in 0..<(if (jc.stereo) 2 else 1)) {
                    dx[0] += f * (eventPoints[0][i][11][0] - eventPoints[0][i][4][0])
                    dx[1] += f * (eventPoints[0][i][11][1] - eventPoints[0][i][4][1])
                    dy[0] += f * (eventPoints[0][i][12][0] - eventPoints[0][i][4][0])
                    dy[1] += f * (eventPoints[0][i][12][1] - eventPoints[0][i][4][1])
                    dz[0] += f * (eventPoints[0][i][13][0] - eventPoints[0][i][4][0])
                    dz[1] += f * (eventPoints[0][i][13][1] - eventPoints[0][i][4][1])
                }

                if (draggingXz) {
                    // express deltax, deltay in terms of dx, dz above
                    //
                    // deltax = A * dxx + B * dzx;
                    // deltay = A * dxy + B * dzy;
                    //
                    // then c.x += A
                    //      c.z += B
                    val det = dx[0] * dz[1] - dx[1] * dz[0]
                    val a = (dz[1] * deltax - dz[0] * deltay) / det
                    val b = (-dx[1] * deltax + dx[0] * deltay) / det

                    c.x += a
                    c.z += b

                    // Snap to z = 0 in local coordinates ("normal" throwing height)
                    if (abs(c.z) < YZ_EVENT_SNAP_CM) {
                        deltay += (dz[1] * (-c.z)).roundToInt()
                        c.z = 0.0
                    }
                }

                if (draggingY) {
                    // express deltax, deltay in terms of dy, dz above
                    //
                    // deltax = A * dyx + B * dzx;
                    // deltay = A * dyy + B * dzy;
                    //
                    // then c.y += A
                    val det = dy[0] * dz[1] - dy[1] * dz[0]
                    val a = (dz[1] * deltax - dz[0] * deltay) / det

                    c.y += a

                    // Snap to y = 0 in local coordinates ("normal" throwing depth)
                    if (abs(c.y) < YZ_EVENT_SNAP_CM) {
                        c.y = 0.0
                    }

                    // Calculate `deltax`, `deltay` that put the event closest to its final
                    // location
                    deltax = ((c.y - eventStart!!.y) * dy[0]).roundToInt()
                    deltay = ((c.y - eventStart!!.y) * dy[1]).roundToInt()
                }

                return c
            }

            if (positionActive) {
                if (!draggingXy && !draggingZ) {
                    return position!!.coordinate
                }

                val c = positionStart!!.copy()

                // screen (pixel) offset of a 1cm offset in each of the cardinal
                // directions in the position's coordinate system (i.e., global
                // coordinates rotated by the position's angle)
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = (if (jc.stereo) 0.5 else 1.0)

                for (i in 0..<(if (jc.stereo) 2 else 1)) {
                    dx[0] += f * (posPoints[i][11][0] - posPoints[i][4][0])
                    dx[1] += f * (posPoints[i][11][1] - posPoints[i][4][1])
                    dy[0] += f * (posPoints[i][12][0] - posPoints[i][4][0])
                    dy[1] += f * (posPoints[i][12][1] - posPoints[i][4][1])
                    dz[0] += f * (posPoints[i][13][0] - posPoints[i][4][0])
                    dz[1] += f * (posPoints[i][13][1] - posPoints[i][4][1])
                }

                if (draggingXy) {
                    // express deltax, deltay in terms of dx, dy above
                    //
                    // deltax = A * dxx + B * dyx;
                    // deltay = A * dxy + B * dyy;
                    //
                    // then position.x += A
                    //      position.y += B
                    val det = dx[0] * dy[1] - dx[1] * dy[0]
                    val a = (dy[1] * deltax - dy[0] * deltay) / det
                    val b = (-dx[1] * deltax + dx[0] * deltay) / det

                    // transform changes to global coordinates
                    val angle = Math.toRadians(position!!.angle)
                    c.x += a * cos(angle) - b * sin(angle)
                    c.y += a * sin(angle) + b * cos(angle)

                    // Snap to selected grid lines
                    var snapped = false
                    val oldcx = c.x
                    val oldcy = c.y

                    var closestGrid: Double =
                        XY_GRID_SPACING_CM * (c.x / XY_GRID_SPACING_CM).roundToInt()
                    if (abs(c.x - closestGrid) < XYZ_GRID_POSITION_SNAP_CM) {
                        c.x = closestGrid
                        snapped = true
                    }
                    closestGrid =
                        XY_GRID_SPACING_CM * (c.y / XY_GRID_SPACING_CM).roundToInt()
                    if (abs(c.y - closestGrid) < XYZ_GRID_POSITION_SNAP_CM) {
                        c.y = closestGrid
                        snapped = true
                    }

                    if (snapped) {
                        // calculate `deltax` and `deltay` that get us closest to the snapped
                        // position
                        val deltacx = c.x - oldcx
                        val deltacy = c.y - oldcy
                        val deltaa = deltacx * cos(angle) + deltacy * sin(angle)
                        val deltab = -deltacx * sin(angle) + deltacy * cos(angle)
                        val deltaXpx = dx[0] * deltaa + dy[0] * deltab
                        val deltaYpx = dx[1] * deltaa + dy[1] * deltab

                        deltax += deltaXpx.roundToInt()
                        deltay += deltaYpx.roundToInt()
                    }
                }

                if (draggingZ) {
                    deltax = 0 // constrain movement to be vertical
                    c.z += deltay / dz[1]

                    if (abs(c.z - 100) < XYZ_GRID_POSITION_SNAP_CM) {
                        deltay += (dz[1] * (100 - c.z)).roundToInt()
                        c.z = 100.0
                    }
                }

                return c
            }

            throw JuggleExceptionInternal("problem in AnimationEditPanel::currentCoordinate()")
        }

    private fun drawLine(
        g: Graphics,
        array: Array<Array<DoubleArray>>,
        index: Int,
        p1: Int,
        p2: Int,
        mouse: Boolean
    ) {
        if (mouse) {
            g.drawLine(
                array[index][p1][0].roundToInt() + deltax,
                array[index][p1][1].roundToInt() + deltay,
                array[index][p2][0].roundToInt() + deltax,
                array[index][p2][1].roundToInt() + deltay
            )
        } else {
            g.drawLine(
                array[index][p1][0].roundToInt(),
                array[index][p1][1].roundToInt(),
                array[index][p2][0].roundToInt(),
                array[index][p2][1].roundToInt()
            )
        }
    }

    //--------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //--------------------------------------------------------------------------

    override fun paintComponent(g: Graphics) {
        if (g is Graphics2D) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        if (message != null) {
            drawString(message!!, g)
        } else if (engineRunning && !writingGIF) {
            try {
                animator.drawBackground(g)
                drawGrid(g)
                animator.drawFrame(time, g, draggingCamera, false)
                drawEvents(g)
                drawPositions(g)
            } catch (jei: JuggleExceptionInternal) {
                killAnimationThread()
                jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
            }
        }
    }

    companion object {
        // constants for rendering events
        private const val EVENT_BOX_HW_CM: Double = 5.0
        private const val UNSELECTED_BOX_HW_CM: Double = 2.0
        private const val YZ_EVENT_SNAP_CM: Double = 3.0
        private const val XZ_CONTROL_SHOW_DEG: Double = 60.0
        private const val Y_CONTROL_SHOW_DEG: Double = 30.0
        private val COLOR_EVENTS: Color = Color.green

        // constants for rendering hand path
        private const val HANDPATH_POINT_SEP_TIME: Double = 0.01 // secs
        private val COLOR_HANDPATH: Color = Color.lightGray

        // constants for rendering positions
        private const val POSITION_BOX_HW_CM: Double = 10.0
        private const val POSITION_BOX_Z_OFFSET_CM: Double = 0.0
        private const val XY_GRID_SPACING_CM: Double = 20.0
        private const val XYZ_GRID_POSITION_SNAP_CM: Double = 3.0
        private const val GRID_SHOW_DEG: Double = 70.0
        private const val ANGLE_CONTROL_SHOW_DEG: Double = 70.0
        private const val XY_CONTROL_SHOW_DEG: Double = 70.0
        private const val Z_CONTROL_SHOW_DEG: Double = 30.0
        private val COLOR_POSITIONS: Color = Color.green
        private val COLOR_GRID: Color = Color.lightGray

        // points in the juggler's coordinate system that are used for drawing the
        // onscreen representation of a selected event
        private val EVENT_CONTROL_POINTS: List<DoubleArray> = listOf(
            // corners of square representing xz movement control
            doubleArrayOf(-EVENT_BOX_HW_CM, 0.0, -EVENT_BOX_HW_CM),
            doubleArrayOf(-EVENT_BOX_HW_CM, 0.0, EVENT_BOX_HW_CM),
            doubleArrayOf(EVENT_BOX_HW_CM, 0.0, EVENT_BOX_HW_CM),
            doubleArrayOf(EVENT_BOX_HW_CM, 0.0, -EVENT_BOX_HW_CM),
            doubleArrayOf(0.0, 0.0, 0.0),  // center
            doubleArrayOf(0.0, 10.0, 0.0),  // end 1 of y-axis control
            doubleArrayOf(0.0, -10.0, 0.0),  // end 2 of y-axis control
            doubleArrayOf(0.0, 7.0, 2.0),  // arrow at end 1 of y-axis control
            doubleArrayOf(0.0, 7.0, -2.0),
            doubleArrayOf(0.0, -7.0, 2.0),  // arrow at end 2 of y-axis control
            doubleArrayOf(0.0, -7.0, -2.0),  // used for moving the event

            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0),
        )

        // faces in terms of indices in EVENT_CONTROL_POINTS[]
        private val FACE_XZ: IntArray = intArrayOf(0, 1, 2, 3)

        // points for an event that is not selected (active)
        private val UNSELECTED_EVENT_POINTS: List<DoubleArray> = listOf(
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(0.0, 0.0, 0.0),
        )

        // points in the juggler's coordinate system that are used for drawing the
        // onscreen representation of a selected position
        private val POS_CONTROL_POINTS: List<DoubleArray> = listOf(
            // corners of square representing xy movement control
            doubleArrayOf(-POSITION_BOX_HW_CM, -POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(-POSITION_BOX_HW_CM, POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(POSITION_BOX_HW_CM, POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(POSITION_BOX_HW_CM, -POSITION_BOX_HW_CM, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0),  // center
            doubleArrayOf(0.0, -20.0, 0.0),  // angle control point
            doubleArrayOf(0.0, 0.0, 20.0),  // end of z-vector control
            doubleArrayOf(2.0, 0.0, 17.0),  // arrow at end of z-vector control
            doubleArrayOf(-2.0, 0.0, 17.0),
            doubleArrayOf(0.0, -250.0, 0.0),  // direction-sighting line when dragging angle
            doubleArrayOf(0.0, 250.0, 0.0),  // used for moving the position

            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0),
        )

        // faces in terms of indices in pos_control_points[]
        private val FACE_XY: IntArray = intArrayOf(0, 1, 2, 3)

        // Test whether a point (x, y) lies inside a polygon.

        private fun isInsidePolygon(
            x: Int,
            y: Int,
            array: Array<Array<DoubleArray>>,
            index: Int, points: IntArray
        ): Boolean {
            var inside = false
            var i = 0
            var j = points.size - 1
            while (i < points.size) {
                val xi = array[index][points[i]][0].roundToInt()
                val yi = array[index][points[i]][1].roundToInt()
                val xj = array[index][points[j]][0].roundToInt()
                val yj = array[index][points[j]][1].roundToInt()

                // note we only evaluate the second term when yj != yi:
                val intersect = (yi > y) != (yj > y) &&
                    x < (xj - xi) * (y - yi) / (yj - yi) + xi
                if (intersect) {
                    inside = !inside
                }
                j = i++
            }
            return inside
        }
    }
}
