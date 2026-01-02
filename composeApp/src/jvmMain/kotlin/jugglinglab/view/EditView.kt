//
// EditView.kt
//
// This view provides the ability to edit a pattern visually. It features a
// ladder diagram on the right and an animator on the left.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.core.*
import jugglinglab.jml.JMLPattern
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.io.File
import java.util.*
import javax.swing.JSplitPane
import javax.swing.border.EmptyBorder

class EditView(
    state: PatternAnimationState,
    patternWindow: PatternWindow
) : View(state, patternWindow) {
    private val ap: AnimationPanel = AnimationPanel(state)
    private val ladder = LadderDiagram(state, patternWindow)
    private val jsp: JSplitPane

    init {
        ap.preferredSize = Dimension(state.prefs.width, state.prefs.height)
        ap.minimumSize = Dimension(10, 10)

        val loc = Locale.getDefault()
        if (ComponentOrientation.getOrientation(loc) == ComponentOrientation.LEFT_TO_RIGHT) {
            jsp = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ap, ladder)
            jsp.setResizeWeight(1.0)
        } else {
            jsp = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ladder, ap)
            jsp.setResizeWeight(0.0)
        }
        jsp.setBorder(EmptyBorder(0, 0, 0, 0))
        jsp.setBackground(Color.white)

        setBackground(Color.white)
        setLayout(BorderLayout())
        add(jsp, BorderLayout.CENTER)
    }

    //--------------------------------------------------------------------------
    // View methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView(pattern: JMLPattern?, prefs: AnimationPrefs?) {
        ap.restartJuggle(pattern, prefs)
        setAnimationPanelPreferredSize(
            Dimension(state.prefs.width, state.prefs.height))

        if (pattern == null) return

        val changingJugglers =
            (pattern.numberOfJugglers != state.pattern.numberOfJugglers)
        if (changingJugglers && parent != null) {
            // the next line gets the JSplitPane divider to reset during layout
            jsp.resetToPreferredSizes()
            if (patternWindow.isWindowMaximized) {
                patternWindow.validate()
            } else {
                patternWindow.pack()
            }
        } else {
            ladder.validate() // to make ladder redraw
        }
        patternWindow.setTitle(pattern.title)
        patternWindow.updateColorsMenu()
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView() {
        ap.restartJuggle()
    }

    override val animationPanelSize: Dimension?
        get() = ap.getSize(Dimension())

    override fun setAnimationPanelPreferredSize(d: Dimension) {
        ap.preferredSize = d
        jsp.resetToPreferredSizes()
    }

    override fun disposeView() {
        ap.disposeAnimation()
    }

    override fun writeGIF(f: File) {
        ap.writingGIF = true
        val origpause = state.isPaused
        state.update(isPaused = true)
        jsp.isEnabled = false
        patternWindow.isResizable = false

        val cleanup =
            Runnable {
                ap.writingGIF = false
                state.update(isPaused = origpause)
                jsp.isEnabled = true
                patternWindow.isResizable = true
            }

        GIFWriter(ap, f, cleanup)
    }
}
