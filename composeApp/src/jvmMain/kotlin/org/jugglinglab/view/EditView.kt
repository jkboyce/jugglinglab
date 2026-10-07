//
// EditView.kt
//
// This view provides the ability to edit a pattern visually. It features a
// ladder diagram on the right and an animator on the left.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.view

import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.ui.desktop.AnimationPanel
import org.jugglinglab.ui.desktop.LadderDiagramPanel
import org.jugglinglab.ui.desktop.PatternWindow
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.PatternAnimationState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.util.Locale
import javax.swing.JSplitPane
import javax.swing.border.EmptyBorder

class EditView(
    state: PatternAnimationState,
    patternWindow: PatternWindow,
    initialAnimationSize: Dimension
) : View(state, patternWindow) {
    private val ap = AnimationPanel(state, onZoom = onViewZoomChange)
    private val ladder = LadderDiagramPanel(state, patternWindow)
    private val jsp: JSplitPane

    init {
        ap.preferredSize = if (patternWindow.isWindowMaximized) {
            // leave enough room for preferred width of ladder; layout
            // will expand the animator dimensions to fit
            Dimension(patternWindow.width / 4, 50)
        } else {
            initialAnimationSize
        }
        ap.minimumSize = Dimension(50, 50)

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
    override fun restartView(pattern: JmlPattern?, prefs: AnimationPrefs?, coldRestart: Boolean) {
        ap.restartJuggle(pattern, prefs, coldRestart)
        if (pattern != null) {
            patternWindow.setTitle(pattern.title)
            patternWindow.updateColorsMenu()
        }
    }

    override val animationPanelSize: Dimension?
        get() = ap.getSize(Dimension())
}
