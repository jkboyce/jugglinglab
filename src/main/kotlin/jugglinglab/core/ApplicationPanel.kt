//
// ApplicationPanel.kt
//
// This class represents the entire contents of the ApplicationWindow frame.
// For a given notation type it creates a tabbed pane with separate panels for
// pattern entry, transitions, and generator.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.JugglingLab.guistrings
import jugglinglab.core.PatternWindow.Companion.bringToFront
import jugglinglab.generator.Generator
import jugglinglab.generator.Generator.Companion.newGenerator
import jugglinglab.generator.GeneratorTarget
import jugglinglab.generator.Transitioner
import jugglinglab.generator.Transitioner.Companion.newTransitioner
import jugglinglab.jml.JMLPattern.Companion.fromBasePattern
import jugglinglab.notation.NotationControl
import jugglinglab.notation.Pattern
import jugglinglab.notation.SiteswapNotationControl
import jugglinglab.util.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.view.View
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.ChangeEvent

class ApplicationPanel
    (
    private val parentFrame: JFrame?,
    // fields below are currently unused; they supported the applet version
    // of Juggling Lab
    private val animtarget: View?,
    private val patlist: PatternListPanel?
) : JPanel(), ActionListener {
    private var jtp: JTabbedPane? = null
    private var patlisttab: Boolean = false

    private var currentnum: Int = -1
    private var juggleButton: JButton? = null
    private var transButton: JButton? = null
    private var genButton: JButton? = null
    private var genBusy: JLabel? = null

    constructor(parentFrame: JFrame?) :
        this(parentFrame, animtarget = null, patlist = null)

    override fun actionPerformed(ae: ActionEvent) {
        val command = ae.getActionCommand()
        try {
            if (command.startsWith("notation")) {
                try {
                    val num = command.substring(8).toInt()
                    if (num != currentnum) {
                        setNotation(num)
                        parentFrame?.pack()
                        currentnum = num
                    }
                } catch (_: NumberFormatException) {
                    throw JuggleExceptionInternal("Error in notation number coding")
                }
            }
        } catch (e: Exception) {
            handleFatalException(e)
        }
    }

    // Input is for example Pattern.NOTATION_SITESWAP.

    fun setNotation(num: Int) {
        if (num > Pattern.builtinNotations.size) return
        if (jtp != null) {
            remove(jtp)
        }
        jtp = JTabbedPane()

        when (num) {
            Pattern.NOTATION_SITESWAP -> addPatternEntryControl(SiteswapNotationControl())
        }

        var pl = patlist
        if (pl == null && patlisttab) {
            pl = PatternListPanel(animtarget)
        }

        val trans = newTransitioner(Pattern.builtinNotations[num - 1])
        if (trans != null) {
            addTransitionerControl(trans, pl)
        }

        val gen = newGenerator(Pattern.builtinNotations[num - 1])
        if (gen != null) {
            addGeneratorControl(gen, pl)
        }

        if (pl != null) {
            jtp!!.addTab(guistrings.getString("Pattern_list_tab"), pl)
            if (patlist != null) {
                jtp!!.setSelectedComponent(pl) // if we loaded from a file
            }
        }

        // change the default button when the tab changes
        jtp!!.addChangeListener { _: ChangeEvent? -> rootPane.defaultButton = defaultButton }

        setLayout(BorderLayout())
        add(jtp!!, BorderLayout.CENTER)
        parentFrame?.rootPane?.defaultButton = defaultButton
    }

    private fun addPatternEntryControl(control: NotationControl) {
        val nbut1 = JButton(guistrings.getString("Defaults")).apply {
            addActionListener { _: ActionEvent? ->
                try {
                    control.resetControl()
                } catch (e: Exception) {
                    handleFatalException(e)
                }
            }
        }

        juggleButton =
            JButton(guistrings.getString("Juggle")).apply {
                setDefaultCapable(true)
                addActionListener { _: ActionEvent? ->
                    try {
                        val pl = control.parameterList
                        val p = control.newPattern().fromParameters(pl)
                        val jc = (AnimationPrefs()).fromParameters(pl)
                        pl.errorIfParametersLeft()

                        val notation = p.notationName
                        val config: String = p.toString()
                        val pat = fromBasePattern(notation, config)
                        pat.layoutPattern()

                        if (bringToFront(pat.hashCode)) {
                            return@addActionListener
                        }

                        if (animtarget != null) {
                            animtarget.restartView(pat, jc)
                        } else {
                            PatternWindow(pat.title, pat, jc)
                        }
                    } catch (je: JuggleExceptionUser) {
                        handleUserException(this@ApplicationPanel, je.message)
                    } catch (e: Exception) {
                        handleFatalException(e)
                    }
                }
            }

        val np2 = JPanel().apply {
            setLayout(FlowLayout(FlowLayout.TRAILING))
            add(nbut1)
            add(juggleButton)
        }
        val np1 = JPanel().apply {
            setLayout(BorderLayout())
            add(control, BorderLayout.PAGE_START)
            add(np2, BorderLayout.PAGE_END)
        }

        jtp!!.addTab(guistrings.getString("Pattern_entry"), np1)
    }

    private fun addTransitionerControl(trans: Transitioner, plp: PatternListPanel?) {
        val but1 = JButton(guistrings.getString("Defaults")).apply {
            addActionListener { _: ActionEvent? -> trans.resetTransitionerControl() }
        }

        transButton = JButton(guistrings.getString("Run")).apply {
            setDefaultCapable(true)
            addActionListener {
                val t: Thread =
                    object : Thread() {
                        override fun run() {
                            transButton!!.setEnabled(false)
                            var pw: PatternListWindow? = null
                            try {
                                trans.initTransitioner()
                                val pwot: GeneratorTarget?
                                if (plp != null) {
                                    plp.clearList()
                                    pwot = GeneratorTarget(plp)
                                    // jtp.setSelectedComponent(plp);
                                } else {
                                    val title =
                                        trans.notationName + " " + guistrings.getString("Patterns")
                                    pw = PatternListWindow(title, this)
                                    pwot = GeneratorTarget(pw.patternListPanel)
                                }
                                trans.runTransitioner(pwot, MAX_PATTERNS, MAX_TIME)
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                            } catch (ex: JuggleExceptionDone) {
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                                val parentComponent = pw ?: plp
                                LabelDialog(
                                    parentComponent,
                                    guistrings.getString("Generator_stopped_title"),
                                    ex.message
                                )
                            } catch (_: JuggleExceptionInterrupted) {
                                // System.out.println("generator thread quit");
                            } catch (ex: JuggleExceptionUser) {
                                pw?.dispose()
                                handleUserException(this@ApplicationPanel, ex.message)
                            } catch (e: Exception) {
                                pw?.dispose()
                                handleFatalException(e)
                            }

                            transButton!!.setEnabled(true)
                        }
                    }
                t.start()
            }
        }

        val p2 = JPanel().apply {
            setLayout(FlowLayout(FlowLayout.TRAILING))
            add(but1)
            add(transButton)
        }
        val p3 = JPanel().apply {
            setLayout(BorderLayout())
            add(p2, BorderLayout.LINE_END)
        }
        val p1 = JPanel().apply {
            setLayout(BorderLayout())
            add(trans.transitionerControl, BorderLayout.PAGE_START)
            add(p3, BorderLayout.PAGE_END)
        }

        jtp!!.addTab(guistrings.getString("Transitions"), p1)
    }

    private fun addGeneratorControl(gen: Generator, plp: PatternListPanel?) {
        val but1 = JButton(guistrings.getString("Defaults")).apply {
            addActionListener { _: ActionEvent? -> gen.resetGeneratorControl() }
        }

        genButton = JButton(guistrings.getString("Run")).apply {
            addActionListener {
                val t: Thread =
                    object : Thread() {
                        override fun run() {
                            genBusy!!.isVisible = true
                            genButton!!.setEnabled(false)
                            var pw: PatternListWindow? = null
                            try {
                                gen.initGenerator()
                                val pwot: GeneratorTarget?
                                if (plp != null) {
                                    plp.clearList()
                                    pwot = GeneratorTarget(plp)
                                    // jtp.setSelectedComponent(plp);
                                } else {
                                    val title =
                                        gen.notationName + " " + guistrings.getString("Patterns")
                                    pw = PatternListWindow(title, this)
                                    pwot = GeneratorTarget(pw.patternListPanel)
                                }
                                gen.runGenerator(pwot, MAX_PATTERNS, MAX_TIME)
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                            } catch (ex: JuggleExceptionDone) {
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                                val parentComponent = pw ?: plp
                                LabelDialog(
                                    parentComponent,
                                    guistrings.getString("Generator_stopped_title"),
                                    ex.message
                                )
                            } catch (_: JuggleExceptionInterrupted) {
                                // System.out.println("generator thread quit");
                            } catch (ex: JuggleExceptionUser) {
                                pw?.dispose()
                                handleUserException(this@ApplicationPanel, ex.message)
                            } catch (e: Exception) {
                                pw?.dispose()
                                handleFatalException(e)
                            }

                            genBusy!!.isVisible = false
                            genButton!!.setEnabled(true)
                        }
                    }
                t.start()
            }
        }

        genBusy = JLabel(guistrings.getString("Processing")).apply {
            isVisible = false
        }

        val gb = GridBagLayout().apply {
            setConstraints(
                genBusy, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 10, 0, 0))
            )
        }
        val p4 = JPanel().apply {
            setLayout(gb)
            add(genBusy)
        }
        val p2 = JPanel().apply {
            setLayout(FlowLayout(FlowLayout.TRAILING))
            add(but1)
            add(genButton)
        }
        val p3 = JPanel().apply {
            setLayout(BorderLayout())
            add(p4, BorderLayout.LINE_START)
            add(p2, BorderLayout.LINE_END)
        }
        val p1 = JPanel().apply {
            setLayout(BorderLayout())
            add(gen.generatorControl, BorderLayout.PAGE_START)
            add(p3, BorderLayout.PAGE_END)
        }

        jtp!!.addTab(guistrings.getString("Generator"), p1)
    }

    private val defaultButton: JButton?
        get() {
            if (jtp == null) {
                return null
            }
            return when (jtp!!.selectedIndex) {
                0 -> juggleButton
                1 -> transButton
                else -> genButton
            }
        }

    companion object {
        // execution limits for generator
        private const val MAX_PATTERNS: Int = 1000
        private const val MAX_TIME: Double = 15.0
    }
}
