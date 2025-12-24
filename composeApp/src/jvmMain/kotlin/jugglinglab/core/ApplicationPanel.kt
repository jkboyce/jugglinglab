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

import jugglinglab.composeapp.generated.resources.*
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
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.view.View
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

open class ApplicationPanel(
    private val parentFrame: JFrame?,
    // the fields below are currently unused; they supported the applet
    // version of Juggling Lab but may be useful in the future
    private val animtarget: View? = null,
    private val patlist: PatternListPanel? = null
) : JPanel(), ActionListener {
    var havePatternListTab: Boolean = false
    var currentNotationNum: Int = -1
    //var genBusy: JLabel? = null

    override fun actionPerformed(ae: ActionEvent) {
        val command = ae.getActionCommand()
        try {
            if (command.startsWith("notation")) {
                try {
                    val num = command.substring(8).toInt()
                    if (num != currentNotationNum) {
                        setNotation(num)
                        parentFrame?.pack()
                        currentNotationNum = num
                    }
                } catch (_: NumberFormatException) {
                    throw JuggleExceptionInternal("Error in notation number coding")
                }
            }
        } catch (e: Exception) {
            jlHandleFatalException(e)
        }
    }

    // Set panel contents for a given notation.
    //
    // Parameter `notationNum` is for example Pattern.NOTATION_SITESWAP.

    open fun setNotation(notationNum: Int) {
        // resources needed by the control panels
        var pl = patlist
        if (pl == null && havePatternListTab) {
            pl = PatternListPanel(animtarget)
        }
        val trans = newTransitioner(Pattern.builtinNotations[notationNum - 1])
        val gen = newGenerator(Pattern.builtinNotations[notationNum - 1])

        val composePanel = ComposePanel().apply {
            // Set a preferred size so pack() on the parent JFrame works correctly
            preferredSize = Dimension(500, 800)

            setContent {
                MaterialTheme {
                    Surface(color = MaterialTheme.colors.surface) {
                        // state variable for tabbed interface
                        var selectedTabIndex by remember { mutableStateOf(0) }

                        val tabs = listOf(
                            stringResource(Res.string.gui_pattern_entry),
                            stringResource(Res.string.gui_transitions),
                            stringResource(Res.string.gui_generator)
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = selectedTabIndex) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTabIndex == index,
                                        onClick = { selectedTabIndex = index },
                                        text = { Text(title) }
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (selectedTabIndex) {
                                    0 -> SiteswapNotationControl(onConfirm = onRunPatternEntry())
                                    1 -> SiteswapTransitionerControl(onConfirm = onRunTransitioner(trans!!, pl))
                                    2 -> SiteswapGeneratorControl(onConfirm = onRunGenerator(gen!!, pl))
                                }
                            }
                        }
                    }
                }
            }
        }

        layout = BorderLayout()
        add(composePanel, BorderLayout.CENTER)

        /*
        if (pl != null) {
            jtp!!.addTab(guistrings.getString("Pattern_list_tab"), pl)
            if (patlist != null) {
                jtp!!.setSelectedComponent(pl) // if we loaded from a file
            }
        }*/
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

                if (!bringToFront(pat.jlHashCode)) {
                    if (animtarget != null) {
                        animtarget.restartView(pat, jc)
                    } else {
                        PatternWindow(pat.title, pat, jc)
                    }
                }
            } catch (je: JuggleExceptionUser) {
                jlHandleUserException(this@ApplicationPanel, je.message)
            } catch (e: Exception) {
                jlHandleFatalException(e)
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
                                    trans.notationName + " " + getStringResource(Res.string.gui_patterns)
                                pw = PatternListWindow(title, this)
                                pwot = GeneratorTargetPatternList(pw.patternListPanel)
                            }
                            trans.runTransitioner(pwot, MAX_PATTERNS, MAX_TIME)
                            /*
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }*/
                        } catch (ex: JuggleExceptionDone) {
                            /*
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }*/
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
                            jlHandleUserException(this@ApplicationPanel, ex.message)
                        } catch (e: Exception) {
                            pw?.dispose()
                            jlHandleFatalException(e)
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
                                    gen.notationName + " " + getStringResource(Res.string.gui_patterns)
                                pw = PatternListWindow(title, this)
                                gtpl = GeneratorTargetPatternList(pw.patternListPanel)
                            }
                            gen.runGenerator(gtpl, MAX_PATTERNS, MAX_TIME)
                            /*
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }*/
                        } catch (ex: JuggleExceptionDone) {
                            /*
                            if (plp != null) {
                                jtp!!.setSelectedComponent(plp)
                            }*/
                            val parentComponent = pw ?: plp
                            jlHandleUserMessage(
                                parentComponent,
                                getStringResource(Res.string.gui_generator_stopped_title),
                                ex.message
                            )
                        } catch (_: JuggleExceptionInterrupted) {
                        } catch (ex: JuggleExceptionUser) {
                            pw?.dispose()
                            jlHandleUserException(this@ApplicationPanel, ex.message)
                        } catch (e: Exception) {
                            pw?.dispose()
                            jlHandleFatalException(e)
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
        const val MAX_PATTERNS: Int = 1000
        const val MAX_TIME: Double = 15.0
    }
}
