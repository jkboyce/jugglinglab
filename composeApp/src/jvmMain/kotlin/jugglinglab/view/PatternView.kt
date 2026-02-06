//
// PatternView.kt
//
// This view provides the ability to edit the text representation of a pattern.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.AnimationPrefs
import jugglinglab.core.PatternAnimationState
import jugglinglab.ui.AnimationPanel
import jugglinglab.ui.PatternWindow
import jugglinglab.jml.JmlPattern
import jugglinglab.jml.PatternBuilder
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlGetImageResource
import jugglinglab.util.jlConstraints
import jugglinglab.util.jlGetStringResource
import androidx.compose.ui.graphics.toAwtImage
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PatternView(
    state: PatternAnimationState,
    patternWindow: PatternWindow
) : View(state, patternWindow), DocumentListener {
    private val ja = AnimationPanel(state, onZoom = onZoomChange)
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
        makePanel(Dimension(state.prefs.width, state.prefs.height))
        updateButtons()
    }

    private fun makePanel(dim: Dimension) {
        setLayout(BorderLayout())

        // animator on the left
        ja.preferredSize = if (patternWindow.isWindowMaximized) {
            Dimension(patternWindow.width * 3 / 4, 50)
        } else {
            dim
        }
        ja.minimumSize = Dimension(50, 50)

        // controls panel on the right
        val viewLabel = JLabel(jlGetStringResource(Res.string.gui_patternview_heading))

        rbBp = JRadioButton(jlGetStringResource(Res.string.gui_patternview_rb1_default))
        bpEditedIcon = run {
            val composeImage = jlGetImageResource("alert.png")
            val editedIcon = ImageIcon(composeImage.toAwtImage(), "alert.png")
            val editedIconScaled =
                ImageIcon(
                    editedIcon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH)
                )
            JLabel(editedIconScaled).apply {
                setToolTipText(jlGetStringResource(Res.string.gui_patternview_alert))
            }
        }
        val bppanel = JPanel().apply {
            setLayout(FlowLayout(FlowLayout.LEFT, 0, 0))
            add(rbBp)
            add(Box.createHorizontalStrut(10))
            add(bpEditedIcon)
        }

        rbJml = JRadioButton(jlGetStringResource(Res.string.gui_patternview_rb2))
        ButtonGroup().apply {
            add(rbBp)
            add(rbJml)
        }

        ta = JTextArea()
        val jscroll = JScrollPane(ta).apply {
            preferredSize = Dimension(400, 1)
            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS)
            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS)
        }

        val gb = GridBagLayout().apply {
            setConstraints(viewLabel, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(15, 4, 10, 0)))
            setConstraints(bppanel, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 4, 0, 0)))
            setConstraints(rbJml, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 4, 0, 0)))
            setConstraints(jscroll, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(15, 0, 0, 0)).apply {
                fill = GridBagConstraints.BOTH
                weighty = 1.0
                weightx = 1.0
            })
        }
        val controls = JPanel().apply {
            setLayout(gb)
            add(viewLabel)
            add(bppanel)
            add(rbJml)
            add(jscroll)
        }

        // split pane dividing the two
        jsp = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ja, controls)
        jsp.setResizeWeight(0.0) // % extra space allocated to left (animation) side
        add(jsp, BorderLayout.CENTER)

        // button + error message label across the bottom
        compile = JButton(jlGetStringResource(Res.string.gui_patternview_compile_button))
        revert = JButton(jlGetStringResource(Res.string.gui_patternview_revert_button))
        lab = JLabel(" ")
        val gb2 = GridBagLayout().apply {
            setConstraints(compile, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(8, 8, 8, 0)))
            setConstraints(revert, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(8, 5, 8, 12)))
            setConstraints(lab, jlConstraints(GridBagConstraints.LINE_START, 2, 0).apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            })
        }
        val lower = JPanel().apply {
            setLayout(gb2)
            add(compile)
            add(revert)
            add(lab)
        }

        add(lower, BorderLayout.PAGE_END)

        // add actions to the various items
        ta.document.addDocumentListener(this)
        rbBp.addActionListener { _: ActionEvent? -> reloadTextArea() }
        rbJml.addActionListener { _: ActionEvent? -> reloadTextArea() }
        compile.addActionListener { _: ActionEvent? -> compilePattern() }
        revert.addActionListener { _: ActionEvent? -> revertPattern() }
    }

    // Update the button configs when a radio button is pressed or the base
    // pattern or JML pattern changes.
    private fun updateButtons() {
        if (!state.pattern.hasBasePattern) {
            // no base pattern set
            rbBp.setEnabled(false)
            bpEditedIcon?.isVisible = false
            rbJml.setEnabled(true)
            rbJml.setSelected(true)
        } else {
            rbBp.setEnabled(true)
            bpEditedIcon?.isVisible = state.pattern.isBasePatternEdited
            rbJml.setEnabled(true)
        }

        if (rbBp.isSelected) {
            compile.setEnabled(state.pattern.isBasePatternEdited || textEdited)
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
            ta.text = state.pattern.basePatternConfig!!.replace(";", ";\n")
        } else if (rbJml.isSelected) {
            ta.text = state.pattern.toString()
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
                val notation = state.pattern.basePatternNotation!!
                val config = ta.getText().replace("\n", "").trim { it <= ' ' }
                val newpat = JmlPattern.fromBasePattern(notation, config)
                restartView(newpat, null)
                state.addCurrentToUndoList()
            } else if (rbJml.isSelected) {
                val newpat = JmlPattern.fromJmlString(ta.getText())
                // set the title in the base pattern
                val record = PatternBuilder.fromJmlPattern(newpat)
                record.setTitleString(newpat.title)
                val newpat2 = JmlPattern.fromPatternBuilder(record)
                restartView(newpat2, null)
                state.addCurrentToUndoList()
            }
        } catch (jeu: JuggleExceptionUser) {
            val errorString = jlGetStringResource(Res.string.error)
            lab.setText("$errorString: " + jeu.message)
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
    override fun restartView(pattern: JmlPattern?, prefs: AnimationPrefs?, coldRestart: Boolean) {
        val sizeChanged = (prefs != null && (prefs.width != state.prefs.width || prefs.height != state.prefs.height))

        ja.restartJuggle(pattern, prefs, coldRestart)
        if (sizeChanged) {
            setAnimationPanelPreferredSize(
                Dimension(state.prefs.width, state.prefs.height)
            )
            jsp.resetToPreferredSizes()
        }
        if (pattern != null) {
            val notation = pattern.basePatternNotation
            val message = if (notation != null) {
                jlGetStringResource(Res.string.gui_patternview_rb1, notation)
            } else {
                jlGetStringResource(Res.string.gui_patternview_rb1_default)
            }
            rbBp.setText(message)

            if (!rbBp.isSelected && !rbJml.isSelected) {
                if (notation == null) {
                    rbJml.setSelected(true)
                } else {
                    rbBp.setSelected(true)
                }
            }

            updateButtons()
            reloadTextArea()
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
