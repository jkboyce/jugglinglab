//
// AnimationPanel.kt
//
// This class creates the juggling animation on screen. It embeds a Composable
// AnimationView which handles rendering. It also interprets mouse
// interactions such as camera drag and click to pause, and supports
// interactions with on-screen representations of JML events and positions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.ui.common.AnimationController
import org.jugglinglab.ui.common.AnimationView
import org.jugglinglab.util.jlHandleFatalException
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.event.*
import javax.swing.JPanel
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color

class AnimationPanel(
    val state: PatternAnimationState,
    val onZoom: (Float) -> Unit = {}
) : JPanel() {
    // animation panel
    private val composePanel = ComposePanel()

    // associated controller to handle mouse interactions
    private val controller = AnimationController(state)

    init {
        layout = BorderLayout()
        add(composePanel, BorderLayout.CENTER)

        composePanel.setContent {
            val colorScheme = lightColorScheme(
                background = Color.White,
                surface = Color.White
            )
            AnimationView(
                state = state,
                colorScheme = colorScheme,
                onPress = controller::handlePress,
                onDrag = controller::handleDrag,
                onRelease = controller::handleRelease,
                onEnter = controller::handleEnter,
                onExit = controller::handleExit,
                onLayoutUpdate = controller::updateLayout,
                onZoom = onZoom,
                onError = { jlHandleFatalException(it) }
            )
        }

        initHandlers()
    }

    //--------------------------------------------------------------------------
    // Methods to (re)start the animator
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun restartJuggle(
        pattern: JmlPattern? = null,
        prefs: AnimationPrefs? = null,
        coldRestart: Boolean = true
    ) {
        controller.restartJuggle(pattern, prefs, coldRestart)
    }

    //--------------------------------------------------------------------------
    // Setup / disposal
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    private fun initHandlers() {
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    try {
                        if (state.prefs.width != size.width || state.prefs.height != size.height) {
                            val newPrefs = state.prefs.copy(width = size.width, height = size.height)
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
