//
// SelectionView.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.PatternAnimationState
import jugglinglab.ui.AnimationPanel
import jugglinglab.ui.PatternWindow
import jugglinglab.jml.JmlPattern
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import java.awt.*
import java.awt.event.*
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.math.min

class SelectionView(
    state: PatternAnimationState,
    patternWindow: PatternWindow
) : View(state, patternWindow) {
    private val ja = List(COUNT) {
        AnimationPanel(
            if (it == CENTER) state else PatternAnimationState(state.pattern, state.prefs),
            onZoom = onZoomChange
        )
    }
    private val layered: JLayeredPane
    private val mutator: Mutator
    private var savedPrefs: AnimationPrefs? = null

    init {
        // JLayeredPane on the left so we can show a grid of animations with an
        // overlay drawn on top
        layered = makeLayeredPane(
            Dimension(state.prefs.width, state.prefs.height),
            makeAnimationGrid(),
            makeOverlay()
        )
        mutator = Mutator()

        val gb = GridBagLayout()
        setLayout(gb)

        add(layered)
        var gbc = GridBagConstraints()
        gbc.anchor = GridBagConstraints.LINE_START
        gbc.fill = GridBagConstraints.BOTH
        gbc.gridwidth = 1
        gbc.gridheight = gbc.gridwidth
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weighty = 1.0
        gbc.weightx = gbc.weighty
        gb.setConstraints(layered, gbc)

        val controls = mutator.controlPanel
        add(controls)
        gbc = GridBagConstraints()
        gbc.anchor = GridBagConstraints.FIRST_LINE_START
        gbc.fill = GridBagConstraints.NONE
        gbc.gridwidth = 1
        gbc.gridheight = gbc.gridwidth
        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.weighty = 1.0
        gb.setConstraints(controls, gbc)
    }

    private fun makeAnimationGrid(): JPanel {
        val grid = JPanel(GridLayout(ROWS, COLUMNS))
        for ((index, ap) in ja.withIndex()) {
            ap.state.update(cameraAngle = ap.state.initialCameraAngle())
            grid.add(ap)

            ap.onCameraChange = { ca ->
                for (i in 0..<COUNT) {
                    if (ap !== ja[i]) {
                        ja[i].state.update(cameraAngle = ca)
                    }
                }
            }

            ap.onSimpleMouseClick = {
                try {
                    restartView(ja[index].state.pattern, null)
                    if (index != CENTER) {
                        ja[CENTER].state.addCurrentToUndoList()
                    }
                } catch (jeu: JuggleExceptionUser) {
                    jlHandleUserException(parent, jeu.message)
                } catch (jei: JuggleExceptionInternal) {
                    jlHandleFatalException(jei)
                }
            }
        }
        
        grid.setOpaque(true)
        return grid
    }

    private fun makeOverlay(): JPanel {
        val overlay =
            object : JPanel() {
                public override fun paintComponent(g: Graphics) {
                    val d = size
                    val xleft: Int = (d.width * ((COLUMNS - 1) / 2)) / COLUMNS
                    val ytop: Int = (d.height * ((ROWS - 1) / 2)) / ROWS
                    val width: Int = d.width / COLUMNS
                    val height: Int = d.height / ROWS

                    val g2 = g.create() as Graphics2D
                    g2.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f)
                    g2.color = Color.lightGray
                    g2.drawRect(xleft, ytop, width, height)
                    g2.dispose()
                }
            }
        overlay.setOpaque(false)
        return overlay
    }

    private fun makeLayeredPane(d: Dimension, grid: JPanel, overlay: JPanel): JLayeredPane {
        val layered = JLayeredPane()
        layered.setLayout(null)
        layered.add(grid, JLayeredPane.DEFAULT_LAYER)
        layered.add(overlay, JLayeredPane.PALETTE_LAYER)

        // JLayeredPane has no layout manager, so we have to "manually"
        // arrange the components inside when its size changes. This will cause
        // the grid's GridLayout to lay out each individual animation panel.
        layered.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    val d = layered.size
                    grid.setBounds(0, 0, d.width, d.height)
                    overlay.setBounds(0, 0, d.width, d.height)
                    this@SelectionView.validate()
                }
            })

        // ensure the entire grid fits on the screen, rescaling if needed
        var prefWidth: Int = COLUMNS * d.width
        var prefHeight: Int = ROWS * d.height

        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val maxWidth = screenSize.width - 300 // allocation for controls etc.
        val maxHeight = screenSize.height - 120

        if (prefWidth > maxWidth || prefHeight > maxHeight) {
            val scale = min(
                maxWidth.toDouble() / prefWidth.toDouble(), maxHeight.toDouble() / prefHeight.toDouble()
            )
            prefWidth = (scale * prefWidth).toInt()
            prefHeight = (scale * prefHeight).toInt()
        }
        layered.preferredSize = Dimension(prefWidth, prefHeight)
        // set initial positions of children, since there is no layout manager
        // see https://docs.oracle.com/javase/tutorial/uiswing/layout/none.html
        grid.setBounds(0, 0, prefWidth, prefHeight)
        overlay.setBounds(0, 0, prefWidth, prefHeight)
        return layered
    }

    //--------------------------------------------------------------------------
    // View methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView(pattern: JmlPattern?, prefs: AnimationPrefs?, coldRestart: Boolean) {
        val sizeChanged = (prefs != null && (prefs.width != state.prefs.width || prefs.height != state.prefs.height))

        var newPrefs: AnimationPrefs? = null
        if (prefs != null) {
            savedPrefs = prefs
            // disable startPause for grid of animations
            newPrefs = prefs.copy(startPaused = false)
        }

        ja[CENTER].restartJuggle(pattern, newPrefs, coldRestart)
        for (i in 0..<COUNT) {
            if (i != CENTER) {
                val newp = if (pattern == null) null else mutator.mutatePattern(pattern)
                ja[i].restartJuggle(newp, newPrefs, coldRestart)
            }
        }

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
        get() = ja[CENTER].getSize(Dimension())

    override fun setAnimationPanelPreferredSize(d: Dimension) {
        // This works differently for this view since the JLayeredPane has no
        // layout manager, so preferred size info can't propagate up from the
        // individual animation panels. So we go the other direction: set a
        // preferred size for the overall JLayeredPane, which gets propagated to
        // the grid (and the individual animations) by the ComponentListener above.
        val width: Int = COLUMNS * d.width
        val height: Int = ROWS * d.height
        layered.preferredSize = Dimension(width, height)
    }

    override var zoom: Double
        get() = ja[CENTER].state.zoom
        set(z) {
            for (ap in ja) {
                ap.state.update(zoom = z)
            }
        }

    override fun disposeView() {
        for (ap in ja) {
            ap.disposeAnimation()
        }
    }

    companion object {
        private const val ROWS: Int = 3
        private const val COLUMNS: Int = 3
        private const val COUNT: Int = ROWS * COLUMNS
        private const val CENTER: Int = (COUNT - 1) / 2
    }
}
