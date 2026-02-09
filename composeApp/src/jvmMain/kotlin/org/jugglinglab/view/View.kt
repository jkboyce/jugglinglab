//
// View.kt
//
// This class represents the entire displayed contents of a PatternWindow.
// Subclasses of this are used to show different pattern views, which the
// user can select from a menu on the pattern window.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.view

import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.ui.PatternWindow
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.AnimationGifWriter
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import java.awt.Dimension
import java.io.File
import javax.swing.JPanel
import kotlin.math.abs

abstract class View(
    val state: PatternAnimationState,
    val patternWindow: PatternWindow
) : JPanel() {
    //--------------------------------------------------------------------------
    // Methods to handle undo/redo functionality. The state owns the undo list
    // so it's preserved when we switch views. Here are the methods to apply
    // Undo/Redo to the current view.
    //--------------------------------------------------------------------------

    // Undo to the previous save state.

    @Throws(JuggleExceptionInternal::class)
    fun undoEdit() {
        if (state.undoIndex == 0)
            return
        try {
            --state.undoIndex
            restartView(state.undoList[state.undoIndex], null, coldRestart = false)
            if (state.undoIndex == 0 || state.undoIndex == state.undoList.size - 2) {
                patternWindow.updateUndoMenu()
            }
        } catch (jeu: JuggleExceptionUser) {
            // pattern was animated before so user error should not occur
            throw JuggleExceptionInternal(jeu.message ?: "")
        }
    }

    // Redo to the next save state.

    @Throws(JuggleExceptionInternal::class)
    fun redoEdit() {
        if (state.undoIndex == state.undoList.size - 1)
            return
        try {
            ++state.undoIndex
            restartView(state.undoList[state.undoIndex], null, coldRestart = false)
            if (state.undoIndex == 1 || state.undoIndex == state.undoList.size - 1) {
                patternWindow.updateUndoMenu()
            }
        } catch (jeu: JuggleExceptionUser) {
            // pattern was animated before so user error should not occur
            throw JuggleExceptionInternal(jeu.message ?: "")
        }
    }

    //--------------------------------------------------------------------------
    // Abstract methods for subclasses to define
    //--------------------------------------------------------------------------

    // restart view with a new pattern and/or preferences
    //
    // note:
    // - a null argument means no update for that item
    // - this method is responsible for setting preferred sizes of all UI
    //   elements, since it may be followed by layout
    // - 'coldRestart = true' resets camera angle, zoom, and prop assignments
    //
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun restartView(
        pattern: JmlPattern? = null,
        prefs: AnimationPrefs? = null,
        coldRestart: Boolean = true
    )

    // size of just the juggler animation, not any extra elements
    abstract val animationPanelSize: Dimension?

    abstract fun setAnimationPanelPreferredSize(d: Dimension)

    // control zoom at the View level because of SelectionView
    open var zoom: Double
        get() = state.zoom
        set(z) = state.update(zoom = z)

    abstract fun disposeView()

    //--------------------------------------------------------------------------
    // Saving animated GIFs
    //--------------------------------------------------------------------------

    fun writeGif(f: File) {
        var prefs = state.prefs
        if (prefs.fps == AnimationPrefs.FPS_DEF) {
            prefs = prefs.copy(fps = 33.3)  // default frames per sec for GIFs
            // Note the GIF header specifies inter-frame delay in terms of
            // hundredths of a second, so only `fps` values like 50, 33 1/3,
            // 25, 20, ... are precisely achievable.
        }
        val gifState = PatternAnimationState(state.pattern, prefs).apply {
            cameraAngle = state.cameraAngle
            zoom = state.zoom
        }
        AnimationGifWriter(gifState, f, patternWindow, null)
    }

    //--------------------------------------------------------------------------
    // Callback for zooming in/out
    //--------------------------------------------------------------------------

    val onZoomChange: (Float) -> Unit = { delta ->
        val zoomFactor = (PatternWindow.Companion.ZOOM_PER_STEP - 1.0) * abs(delta) + 1.0
        if (delta > 0) {
            if (zoom < PatternWindow.Companion.MAX_ZOOM / zoomFactor) {
                zoom *= zoomFactor
            }
        } else if (delta < 0) {
            if (zoom > PatternWindow.Companion.MIN_ZOOM * zoomFactor) {
                zoom /= zoomFactor
            }
        }
    }
}
