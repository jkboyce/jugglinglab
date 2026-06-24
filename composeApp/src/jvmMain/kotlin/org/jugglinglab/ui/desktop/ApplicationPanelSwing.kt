//
// ApplicationPanelSwing.kt
//
// Swing UI version of ApplicationPanel.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.Constants.MAX_PATTERNS
import org.jugglinglab.core.Constants.MAX_TIME_SEC
import org.jugglinglab.generator.Generator
import org.jugglinglab.generator.Generator.Companion.newGenerator
import org.jugglinglab.generator.Transitioner
import org.jugglinglab.generator.Transitioner.Companion.newTransitioner
import org.jugglinglab.generator.GeneratorTargetPatternList
import org.jugglinglab.jml.JmlPattern.Companion.fromBasePattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.notation.Pattern
import org.jugglinglab.util.jlHandleFatalException
import org.jugglinglab.util.jlHandleUserException
import org.jugglinglab.util.JuggleExceptionDone
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlConstraints
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlHandleUserMessage
import org.jugglinglab.view.View
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ChangeEvent
import kotlinx.coroutines.*

class ApplicationPanelSwing(
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
        val notationName = Pattern.builtinNotations[notationNum - 1]

        when (notationNum) {
            Pattern.NOTATION_SITESWAP -> addPatternEntryControl(SiteswapNotationControlSwing())
        }

        var pl = patlist
        if (pl == null && patlisttab) {
            pl = PatternListPanel(patternList = JmlPatternList(), animTarget = animtarget)
        }
        if (Transitioner.isTransitionerSupported(notationName)) {
            addTransitionerControl(notationName, pl)
        }
        if (Generator.isGeneratorSupported(notationName)) {
            addGeneratorControl(notationName, pl)
        }
        if (pl != null) {
            jtp!!.addTab(jlGetStringResource(Res.string.gui_pattern_list_tab), pl)
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
        val nbut1 = JButton(jlGetStringResource(Res.string.gui_defaults)).apply {
            addActionListener { _: ActionEvent? ->
                try {
                    control.resetControl()
                } catch (e: Exception) {
                    jlHandleFatalException(e)
                }
            }
        }

        juggleButton =
            JButton(jlGetStringResource(Res.string.gui_juggle)).apply {
                setDefaultCapable(true)
                addActionListener { _: ActionEvent? ->
                    try {
                        val pl = control.parameterList
                        val p = control.newPattern().fromParameters(pl)
                        val jc = AnimationPrefs.fromParameters(pl)
                        pl.errorIfParametersLeft()

                        val notation = p.notationName
                        val config: String = p.toString()
                        val pat = fromBasePattern(notation, config)
                        pat.layout
                        if (PatternWindow.bringToFront(pat.jlHashCode)) {
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

        jtp!!.addTab(jlGetStringResource(Res.string.gui_pattern_entry), np1)
    }

    private fun addTransitionerControl(notationName: String, plp: PatternListPanel?) {
        val transControl = SiteswapTransitionerControlSwing()

        val but1 = JButton(jlGetStringResource(Res.string.gui_defaults)).apply {
            addActionListener { _: ActionEvent? -> transControl.resetControl() }
        }

        transButton = JButton(jlGetStringResource(Res.string.gui_run)).apply {
            setDefaultCapable(true)
            addActionListener {
                coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        transButton!!.setEnabled(false)
                    }
                    var pw: PatternListWindow? = null
                    try {
                        val trans = newTransitioner(notationName, transControl.params)
                            ?: throw JuggleExceptionInternal("Unknown transitioner")
                        var pwot: GeneratorTargetPatternList? = null
                        withContext(Dispatchers.Main) {
                            val title =
                                trans.notationName + " " + jlGetStringResource(Res.string.gui_patterns)
                            val window = PatternListWindow(windowTitle = title, generatorJob = null)
                            pw = window
                            pwot = GeneratorTargetPatternList(window.patternListPanel)
                        }
                        val generatorJob = async(Dispatchers.Default) {
                            trans.runTransitioner(pwot!!, MAX_PATTERNS, MAX_TIME_SEC)
                        }
                        if (pw != null) {
                            withContext(Dispatchers.Main) {
                                pw.generatorJob = generatorJob
                                pw.setTitle("${pw.patternList.title} (running)")
                            }
                        }
                        generatorJob.await()
                        withContext(Dispatchers.Main) {
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }
                        }
                    } catch (ex: JuggleExceptionDone) {
                        withContext(Dispatchers.Main) {
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }
                            val parentComponent = pw ?: plp
                            jlHandleUserMessage(
                                parentComponent,
                                jlGetStringResource(Res.string.gui_generator_stopped_title),
                                ex.message
                            )
                        }
                    } catch (_: CancellationException) {
                        // User cancelled
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            pw?.dispose()
                            if (e is JuggleExceptionUser)
                                jlHandleUserException(this@ApplicationPanelSwing, e.message)
                            else
                                jlHandleFatalException(e)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            pw?.onGeneratorDone()
                            transButton!!.setEnabled(true)
                        }
                    }
                }
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

        jtp!!.addTab(jlGetStringResource(Res.string.gui_transitions), p1)
    }

    private fun addGeneratorControl(notationName: String, plp: PatternListPanel?) {
        val genControl = SiteswapGeneratorControlSwing()

        val but1 = JButton(jlGetStringResource(Res.string.gui_defaults)).apply {
            addActionListener { _: ActionEvent? -> genControl.resetControl() }
        }

        genButton = JButton(jlGetStringResource(Res.string.gui_run)).apply {
            addActionListener {
                coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        genBusy!!.isVisible = true
                        genButton!!.setEnabled(false)
                    }
                    var pw: PatternListWindow? = null
                    try {
                        val gen = newGenerator(notationName, genControl.params)
                            ?: throw JuggleExceptionInternal("Unknown generator")
                        var gtpl: GeneratorTargetPatternList? = null
                        withContext(Dispatchers.Main) {
                            val title =
                                gen.notationName + " " + jlGetStringResource(Res.string.gui_patterns)
                            val window = PatternListWindow(windowTitle = title, generatorJob = null)
                            pw = window
                            gtpl = GeneratorTargetPatternList(window.patternListPanel)
                        }
                        val generatorJob = async(Dispatchers.Default) {
                            gen.runGenerator(gtpl!!, MAX_PATTERNS, MAX_TIME_SEC)
                        }
                        if (pw != null) {
                            withContext(Dispatchers.Main) {
                                pw.generatorJob = generatorJob
                                pw.setTitle("${pw.patternList.title} (running)")
                            }
                        }
                        generatorJob.await()
                        withContext(Dispatchers.Main) {
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }
                        }
                    } catch (ex: JuggleExceptionDone) {
                        withContext(Dispatchers.Main) {
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }
                            val parentComponent = pw ?: plp
                            jlHandleUserMessage(
                                parentComponent,
                                jlGetStringResource(Res.string.gui_generator_stopped_title),
                                ex.message
                            )
                        }
                    } catch (_: CancellationException) {
                        // User cancelled
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            pw?.dispose()
                            if (e is JuggleExceptionUser)
                                jlHandleUserException(this@ApplicationPanelSwing, e.message)
                            else
                                jlHandleFatalException(e)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            pw?.onGeneratorDone()
                            genBusy!!.isVisible = false
                            genButton!!.setEnabled(true)
                        }
                    }
                }
            }
        }

        genBusy = JLabel(jlGetStringResource(Res.string.gui_processing)).apply {
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

        jtp!!.addTab(jlGetStringResource(Res.string.gui_generator), p1)
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
}
