//
// PatternView.kt
//
// This view provides the ability to edit the text representation of a pattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.AnimationPanel
import jugglinglab.core.AnimationPrefs
import jugglinglab.jml.JMLPattern
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlConstraints
import jugglinglab.util.getStringResource
import java.awt.*
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PatternView(dim: Dimension?) : View(), DocumentListener {
    private val ja: AnimationPanel = AnimationPanel()
    private lateinit var jsp: JSplitPane
    private lateinit var rbBp: JRadioButton
    private var bpEditedIcon: JLabel? = null
    private lateinit var rbJml: JRadioButton
    private lateinit var ta: JTextArea
    private lateinit var compile: JButton
    private lateinit var revert: JButton
    private lateinit var lab: JLabel
    private var textEdited: Boolean = false

    init {
        makePanel(dim)
        updateButtons()
    }

    private fun makePanel(dim: Dimension?) {
        setLayout(BorderLayout())

        // animator on the left
        ja.preferredSize = dim
        ja.minimumSize = Dimension(10, 10)

        // controls panel on the right
        val controls = JPanel()
        val gb = GridBagLayout()
        controls.setLayout(gb)

        val labView = JLabel(getStringResource(Res.string.gui_patternview_heading))
        gb.setConstraints(
            labView,
            jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(15, 4, 10, 0))
        )
        controls.add(labView)

        val bg = ButtonGroup()
        val bppanel = JPanel()
        bppanel.setLayout(FlowLayout(FlowLayout.LEFT, 0, 0))
        rbBp = JRadioButton(getStringResource(Res.string.gui_patternview_rb1_default))
        bg.add(rbBp)
        bppanel.add(rbBp)
        val url = PatternView::class.java.getResource("/alert.png")
        if (url != null) {
            val editedIcon = ImageIcon(url)
            val editedIconScaled =
                ImageIcon(
                    editedIcon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH)
                )
            bpEditedIcon = JLabel(editedIconScaled)
            bpEditedIcon?.setToolTipText(getStringResource(Res.string.gui_patternview_alert))
            bppanel.add(Box.createHorizontalStrut(10))
            bppanel.add(bpEditedIcon)
        }
        controls.add(bppanel)
        gb.setConstraints(
            bppanel, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 4, 0, 0))
        )

        rbJml = JRadioButton(getStringResource(Res.string.gui_patternview_rb2))
        bg.add(rbJml)
        controls.add(rbJml)
        gb.setConstraints(
            rbJml, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 4, 0, 0))
        )

        ta = JTextArea()
        val jscroll = JScrollPane(ta)
        jscroll.preferredSize = Dimension(400, 1)
        jscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS)
        jscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS)
        controls.add(jscroll)
        val gbc =
            jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(15, 0, 0, 0))
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        gbc.weightx = gbc.weighty
        gb.setConstraints(jscroll, gbc)

        // split pane dividing the two
        jsp = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ja, controls)
        jsp.setResizeWeight(0.75) // % extra space allocated to left (animation) side

        add(jsp, BorderLayout.CENTER)

        // button + error message label across the bottom
        val lower = JPanel()
        val gb2 = GridBagLayout()
        lower.setLayout(gb2)
        compile = JButton(getStringResource(Res.string.gui_patternview_compile_button))
        gb2.setConstraints(
            compile, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(8, 8, 8, 0))
        )
        lower.add(compile)
        revert = JButton(getStringResource(Res.string.gui_patternview_revert_button))
        gb2.setConstraints(
            revert, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(8, 5, 8, 12))
        )
        lower.add(revert)
        lab = JLabel(" ")
        val gbc2 = jlConstraints(GridBagConstraints.LINE_START, 2, 0)
        gbc2.fill = GridBagConstraints.HORIZONTAL
        gbc2.weightx = 1.0
        gb2.setConstraints(lab, gbc2)
        lower.add(lab)

        add(lower, BorderLayout.PAGE_END)

        // add actions to the various items
        ta.document.addDocumentListener(this)
        rbBp.addActionListener { _: ActionEvent? -> reloadTextArea() }
        rbJml.addActionListener { _: ActionEvent? -> reloadTextArea() }
        compile.addActionListener { _: ActionEvent? -> compilePattern() }
        revert.addActionListener { _: ActionEvent? -> revertPattern() }
    }

    // Update the button configs when a radio button is pressed, the base
    // pattern or JML pattern changes, or we start/stop writing an animated GIF.
    private fun updateButtons() {
        if (ja.writingGIF) {
            // writing a GIF
            rbBp.setEnabled(false)
            rbJml.setEnabled(false)
            compile.setEnabled(false)
            revert.setEnabled(false)
            return
        }

        val pat = pattern

        if (pat == null) {
            rbBp.setEnabled(false)
            bpEditedIcon?.isVisible = false
            rbJml.setEnabled(false)
        } else if (pat.basePatternNotation == null || pat.basePatternConfig == null) {
            // no base pattern set
            rbBp.setEnabled(false)
            bpEditedIcon?.isVisible = false
            rbJml.setEnabled(true)
            rbJml.setSelected(true)
        } else {
            rbBp.setEnabled(true)
            bpEditedIcon?.isVisible = pat.isBasePatternEdited
            rbJml.setEnabled(true)
        }

        if (rbBp.isSelected) {
            compile.setEnabled(pat != null && (pat.isBasePatternEdited || textEdited))
            revert.setEnabled(textEdited)
        } else if (rbJml.isSelected) {
            compile.setEnabled(textEdited)
            revert.setEnabled(textEdited)
        }
    }

    // (Re)load the text in the JTextArea from the pattern, overwriting anything
    // that was there.
    private fun reloadTextArea() {
        if (rbBp.isSelected) {
            ta.text = pattern!!.basePatternConfig!!.replace(";", ";\n")
        } else if (rbJml.isSelected) {
            ta.text = pattern.toString()
        }

        ta.setCaretPosition(0)
        lab.setText(" ")
        setTextEdited(false)

        // Note the above always triggers an updateButtons() call, since
        // text_edited is cycled from true (from the setText() calls) to false.
    }

    private fun setTextEdited(edited: Boolean) {
        if (textEdited != edited) {
            textEdited = edited
            updateButtons()
        }
    }

    private fun compilePattern() {
        try {
            if (rbBp.isSelected) {
                val notation = pattern!!.basePatternNotation!!
                val config = ta.getText().replace("\n", "").trim { it <= ' ' }
                val newpat = JMLPattern.fromBasePattern(notation, config)
                restartView(newpat, null)
                addToUndoList(newpat)
            } else if (rbJml.isSelected) {
                val newpat = JMLPattern(ta.getText())
                restartView(newpat, null)
                addToUndoList(newpat)
            }
        } catch (jeu: JuggleExceptionUser) {
            lab.setText(jeu.message)
            setTextEdited(true)
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
            setTextEdited(true)
        }
    }

    private fun revertPattern() {
        reloadTextArea()
    }

    //--------------------------------------------------------------------------
    // View methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun restartView(p: JMLPattern?, c: AnimationPrefs?) {
        ja.restartJuggle(p, c)
        setAnimationPanelPreferredSize(
            Dimension(animationPrefs.size.width, animationPrefs.size.height))

        if (p != null) {
            val notation = p.basePatternNotation
            val message = getStringResource(Res.string.gui_patternview_rb1, "none set")
            rbBp.setText(message)

            if (!(rbBp.isSelected || rbJml.isSelected)) {
                if (notation == null) {
                    rbJml.setSelected(true)
                } else {
                    rbBp.setSelected(true)
                }
            }

            updateButtons()
            reloadTextArea()
            patternWindow?.setTitle(p.title)
            patternWindow?.updateColorsMenu()
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
        jsp.resetToPreferredSizes()
    }

    override val pattern: JMLPattern?
        get() = ja.pattern

    override val animationPrefs: AnimationPrefs
        get() = ja.animationPrefs

    override var zoomLevel: Double
        get() = ja.zoomLevel
        set(z) {
            ja.zoomLevel = z
        }

    override var isPaused: Boolean
        get() = ja.isPaused
        set(pause) {
            if (ja.message == null) {
                ja.isPaused = pause
            }
        }

    override fun disposeView() {
        ja.disposeAnimation()
    }

    override fun writeGIF(f: File) {
        ja.writingGIF = true
        updateButtons()
        val origpause = isPaused
        isPaused = true
        jsp.isEnabled = false
        patternWindow?.isResizable = false

        val cleanup =
            Runnable {
                ja.writingGIF = false
                isPaused = origpause
                updateButtons()
                jsp.isEnabled = true
                patternWindow?.isResizable = true
            }

        GIFWriter(ja, f, cleanup)
    }

    //--------------------------------------------------------------------------
    // javax.swing.event.DocumentListener methods
    //--------------------------------------------------------------------------

    override fun insertUpdate(e: DocumentEvent?) {
        setTextEdited(true)
    }

    override fun removeUpdate(e: DocumentEvent?) {
        setTextEdited(true)
    }

    override fun changedUpdate(e: DocumentEvent?) {
        setTextEdited(true)
    }
}
