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
import jugglinglab.util.Coordinate.Companion.sub
import jugglinglab.util.ErrorDialog.handleFatalException
import java.awt.*
import java.awt.event.*
import java.awt.geom.Path2D
import javax.swing.SwingUtilities
import kotlin.math.*

class AnimationEditPanel : AnimationPanel(), MouseListener, MouseMotionListener {
    // for when an event is activated/dragged
    protected var event_active: Boolean = false
    protected var event: JMLEvent? = null
    protected var dragging_xz: Boolean = false
    protected var dragging_y: Boolean = false
    protected var show_xz_drag_control: Boolean = false
    protected var show_y_drag_control: Boolean = false
    protected var event_start: Coordinate? = null
    protected var event_master_start: Coordinate? = null
    protected var visible_events: ArrayList<JMLEvent>? = null
    protected var event_points: Array<Array<Array<DoubleArray>>>
    protected var handpath_points: Array<Array<DoubleArray>>
    protected var handpath_start_time: Double = 0.0
    protected var handpath_end_time: Double = 0.0
    protected var handpath_hold: BooleanArray = BooleanArray(0)

    // for when a position is activated/dragged
    protected var position_active: Boolean = false
    protected var position: JMLPosition? = null
    protected var pos_points: Array<Array<DoubleArray>>
    protected var dragging_xy: Boolean = false
    protected var dragging_z: Boolean = false
    protected var dragging_angle: Boolean = false
    protected var show_xy_drag_control: Boolean = false
    protected var show_z_drag_control: Boolean = false
    protected var show_angle_drag_control: Boolean = false
    protected var position_start: Coordinate? = null
    protected var startangle: Double = 0.0

    // for when a position angle is being dragged
    protected var deltaangle: Double = 0.0
    protected var start_dx: DoubleArray = doubleArrayOf(0.0, 0.0)
    protected var start_dy: DoubleArray = doubleArrayOf(0.0, 0.0)
    protected var start_control: DoubleArray = doubleArrayOf(0.0, 0.0)

    // for when either an event or position is being dragged
    protected var dragging: Boolean = false
    protected var dragging_left: Boolean = false // for stereo mode; may not be necessary?
    protected var deltax: Int = 0
    protected var deltay: Int = 0 // extent of drag action (pixels)

    init {
        event_points = Array(0) { Array(1) { Array(0) { DoubleArray(2) } } }
        handpath_points = Array(1) { Array(0) { DoubleArray(2) } }
        handpath_hold = BooleanArray(0)
        pos_points = Array(2) { Array(0) { DoubleArray(2) } }
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
        if (jc.mousePause && lastpress == lastenter) {
            return
        }
        if (!engineAnimating) {
            return
        }
        if (writingGIF) {
            return
        }

        startx = me.getX()
        starty = me.getY()

        if (event_active) {
            val mx = me.getX()
            val my = me.getY()

            for (i in 0..<(if (jc.stereo) 2 else 1)) {
                val t = i * getSize().width / 2

                if (show_y_drag_control) {
                    dragging_y =
                        isNearLine(
                            mx - t,
                            my,
                            Math.round(event_points!![0]!![i]!![5]!![0]).toInt(),
                            Math.round(event_points!![0]!![i]!![5]!![1]).toInt(),
                            Math.round(event_points!![0]!![i]!![6]!![0]).toInt(),
                            Math.round(event_points!![0]!![i]!![6]!![1]).toInt(),
                            4
                        )

                    if (dragging_y) {
                        dragging = true
                        dragging_left = (i == 0)
                        deltay = 0
                        deltax = deltay
                        event_start = event!!.localCoordinate
                        val master = (if (event!!.isMaster) event else event!!.master)!!
                        event_master_start = master.localCoordinate
                        repaint()
                        return
                    }
                }

                if (show_xz_drag_control) {
                    for (j in event_points!!.indices) {
                        if (!Companion.isInsidePolygon(mx - t, my, event_points!![j]!!, i, face_xz)) {
                            continue
                        }

                        if (j > 0) {
                            try {
                                activateEvent(pattern!!.getEventImageInLoop(visible_events!!.get(j)))

                                for (att in attachments) {
                                    if (att is EditLadderDiagram) {
                                        att.activateEvent(event)
                                    }
                                    att.repaintAttachment()
                                }
                            } catch (jei: JuggleExceptionInternal) {
                                jei.attachPattern(pattern)
                                handleFatalException(jei)
                            }
                        }

                        dragging_xz = true
                        dragging = true
                        dragging_left = (i == 0)
                        deltay = 0
                        deltax = deltay
                        event_start = event!!.localCoordinate
                        val master = (if (event!!.isMaster) event else event!!.master)!!
                        event_master_start = master.localCoordinate
                        repaint()
                        return
                    }
                }
            }
        }

        if (position_active) {
            val mx = me.getX()
            val my = me.getY()

            for (i in 0..<(if (jc.stereo) 2 else 1)) {
                val t = i * getSize().width / 2

                if (show_z_drag_control) {
                    dragging_z =
                        isNearLine(
                            mx - t,
                            my,
                            Math.round(pos_points[i]!![4]!![0]).toInt(),
                            Math.round(pos_points[i]!![4]!![1]).toInt(),
                            Math.round(pos_points[i]!![6]!![0]).toInt(),
                            Math.round(pos_points[i]!![6]!![1]).toInt(),
                            4
                        )

                    if (dragging_z) {
                        dragging = true
                        dragging_left = (i == 0)
                        deltay = 0
                        deltax = deltay
                        position_start = position!!.coordinate
                        repaint()
                        return
                    }
                }

                if (show_xy_drag_control) {
                    dragging_xy = isInsidePolygon(mx - t, my, pos_points, i, face_xy)

                    if (dragging_xy) {
                        dragging = true
                        dragging_left = (i == 0)
                        deltay = 0
                        deltax = deltay
                        position_start = position!!.coordinate
                        repaint()
                        return
                    }
                }

                if (show_angle_drag_control) {
                    val dmx = mx - t - Math.round(pos_points[i]!![5]!![0]).toInt()
                    val dmy = my - Math.round(pos_points[i]!![5]!![1]).toInt()
                    dragging_angle = (dmx * dmx + dmy * dmy < 49.0)

                    if (dragging_angle) {
                        dragging = true
                        dragging_left = (i == 0)
                        deltay = 0
                        deltax = deltay

                        // record pixel coordinates of x and y unit vectors
                        // in juggler's frame, at start of angle drag
                        start_dx =
                            doubleArrayOf(
                                pos_points[i]!![11]!![0] - pos_points[i]!![4]!![0],
                                pos_points[i]!![11]!![1] - pos_points[i]!![4]!![1]
                            )
                        start_dy =
                            doubleArrayOf(
                                pos_points[i]!![12]!![0] - pos_points[i]!![4]!![0],
                                pos_points[i]!![12]!![1] - pos_points[i]!![4]!![1]
                            )
                        start_control =
                            doubleArrayOf(
                                pos_points[i]!![5]!![0] - pos_points[i]!![4]!![0],
                                pos_points[i]!![5]!![1] - pos_points[i]!![4]!![1]
                            )

                        repaint()
                        return
                    }
                }
            }
        }
    }

