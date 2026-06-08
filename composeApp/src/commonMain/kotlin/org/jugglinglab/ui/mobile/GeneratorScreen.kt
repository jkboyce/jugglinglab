//
// GeneratorScreen.kt
//
// Screen wrapper for GeneratorControlCombined in Juggling Lab.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.core.Constants.MAX_PATTERNS
import org.jugglinglab.core.Constants.MAX_TIME_SEC
import org.jugglinglab.generator.GeneratorTarget
import org.jugglinglab.generator.SiteswapGenerator
import org.jugglinglab.generator.SiteswapTransitioner
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.JmlPatternList.PatternRecord
import org.jugglinglab.ui.common.*
import org.jugglinglab.util.JuggleExceptionInterrupted
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@Composable
fun GeneratorScreen(
    isBusy: Boolean,
    generator: SiteswapGenerator,
    transitioner: SiteswapTransitioner,
    patternList: JmlPatternList,
    onIsEditableChange: (Boolean) -> Unit,
    onPathChange: (okio.Path?) -> Unit,
    onHasLoadedChange: (Boolean) -> Unit,
    onPatternListScrollStateChange: (LazyListState) -> Unit,
    generatorState: SiteswapGeneratorState,
    transitionerState: SiteswapTransitionerState,
    combinedState: GeneratorControlCombinedState,
    onNavigateTo: (String) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit,
    onGeneratorThreadChange: (Thread?) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    GeneratorControlCombined(
        isBusy = isBusy,
        onGeneratorConfirm = { params ->
            try {
                generator.initGenerator(params)
                patternList.clearModel()
                patternList.title = "Siteswap Patterns"
                onIsEditableChange(true)
                onPathChange(null)
                onHasLoadedChange(true)
                onPatternListScrollStateChange(LazyListState())
                onNavigateTo("PatternList")
                onBusyChange(true)
                coroutineScope.launch {
                    try {
                        channelFlow {
                            val genTarget = object : GeneratorTarget {
                                override fun addResult(
                                    display: String,
                                    notation: String?,
                                    anim: String?
                                ) {
                                    trySend(
                                        PatternRecord.create(
                                            display,
                                            null,
                                            notation,
                                            anim,
                                            null,
                                            null
                                        )
                                    )
                                }
                            }
                            try {
                                onGeneratorThreadChange(Thread.currentThread())
                                generator.runGenerator(
                                    genTarget,
                                    MAX_PATTERNS,
                                    MAX_TIME_SEC
                                )
                            } finally {
                                onGeneratorThreadChange(null)
                            }
                        }
                            .buffer(Channel.UNLIMITED)
                            .flowOn(Dispatchers.Default)
                            .collect { record ->
                                patternList.addLine(-1, record)
                            }
                    } catch (_: JuggleExceptionInterrupted) {
                        // ignore expected interruption
                    } catch (e: Throwable) {
                        onError(e)
                    } finally {
                        onBusyChange(false)
                    }
                }
            } catch (e: Throwable) {
                onError(e)
                onBusyChange(false)
            }
        },
        onTransitionerConfirm = { params ->
            try {
                transitioner.initTransitioner(params)
                patternList.clearModel()
                patternList.title = "Siteswap Patterns"
                onIsEditableChange(true)
                onPathChange(null)
                onPatternListScrollStateChange(LazyListState())
                onNavigateTo("PatternList")
                onBusyChange(true)
                coroutineScope.launch {
                    try {
                        channelFlow {
                            val genTarget = object : GeneratorTarget {
                                override fun addResult(
                                    display: String,
                                    notation: String?,
                                    anim: String?
                                ) {
                                    trySend(
                                        PatternRecord.create(
                                            display,
                                            null,
                                            notation,
                                            anim,
                                            null,
                                            null
                                        )
                                    )
                                }
                            }
                            try {
                                onGeneratorThreadChange(Thread.currentThread())
                                transitioner.runTransitioner(
                                    genTarget,
                                    MAX_PATTERNS,
                                    MAX_TIME_SEC
                                )
                            } finally {
                                onGeneratorThreadChange(null)
                            }
                        }
                            .buffer(Channel.UNLIMITED)
                            .flowOn(Dispatchers.Default)
                            .collect { record ->
                                patternList.addLine(-1, record)
                            }
                    } catch (_: JuggleExceptionInterrupted) {
                        // ignore expected interruption
                    } catch (e: Exception) {
                        onError(e)
                    } finally {
                        onBusyChange(false)
                    }
                }
            } catch (e: Exception) {
                onError(e)
                onBusyChange(false)
            }
        },
        generatorState = generatorState,
        transitionerState = transitionerState,
        combinedState = combinedState
    )
}
