//
// AnimationPanel.kt
//
// This class creates the juggling animation on screen. It embeds a Composable AnimationView
// which handles the rendering loop. It also interprets some mouse interactions
// such as camera drag and click to pause, and supports interactions with on-
// screen representations of JML events and positions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.ui.AnimationLayout
import jugglinglab.ui.AnimationView
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.JMLEvent
import jugglinglab.jml.JMLPosition
import jugglinglab.jml.PatternBuilder
import jugglinglab.renderer.ComposeRenderer
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.distance
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

class AnimationPanel(
    val state: PatternAnimationState
) : JPanel(), MouseListener, MouseMotionListener {
    private val composePanel = ComposePanel()
    private val inputPanel = JPanel()
    
    // AnimationLayout holds the display data for overlays
    private var currentLayout: AnimationLayout? = null
    private var currentDensity: Float = 1.0f

    // We pass a mutable state or just force recomposition. Since AnimationView takes layout as param, 
    // we can re-set the content when layout updates? Or use a MutableState inside.
    private val layoutState = mutableStateOf<AnimationLayout?>(null)

    // Replacement for FrameDrawer - used only for calculations in this panel (selection logic)
    private val calculationRenderer = ComposeRenderer()
    private val calculationRenderer2 = ComposeRenderer() // Stereo

    var message: String? = null

    private var catchClip: Clip? = null
    private var bounceClip: Clip? = null

    private var waspaused: Boolean = false // for pause on mouse away
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

    //----------------------------------

    // for when an event is activated/dragged
    private var eventActive: Boolean = false
    private var activeEvent: JMLEvent? = null
    private var activeEventPrimary: JMLEvent? = null

    private var draggingXz: Boolean = false
    private var draggingY: Boolean = false
    private var showXzDragControl: Boolean = false
    private var showYDragControl: Boolean = false
    private var eventStart: Coordinate? = null
    private var eventPrimaryStart: Coordinate? = null
    private var visibleEvents: List<JMLEvent> = listOf()
    private var eventPoints: Array<Array<Array<DoubleArray>>> = Array(0) { Array(0) { Array(0) { DoubleArray(0) } } }
    private var handpathPoints: Array<Array<DoubleArray>> = Array(0) { Array(0) { DoubleArray(0) } }
    private var handpathStartTime: Double = 0.0
    private var handpathEndTime: Double = 0.0
    private var handpathHold: BooleanArray = BooleanArray(0)

    // for when a position is activated/dragged
    private var positionActive: Boolean = false
    private var activePosition: JMLPosition? = null

    private var posPoints: Array<Array<DoubleArray>> = Array(0) { Array(0) { DoubleArray(0) } }
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

                SideEffect {
                    // currentLayout = layout
                    currentDensity = density
                }

                AnimationView(
                    state = state,
                    layout = currentLayout,
                    onFrame = { time -> onAnimationFrame(time) }
                )
            }
        }

        loadAudioClips()
        initHandlers()
        
        // Initialize renderers
        updateCalculationRenderers()
        
        // Initial build
        buildSelectionView()

        state.addListener(onPatternChange = {
            try {
                updateCalculationRenderers()
                buildSelectionView()
            } catch (e: Exception) {
                jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
            }
        })
        state.addListener(onPrefsChange = {
            try {
                updateCalculationRenderers()
                buildSelectionView()
            } catch (e: Exception) {
                jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
            }
        })
        state.addListener(onCameraAngleChange = {
            updateCalculationRenderers()
            buildSelectionView()
        })
        state.addListener(onZoomChange = {
            try {
                updateCalculationRenderers()
                buildSelectionView()
            } catch (e: Exception) {
                jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
            }
        })
        state.addListener(onSelectedItemHashChange = {
            try {
                buildSelectionView()
            } catch (e: Exception) {
                jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
            }
        })
    }

    //--------------------------------------------------------------------------
    // Methods to respond to state changes
    //--------------------------------------------------------------------------

    private fun updateCalculationRenderers() {
        if (width > 0 && height > 0) {
            val (overallMin, overallMax) = calculateBoundingBox(state)

            if (state.prefs.stereo) {
                calculationRenderer.initDisplay(width/2, height, state.prefs.borderPixels, overallMax, overallMin)
                calculationRenderer2.initDisplay(width/2, height, state.prefs.borderPixels, overallMax, overallMin)
            } else {
                calculationRenderer.initDisplay(width, height, state.prefs.borderPixels, overallMax, overallMin)
            }
        }

        calculationRenderer.setPattern(state.pattern)
        calculationRenderer.zoomLevel = state.zoom

        val ca = state.cameraAngle.toDoubleArray()
        if (state.prefs.stereo) {
            calculationRenderer2.setPattern(state.pattern)
            calculationRenderer2.zoomLevel = state.zoom
            val sep = 0.10
            calculationRenderer.cameraAngle = doubleArrayOf(ca[0] - sep/2, ca[1])
            calculationRenderer2.cameraAngle = doubleArrayOf(ca[0] + sep/2, ca[1])
        } else {
            calculationRenderer.cameraAngle = ca
        }
    }

    fun buildSelectionView() {
        activeEvent = null
        activeEventPrimary = null
        eventActive = false
        eventPoints = Array(0) { Array(1) { Array(0) { DoubleArray(2) } } }
        visibleEvents = mutableListOf()
        handpathPoints = Array(1) { Array(0) { DoubleArray(2) } }

        activePosition = null
        positionActive = false

        for ((ev, evPrimary) in state.pattern.loopEvents) {
            if (ev.jlHashCode == state.selectedItemHashCode) {
                activeEvent = ev
                activeEventPrimary = evPrimary
                eventActive = true
                createEventView()
                break
            } else if (ev.transitions.withIndex().any { (transNum, _) ->
                    val trHash = ev.jlHashCode + 23 + transNum * 27
                    trHash == state.selectedItemHashCode
                }) {
                activeEvent = ev
                activeEventPrimary = evPrimary
                eventActive = true
                createEventView()
                break
            }
        }
        for (pos in state.pattern.positions) {
            if (pos.jlHashCode == state.selectedItemHashCode) {
                activePosition = pos
                positionActive = true
                createPositionView()
                break
            }
        }
        updateLayout()
    }

    private fun updateLayout() {
        currentLayout = AnimationLayout(
            eventPoints = eventPoints,
            handpathPoints = handpathPoints,
            handpathHold = handpathHold,
            posPoints = posPoints,
            showXzDragControl = showXzDragControl,
            showYDragControl = showYDragControl,
            showXyDragControl = showXyDragControl,
            showZDragControl = showZDragControl,
            showAngleDragControl = showAngleDragControl
        )
    }

    private fun createEventView() {
        if (!eventActive) return
        val pat = state.pattern
        val ev = activeEvent!!
        handpathStartTime = ev.t
        handpathEndTime = ev.t

        val index = pat.allEvents.indexOfFirst { it.event == ev }
        if (index == -1) {
            eventActive = false
            activeEvent = null
            return
        }

        visibleEvents = buildList {
            add(ev)
            for (image in pat.allEvents.subList(index + 1, pat.allEvents.size).filter { it.event.hand == ev.hand && it.event.juggler == ev.juggler }) {
                handpathEndTime = max(handpathEndTime, image.event.t)
                if (image.primary != activeEventPrimary) add(image.event) else break
                if (image.event.hasThrowOrCatch) break
            }
            for (image in pat.allEvents.subList(0, index).asReversed().filter { it.event.hand == ev.hand && it.event.juggler == ev.juggler }) {
                handpathStartTime = min(handpathStartTime, image.event.t)
                if (image.primary != activeEventPrimary) add(image.event) else break
                if (image.event.hasThrowOrCatch) break
            }
        }

        val rendererCount = if (state.prefs.stereo) 2 else 1
        eventPoints = Array(visibleEvents.size) { Array(rendererCount) { Array(EVENT_CONTROL_POINTS.size) { DoubleArray(2) } } }

        for ((evNum, ev2) in visibleEvents.withIndex()) {
            for (i in 0..<rendererCount) {
                val ren = if (i == 0) calculationRenderer else calculationRenderer2 // Use local renderers
                val c = pat.layout.getGlobalCoordinate(ev2)
                val c2 = ren.getScreenTranslatedCoordinate(c, 1, 0)
                val dl = 1.0 / distance(c, c2)

                val ca = ren.cameraAngle
                val theta = ca[0] + Math.toRadians(pat.layout.getJugglerAngle(ev2.juggler, ev2.t))
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
                val targetPoints = if (ev2 == activeEvent) EVENT_CONTROL_POINTS else UNSELECTED_EVENT_POINTS

                for (j in targetPoints.indices) {
                    eventPoints[evNum][i][j][0] = center[0].toDouble() + dxx * targetPoints[j][0] + dyx * targetPoints[j][1] + dzx * targetPoints[j][2]
                    eventPoints[evNum][i][j][1] = center[1].toDouble() + dxy * targetPoints[j][0] + dyy * targetPoints[j][1] + dzy * targetPoints[j][2]
                }

                if (ev2 == activeEvent) {
                    showXzDragControl = (anglediff(phi - Math.PI / 2) < Math.toRadians(XZ_CONTROL_SHOW_DEG) &&
                        (anglediff(theta) < Math.toRadians(XZ_CONTROL_SHOW_DEG) || anglediff(theta - Math.PI) < Math.toRadians(XZ_CONTROL_SHOW_DEG)))
                    showYDragControl = !(anglediff(phi - Math.PI / 2) < Math.toRadians(Y_CONTROL_SHOW_DEG) &&
                        (anglediff(theta) < Math.toRadians(Y_CONTROL_SHOW_DEG) || anglediff(theta - Math.PI) < Math.toRadians(Y_CONTROL_SHOW_DEG)))
                }
            }
        }
        createHandpathView()
    }

    private fun createHandpathView() {
        if (!eventActive) return
        val pat = state.pattern
        val ev = activeEvent!!
        val rendererCount = if (state.prefs.stereo) 2 else 1
        val numHandpathPoints = ceil((handpathEndTime - handpathStartTime) / HANDPATH_POINT_SEP_TIME).toInt() + 1
        handpathPoints = Array(rendererCount) { Array(numHandpathPoints) { DoubleArray(2) } }
        handpathHold = BooleanArray(numHandpathPoints)

        for (i in 0..<rendererCount) {
            val ren = if (i == 0) calculationRenderer else calculationRenderer2
            val c = Coordinate()
            for (j in 0..<numHandpathPoints) {
                val t = handpathStartTime + j * HANDPATH_POINT_SEP_TIME
                pat.layout.getHandCoordinate(ev.juggler, ev.hand, t, c)
                val point = ren.getXY(c)
                handpathPoints[i][j][0] = point[0].toDouble()
                handpathPoints[i][j][1] = point[1].toDouble()
                handpathHold[j] = pat.layout.isHandHolding(ev.juggler, ev.hand, t + 0.0001)
            }
        }
    }

    private fun createPositionView() {
        if (!positionActive) return
        posPoints = Array(2) { Array(POS_CONTROL_POINTS.size) { DoubleArray(2) } }
        for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
            val ren = if (i == 0) calculationRenderer else calculationRenderer2
            val c = Coordinate.add(activePosition!!.coordinate, Coordinate(0.0, 0.0, POSITION_BOX_Z_OFFSET_CM))
            val c2 = ren.getScreenTranslatedCoordinate(c!!, 1, 0)
            val dl = 1.0 / distance(c, c2)

            val ca = ren.cameraAngle
            val theta = ca[0] + Math.toRadians(activePosition!!.angle)
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
                posPoints[i][j][0] = center[0].toDouble() + dxx * POS_CONTROL_POINTS[j][0] + dyx * POS_CONTROL_POINTS[j][1] + dzx * POS_CONTROL_POINTS[j][2]
                posPoints[i][j][1] = center[1].toDouble() + dxy * POS_CONTROL_POINTS[j][0] + dyy * POS_CONTROL_POINTS[j][1] + dzy * POS_CONTROL_POINTS[j][2]
            }

            showAngleDragControl = (anglediff(phi - Math.PI/2) > Math.toRadians(90 - ANGLE_CONTROL_SHOW_DEG))
            showXyDragControl = (anglediff(phi - Math.PI/2) > Math.toRadians(90 - XY_CONTROL_SHOW_DEG))
            showZDragControl = (anglediff(phi - Math.PI/2) < Math.toRadians(90 - Z_CONTROL_SHOW_DEG))
        }
    }

    // Play sounds clips if needed

    private var lastAudioCheckTime: Double = 0.0

    private fun onAnimationFrame(currentTime: Double) {
        val oldTime = lastAudioCheckTime
        lastAudioCheckTime = currentTime

        // Audio Logic
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
    // Utility methods to (re)start the animator
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun restartJuggle(pat: JMLPattern?, newjc: AnimationPrefs?) {
        pat?.layout

        if (pat != null) state.update(pattern = pat)
        if (newjc != null) state.update(prefs = newjc)
        state.update(
            isPaused = state.prefs.startPaused,
            cameraAngle = state.initialCameraAngle(),
            zoom = 1.0,
            propForPath = state.initialPropForPath(),
            fitToFrame = true
        )
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

                        if (state.prefs.width != size.width || state.prefs.height != size.height) {
                            val newPrefs = state.prefs.copy(width = size.width, height = size.height)
                            state.update(prefs = newPrefs)
                        }
                        updateCalculationRenderers()
                        buildSelectionView()
                    } catch (e: Exception) {
                        jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
                    }
                }
            })
    }

    fun disposeAnimation() {
        state.update(isPaused = true)
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
            startX = me.getX()
            startY = me.getY()

            if (eventActive) {
                val mx = me.getX()
                val my = me.getY()

                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    val t = i * size.width / 2

                    if (showYDragControl) {
                        draggingY = jlIsNearLine(
                            mx - t, my,
                            eventPoints[0][i][5][0].roundToInt(), eventPoints[0][i][5][1].roundToInt(),
                            eventPoints[0][i][6][0].roundToInt(), eventPoints[0][i][6][1].roundToInt(),
                            4
                        )
                        if (draggingY) {
                            dragging = true
                            draggingLeft = (i == 0)
                            deltaX = 0; deltaY = 0
                            eventStart = activeEvent!!.localCoordinate
                            eventPrimaryStart = activeEventPrimary!!.localCoordinate
                            state.update(fitToFrame = false)
                            return
                        }
                    }

                    if (showXzDragControl) {
                        for (j in eventPoints.indices) {
                            if (!isInsidePolygon(mx - t, my, eventPoints[j], i, FACE_XZ)) continue
                            if (j > 0) {
                                val image = state.pattern.allEvents.find { it.event == visibleEvents[j] }
                                    ?: throw JuggleExceptionInternal("Error 1 in AEP.mousePressed()")
                                val code = state.pattern.loopEvents.find {
                                    it.primary == image.primary &&
                                    it.event.juggler == image.event.juggler &&
                                    it.event.hand == image.event.hand
                                }?.event?.jlHashCode ?: throw JuggleExceptionInternal("Error 2 in AEP.mousePressed()")
                                state.update(selectedItemHashCode = code)
                            }
                            draggingXz = true
                            dragging = true
                            draggingLeft = (i == 0)
                            deltaX = 0; deltaY = 0
                            eventStart = activeEvent!!.localCoordinate
                            eventPrimaryStart = activeEventPrimary!!.localCoordinate
                            state.update(fitToFrame = false)
                            return
                        }
                    }
                }
            }

            if (positionActive) {
                val mx = me.getX()
                val my = me.getY()
                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    val t = i * size.width / 2

                    if (showZDragControl) {
                        draggingZ = jlIsNearLine(
                            mx - t, my,
                            posPoints[i][4][0].roundToInt(), posPoints[i][4][1].roundToInt(),
                            posPoints[i][6][0].roundToInt(), posPoints[i][6][1].roundToInt(), 4
                        )
                        if (draggingZ) {
                            dragging = true
                            draggingLeft = (i == 0)
                            deltaX = 0; deltaY = 0
                            positionStart = activePosition!!.coordinate
                            state.update(fitToFrame = false)
                            return
                        }
                    }
                    if (showXyDragControl) {
                        draggingXy = isInsidePolygon(mx - t, my, posPoints, i, FACE_XY)
                        if (draggingXy) {
                            dragging = true
                            draggingLeft = (i == 0)
                            deltaX = 0; deltaY = 0
                            positionStart = activePosition!!.coordinate
                            state.update(fitToFrame = false)
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
                            deltaX = 0; deltaY = 0
                            startAngle = Math.toRadians(activePosition!!.angle)
                            startDx = doubleArrayOf(posPoints[i][11][0] - posPoints[i][4][0], posPoints[i][11][1] - posPoints[i][4][1])
                            startDy = doubleArrayOf(posPoints[i][12][0] - posPoints[i][4][0], posPoints[i][12][1] - posPoints[i][4][1])
                            startControl = doubleArrayOf(posPoints[i][5][0] - posPoints[i][4][0], posPoints[i][5][1] - posPoints[i][4][1])
                            state.update(fitToFrame = false)
                            return
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
        
        if (!draggingCamera && !dragging) {
            val mouseMoved = (me.getX() != startX) || (me.getY() != startY)
            if (!mouseMoved) {
                state.update(isPaused = !state.isPaused)
                parent.dispatchEvent(me)
            }
        }

        try {
            val mouseMoved = (me.getX() != startX) || (me.getY() != startY)

            if ((eventActive || positionActive) && dragging && mouseMoved) {
                state.update(fitToFrame = true)
                updateCalculationRenderers()
                buildSelectionView()
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
        if (state.prefs.mousePause) state.update(isPaused = waspaused)
        outside = false
        outsideValid = true
    }

    override fun mouseExited(me: MouseEvent?) {
        if (state.prefs.mousePause) {
            waspaused = state.isPaused
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
                     val dcontrol = doubleArrayOf(startControl[0] + mx - startX, startControl[1] + my - startY)
                     val det = startDx[0] * startDy[1] - startDx[1] * startDy[0]
                     val a = (startDy[1] * dcontrol[0] - startDy[0] * dcontrol[1]) / det
                     val b = (-startDx[1] * dcontrol[0] + startDx[0] * dcontrol[1]) / det
                     deltaAngle = -atan2(-a, -b)
                     
                     val newAngle = startAngle + deltaAngle
                     var finalAngle = Math.toDegrees(newAngle)
                     while (finalAngle > 360) finalAngle -= 360.0
                     while (finalAngle < 0) finalAngle += 360.0
                     
                     val rec = PatternBuilder.fromJMLPattern(state.pattern)
                     val index = rec.positions.indexOf(activePosition!!)
                     if (index < 0) throw JuggleExceptionInternal("Error 1 in AEP.mouseDragged()")
                     val newPosition = activePosition!!.copy(angle = finalAngle)
                     rec.positions[index] = newPosition
                     state.update(pattern = JMLPattern.fromPatternBuilder(rec), selectedItemHashCode = newPosition.jlHashCode)
                     createPositionView()
                } else {
                     deltaX = mx - startX
                     deltaY = my - startY
                     val cc = currentCoordinate
                     
                     if (eventActive) {
                         var deltalc = sub(cc, eventStart)!!
                        deltalc = Coordinate.truncate(deltalc, 1e-7)

                        val newEventCoordinate = Coordinate.add(eventStart, deltalc)!!
                        val newEvent = activeEvent!!.copy(
                            x = newEventCoordinate.x,
                            y = newEventCoordinate.y,
                            z = newEventCoordinate.z
                        )

                        if (activeEvent!!.hand != activeEventPrimary!!.hand) {
                            deltalc.x = -deltalc.x
                        }
                        val newPrimaryCoordinate = Coordinate.add(eventPrimaryStart, deltalc)!!
                        val newPrimary = activeEventPrimary!!.copy(
                            x = newPrimaryCoordinate.x,
                            y = newPrimaryCoordinate.y,
                            z = newPrimaryCoordinate.z
                        )

                        val record = PatternBuilder.fromJMLPattern(state.pattern)
                        val index = record.events.indexOf(activeEventPrimary)
                        record.events[index] = newPrimary
                        state.update(
                            pattern = JMLPattern.fromPatternBuilder(record),
                            selectedItemHashCode = newEvent.jlHashCode
                        )
                         createEventView()
                     }
                     if (positionActive) {
                         val rec = PatternBuilder.fromJMLPattern(state.pattern)
                        val index = rec.positions.indexOf(activePosition!!)
                        if (index < 0) {
                            throw JuggleExceptionInternal("Error 2 in AEP.mouseDragged()")
                        }
                        val newPosition = activePosition!!.copy(x = cc.x, y = cc.y, z = cc.z)
                        rec.positions[index] = newPosition
                        state.update(
                            pattern = JMLPattern.fromPatternBuilder(rec),
                            selectedItemHashCode = newPosition.jlHashCode
                        )
                        createPositionView()
                     }
                }
                updateLayout()
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
                parent.dispatchEvent(me)
            }
            if (eventActive && draggingCamera) { createEventView(); updateLayout() }
            if (positionActive && (draggingCamera || draggingAngle)) { createPositionView(); updateLayout() }

        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    override fun mouseMoved(e: MouseEvent?) {}

    //--------------------------------------------------------------------------
    // Utility methods for mouse interactions
    //--------------------------------------------------------------------------

    private fun snapCamera(ca: List<Double>): List<Double> {
        val result = DoubleArray(2)
        result[0] = ca[0]
        result[1] = ca[1]

        if (result[1] < SNAPANGLE) {
            result[1] = Math.toRadians(0.0001)
        } else if (anglediff(Math.toRadians(90.0) - result[1]) < SNAPANGLE) {
            result[1] = Math.toRadians(90.0)
        } else if (result[1] > (Math.toRadians(180.0) - SNAPANGLE)) {
            result[1] = Math.toRadians(179.9999)
        }

        var a = 0.0
        var snapHorizontal = true

        if (eventActive) {
            a = -Math.toRadians(state.pattern.layout.getJugglerAngle(activeEvent!!.juggler, activeEvent!!.t))
        } else if (positionActive) {
            a = 0.0
        } else if (state.pattern.numberOfJugglers == 1) {
            a = -Math.toRadians(state.pattern.layout.getJugglerAngle(1, state.time))
        } else {
            snapHorizontal = false
        }

        if (snapHorizontal) {
            while (a < 0) a += Math.toRadians(360.0)
            while (a >= Math.toRadians(360.0)) a -= Math.toRadians(360.0)

            if (anglediff(a - result[0]) < SNAPANGLE) result[0] = a
            else if (anglediff(a + 0.5 * Math.PI - result[0]) < SNAPANGLE) result[0] = a + 0.5 * Math.PI
            else if (anglediff(a + Math.PI - result[0]) < SNAPANGLE) result[0] = a + Math.PI
            else if (anglediff(a + 1.5 * Math.PI - result[0]) < SNAPANGLE) result[0] = a + 1.5 * Math.PI
        }
        return result.toList()
    }

    companion object {
        val SNAPANGLE: Double = Math.toRadians(8.0)
        fun anglediff(delta: Double): Double {
            var delta = delta
            while (delta > Math.PI) delta -= 2 * Math.PI
            while (delta <= -Math.PI) delta += 2 * Math.PI
            return abs(delta)
        }
        
        private const val EVENT_BOX_HW_CM: Double = 5.0
        private const val UNSELECTED_BOX_HW_CM: Double = 2.0
        private const val YZ_EVENT_SNAP_CM: Double = 3.0
        private const val XZ_CONTROL_SHOW_DEG: Double = 60.0
        private const val Y_CONTROL_SHOW_DEG: Double = 30.0
        private const val HANDPATH_POINT_SEP_TIME: Double = 0.005
        private const val POSITION_BOX_HW_CM: Double = 10.0
        private const val POSITION_BOX_Z_OFFSET_CM: Double = 0.0
        private const val XY_GRID_SPACING_CM: Double = 20.0
        private const val XYZ_GRID_POSITION_SNAP_CM: Double = 3.0
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

        private val FACE_XZ: IntArray = intArrayOf(0, 1, 2, 3)

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

        private val FACE_XY: IntArray = intArrayOf(0, 1, 2, 3)
        
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
    
    private val currentCoordinate: Coordinate
        get() {
            if (eventActive) {
                if (!dragging) return activeEvent!!.localCoordinate
                val c = eventStart!!.copy()
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = if (state.prefs.stereo) 0.5 else 1.0
                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    dx[0] += f * (eventPoints[0][i][11][0] - eventPoints[0][i][4][0])
                    dx[1] += f * (eventPoints[0][i][11][1] - eventPoints[0][i][4][1])
                    dy[0] += f * (eventPoints[0][i][12][0] - eventPoints[0][i][4][0])
                    dy[1] += f * (eventPoints[0][i][12][1] - eventPoints[0][i][4][1])
                    dz[0] += f * (eventPoints[0][i][13][0] - eventPoints[0][i][4][0])
                    dz[1] += f * (eventPoints[0][i][13][1] - eventPoints[0][i][4][1])
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
             
            if (positionActive) {
                if (!draggingXy && !draggingZ) return activePosition!!.coordinate
                val c = positionStart!!.copy()
                val dx = doubleArrayOf(0.0, 0.0)
                val dy = doubleArrayOf(0.0, 0.0)
                val dz = doubleArrayOf(0.0, 0.0)
                val f = if (state.prefs.stereo) 0.5 else 1.0
                for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                    dx[0] += f * (posPoints[i][11][0] - posPoints[i][4][0])
                    dx[1] += f * (posPoints[i][11][1] - posPoints[i][4][1])
                    dy[0] += f * (posPoints[i][12][0] - posPoints[i][4][0])
                    dy[1] += f * (posPoints[i][12][1] - posPoints[i][4][1])
                    dz[0] += f * (posPoints[i][13][0] - posPoints[i][4][0])
                    dz[1] += f * (posPoints[i][13][1] - posPoints[i][4][1])
                }
                if (draggingXy) {
                    val det = dx[0] * dy[1] - dx[1] * dy[0]
                    val a = (dy[1] * deltaX - dy[0] * deltaY) / det
                    val b = (-dx[1] * deltaX + dx[0] * deltaY) / det
                    val angle = Math.toRadians(activePosition!!.angle)
                    c.x += a * cos(angle) - b * sin(angle)
                    c.y += a * sin(angle) + b * cos(angle)
                    
                    var snapped = false
                    val oldcx = c.x
                    val oldcy = c.y
                    val closestGridX = XY_GRID_SPACING_CM * (c.x / XY_GRID_SPACING_CM).roundToInt()
                    if (abs(c.x - closestGridX) < XYZ_GRID_POSITION_SNAP_CM) { c.x = closestGridX; snapped = true }
                    val closestGridY = XY_GRID_SPACING_CM * (c.y / XY_GRID_SPACING_CM).roundToInt()
                    if (abs(c.y - closestGridY) < XYZ_GRID_POSITION_SNAP_CM) { c.y = closestGridY; snapped = true }
                    
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
    
    // Helper to match AnimationView's logic
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
        
        safeMax.z = max(safeMax.z, 180.0)
        safeMin.x = min(safeMin.x, -50.0)
        safeMax.x = max(safeMax.x, 50.0)
        
        return Pair(safeMin, safeMax)
    }
}
