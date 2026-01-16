//
// AnimationPanel.kt
//
// This class creates the juggling animation on screen. It embeds a Composable
// AnimationView which handles rendering. It also interprets some mouse
// interactions such as camera drag and click to pause, and supports interactions
// with on-screen representations of JML events and positions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.ui.AnimationController
import jugglinglab.ui.AnimationLayout
import jugglinglab.ui.AnimationView
import jugglinglab.jml.JMLPattern
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.event.*
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.swing.JPanel
import javax.swing.SwingUtilities
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalDensity

class AnimationPanel(
    val state: PatternAnimationState
) : JPanel() {
    // animation panel
    private val composePanel = ComposePanel()

    // associated controller to handle mouse interactions
    private val controller = AnimationController(state)

    var message: String? = null
    private var catchClip: Clip? = null
    private var bounceClip: Clip? = null

    init {
        layout = BorderLayout()
        add(composePanel, BorderLayout.CENTER)

        composePanel.setContent {
            BoxWithConstraints {
                val widthPx = constraints.maxWidth
                val heightPx = constraints.maxHeight
                val density = LocalDensity.current.density

                // Calculate logical dimensions for the layout
                val width = (widthPx / density).toInt()
                val height = (heightPx / density).toInt()
                val layout = AnimationLayout(state, width, height)

                AnimationView(
                    layout = layout,
                    state = state,
                    onPress = { offset -> controller.handlePress(offset) },
                    onDrag = { offset -> controller.handleDrag(offset) },
                    onRelease = { controller.handleRelease() },
                    onEnter = { controller.handleEnter() },
                    onExit = { controller.handleExit() },
                    onLayoutUpdate = { layout -> controller.updateLayout(layout) },
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
        controller.restartJuggle(pat, newjc, coldRestart)
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun restartJuggle() {
        controller.restartJuggle()
    }

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
    // Mouse interaction callbacks
    //--------------------------------------------------------------------------

    // optional callbacks for when camera angle is changed and when mouse is
    // clicked w/o dragging; used by SelectionView

    var onCameraChange: ((List<Double>) -> Unit)?
        get() = controller.onCameraChange
        set(value) {
            controller.onCameraChange = value
        }

    var onSimpleMouseClick: (() -> Unit)?
        get() = controller.onSimpleMouseClick
        set(value) {
            controller.onSimpleMouseClick = value
        }
}