    override fun mouseReleased(me: MouseEvent) {
        if (jc.mousePause && lastpress == lastenter) {
            return
        }
        if (writingGIF) {
            return
        }
        if (!engineAnimating && engine != null && engine!!.isAlive()) {
            isPaused = !enginePaused
            return
        }

        val mouse_moved = (me.getX() != startx) || (me.getY() != starty)

        if (event_active && dragging && mouse_moved) {
            dragging_y = false
            dragging_xz = dragging_y

            try {
                for (att in attachments) {
                    if (att is EditLadderDiagram) {
                        // reactivate the event in ladder diagram, since we've
                        // called layoutPattern() and events may have changed
                        event = att.reactivateEvent()
                        att.addToUndoList()
                    }
                }

                animator.initAnimator()
                activateEvent(event)
            } catch (jei: JuggleExceptionInternal) {
                jei.attachPattern(pattern)
                handleFatalException(jei)
            }
        }

        if (position_active && dragging && mouse_moved) {
            dragging_angle = false
            dragging_z = dragging_angle
            dragging_xy = dragging_z
            deltaangle = 0.0

            for (att in attachments) {
                if (att is EditLadderDiagram) {
                    att.addToUndoList()
                }
            }

            animator.initAnimator()
            activatePosition(position)
        }

        if (!mouse_moved && !dragging && engine != null && engine!!.isAlive()) {
            isPaused = !enginePaused
        }

        draggingCamera = false
        dragging = false
        dragging_y = false
        dragging_xz = dragging_y
        dragging_angle = false
        dragging_z = dragging_angle
        dragging_xy = dragging_z
        deltay = 0
        deltax = deltay
        deltaangle = 0.0
        event_master_start = null
        event_start = event_master_start
        position_start = null
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

    //----------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //----------------------------------------------------------------------------
    override fun mouseDragged(me: MouseEvent) {
        if (!engineAnimating) {
            return
        }
        if (writingGIF) {
            return
        }

        if (dragging) {
            val mx = me.getX()
            val my = me.getY()
            var dolayout = false

            if (dragging_angle) {
                // shift pixel coords of control point by mouse drag
                val dcontrol = doubleArrayOf(start_control[0] + mx - startx, start_control[1] + my - starty)

                // re-express control point location in coordinate
                // system of juggler:
                //
                // dcontrol_x = A * start_dx_x + B * start_dy_x;
                // dcontrol_y = A * start_dx_y + B * start_dy_y;
                //
                // then (A, B) are coordinates of shifted control
                // point, in juggler space
                val det = start_dx[0] * start_dy[1] - start_dx[1] * start_dy[0]
                val a = (start_dy[1] * dcontrol[0] - start_dy[0] * dcontrol[1]) / det
                val b = (-start_dx[1] * dcontrol[0] + start_dx[0] * dcontrol[1]) / det
                deltaangle = -atan2(-a, -b)

                // snap the angle to the four cardinal directions
                val new_angle = startangle + deltaangle
                if (anglediff(new_angle) < SNAPANGLE / 2) {
                    deltaangle = -startangle
                } else if (anglediff(new_angle + 0.5 * Math.PI) < SNAPANGLE / 2) {
                    deltaangle = -startangle - 0.5 * Math.PI
                } else if (anglediff(new_angle + Math.PI) < SNAPANGLE / 2) {
                    deltaangle = -startangle + Math.PI
                } else if (anglediff(new_angle + 1.5 * Math.PI) < SNAPANGLE / 2) {
                    deltaangle = -startangle + 0.5 * Math.PI
                }

                var final_angle = Math.toDegrees(startangle + deltaangle)
                while (final_angle > 360) {
                    final_angle -= 360.0
                }
                while (final_angle < 0) {
                    final_angle += 360.0
                }
                position!!.angle = final_angle

                dolayout = true
            } else {
                deltax = mx - startx
                deltay = my - starty

                // Get updated event/position coordinate based on mouse position.
                // This modifies deltax, deltay based on snapping and projection.
                val cc = this.currentCoordinate

                if (event_active) {
                    var deltalc = sub(cc, event_start)
                    deltalc = Coordinate.Companion.truncate(deltalc!!, 1e-7)
                    event!!.localCoordinate = add(event_start, deltalc)!!

                    if (!event!!.isMaster) {
                        // set new coordinate in the master event
                        val master = event!!.master
                        val flipx = (event!!.hand != master.hand)
                        if (flipx) {
                            deltalc.x = -deltalc.x
                        }
                        master.localCoordinate = add(event_master_start, deltalc)!!
                    }

                    dolayout = true
                }

                if (position_active) {
                    position!!.coordinate = cc
                    dolayout = true
                }
            }

            if (dolayout) {
                try {
                    synchronized(animator.pat!!) {
                        animator.pat!!.setNeedsLayout()
                        animator.pat!!.layoutPattern()
                    }
                    if (event_active) {
                        createHandpathView()
                    }
                } catch (je: JuggleException) {
                    // The editing operations here should never put the pattern
                    // into an invalid state, so we shouldn't ever get here
                    handleFatalException(je)
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

        if (event_active && draggingCamera) {
            try {
                createEventView()
            } catch (jei: JuggleExceptionInternal) {
                jei.attachPattern(pattern)
                handleFatalException(jei)
            }
        }

        if (position_active && (draggingCamera || dragging_angle)) {
            createPositionView()
        }

        if (isPaused) {
            repaint()
        }
    }

    override fun mouseMoved(e: MouseEvent?) {}

    //----------------------------------------------------------------------------
    // AnimationPanel methods
    //----------------------------------------------------------------------------
    override fun initHandlers() {
        addMouseListener(this)
        addMouseMotionListener(this)

        addComponentListener(
            object : ComponentAdapter() {
                var hasResized: Boolean = false

                override fun componentResized(e: ComponentEvent?) {
                    if (!engineAnimating) {
                        return
                    }
                    if (writingGIF) {
                        return
                    }

                    animator.dimension = getSize()
                    if (event_active) {
                        try {
                            createEventView()
                        } catch (jei: JuggleExceptionInternal) {
                            jei.attachPattern(pattern)
                            handleFatalException(jei)
                        }
                    }
                    if (position_active) {
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
                        jc.size = getSize()
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
        var snap_horizontal = true

        if (event_active) {
            a = -Math.toRadians(animator.pat!!.getJugglerAngle(event!!.juggler, event!!.t))
        } else if (position_active) {
            // a = -Math.toRadians(anim.pat.getJugglerAngle(position.getJuggler(), position.getT()));
            a = 0.0
        } else if (animator.pat!!.numberOfJugglers == 1) {
            a = -Math.toRadians(animator.pat!!.getJugglerAngle(1, time))
        } else {
            snap_horizontal = false
        }

        if (snap_horizontal) {
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
    public override fun restartJuggle(pat: JMLPattern?, newjc: AnimationPrefs?) {
        super.restartJuggle(pat, newjc)
        if (event_active) {
            createEventView()
        }
        if (position_active) {
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
                    jei.attachPattern(pattern)
                    handleFatalException(jei)
                }
                createPositionView()
                repaint()
            }
        }

    //----------------------------------------------------------------------------
    // Helper functions related to event editing
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionInternal::class)
    fun activateEvent(ev: JMLEvent?) {
        deactivatePosition()
        event = ev
        event_active = true
        createEventView()
    }

    fun deactivateEvent() {
        event = null
        event_active = false
        dragging_y = false
        dragging_xz = false
        event_points = Array(0) { Array(1) { Array(0) { DoubleArray(2) } } }
        visible_events = null
        handpath_points = Array(1) { Array(0) { DoubleArray(2) } }
    }

    @Throws(JuggleExceptionInternal::class)
    protected fun createEventView() {
        if (!event_active) {
            return
        }

        // determine which events to display on-screen
        visible_events = ArrayList<JMLEvent>()
        visible_events!!.add(event!!)
        handpath_start_time = event!!.t
        handpath_end_time = event!!.t

        var ev2 = event!!.previous
        while (ev2 != null) {
            if (ev2.juggler == event!!.juggler && ev2.hand == event!!.hand) {
                handpath_start_time = min(handpath_start_time, ev2.t)

                var new_master = true
                for (ev3 in visible_events!!) {
                    if (ev3.hasSameMasterAs(ev2)) {
                        new_master = false
                    }
                }
                if (new_master) {
                    visible_events!!.add(ev2)
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
                handpath_end_time = max(handpath_end_time, ev2.t)

                var new_master = true
                for (ev3 in visible_events!!) {
                    if (ev3.hasSameMasterAs(ev2)) {
                        new_master = false
                    }
                }
                if (new_master) {
                    visible_events!!.add(ev2)
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
        val renderer_count = (if (jc.stereo) 2 else 1)
        event_points =
            Array(visible_events!!.size) {
                Array(renderer_count) {
                    Array(event_control_points.size) { DoubleArray(2) }
                }
            }

        var ev_num = 0
        for (ev in visible_events!!) {
            for (i in 0..<renderer_count) {
                val ren = (if (i == 0) animator.ren1 else animator.ren2)

                // translate by one pixel and see how far it is in juggler space
                val c = ev.globalCoordinate
                if (c == null) {
                    throw JuggleExceptionInternal("AEP: No coord on event " + ev, pattern)
                }
                val c2 = ren!!.getScreenTranslatedCoordinate(c, 1, 0)
                val dl = 1.0 / distance(c, c2) // pixels/cm

                val ca = ren.cameraAngle
                val theta =
                    ca[0] + Math.toRadians(pattern!!.getJugglerAngle(ev.juggler, ev.t))
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
                    for (j in event_control_points.indices) {
                        event_points!![0]!![i]!![j]!![0] =
                            (center[0].toDouble() + dxx * event_control_points[j]!![0] + dyx * event_control_points[j]!![1] + dzx * event_control_points[j]!![2])
                        event_points!![0]!![i]!![j]!![1] =
                            (center[1].toDouble() + dxy * event_control_points[j]!![0] + dyy * event_control_points[j]!![1] + dzy * event_control_points[j]!![2])
                    }

                    show_xz_drag_control = (anglediff(phi - Math.PI / 2) < Math.toRadians(XZ_CONTROL_SHOW_DEG)
                            && (anglediff(theta) < Math.toRadians(XZ_CONTROL_SHOW_DEG)
                            || anglediff(theta - Math.PI) < Math.toRadians(XZ_CONTROL_SHOW_DEG))
                            )
                    show_y_drag_control = !(anglediff(phi - Math.PI / 2) < Math.toRadians(Y_CONTROL_SHOW_DEG)
                            && (anglediff(theta) < Math.toRadians(Y_CONTROL_SHOW_DEG)
                            || anglediff(theta - Math.PI) < Math.toRadians(Y_CONTROL_SHOW_DEG))
                            )
                } else {
                    for (j in unselected_event_points.indices) {
                        event_points!![ev_num]!![i]!![j]!![0] =
                            (center[0].toDouble() + dxx * unselected_event_points[j]!![0] + dyx * unselected_event_points[j]!![1] + dzx * unselected_event_points[j]!![2])
                        event_points!![ev_num]!![i]!![j]!![1] =
                            (center[1].toDouble() + dxy * unselected_event_points[j]!![0] + dyy * unselected_event_points[j]!![1] + dzy * unselected_event_points[j]!![2])
                    }
                }
            }

            ++ev_num
        }

        createHandpathView()
    }

    @Throws(JuggleExceptionInternal::class)
    protected fun createHandpathView() {
        if (!event_active) {
            return
        }

        val pat = pattern
        val renderer_count = (if (jc.stereo) 2 else 1)
        val num_handpath_points =
            ceil((handpath_end_time - handpath_start_time) / HANDPATH_POINT_SEP_TIME).toInt() + 1
        handpath_points =
            Array(renderer_count) { Array(num_handpath_points) { DoubleArray(2) } }
        handpath_hold = BooleanArray(num_handpath_points)

        for (i in 0..<renderer_count) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)
            val c = Coordinate()

            for (j in 0..<num_handpath_points) {
                val t: Double = handpath_start_time + j * HANDPATH_POINT_SEP_TIME
                pat!!.getHandCoordinate(event!!.juggler, event!!.hand, t, c)
                val point = ren!!.getXY(c)
                handpath_points!![i]!![j]!![0] = point[0].toDouble()
                handpath_points!![i]!![j]!![1] = point[1].toDouble()
                handpath_hold[j] = pat.isHandHolding(event!!.juggler, event!!.hand, t + 0.0001)
            }
        }
    }

    @Throws(JuggleExceptionInternal::class)
    protected fun drawEvents(g: Graphics) {
        if (!event_active) {
            return
        }

        val d = getSize()
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
                g2
                    .setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            // draw hand path
            val num_handpath_points = handpath_points!![0]!!.size

            val path_solid = Path2D.Double()
            val path_dashed = Path2D.Double()
            for (j in 0..<num_handpath_points - 1) {
                val path = (if (handpath_hold[j]) path_solid else path_dashed)

                if (path.getCurrentPoint() == null) {
                    path.moveTo(handpath_points!![i]!![j]!![0], handpath_points!![i]!![j]!![1])
                    path.lineTo(handpath_points!![i]!![j + 1]!![0], handpath_points!![i]!![j + 1]!![1])
                } else {
                    path.lineTo(handpath_points!![i]!![j + 1]!![0], handpath_points!![i]!![j + 1]!![1])
                }
            }

            if (path_dashed.getCurrentPoint() != null) {
                val gdash = g2.create() as Graphics2D
                val dashed: Stroke =
                    BasicStroke(
                        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, floatArrayOf(5f, 3f), 0f
                    )
                gdash.setStroke(dashed)
                gdash.setColor(COLOR_HANDPATH)
                gdash.draw(path_dashed)
                gdash.dispose()
            }

            if (path_solid.getCurrentPoint() != null) {
                val gsolid = g2.create() as Graphics2D
                gsolid.setColor(COLOR_HANDPATH)
                gsolid.draw(path_solid)
                gsolid.dispose()
            }

            // draw event
            g2.setColor(COLOR_EVENTS)

            // dot at center
            g2.fillOval(
                Math.round(event_points!![0]!![i]!![4]!![0]).toInt() + deltax - 2,
                Math.round(event_points!![0]!![i]!![4]!![1]).toInt() + deltay - 2,
                5,
                5
            )

            if (show_xz_drag_control || dragging) {
                // edges of xz plane control
                drawLine(g2, event_points!![0]!!, i, 0, 1, true)
                drawLine(g2, event_points!![0]!!, i, 1, 2, true)
                drawLine(g2, event_points!![0]!!, i, 2, 3, true)
                drawLine(g2, event_points!![0]!!, i, 3, 0, true)

                for (j in 1..<event_points!!.size) {
                    drawLine(g2, event_points!![j]!!, i, 0, 1, false)
                    drawLine(g2, event_points!![j]!!, i, 1, 2, false)
                    drawLine(g2, event_points!![j]!!, i, 2, 3, false)
                    drawLine(g2, event_points!![j]!!, i, 3, 0, false)
                    g2.fillOval(
                        Math.round(event_points!![j]!![i]!![4]!![0]).toInt() - 1,
                        Math.round(event_points!![j]!![i]!![4]!![1]).toInt() - 1,
                        3,
                        3
                    )
                }
            }

            if (show_y_drag_control && (!dragging || dragging_y)) {
                // y-axis control pointing forward/backward
                drawLine(g2, event_points!![0]!!, i, 5, 6, true)
                drawLine(g2, event_points!![0]!!, i, 5, 7, true)
                drawLine(g2, event_points!![0]!!, i, 5, 8, true)
                drawLine(g2, event_points!![0]!!, i, 6, 9, true)
                drawLine(g2, event_points!![0]!!, i, 6, 10, true)
            }
        }
    }

    //----------------------------------------------------------------------------
    // Helper functions related to position editing
    //----------------------------------------------------------------------------
    fun activatePosition(pos: JMLPosition?) {
        deactivateEvent()
        position = pos
        position_active = true
        startangle = Math.toRadians(position!!.angle)
        createPositionView()
    }

    fun deactivatePosition() {
        position = null
        position_active = false
        dragging_angle = false
        dragging_z = false
        dragging_xy = false
    }

    protected fun createPositionView() {
        if (!position_active) {
            return
        }

        pos_points = Array(2) { Array(pos_control_points.size) { DoubleArray(2) } }

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)

            // translate by one pixel and see how far it is in juggler space
            val c =
                add(position!!.coordinate, Coordinate(0.0, 0.0, POSITION_BOX_Z_OFFSET_CM))
            val c2 = ren!!.getScreenTranslatedCoordinate(c!!, 1, 0)
            val dl = 1.0 / Coordinate.Companion.distance(c, c2) // pixels/cm

            val ca = ren.cameraAngle
            val theta = ca[0] + startangle + deltaangle
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
            for (j in pos_control_points.indices) {
                pos_points[i]!![j]!![0] =
                    (center[0].toDouble() + dxx * pos_control_points[j]!![0] + dyx * pos_control_points[j]!![1] + dzx * pos_control_points[j]!![2])
                pos_points[i]!![j]!![1] =
                    (center[1].toDouble() + dxy * pos_control_points[j]!![0] + dyy * pos_control_points[j]!![1] + dzy * pos_control_points[j]!![2])
            }

            show_angle_drag_control =
                (anglediff(phi - Math.PI / 2) > Math.toRadians(90 - ANGLE_CONTROL_SHOW_DEG))
            show_xy_drag_control =
                (anglediff(phi - Math.PI / 2) > Math.toRadians(90 - XY_CONTROL_SHOW_DEG))
            show_z_drag_control =
                (anglediff(phi - Math.PI / 2) < Math.toRadians(90 - Z_CONTROL_SHOW_DEG))
        }
    }

    @Throws(JuggleExceptionInternal::class)
    protected fun drawPositions(g: Graphics) {
        if (!position_active) {
            return
        }

        val d = getSize()
        var g2 = g

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)

            if (jc.stereo && i == 0) {
                g2 = g.create(0, 0, d.width / 2, d.height)
            } else if (jc.stereo && i == 1) {
                g2 = g.create(d.width / 2, 0, d.width / 2, d.height)
            }

            if (g2 is Graphics2D) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            g2.setColor(COLOR_POSITIONS)

            // dot at center
            g2.fillOval(
                Math.round(pos_points[i]!![4]!![0]).toInt() + deltax - 2,
                Math.round(pos_points[i]!![4]!![1]).toInt() + deltay - 2,
                5,
                5
            )

            if (show_xy_drag_control || dragging) {
                // edges of xy plane control
                drawLine(g2, pos_points, i, 0, 1, true)
                drawLine(g2, pos_points, i, 1, 2, true)
                drawLine(g2, pos_points, i, 2, 3, true)
                drawLine(g2, pos_points, i, 3, 0, true)
            }

            if (show_z_drag_control && (!dragging || dragging_z)) {
                // z-axis control pointing upward
                drawLine(g2, pos_points, i, 4, 6, true)
                drawLine(g2, pos_points, i, 6, 7, true)
                drawLine(g2, pos_points, i, 6, 8, true)
            }

            if (show_angle_drag_control && (!dragging || dragging_angle)) {
                // angle-changing control pointing backward
                drawLine(g2, pos_points, i, 4, 5, true)
                g2.fillOval(
                    Math.round(pos_points[i]!![5]!![0]).toInt() - 4 + deltax,
                    Math.round(pos_points[i]!![5]!![1]).toInt() - 4 + deltay,
                    10,
                    10
                )
            }

            if (dragging_angle) {
                // sighting line during angle rotation
                drawLine(g2, pos_points, i, 9, 10, true)
            }

            if (!dragging_angle) {
                if (dragging_z || (cameraAngle[1] <= Math.toRadians(GRID_SHOW_DEG))) {
                    // line dropping down to projection on ground (z = 0)
                    val c = this.currentCoordinate
                    val z = c.z
                    c.z = 0.0
                    val xy_projection = ren!!.getXY(c)
                    g2.drawLine(
                        xy_projection[0],
                        xy_projection[1],
                        Math.round(pos_points[i]!![4]!![0]).toInt() + deltax,
                        Math.round(pos_points[i]!![4]!![1]).toInt() + deltay
                    )
                    g2.fillOval(xy_projection[0] - 2, xy_projection[1] - 2, 5, 5)

                    if (dragging_z) {
                        // z-label on the line
                        val y = max(
                            max(pos_points[i]!![0]!![1], pos_points[i]!![1]!![1]),
                            max(pos_points[i]!![2]!![1], pos_points[i]!![3]!![1])
                        )
                        val message_y = Math.round(y).toInt() + deltay + 40

                        g2.setColor(Color.black)
                        g2.drawString(
                            "z = " + toStringRounded(z, 1) + " cm", xy_projection[0] + 5, message_y
                        )
                    }
                }
            }
        }
    }

    // In position editing mode, draw an xy grid at ground level (z = 0)
    private fun drawGrid(g: Graphics) {
        if (!position_active) return

        // need a Graphics2D object for setStroke() below
        if (g !is Graphics2D) return
        var g2: Graphics2D = g

        // only draw grid when looking down from above
        if (cameraAngle[1] > Math.toRadians(GRID_SHOW_DEG)) return

        g2.setColor(COLOR_GRID)
        val d = getSize()

        val width = (if (jc.stereo) d.width / 2 else d.width)

        for (i in 0..<(if (jc.stereo) 2 else 1)) {
            val ren = (if (i == 0) animator.ren1 else animator.ren2)

            if (jc.stereo) {
                if (i == 0) {
                    g2 = g2.create(0, 0, d.width / 2, d.height) as Graphics2D
                } else {
                    g2 = g2.create(d.width / 2, 0, d.width / 2, d.height) as Graphics2D
                }
            }

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Figure out pixel deltas for 1cm vectors along x and y axes
            val center = ren!!.getXY(Coordinate(0.0, 0.0, 0.0))

            val dx = ren.getXY(Coordinate(100.0, 0.0, 0.0))
            val dy = ren.getXY(Coordinate(0.0, 100.0, 0.0))
            val xaxis_spacing = doubleArrayOf(
                XY_GRID_SPACING_CM * ((dx[0] - center[0]).toDouble() / 100.0),
                XY_GRID_SPACING_CM * ((dx[1] - center[1]).toDouble() / 100.0)
            )
            val yaxis_spacing = doubleArrayOf(
                XY_GRID_SPACING_CM * ((dy[0] - center[0]).toDouble() / 100.0),
                XY_GRID_SPACING_CM * ((dy[1] - center[1]).toDouble() / 100.0)
            )

            val axis1 = xaxis_spacing
            val axis2 = yaxis_spacing

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
                val x1 = Math.round(center[0] + j * axis1[0] + nmin * axis2[0]).toInt()
                val y1 = Math.round(center[1] + j * axis1[1] + nmin * axis2[1]).toInt()
                val x2 = Math.round(center[0] + j * axis1[0] + nmax * axis2[0]).toInt()
                val y2 = Math.round(center[1] + j * axis1[1] + nmax * axis2[1]).toInt()
                if (j == 0) {
                    g2.setStroke(BasicStroke(3f))
                }
                g2.drawLine(x1, y1, x2, y2)
                if (j == 0) {
                    g2.setStroke(BasicStroke(1f))
                }
            }
            for (j in nmin..nmax) {
                val x1 = Math.round(center[0] + mmin * axis1[0] + j * axis2[0]).toInt()
                val y1 = Math.round(center[1] + mmin * axis1[1] + j * axis2[1]).toInt()
                val x2 = Math.round(center[0] + mmax * axis1[0] + j * axis2[0]).toInt()
                val y2 = Math.round(center[1] + mmax * axis1[1] + j * axis2[1]).toInt()
                if (j == 0) {
                    g2.setStroke(BasicStroke(3f))
                }
                g2.drawLine(x1, y1, x2, y2)
                if (j == 0) {
                    g2.setStroke(BasicStroke(1f))
                }
            }
        }
    }

    protected val currentCoordinate: Coordinate
        get() {
            if (event_active) {
                if (!dragging) {
                    return event!!.localCoordinate
                }

                val c = Coordinate(event_start!!.x, event_start!!.y, event_start!!.z)

                // screen (pixel) offset of a 1cm offset in each of the cardinal
                // directions in the juggler's coordinate system (i.e., global
                // coordinates rotated by the juggler's angle)
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = (if (jc.stereo) 0.5 else 1.0)

                for (i in 0..<(if (jc.stereo) 2 else 1)) {
                    dx[0] += f * (event_points!![0]!![i]!![11]!![0] - event_points!![0]!![i]!![4]!![0])
                    dx[1] += f * (event_points!![0]!![i]!![11]!![1] - event_points!![0]!![i]!![4]!![1])
                    dy[0] += f * (event_points!![0]!![i]!![12]!![0] - event_points!![0]!![i]!![4]!![0])
                    dy[1] += f * (event_points!![0]!![i]!![12]!![1] - event_points!![0]!![i]!![4]!![1])
                    dz[0] += f * (event_points!![0]!![i]!![13]!![0] - event_points!![0]!![i]!![4]!![0])
                    dz[1] += f * (event_points!![0]!![i]!![13]!![1] - event_points!![0]!![i]!![4]!![1])
                }

                if (dragging_xz) {
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
                        deltay += Math.round(dz[1] * (-c.z)).toInt()
                        c.z = 0.0
                    }
                }

                if (dragging_y) {
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
                    deltax = Math.round((c.y - event_start!!.y) * dy[0]).toInt()
                    deltay = Math.round((c.y - event_start!!.y) * dy[1]).toInt()
                }

                return c
            }

            if (position_active) {
                if (!dragging_xy && !dragging_z) {
                    return position!!.coordinate
                }

                val c = Coordinate(position_start!!.x, position_start!!.y, position_start!!.z)

                // screen (pixel) offset of a 1cm offset in each of the cardinal
                // directions in the position's coordinate system (i.e., global
                // coordinates rotated by the position's angle)
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = (if (jc.stereo) 0.5 else 1.0)

                for (i in 0..<(if (jc.stereo) 2 else 1)) {
                    dx[0] += f * (pos_points[i]!![11]!![0] - pos_points[i]!![4]!![0])
                    dx[1] += f * (pos_points[i]!![11]!![1] - pos_points[i]!![4]!![1])
                    dy[0] += f * (pos_points[i]!![12]!![0] - pos_points[i]!![4]!![0])
                    dy[1] += f * (pos_points[i]!![12]!![1] - pos_points[i]!![4]!![1])
                    dz[0] += f * (pos_points[i]!![13]!![0] - pos_points[i]!![4]!![0])
                    dz[1] += f * (pos_points[i]!![13]!![1] - pos_points[i]!![4]!![1])
                }

                if (dragging_xy) {
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

                    var closest_grid: Double =
                        XY_GRID_SPACING_CM * Math.round(c.x / XY_GRID_SPACING_CM)
                    if (abs(c.x - closest_grid) < XYZ_GRID_POSITION_SNAP_CM) {
                        c.x = closest_grid
                        snapped = true
                    }
                    closest_grid =
                        XY_GRID_SPACING_CM * Math.round(c.y / XY_GRID_SPACING_CM)
                    if (abs(c.y - closest_grid) < XYZ_GRID_POSITION_SNAP_CM) {
                        c.y = closest_grid
                        snapped = true
                    }

                    if (snapped) {
                        // calculate `deltax` and `deltay` that get us closest to the snapped
                        // position
                        val deltacx = c.x - oldcx
                        val deltacy = c.y - oldcy
                        val deltaa = deltacx * cos(angle) + deltacy * sin(angle)
                        val deltab = -deltacx * sin(angle) + deltacy * cos(angle)
                        val delta_x_px = dx[0] * deltaa + dy[0] * deltab
                        val delta_y_px = dx[1] * deltaa + dy[1] * deltab

                        deltax += Math.round(delta_x_px).toInt()
                        deltay += Math.round(delta_y_px).toInt()
                    }
                }

                if (dragging_z) {
                    deltax = 0 // constrain movement to be vertical
                    c.z += deltay / dz[1]

                    if (abs(c.z - 100) < XYZ_GRID_POSITION_SNAP_CM) {
                        deltay += Math.round(dz[1] * (100 - c.z)).toInt()
                        c.z = 100.0
                    }
                }

                return c
            }

            throw JuggleExceptionInternal("problem in AnimationEditPanel::currentCoordinate()")
        }

    protected fun drawLine(
        g: Graphics, array: Array<Array<DoubleArray>>, index: Int, p1: Int, p2: Int, mouse: Boolean
    ) {
        if (mouse) {
            g.drawLine(
                Math.round(array[index]!![p1]!![0]).toInt() + deltax,
                Math.round(array[index]!![p1]!![1]).toInt() + deltay,
                Math.round(array[index]!![p2]!![0]).toInt() + deltax,
                Math.round(array[index]!![p2]!![1]).toInt() + deltay
            )
        } else {
            g.drawLine(
                Math.round(array[index]!![p1]!![0]).toInt(),
                Math.round(array[index]!![p1]!![1]).toInt(),
                Math.round(array[index]!![p2]!![0]).toInt(),
                Math.round(array[index]!![p2]!![1]).toInt()
            )
        }
    }

    //----------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //----------------------------------------------------------------------------
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
                jei.attachPattern(pattern)
                handleFatalException(jei)
            }
        }
    }

    companion object {
        // constants for rendering events
        protected const val EVENT_BOX_HW_CM: Double = 5.0
        protected const val UNSELECTED_BOX_HW_CM: Double = 2.0
        protected const val YZ_EVENT_SNAP_CM: Double = 3.0
        protected const val XZ_CONTROL_SHOW_DEG: Double = 60.0
        protected const val Y_CONTROL_SHOW_DEG: Double = 30.0
        protected val COLOR_EVENTS: Color? = Color.green

        // constants for rendering hand path
        protected const val HANDPATH_POINT_SEP_TIME: Double = 0.01 // secs
        protected val COLOR_HANDPATH: Color? = Color.lightGray

        // constants for rendering positions
        protected const val POSITION_BOX_HW_CM: Double = 10.0
        protected const val POSITION_BOX_Z_OFFSET_CM: Double = 0.0
        protected const val XY_GRID_SPACING_CM: Double = 20.0
        protected const val XYZ_GRID_POSITION_SNAP_CM: Double = 3.0
        protected const val GRID_SHOW_DEG: Double = 70.0
        protected const val ANGLE_CONTROL_SHOW_DEG: Double = 70.0
        protected const val XY_CONTROL_SHOW_DEG: Double = 70.0
        protected const val Z_CONTROL_SHOW_DEG: Double = 30.0
        protected val COLOR_POSITIONS: Color? = Color.green
        protected val COLOR_GRID: Color? = Color.lightGray

        // Points in the juggler's coordinate system that are used for drawing the
        // onscreen representation of a selected event.
        protected val event_control_points: Array<DoubleArray?> = arrayOf<DoubleArray?>(
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

        // faces in terms of indices in event_control_points[]
        protected val face_xz: IntArray = intArrayOf(0, 1, 2, 3)

        // points for an event that is not selected (active)
        protected val unselected_event_points: Array<DoubleArray?> = arrayOf<DoubleArray?>(
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(-UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, UNSELECTED_BOX_HW_CM),
            doubleArrayOf(UNSELECTED_BOX_HW_CM, 0.0, -UNSELECTED_BOX_HW_CM),
            doubleArrayOf(0.0, 0.0, 0.0),
        )

        // Points in the juggler's coordinate system that are used for drawing the
        // onscreen representation of a selected position.
        protected val pos_control_points: Array<DoubleArray?> = arrayOf<DoubleArray?>(
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
        protected val face_xy: IntArray = intArrayOf(0, 1, 2, 3)

        // Test whether a point (x, y) lies inside a polygon.
        protected fun isInsidePolygon(
            x: Int, y: Int, array: Array<Array<DoubleArray>>, index: Int, points: IntArray
        ): Boolean {
            var inside = false
            var i = 0
            var j = points.size - 1
            while (i < points.size) {
                val xi = Math.round(array[index]!![points[i]]!![0]).toInt()
                val yi = Math.round(array[index]!![points[i]]!![1]).toInt()
                val xj = Math.round(array[index]!![points[j]]!![0]).toInt()
                val yj = Math.round(array[index]!![points[j]]!![1]).toInt()

                // note we only evaluate the second term when yj != yi:
                val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
                if (intersect) {
                    inside = !inside
                }
                j = i++
            }

            return inside
        }
    }
}
