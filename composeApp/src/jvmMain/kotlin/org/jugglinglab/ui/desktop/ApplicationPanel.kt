//
// ApplicationPanel.kt
//
// This class represents the entire contents of the ApplicationWindow frame.
// For a given notation type it creates a tabbed pane with separate panels for
// pattern entry, transitions, and generator.
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
import org.jugglinglab.generator.GeneratorTargetPatternList
import org.jugglinglab.generator.Transitioner
import org.jugglinglab.generator.Transitioner.Companion.newTransitioner
import org.jugglinglab.jml.JmlPattern.Companion.fromBasePattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.notation.Pattern
import org.jugglinglab.ui.common.SiteswapNotationControl
import org.jugglinglab.ui.common.SiteswapTransitionerControl
import org.jugglinglab.ui.common.SiteswapGeneratorControl
import org.jugglinglab.util.jlHandleFatalException
import org.jugglinglab.util.jlHandleUserException
import org.jugglinglab.util.JuggleExceptionDone
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.ParameterList
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlHandleUserMessage
import org.jugglinglab.view.View
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
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource

open class ApplicationPanel(
    private val parentFrame: JFrame?,
    // the fields below are currently unused; they supported the applet
    // version of Juggling Lab but may be useful in the future
    private val animtarget: View? = null,
    private val patlist: PatternListPanel? = null
) : JPanel(), ActionListener {
    protected val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
            pl = PatternListPanel(patternList = JmlPatternList(), animTarget = animtarget)
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
                                    0 -> SiteswapNotationControl(
                                        initialParams = "pattern=3",
                                        onConfirm = onRunPatternEntry()
                                    )

                                    1 -> SiteswapTransitionerControl(
                                        onConfirm = onRunTransitioner(
                                            trans!!,
                                            pl
                                        )
                                    )

                                    2 -> SiteswapGeneratorControl(
                                        onConfirm = onRunGenerator(
                                            gen!!,
                                            pl
                                        )
                                    )
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
        return { parameterString ->
            try {
                val pl = ParameterList(parameterString)
                val p = SiteswapPattern().fromParameters(pl)
                val jc = AnimationPrefs.fromParameters(pl)
                pl.errorIfParametersLeft()

                // make the JML pattern
                val notation = p.notationName
                val config: String = p.toString()
                val pat = fromBasePattern(notation, config)
                if (!PatternWindow.bringToFront(pat.jlHashCode)) {
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
            coroutineScope.launch {
                var pw: PatternListWindow? = null
                try {
                    trans.initTransitioner(params)
                    var pwot: GeneratorTargetPatternList? = null
                    withContext(Dispatchers.Main) {
                        if (plp != null) {
                            plp.clearList()
                            pwot = GeneratorTargetPatternList(plp)
                        } else {
                            val title =
                                trans.notationName + " " + jlGetStringResource(Res.string.gui_patterns)
                            val window = PatternListWindow(windowTitle = title, generatorJob = null)
                            pw = window
                            pwot = GeneratorTargetPatternList(window.patternListPanel)
                        }
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
                } catch (ex: JuggleExceptionDone) {
                    withContext(Dispatchers.Main) {
                        val parentComponent = pw ?: plp
                        jlHandleUserMessage(
                            parentComponent,
                            jlGetStringResource(Res.string.gui_generator_stopped_title),
                            ex.message
                        )
                    }
                } catch (_: CancellationException) {
                    // Handled by finally block
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        pw?.dispose()
                        if (e is JuggleExceptionUser)
                            jlHandleUserException(this@ApplicationPanel, e.message)
                        else
                            jlHandleFatalException(e)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        pw?.onGeneratorDone()
                    }
                }
            }
        }
    }

    // Callback function to invoke on "Run" on the generator control

    private fun onRunGenerator(
        gen: Generator,
        plp: PatternListPanel?
    ): (String) -> Unit {
        return { params ->
            coroutineScope.launch {
                var pw: PatternListWindow? = null
                try {
                    gen.initGenerator(params)
                    var gtpl: GeneratorTargetPatternList? = null
                    withContext(Dispatchers.Main) {
                        if (plp != null) {
                            plp.clearList()
                            gtpl = GeneratorTargetPatternList(plp)
                        } else {
                            val title =
                                gen.notationName + " " + jlGetStringResource(Res.string.gui_patterns)
                            val window = PatternListWindow(windowTitle = title, generatorJob = null)
                            pw = window
                            gtpl = GeneratorTargetPatternList(window.patternListPanel)
                        }
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
                } catch (ex: JuggleExceptionDone) {
                    withContext(Dispatchers.Main) {
                        val parentComponent = pw ?: plp
                        jlHandleUserMessage(
                            parentComponent,
                            jlGetStringResource(Res.string.gui_generator_stopped_title),
                            ex.message
                        )
                    }
                } catch (_: CancellationException) {
                    // Handled by finally block
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        pw?.dispose()
                        if (e is JuggleExceptionUser)
                            jlHandleUserException(this@ApplicationPanel, e.message)
                        else
                            jlHandleFatalException(e)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        pw?.onGeneratorDone()
                    }
                }
            }
        }
    }
}
