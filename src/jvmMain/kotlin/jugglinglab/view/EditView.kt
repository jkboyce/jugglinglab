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
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.border.EmptyBorder

class EditView(dim: Dimension?, pat: JMLPattern) : View() {
    private val ap: AnimationPanel = AnimationEditPanel()
    private val ladderPanel: JPanel
    private val jsp: JSplitPane

    init {
        ap.preferredSize = dim
        ap.minimumSize = Dimension(10, 10)

        ladderPanel = JPanel()
        ladderPanel.setLayout(BorderLayout())
        ladderPanel.setBackground(Color.white)

        // add a ladder diagram now to get dimensions correct; will be replaced in
        // restartView()
        ladderPanel.add(LadderDiagram(pat), BorderLayout.CENTER)

        val loc = Locale.getDefault()
        if (ComponentOrientation.getOrientation(loc) == ComponentOrientation.LEFT_TO_RIGHT) {
            jsp = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ap, ladderPanel)
            jsp.setResizeWeight(1.0)
        } else {
            jsp = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ladderPanel, ap)
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
    override fun restartView(p: JMLPattern?, c: AnimationPrefs?) {
        val changingJugglers =
            (p != null && pattern != null && p.numberOfJugglers != pattern!!.numberOfJugglers)

        ap.restartJuggle(p, c)
        setAnimationPanelPreferredSize(animationPrefs.size)

        if (p == null) {
            return
        }

        val newLadder = if (ap is AnimationEditPanel) {
            EditLadderDiagram(p, patternWindow, this)
        } else {
            LadderDiagram(p)
        }
        newLadder.setAnimationPanel(ap)

        ap.removeAllAttachments()
        ap.addAnimationAttachment(newLadder)

        val ap2 = ap
        if (ap2 is AnimationEditPanel) {
            ap2.deactivateEvent()
            ap2.deactivatePosition()
        }

        ladderPanel.removeAll()
        ladderPanel.add(newLadder, BorderLayout.CENTER)

        if (changingJugglers && parent != null) {
            // the next line gets the JSplitPane divider to reset during layout
            jsp.resetToPreferredSizes()

            if (patternWindow != null) {
                val pw2 = patternWindow!!
                if (pw2.isWindowMaximized) {
                    pw2.validate()
                } else {
                    pw2.pack()
                }
            }
        } else {
            ladderPanel.validate() // to make ladder redraw
        }

        patternWindow?.setTitle(p.title)
        patternWindow?.updateColorsMenu()
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

    override val pattern: JMLPattern?
        get() = ap.pattern

    override val animationPrefs: AnimationPrefs
        get() = ap.animationPrefs

    override var zoomLevel: Double
        get() = ap.zoomLevel
        set(z) {
            ap.zoomLevel = z
        }

    override var isPaused: Boolean
        get() = ap.isPaused
        set(pause) {
            if (ap.message == null) {
                ap.isPaused = pause
            }
        }

    override fun disposeView() {
        ap.disposeAnimation()
    }

    override fun writeGIF(f: File) {
        ap.writingGIF = true
        val origpause = isPaused
        isPaused = true
        jsp.isEnabled = false
        patternWindow?.isResizable = false

        val cleanup =
            Runnable {
                ap.writingGIF = false
                isPaused = origpause
                jsp.isEnabled = true
                patternWindow?.isResizable = true
            }

        GIFWriter(ap, f, cleanup)
    }
}
