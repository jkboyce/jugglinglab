//
// SimpleView.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.PatternAnimationState
import jugglinglab.ui.AnimationPanel
import jugglinglab.ui.PatternWindow
import jugglinglab.jml.JmlPattern
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.Dimension

class SimpleView(
    state: PatternAnimationState,
    patternWindow: PatternWindow
) : View(state, patternWindow) {
    private val ja = AnimationPanel(state)

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

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView() {
        ja.restartJuggle()
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
