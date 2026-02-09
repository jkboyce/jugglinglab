//
// SimpleView.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.view

import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.ui.AnimationPanel
import org.jugglinglab.ui.PatternWindow
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.Dimension

class SimpleView(
    state: PatternAnimationState,
    patternWindow: PatternWindow
) : View(state, patternWindow) {
    private val ja = AnimationPanel(state, onZoom = onZoomChange)

    init {
        ja.preferredSize = Dimension(state.prefs.width, state.prefs.height)
        ja.minimumSize = Dimension(50, 50)
        setLayout(BorderLayout())
        add(ja, BorderLayout.CENTER)
    }

    //--------------------------------------------------------------------------
    // View methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView(pattern: JmlPattern?, prefs: AnimationPrefs?, coldRestart: Boolean) {
        val sizeChanged = (prefs != null && (prefs.width != state.prefs.width || prefs.height != state.prefs.height))

        ja.restartJuggle(pattern, prefs, coldRestart)
        if (sizeChanged) {
            setAnimationPanelPreferredSize(
                Dimension(state.prefs.width, state.prefs.height)
            )
        }
        if (pattern != null) {
            patternWindow.setTitle(pattern.title)
            patternWindow.updateColorsMenu()
        }
    }

    override val animationPanelSize: Dimension?
        get() = ja.getSize(Dimension())

    override fun setAnimationPanelPreferredSize(d: Dimension) {
        ja.preferredSize = d
    }

    override fun disposeView() {
        ja.disposeAnimation()
    }
}
