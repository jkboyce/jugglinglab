//
// ApplicationPanelSwing.kt
//
// Swing UI version of ApplicationPanel.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.PatternWindow.Companion.bringToFront
import jugglinglab.generator.*
import jugglinglab.generator.Generator.Companion.newGenerator
import jugglinglab.generator.Transitioner.Companion.newTransitioner
import jugglinglab.jml.JMLPattern.Companion.fromBasePattern
import jugglinglab.notation.Pattern
import jugglinglab.notation.SiteswapNotationControlSwing
import jugglinglab.util.*
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.view.View
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ChangeEvent

class ApplicationPanelSwing
    (
    private val parentFrame: JFrame?,
    // fields below are currently unused; they supported the applet version
    // of Juggling Lab
    private val animtarget: View?,
    private val patlist: PatternListPanel?
) : ApplicationPanel(parentFrame, animtarget, patlist) {
    private var jtp: JTabbedPane? = null
    private var patlisttab: Boolean = false

    private var juggleButton: JButton? = null
    private var transButton: JButton? = null
    private var genButton: JButton? = null
    private var genBusy: JLabel? = null

    constructor(parentFrame: JFrame?) :
        this(parentFrame, animtarget = null, patlist = null)

    // Input is for example Pattern.NOTATION_SITESWAP.

    override fun setNotation(notationNum: Int) {
        if (notationNum > Pattern.builtinNotations.size) return
        if (jtp != null) {
            remove(jtp)
        }
        jtp = JTabbedPane()
        val trans = newTransitioner(Pattern.builtinNotations[notationNum - 1])
        val gen = newGenerator(Pattern.builtinNotations[notationNum - 1])

        when (notationNum) {
            Pattern.NOTATION_SITESWAP -> addPatternEntryControl(SiteswapNotationControlSwing())
        }

        var pl = patlist
        if (pl == null && patlisttab) {
            pl = PatternListPanel(animtarget)
        }
        if (trans != null) {
            addTransitionerControl(trans, pl)
        }
        if (gen != null) {
            addGeneratorControl(gen, pl)
        }
        if (pl != null) {
            jtp!!.addTab(getStringResource(Res.string.gui_pattern_list_tab), pl)
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

    private fun addPatternEntryControl(control: SiteswapNotationControlSwing) {
        val nbut1 = JButton(getStringResource(Res.string.gui_defaults)).apply {
            addActionListener { _: ActionEvent? ->
                try {
                    control.resetControl()
                } catch (e: Exception) {
                    jlHandleFatalException(e)
                }
            }
        }

        juggleButton =
            JButton(getStringResource(Res.string.gui_juggle)).apply {
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
                        pat.layout

                        if (bringToFront(pat.jlHashCode)) {
                            return@addActionListener
                        }

                        if (animtarget != null) {
                            animtarget.restartView(pat, jc)
                        } else {
                            PatternWindow(pat.title, pat, jc)
                        }
                    } catch (je: JuggleExceptionUser) {
                        jlHandleUserException(this@ApplicationPanelSwing, je.message)
                    } catch (e: Exception) {
                        jlHandleFatalException(e)
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

        jtp!!.addTab(getStringResource(Res.string.gui_pattern_entry), np1)
    }

    private fun addTransitionerControl(trans: Transitioner, plp: PatternListPanel?) {
        val transControl = SiteswapTransitionerControlSwing()

        val but1 = JButton(getStringResource(Res.string.gui_defaults)).apply {
            addActionListener { _: ActionEvent? -> transControl.resetControl() }
        }

        transButton = JButton(getStringResource(Res.string.gui_run)).apply {
            setDefaultCapable(true)
            addActionListener {
                val t: Thread =
                    object : Thread() {
                        override fun run() {
                            transButton!!.setEnabled(false)
                            var pw: PatternListWindow? = null
                            try {
                                trans.initTransitioner(transControl.params)
                                val title =
                                    trans.notationName + " " + getStringResource(Res.string.gui_patterns)
                                pw = PatternListWindow(title, this)
                                val pwot = GeneratorTargetPatternList(pw.patternListPanel)
                                trans.runTransitioner(pwot, MAX_PATTERNS, MAX_TIME)
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                            } catch (ex: JuggleExceptionDone) {
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                                val parentComponent = pw ?: plp
                                jlHandleUserMessage(
                                    parentComponent,
                                    getStringResource(Res.string.gui_generator_stopped_title),
                                    ex.message
                                )
                            } catch (_: JuggleExceptionInterrupted) {
                                // System.out.println("generator thread quit");
                            } catch (ex: JuggleExceptionUser) {
                                pw?.dispose()
                                jlHandleUserException(this@ApplicationPanelSwing, ex.message)
                            } catch (e: Exception) {
                                pw?.dispose()
                                jlHandleFatalException(e)
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
            add(transControl, BorderLayout.PAGE_START)
            add(p3, BorderLayout.PAGE_END)
        }

        jtp!!.addTab(getStringResource(Res.string.gui_transitions), p1)
    }

    private fun addGeneratorControl(gen: Generator, plp: PatternListPanel?) {
        val genControl = SiteswapGeneratorControlSwing()

        val but1 = JButton(getStringResource(Res.string.gui_defaults)).apply {
            addActionListener { _: ActionEvent? -> genControl.resetControl() }
        }

        genButton = JButton(getStringResource(Res.string.gui_run)).apply {
            addActionListener {
                val t: Thread =
                    object : Thread() {
                        override fun run() {
                            genBusy!!.isVisible = true
                            genButton!!.setEnabled(false)
                            var pw: PatternListWindow? = null
                            try {
                                gen.initGenerator(genControl.params)
                                val title =
                                    gen.notationName + " " + getStringResource(Res.string.gui_patterns)
                                pw = PatternListWindow(title, this)
                                val pwot = GeneratorTargetPatternList(pw.patternListPanel)
                                gen.runGenerator(pwot, MAX_PATTERNS, MAX_TIME)
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                            } catch (ex: JuggleExceptionDone) {
                                if (plp != null) {
                                    jtp!!.setSelectedComponent(plp)
                                }
                                val parentComponent = pw ?: plp
                                jlHandleUserMessage(
                                    parentComponent,
                                    getStringResource(Res.string.gui_generator_stopped_title),
                                    ex.message
                                )
                            } catch (_: JuggleExceptionInterrupted) {
                                // System.out.println("generator thread quit");
                            } catch (ex: JuggleExceptionUser) {
                                pw?.dispose()
                                jlHandleUserException(this@ApplicationPanelSwing, ex.message)
                            } catch (e: Exception) {
                                pw?.dispose()
                                jlHandleFatalException(e)
                            }

                            genBusy!!.isVisible = false
                            genButton!!.setEnabled(true)
                        }
                    }
                t.start()
            }
        }

        genBusy = JLabel(getStringResource(Res.string.gui_processing)).apply {
            isVisible = false
        }

        val gb = GridBagLayout().apply {
            setConstraints(
                genBusy, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 10, 0, 0))
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
            add(genControl, BorderLayout.PAGE_START)
            add(p3, BorderLayout.PAGE_END)
        }

        jtp!!.addTab(getStringResource(Res.string.gui_generator), p1)
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
