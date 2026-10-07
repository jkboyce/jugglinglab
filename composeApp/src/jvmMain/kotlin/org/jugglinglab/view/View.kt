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
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.ui.common.AnimationController.Companion.MAX_ZOOM
import org.jugglinglab.ui.common.AnimationController.Companion.MIN_ZOOM
import org.jugglinglab.ui.desktop.PatternWindow
import org.jugglinglab.util.AnimationGifWriter
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import java.awt.Dimension
import java.io.File
import javax.swing.JPanel

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
            restartView(pattern = state.undoList[state.undoIndex], coldRestart = false)
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
            restartView(pattern = state.undoList[state.undoIndex], coldRestart = false)
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

    // control zoom at the View level because of SelectionView
    open var zoom: Double
        get() = state.zoom
        set(z) = state.update(zoom = z)

    // AnimationView callback for zooming in/out
    val onViewZoomChange: (Float) -> Unit = { zoomFactor ->
        zoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    companion object {
        const val DEFAULT_ANIMATION_WIDTH: Int = 400
        const val DEFAULT_ANIMATION_HEIGHT: Int = 450
        const val DEFAULT_GIF_FPS: Double = 33.3
    }

    //--------------------------------------------------------------------------
    // Saving animated GIFs
    //--------------------------------------------------------------------------

    fun writeGif(
        file: File,
        width: Int,
        height: Int,
        fps: Double
    ) {
        val gifState = PatternAnimationState(state.pattern, state.prefs).apply {
            cameraAngle = state.cameraAngle
            zoom = state.zoom
        }
        AnimationGifWriter(
            gifState = gifState,
            file = file,
            width = width,
            height = height,
            fps = fps,
            parent = patternWindow,
            cleanup = null
        )
    }
}
