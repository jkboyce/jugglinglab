//
// AnimationPanel.kt
//
// This class creates the juggling animation on screen. It embeds a Composable
// AnimationView which handles the rendering loop. It also interprets some mouse
// interactions such as camera drag and click to pause, and supports interactions
// with on-screen representations of JML events and positions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.ui.AnimationLayout
import jugglinglab.ui.AnimationView
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.PatternBuilder
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.sub
import jugglinglab.util.jlIsNearLine
import java.awt.event.*
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.OverlayLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalDensity
import jugglinglab.ui.AnimationLayout.Companion.getActiveEvent
import jugglinglab.ui.AnimationLayout.Companion.getActivePosition
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class AnimationPanel(
    val state: PatternAnimationState
) : JPanel(), MouseListener, MouseMotionListener {
    private val composePanel = ComposePanel()
    private val inputPanel = JPanel()
    var message: String? = null

    private var catchClip: Clip? = null
    private var bounceClip: Clip? = null

    private var wasPaused: Boolean = false // for pause on mouse away
    private var outside: Boolean = false
    private var outsideValid: Boolean = false

    // for camera dragging
    private var draggingCamera: Boolean = false
    private var startX: Int = 0
    private var startY: Int = 0
    private var lastX: Int = 0
    private var lastY: Int = 0

    // this angle is before camera snapping; value in state is after snapping
    private var dragCameraAngle: List<Double> = listOf(0.0, 0.0)

    // Event/position editing items below

    // data for selected event/position overlays, expressed in logical coordinates
    private var currentLayout = AnimationLayout(state, width, height)

    // for when an event is activated/dragged
    private var draggingXz: Boolean = false
    private var draggingY: Boolean = false
    private var eventStart: Coordinate? = null
    private var eventPrimaryStart: Coordinate? = null

    // for when a position is activated/dragged
    private var draggingXy: Boolean = false
    private var draggingZ: Boolean = false
    private var draggingAngle: Boolean = false
    private var positionStart: Coordinate? = null
    private var startAngle: Double = 0.0

    // for when a position angle is being dragged
    private var deltaAngle: Double = 0.0
    private var startDx: DoubleArray = doubleArrayOf(0.0, 0.0)
    private var startDy: DoubleArray = doubleArrayOf(0.0, 0.0)
    private var startControl: DoubleArray = doubleArrayOf(0.0, 0.0)

    // for when either an event or position is being dragged
    private var dragging: Boolean = false
    private var draggingLeft: Boolean = false // for stereo mode
    private var deltaX: Int = 0
    private var deltaY: Int = 0 // extent of drag action (pixels)

    init {
        layout = OverlayLayout(this)
        inputPanel.isOpaque = false
        add(inputPanel)
        add(composePanel)

        composePanel.setContent {
            BoxWithConstraints {
                val widthPx = constraints.maxWidth
                val heightPx = constraints.maxHeight
                val density = LocalDensity.current.density

                // Calculate logical dimensions for the layout
                val width = (widthPx / density).toInt()
                val height = (heightPx / density).toInt()
                val layout = AnimationLayout(state, width, height)

                SideEffect {
                    currentLayout = layout
                }

                AnimationView(
                    layout = layout,
                    state = state,
                    onPress = { offset -> handlePress(offset) },
                    onDrag = { offset -> handleDrag(offset) },
                    onRelease = { handleRelease() },
                    onFrame = { time -> onAnimationFrame(time) }
                )
            }
        }

        loadAudioClips()
        initHandlers()
    }

    //--------------------------------------------------------------------------
    // Methods to (re)start the animator
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun restartJuggle(pat: JMLPattern?, newjc: AnimationPrefs?, coldRestart: Boolean = true) {
        // Do layout first so an error won't disrupt the current animation
        pat?.layout

        if (pat != null) state.update(pattern = pat)
        if (newjc != null) state.update(prefs = newjc)
        if (coldRestart) {
            state.update(
                isPaused = state.prefs.startPaused,
                cameraAngle = state.initialCameraAngle(),
                zoom = 1.0,
                propForPath = state.initialPropForPath(),
                fitToFrame = true
            )
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun restartJuggle() = restartJuggle(null, null)

    //--------------------------------------------------------------------------
    // Setup / disposal
    //--------------------------------------------------------------------------

    private fun loadAudioClips() {
        try {
            val catchurl = AnimationPanel::class.java.getResource("/catch.au")
            val catchAudioIn = AudioSystem.getAudioInputStream(catchurl)
            val info = DataLine.Info(Clip::class.java, catchAudioIn.getFormat())
            catchClip = AudioSystem.getLine(info) as Clip?
            catchClip!!.open(catchAudioIn)
        } catch (_: Exception) {
            catchClip = null
        }
        try {
            val bounceurl = AnimationPanel::class.java.getResource("/bounce.au")
            val bounceAudioIn = AudioSystem.getAudioInputStream(bounceurl)
            val info = DataLine.Info(Clip::class.java, bounceAudioIn.getFormat())
            bounceClip = AudioSystem.getLine(info) as Clip?
            bounceClip!!.open(bounceAudioIn)
        } catch (_: Exception) {
            bounceClip = null
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun initHandlers() {
        inputPanel.addMouseListener(this)
        inputPanel.addMouseMotionListener(this)

        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    try {
                        val comp = SwingUtilities.getRoot(this@AnimationPanel)
                        if (comp is PatternWindow && comp.isWindowMaximized) return

                        if (state.prefs.width != width || state.prefs.height != height) {
                            val newPrefs =
                                state.prefs.copy(width = width, height = height)
                            state.update(prefs = newPrefs)
                        }
                    } catch (e: Exception) {
                        jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
                    }
                }
            })
    }

    fun disposeAnimation() {}

    //--------------------------------------------------------------------------
    // Callback to play sound clips if needed
    //--------------------------------------------------------------------------

    private var lastAudioCheckTime: Double = 0.0

    private fun onAnimationFrame(currentTime: Double) {
        var oldTime = lastAudioCheckTime
        lastAudioCheckTime = currentTime
        if (currentTime < oldTime) {
            oldTime -= (state.pattern.loopEndTime - state.pattern.loopStartTime)
        }

        if (state.prefs.catchSound && catchClip != null) {
            for (path in 1..state.pattern.numberOfPaths) {
                if (state.pattern.layout.getPathCatchVolume(path, oldTime, currentTime) > 0.0) {
                    SwingUtilities.invokeLater {
                        if (catchClip!!.isActive) catchClip!!.stop()
                        catchClip!!.framePosition = 0
                        catchClip!!.start()
                    }
                }
            }
        }
        if (state.prefs.bounceSound && bounceClip != null) {
            for (path in 1..state.pattern.numberOfPaths) {
                if (state.pattern.layout.getPathBounceVolume(path, oldTime, currentTime) > 0.0) {
                    SwingUtilities.invokeLater {
                        if (bounceClip!!.isActive) bounceClip!!.stop()
                        bounceClip!!.framePosition = 0
                        bounceClip!!.start()
                    }
                }
            }
        }
    }

    //--------------------------------------------------------------------------
    // Mouse event handlers (called from Compose)
    //--------------------------------------------------------------------------

    private fun handlePress(offset: androidx.compose.ui.geometry.Offset) {
        val mx = offset.x.toInt()
        val my = offset.y.toInt()
        //mousePressedLogic(mx, my)
    }

    private fun handleDrag(offset: androidx.compose.ui.geometry.Offset) {
        val mx = offset.x.toInt()
        val my = offset.y.toInt()
        //mouseDraggedLogic(mx, my)
    }

    private fun handleRelease() {
        //mouseReleasedLogic()
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //--------------------------------------------------------------------------

    private var lastpress: Long = 0L
    private var lastenter: Long = 1L

    override fun mousePressed(me: MouseEvent) {
        lastpress = me.getWhen()
        if (state.prefs.mousePause && lastpress == lastenter) return

        try {
            val layout = currentLayout
            startX = me.getX()
            startY = me.getY()

            val activeEventImage = getActiveEvent(state)
            if (activeEventImage != null) {
                val mx = me.getX()
                val my = me.getY()

                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    val t = i * size.width / 2

                    if (layout.showYDragControl) {
                        draggingY = jlIsNearLine(
                            mx - t,
                            my,
                            layout.eventPoints[0][i][5][0].roundToInt(),
                            layout.eventPoints[0][i][5][1].roundToInt(),
                            layout.eventPoints[0][i][6][0].roundToInt(),
                            layout.eventPoints[0][i][6][1].roundToInt(),
                            4
                        )
                        if (draggingY) {
                            dragging = true
                            draggingLeft = (i == 0)
                            deltaX = 0; deltaY = 0
                            val activeEventImage = getActiveEvent(state)!!
                            eventStart = activeEventImage.first.localCoordinate
                            eventPrimaryStart = activeEventImage.second.localCoordinate
                            state.update(fitToFrame = false)
                            return
                        }
                    }

                    if (layout.showXzDragControl) {
                        for (j in layout.eventPoints.indices) {
                            if (!isInsidePolygon(mx - t, my, layout.eventPoints[j], i, FACE_XZ)) continue
                            if (j > 0) {
                                val image =
                                    state.pattern.allEvents.find { it.event == layout.visibleEvents[j] }
                                        ?: throw JuggleExceptionInternal("Error 1 in AP.mousePressed()")
                                val code = state.pattern.loopEvents.find {
                                    it.primary == image.primary &&
                                        it.event.juggler == image.event.juggler &&
                                        it.event.hand == image.event.hand
                                }?.event?.jlHashCode
                                    ?: throw JuggleExceptionInternal("Error 2 in AP.mousePressed()")
                                state.update(selectedItemHashCode = code)
                            }
                            draggingXz = true
                            dragging = true
                            draggingLeft = (i == 0)
                            deltaX = 0; deltaY = 0
                            val activeEventImage = getActiveEvent(state)!!
                            eventStart = activeEventImage.first.localCoordinate
                            eventPrimaryStart = activeEventImage.second.localCoordinate
                            state.update(fitToFrame = false)
                            return
                        }
                    }
                }
            } else {
                val activePosition = getActivePosition(state)

                if (activePosition != null) {
                    val mx = me.getX()
                    val my = me.getY()
                    for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                        val t = i * size.width / 2

                        if (layout.showZDragControl) {
                            draggingZ = jlIsNearLine(
                                mx - t, my,
                                layout.posPoints[i][4][0].roundToInt(),
                                layout.posPoints[i][4][1].roundToInt(),
                                layout.posPoints[i][6][0].roundToInt(),
                                layout.posPoints[i][6][1].roundToInt(), 4
                            )
                            if (draggingZ) {
                                dragging = true
                                draggingLeft = (i == 0)
                                deltaX = 0; deltaY = 0
                                positionStart = activePosition.coordinate
                                state.update(fitToFrame = false)
                                return
                            }
                        }
                        if (layout.showXyDragControl) {
                            draggingXy = isInsidePolygon(mx - t, my, layout.posPoints, i, FACE_XY)
                            if (draggingXy) {
                                dragging = true
                                draggingLeft = (i == 0)
                                deltaX = 0; deltaY = 0
                                positionStart = activePosition.coordinate
                                state.update(fitToFrame = false)
                                return
                            }
                        }
                        if (layout.showAngleDragControl) {
                            val dmx = mx - t - layout.posPoints[i][5][0].roundToInt()
                            val dmy = my - layout.posPoints[i][5][1].roundToInt()
                            draggingAngle = (dmx * dmx + dmy * dmy < 49.0)
                            if (draggingAngle) {
                                dragging = true
                                draggingLeft = (i == 0)
                                deltaX = 0; deltaY = 0
                                startAngle = Math.toRadians(activePosition.angle)
                                startDx = doubleArrayOf(
                                    layout.posPoints[i][11][0] - layout.posPoints[i][4][0],
                                    layout.posPoints[i][11][1] - layout.posPoints[i][4][1]
                                )
                                startDy = doubleArrayOf(
                                    layout.posPoints[i][12][0] - layout.posPoints[i][4][0],
                                    layout.posPoints[i][12][1] - layout.posPoints[i][4][1]
                                )
                                startControl = doubleArrayOf(
                                    layout.posPoints[i][5][0] - layout.posPoints[i][4][0],
                                    layout.posPoints[i][5][1] - layout.posPoints[i][4][1]
                                )
                                state.update(fitToFrame = false)
                                return
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    override fun mouseReleased(me: MouseEvent) {
        if (state.prefs.mousePause && lastpress == lastenter) return

        val mouseMoved = (me.getX() != startX) || (me.getY() != startY)

        if (!draggingCamera && !dragging) {
            if (!mouseMoved) {
                state.update(isPaused = !state.isPaused)
                parent.dispatchEvent(me)
            }
        }

        try {
            val activeEventImage = getActiveEvent(state)
            val activePosition = getActivePosition(state)

            if ((activeEventImage != null || activePosition != null) && dragging && mouseMoved) {
                state.update(fitToFrame = true)
                state.addCurrentToUndoList()
            }

            draggingCamera = false
            dragging = false
            draggingY = false
            draggingXz = false
            draggingAngle = false
            draggingZ = false
            draggingXy = false
            deltaX = 0
            deltaY = 0
            deltaAngle = 0.0
            eventStart = null
            eventPrimaryStart = null
            positionStart = null
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    override fun mouseClicked(e: MouseEvent?) {}

    override fun mouseEntered(me: MouseEvent) {
        lastenter = me.getWhen()
        if (state.prefs.mousePause) {
            state.update(isPaused = wasPaused)
        }
        outside = false
        outsideValid = true
    }

    override fun mouseExited(me: MouseEvent?) {
        if (state.prefs.mousePause) {
            wasPaused = state.isPaused
            state.update(isPaused = true)
        }
        outside = true
        outsideValid = true
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //--------------------------------------------------------------------------

    override fun mouseDragged(me: MouseEvent) {
        try {
            if (dragging) {
                val mx = me.getX()
                val my = me.getY()

                if (draggingAngle) {
                    val dcontrol =
                        doubleArrayOf(startControl[0] + mx - startX, startControl[1] + my - startY)
                    val det = startDx[0] * startDy[1] - startDx[1] * startDy[0]
                    val a = (startDy[1] * dcontrol[0] - startDy[0] * dcontrol[1]) / det
                    val b = (-startDx[1] * dcontrol[0] + startDx[0] * dcontrol[1]) / det
                    deltaAngle = -atan2(-a, -b)

                    val newAngle = startAngle + deltaAngle
                    var finalAngle = Math.toDegrees(newAngle)
                    while (finalAngle > 360) finalAngle -= 360.0
                    while (finalAngle < 0) finalAngle += 360.0

                    val activePosition = getActivePosition(state)!!
                    val rec = PatternBuilder.fromJMLPattern(state.pattern)
                    val index = rec.positions.indexOf(activePosition)
                    if (index < 0) throw JuggleExceptionInternal("Error 1 in AP.mouseDragged()")
                    val newPosition = activePosition.copy(angle = finalAngle)
                    rec.positions[index] = newPosition
                    state.update(
                        pattern = JMLPattern.fromPatternBuilder(rec),
                        selectedItemHashCode = newPosition.jlHashCode
                    )
                } else {
                    deltaX = mx - startX
                    deltaY = my - startY
                    val cc = currentCoordinate
                    val activeEventImage = getActiveEvent(state)

                    if (activeEventImage != null) {
                        var deltalc = sub(cc, eventStart)!!
                        deltalc = Coordinate.truncate(deltalc, 1e-7)

                        val newEventCoordinate = Coordinate.add(eventStart, deltalc)!!
                        val newEvent = activeEventImage.first.copy(
                            x = newEventCoordinate.x,
                            y = newEventCoordinate.y,
                            z = newEventCoordinate.z
                        )

                        if (activeEventImage.first.hand != activeEventImage.second.hand) {
                            deltalc.x = -deltalc.x
                        }
                        val newPrimaryCoordinate = Coordinate.add(eventPrimaryStart, deltalc)!!
                        val newPrimary = activeEventImage.second.copy(
                            x = newPrimaryCoordinate.x,
                            y = newPrimaryCoordinate.y,
                            z = newPrimaryCoordinate.z
                        )

                        val record = PatternBuilder.fromJMLPattern(state.pattern)
                        val index = record.events.indexOf(activeEventImage.second)
                        record.events[index] = newPrimary
                        state.update(
                            pattern = JMLPattern.fromPatternBuilder(record),
                            selectedItemHashCode = newEvent.jlHashCode
                        )
                    } else {
                        val activePosition = getActivePosition(state)

                        if (activePosition != null) {
                            val rec = PatternBuilder.fromJMLPattern(state.pattern)
                            val index = rec.positions.indexOf(activePosition)
                            if (index < 0) {
                                throw JuggleExceptionInternal("Error 2 in AP.mouseDragged()")
                            }
                            val newPosition = activePosition.copy(x = cc.x, y = cc.y, z = cc.z)
                            rec.positions[index] = newPosition
                            state.update(
                                pattern = JMLPattern.fromPatternBuilder(rec),
                                selectedItemHashCode = newPosition.jlHashCode
                            )
                        }
                    }
                }
            } else if (!draggingCamera) {
                draggingCamera = true
                lastX = startX
                lastY = startY
                dragCameraAngle = state.cameraAngle
            }

            if (draggingCamera) {
                val dx = me.getX() - lastX
                val dy = me.getY() - lastY
                lastX = me.getX()
                lastY = me.getY()
                var ca0 = dragCameraAngle[0]
                var ca1 = dragCameraAngle[1]
                ca0 += dx.toDouble() * 0.02
                ca1 -= dy.toDouble() * 0.02
                if (ca1 < Math.toRadians(0.0001)) ca1 = Math.toRadians(0.0001)
                if (ca1 > Math.toRadians(179.9999)) ca1 = Math.toRadians(179.9999)
                while (ca0 < 0) ca0 += 2.0 * Math.PI
                while (ca0 >= 2.0 * Math.PI) ca0 -= 2.0 * Math.PI

                dragCameraAngle = listOf(ca0, ca1)
                state.update(cameraAngle = snapCamera(dragCameraAngle))
                parent.dispatchEvent(me)  // for SelectionView
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    override fun mouseMoved(e: MouseEvent?) {}

    //--------------------------------------------------------------------------
    // Utility methods for mouse interactions
    //--------------------------------------------------------------------------

    // Snap camera angle to certain preferred directions.

    private fun snapCamera(ca: List<Double>): List<Double> {
        val result = DoubleArray(2)
        result[0] = ca[0]
        result[1] = ca[1]

        if (result[1] < SNAP_ANGLE) {
            result[1] = Math.toRadians(0.0001)
        } else if (anglediff(Math.toRadians(90.0) - result[1]) < SNAP_ANGLE) {
            result[1] = Math.toRadians(90.0)
        } else if (result[1] > (Math.toRadians(180.0) - SNAP_ANGLE)) {
            result[1] = Math.toRadians(179.9999)
        }

        var a = 0.0
        var snapHorizontal = true
        val activeEventImage = getActiveEvent(state)
        val activePosition = getActivePosition(state)

        if (activeEventImage != null) {
            a = -Math.toRadians(
                state.pattern.layout.getJugglerAngle(
                    activeEventImage.first.juggler, activeEventImage.first.t
                )
            )
        } else if (activePosition != null) {
            a = 0.0
        } else if (state.pattern.numberOfJugglers == 1) {
            a = -Math.toRadians(state.pattern.layout.getJugglerAngle(1, state.time))
        } else {
            snapHorizontal = false
        }

        if (snapHorizontal) {
            while (a < 0) a += Math.toRadians(360.0)
            while (a >= Math.toRadians(360.0)) a -= Math.toRadians(360.0)

            if (anglediff(a - result[0]) < SNAP_ANGLE) result[0] = a
            else if (anglediff(a + 0.5 * Math.PI - result[0]) < SNAP_ANGLE) result[0] =
                a + 0.5 * Math.PI
            else if (anglediff(a + Math.PI - result[0]) < SNAP_ANGLE) result[0] = a + Math.PI
            else if (anglediff(a + 1.5 * Math.PI - result[0]) < SNAP_ANGLE) result[0] =
                a + 1.5 * Math.PI
        }
        return result.toList()
    }

    // Snap event/position coordinates to preferred values.

    private val currentCoordinate: Coordinate
        get() {
            val layout = currentLayout
            val activeEventImage = getActiveEvent(state)

            if (activeEventImage != null) {
                if (!dragging) return activeEventImage.first.localCoordinate
                val c = eventStart!!.copy()
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = if (state.prefs.stereo) 0.5 else 1.0
                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    dx[0] += f * (layout.eventPoints[0][i][11][0] - layout.eventPoints[0][i][4][0])
                    dx[1] += f * (layout.eventPoints[0][i][11][1] - layout.eventPoints[0][i][4][1])
                    dy[0] += f * (layout.eventPoints[0][i][12][0] - layout.eventPoints[0][i][4][0])
                    dy[1] += f * (layout.eventPoints[0][i][12][1] - layout.eventPoints[0][i][4][1])
                    dz[0] += f * (layout.eventPoints[0][i][13][0] - layout.eventPoints[0][i][4][0])
                    dz[1] += f * (layout.eventPoints[0][i][13][1] - layout.eventPoints[0][i][4][1])
                }
                if (draggingXz) {
                    val det = dx[0] * dz[1] - dx[1] * dz[0]
                    val a = (dz[1] * deltaX - dz[0] * deltaY) / det
                    val b = (-dx[1] * deltaX + dx[0] * deltaY) / det
                    c.x += a
                    c.z += b
                    if (abs(c.z) < YZ_EVENT_SNAP_CM) {
                        deltaY += (dz[1] * (-c.z)).roundToInt()
                        c.z = 0.0
                    }
                }
                if (draggingY) {
                    val det = dy[0] * dz[1] - dy[1] * dz[0]
                    if (abs(det) > 1.0e-4) {
                        val a = (dz[1] * deltaX - dz[0] * deltaY) / det
                        c.y += a
                    } else {
                        c.y += deltaY / dy[1]
                    }
                    if (abs(c.y) < YZ_EVENT_SNAP_CM) c.y = 0.0
                    deltaX = ((c.y - eventStart!!.y) * dy[0]).roundToInt()
                    deltaY = ((c.y - eventStart!!.y) * dy[1]).roundToInt()
                }
                return c
            }

            val activePosition = getActivePosition(state)
            if (activePosition != null) {
                if (!draggingXy && !draggingZ) return activePosition.coordinate
                val c = positionStart!!.copy()
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = if (state.prefs.stereo) 0.5 else 1.0
                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    dx[0] += f * (layout.posPoints[i][11][0] - layout.posPoints[i][4][0])
                    dx[1] += f * (layout.posPoints[i][11][1] - layout.posPoints[i][4][1])
                    dy[0] += f * (layout.posPoints[i][12][0] - layout.posPoints[i][4][0])
                    dy[1] += f * (layout.posPoints[i][12][1] - layout.posPoints[i][4][1])
                    dz[0] += f * (layout.posPoints[i][13][0] - layout.posPoints[i][4][0])
                    dz[1] += f * (layout.posPoints[i][13][1] - layout.posPoints[i][4][1])
                }
                if (draggingXy) {
                    val det = dx[0] * dy[1] - dx[1] * dy[0]
                    val a = (dy[1] * deltaX - dy[0] * deltaY) / det
                    val b = (-dx[1] * deltaX + dx[0] * deltaY) / det
                    val angle = Math.toRadians(activePosition.angle)
                    c.x += a * cos(angle) - b * sin(angle)
                    c.y += a * sin(angle) + b * cos(angle)

                    var snapped = false
                    val oldcx = c.x
                    val oldcy = c.y
                    val closestGridX = XY_GRID_SPACING_CM * (c.x / XY_GRID_SPACING_CM).roundToInt()
                    if (abs(c.x - closestGridX) < XYZ_GRID_POSITION_SNAP_CM) {
                        c.x = closestGridX; snapped = true
                    }
                    val closestGridY = XY_GRID_SPACING_CM * (c.y / XY_GRID_SPACING_CM).roundToInt()
                    if (abs(c.y - closestGridY) < XYZ_GRID_POSITION_SNAP_CM) {
                        c.y = closestGridY; snapped = true
                    }

                    if (snapped) {
                        val deltacx = c.x - oldcx
                        val deltacy = c.y - oldcy
                        val deltaa = deltacx * cos(angle) + deltacy * sin(angle)
                        val deltab = -deltacx * sin(angle) + deltacy * cos(angle)
                        val deltaXpx = dx[0] * deltaa + dy[0] * deltab
                        val deltaYpx = dx[1] * deltaa + dy[1] * deltab
                        deltaX += deltaXpx.roundToInt()
                        deltaY += deltaYpx.roundToInt()
                    }
                }
                if (draggingZ) {
                    deltaX = 0
                    c.z += deltaY / dz[1]
                    if (abs(c.z - 100) < XYZ_GRID_POSITION_SNAP_CM) {
                        deltaY += (dz[1] * (100 - c.z)).roundToInt()
                        c.z = 100.0
                    }
                    if (abs(c.z) < XYZ_GRID_POSITION_SNAP_CM) {
                        deltaY += (dz[1] * (-c.z)).roundToInt()
                        c.z = 0.0
                    }
                }
                return c
            }

            return Coordinate()
        }

    companion object {
        private const val YZ_EVENT_SNAP_CM: Double = 3.0
        private const val XY_GRID_SPACING_CM: Double = 20.0
        private const val XYZ_GRID_POSITION_SNAP_CM: Double = 3.0

        private val FACE_XZ: IntArray = intArrayOf(0, 1, 2, 3)
        private val FACE_XY: IntArray = intArrayOf(0, 1, 2, 3)

        private val SNAP_ANGLE: Double = Math.toRadians(8.0)

        fun anglediff(delta: Double): Double {
            var delta = delta
            while (delta > Math.PI) delta -= 2 * Math.PI
            while (delta <= -Math.PI) delta += 2 * Math.PI
            return abs(delta)
        }

        private fun isInsidePolygon(
            x: Int,
            y: Int,
            array: Array<Array<DoubleArray>>,
            index: Int,
            points: IntArray
        ): Boolean {
            var inside = false
            var i = 0
            var j = points.size - 1
            while (i < points.size) {
                val xi = array[index][points[i]][0].roundToInt()
                val yi = array[index][points[i]][1].roundToInt()
                val xj = array[index][points[j]][0].roundToInt()
                val yj = array[index][points[j]][1].roundToInt()
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
