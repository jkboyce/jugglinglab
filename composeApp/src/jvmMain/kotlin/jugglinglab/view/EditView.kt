//
// EditView.kt
//
// This view provides the ability to edit a pattern visually. It features a
// ladder diagram on the right and an animator on the left.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.core.*
import jugglinglab.jml.JmlPattern
import jugglinglab.ui.AnimationPanel
import jugglinglab.ui.LadderDiagramPanel
import jugglinglab.ui.PatternWindow
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.BorderLayout
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.util.Locale
import javax.swing.JSplitPane
import javax.swing.border.EmptyBorder

class EditView(
    state: PatternAnimationState,
    patternWindow: PatternWindow
) : View(state, patternWindow) {
    private val ap = AnimationPanel(state)
    private val ladder = LadderDiagramPanel(state, patternWindow)
    private val jsp: JSplitPane

    init {
        ap.preferredSize =  if (patternWindow.isWindowMaximized) {
            // leave enough room for preferred width of ladder; layout
            // will expand the animator dimensions to fit
            Dimension(patternWindow.width * 3 / 4, 50)
        } else {
            Dimension(state.prefs.width, state.prefs.height)
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
        val sizeChanged = (prefs != null && (prefs.width != state.prefs.width || prefs.height != state.prefs.height))

        ap.restartJuggle(pattern, prefs, coldRestart)
        if (sizeChanged) {
            // The containing window will do a layout (validate() or pack()) in
            // PatternWindow.doMenuCommand(MenuCommand.VIEW_ANIMPREFS). Before
            // that, here we set the panels' preferred sizes so the layout
            // manager will allocate the right amount of space.
            setAnimationPanelPreferredSize(
                Dimension(state.prefs.width, state.prefs.height)
            )
            ladder.preferredSize = Dimension(ladder.size.width, state.prefs.height)

            // This makes the JSplitPane divider reset during layout
            jsp.resetToPreferredSizes()
        }
        if (pattern != null) {
            patternWindow.setTitle(pattern.title)
            patternWindow.updateColorsMenu()
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView() {
        ap.restartJuggle()
    }

    override val animationPanelSize: Dimension?
        get() = ap.getSize(Dimension())

    override fun setAnimationPanelPreferredSize(d: Dimension) {
        ap.preferredSize = d
    }

    override fun disposeView() {
        ap.disposeAnimation()
    }
}
