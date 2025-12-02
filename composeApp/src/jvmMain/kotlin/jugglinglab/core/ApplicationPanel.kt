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
    // the fields below are currently unused; they supported the applet version
    // of Juggling Lab
    private val animtarget: View? = null,
    private val patlist: PatternListPanel? = null
) : JPanel(), ActionListener {
    private var jtp: JTabbedPane? = null
    private var patlisttab: Boolean = false

    private var currentnum: Int = -1

    //private var genBusy: JLabel? = null

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
        layout = BorderLayout()
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
        val trans = newTransitioner(Pattern.builtinNotations[num - 1])
        val gen = newGenerator(Pattern.builtinNotations[num - 1])

        val composePanel = ComposePanel().apply {
            // Set a preferred size so that pack() on the parent JFrame works correctly,
            // shrinking the window to fit the content instead of using a default large size.
            preferredSize = Dimension(500, 700)  // pattern entry
            //preferredSize = Dimension(475, 500)  // transitioner
            //preferredSize = Dimension(400, 710)  // generator

            setContent {
                MaterialTheme {
                    Surface(color = MaterialTheme.colors.surface) {
                        SiteswapNotationControl(onConfirm = onRunPatternEntry())

                        //SiteswapTransitionerControl(onConfirm = onRunTransitioner(trans!!, pl))

                        //SiteswapGeneratorControl(onConfirm = onRunGenerator(gen!!, pl))

                    }
                }
            }
        }

        layout = BorderLayout()
        add(composePanel, BorderLayout.CENTER)

        //jtp!!.addTab(guistrings.getString("Pattern_entry"), np1)
        /*
        if (trans != null) {
            val transControl = makeTransitionerControlPanel(trans, pl)
            add(transControl, BorderLayout.CENTER)

            //jtp!!.addTab(guistrings.getString("Transitions"), transControl)
        }*/

/*
        if (gen != null) {
            val genControl = makeGeneratorControlPanel(gen, pl)
            add(genControl, BorderLayout.CENTER)

            // jtp!!.addTab(guistrings.getString("Generator"), genControl)
        }*/

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

    // Return the callback function to invoke when the user clicks
    // "Run" on the pattern entry control.

    private fun onRunPatternEntry(): (String) -> Unit {
        return { params ->
            try {
                val pl = ParameterList(params)
                val p = SiteswapPattern().fromParameters(pl)
                val jc = (AnimationPrefs()).fromParameters(pl)
                pl.errorIfParametersLeft()

                // make the JML pattern
                val notation = p.notationName
                val config: String = p.toString()
                val pat = fromBasePattern(notation, config)
                pat.layoutPattern()

                if (!bringToFront(pat.hashCode)) {
                    if (animtarget != null) {
                        animtarget.restartView(pat, jc)
                    } else {
                        PatternWindow(pat.title, pat, jc)
                    }
                }
            } catch (je: JuggleExceptionUser) {
                handleUserException(this@ApplicationPanel, je.message)
            } catch (e: Exception) {
                handleFatalException(e)
            }
        }
    }

    // Callback function to invoke on "Run" on the transitioner control

    private fun onRunTransitioner(
        trans: Transitioner,
        plp: PatternListPanel?
    ): (String) -> Unit {
        return { params ->
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
    }

    // Callback function to invoke on "Run" on the generator control

    private fun onRunGenerator(
        gen: Generator,
        plp: PatternListPanel?
    ): (String) -> Unit {
        return { params ->
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

        /*
        genBusy = JLabel(guistrings.getString("Processing")).apply {
            isVisible = false
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
