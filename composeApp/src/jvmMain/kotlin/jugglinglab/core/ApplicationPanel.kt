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

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.awt.ComposePanel
import jugglinglab.JugglingLab.guistrings
import jugglinglab.core.PatternWindow.Companion.bringToFront
import jugglinglab.generator.Generator
import jugglinglab.generator.Generator.Companion.newGenerator
import jugglinglab.generator.GeneratorTargetPatternList
import jugglinglab.generator.SiteswapGeneratorControl
import jugglinglab.generator.Transitioner
import jugglinglab.jml.JMLPattern.Companion.fromBasePattern
import jugglinglab.generator.SiteswapTransitionerControl
import jugglinglab.generator.Transitioner.Companion.newTransitioner
import jugglinglab.notation.SiteswapPattern
import jugglinglab.notation.SiteswapNotationControl
import jugglinglab.notation.Pattern
import jugglinglab.util.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.view.View
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

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
        setLayout(BorderLayout())
        /*
        if (num > Pattern.builtinNotations.size) return
        if (jtp != null) {
            remove(jtp)
        }
        jtp = JTabbedPane()

        when (num) {
            Pattern.NOTATION_SITESWAP -> addPatternEntryControl(SiteswapNotationControl())
        }
        */
        var pl = patlist
        if (pl == null && patlisttab) {
            pl = PatternListPanel(animtarget)
        }

        val patternControl = makePatternEntryControlPanel()
        add(patternControl, BorderLayout.CENTER)

        /*
        val trans = newTransitioner(Pattern.builtinNotations[num - 1])
        if (trans != null) {
            val transControl = makeTransitionerControlPanel(trans, pl)
            add(transControl, BorderLayout.CENTER)

            //jtp!!.addTab(guistrings.getString("Transitions"), transControl)
        }*/


        val gen = newGenerator(Pattern.builtinNotations[num - 1])
        if (gen != null) {
            val genControl = makeGeneratorControlPanel(gen, pl)
            add(genControl, BorderLayout.CENTER)

            // jtp!!.addTab(guistrings.getString("Generator"), genControl)
        }

        /*
        if (pl != null) {
            jtp!!.addTab(guistrings.getString("Pattern_list_tab"), pl)
            if (patlist != null) {
                jtp!!.setSelectedComponent(pl) // if we loaded from a file
            }
        }

        // change the default button when the tab changes
        jtp!!.addChangeListener { _: ChangeEvent? -> rootPane.defaultButton = defaultButton }
        */
        //add(jtp!!, BorderLayout.CENTER)
    }

    private fun makePatternEntryControlPanel(): JPanel {

        val onRunCallback: (String) -> Unit = { params ->
            try {
                val pl = ParameterList(params)
                val p = SiteswapPattern().fromParameters(pl)
                val jc = (AnimationPrefs()).fromParameters(pl)
                pl.errorIfParametersLeft()

                val notation = p.notationName
                val config: String = p.toString()
                val pat = fromBasePattern(notation, config)
                pat.layoutPattern()

                if (bringToFront(pat.hashCode)) {
                    // TODO fix this:
                    //return
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

        val composePanel = ComposePanel().apply {
            // Set a preferred size so that pack() on the parent JFrame works correctly,
            // shrinking the window to fit the content instead of using a default large size.
            preferredSize = Dimension(500, 700)

            setContent {
                MaterialTheme {
                    Surface(color = MaterialTheme.colors.surface) {
                        // Pass the class-level callback to the Composable
                        SiteswapNotationControl(onConfirm = onRunCallback)
                    }
                }
            }
        }

        return JPanel().apply {
            layout = BorderLayout()
            add(composePanel, BorderLayout.CENTER)
        }

        //jtp!!.addTab(guistrings.getString("Pattern_entry"), np1)
    }

    private fun makeTransitionerControlPanel(
        trans: Transitioner,
        plp: PatternListPanel?
    ): JPanel {
        // Callback lambda for when the "Run" button is clicked
        val onRunCallback: (String) -> Unit = { params ->
            val t: Thread =
                object : Thread() {
                    override fun run() {
                        var pw: PatternListWindow? = null
                        try {
                            trans.initTransitioner(params)
                            val pwot: GeneratorTargetPatternList
                            if (plp != null) {
                                plp.clearList()
                                pwot = GeneratorTargetPatternList(plp)
                                // jtp.setSelectedComponent(plp);
                            } else {
                                val title =
                                    trans.notationName + " " + guistrings.getString("Patterns")
                                pw = PatternListWindow(title, this)
                                pwot = GeneratorTargetPatternList(pw.patternListPanel)
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
                    }
                }
            t.start()
        }

        val composePanel = ComposePanel().apply {
            // Set a preferred size so that pack() on the parent JFrame works correctly,
            // shrinking the window to fit the content instead of using a default large size.
            preferredSize = Dimension(450, 500)

            setContent {
                MaterialTheme {
                    Surface(color = MaterialTheme.colors.surface) {
                        // Pass the class-level callback to the Composable
                        SiteswapTransitionerControl(onConfirm = onRunCallback)
                    }
                }
            }
        }

        return JPanel().apply {
            layout = BorderLayout()
            add(composePanel, BorderLayout.CENTER)
        }
    }

    private fun makeGeneratorControlPanel(
        gen: Generator,
        plp: PatternListPanel?
    ): JPanel {
        val onRunCallback: (String) -> Unit = { params ->
            val t: Thread =
                object : Thread() {
                    override fun run() {
                        //genBusy!!.isVisible = true
                        //genButton!!.setEnabled(false)
                        var pw: PatternListWindow? = null
                        try {
                            gen.initGenerator(params)
                            val gtpl: GeneratorTargetPatternList?
                            if (plp != null) {
                                plp.clearList()
                                gtpl = GeneratorTargetPatternList(plp)
                                // jtp.setSelectedComponent(plp);
                            } else {
                                val title =
                                    gen.notationName + " " + guistrings.getString("Patterns")
                                pw = PatternListWindow(title, this)
                                gtpl = GeneratorTargetPatternList(pw.patternListPanel)
                            }
                            gen.runGenerator(gtpl, MAX_PATTERNS, MAX_TIME)
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

                        //genBusy!!.isVisible = false
                        //genButton!!.setEnabled(true)
                    }
                }
            t.start()
        }

        val composePanel = ComposePanel().apply {
            // Set a preferred size so that pack() on the parent JFrame works correctly,
            // shrinking the window to fit the content instead of using a default large size.
            preferredSize = Dimension(540, 810)

            setContent {
                MaterialTheme {
                    Surface(color = MaterialTheme.colors.surface) {
                        // Pass the class-level callback to the Composable
                        SiteswapGeneratorControl(onConfirm = onRunCallback)
                    }
                }
            }
        }

        return JPanel().apply {
            layout = BorderLayout()
            add(composePanel, BorderLayout.CENTER)
        }

        /*
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
            if (genControl != null) {
                add(genControl, BorderLayout.PAGE_START)
            }
            add(p3, BorderLayout.PAGE_END)
        }

        jtp!!.addTab(guistrings.getString("Generator"), p1)
        */
    }

    companion object {
        // execution limits for generator
        private const val MAX_PATTERNS: Int = 1000
        private const val MAX_TIME: Double = 15.0
    }
}
