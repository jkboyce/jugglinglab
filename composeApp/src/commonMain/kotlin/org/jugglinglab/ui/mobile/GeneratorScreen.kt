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
import org.jugglinglab.util.JuggleExceptionDone
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

@Composable
fun GeneratorScreen(
    isBusy: Boolean,
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
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onGeneratorJobChange: (Job?) -> Unit = {}
) {
    GeneratorControlCombined(
        isBusy = isBusy,
        onGeneratorConfirm = { params ->
            onBusyChange(true)
            val job = coroutineScope.launch {
                try {
                    val generator = SiteswapGenerator(params)
                    patternList.clearModel()
                    patternList.title = "Siteswap Patterns"
                    onIsEditableChange(true)
                    onPathChange(null)
                    onHasLoadedChange(true)
                    onPatternListScrollStateChange(LazyListState())
                    onNavigateTo("PatternList")

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
                        generator.runGenerator(
                            genTarget,
                            MAX_PATTERNS,
                            MAX_TIME_SEC
                        )
                    }
                        .buffer(Channel.UNLIMITED)
                        .flowOn(Dispatchers.Default)
                        .collect { record ->
                            patternList.addLine(-1, record)
                        }
                } catch (e: JuggleExceptionDone) {
                    // generator hit a limit and stopped prematurely; notify the user
                    onError(e)
                } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                    // ignore user-initiated cancellation or interruption
                } catch (e: Throwable) {
                    onError(e)
                } finally {
                    onBusyChange(false)
                    onGeneratorJobChange(null)
                }
            }
            onGeneratorJobChange(job)
        },
        onTransitionerConfirm = { params ->
            onBusyChange(true)
            val job = coroutineScope.launch {
                try {
                    val transitioner = withContext(Dispatchers.Default) {
                        // potentially slow operation parsing from/to patterns
                        SiteswapTransitioner(params)
                    }
                    patternList.clearModel()
                    patternList.title = "Siteswap Patterns"
                    onIsEditableChange(true)
                    onPathChange(null)
                    onHasLoadedChange(true)
                    onPatternListScrollStateChange(LazyListState())
                    onNavigateTo("PatternList")
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
                        transitioner.runTransitioner(
                            genTarget,
                            MAX_PATTERNS,
                            MAX_TIME_SEC
                        )
                    }
                        .buffer(Channel.UNLIMITED)
                        .flowOn(Dispatchers.Default)
                        .collect { record ->
                            patternList.addLine(-1, record)
                        }
                } catch (e: JuggleExceptionDone) {
                    onError(e)
                } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                    // ignore user-initiated cancellation or interruption
                } catch (e: Throwable) {
                    onError(e)
                } finally {
                    onBusyChange(false)
                    onGeneratorJobChange(null)
                }
            }
            onGeneratorJobChange(job)
        },
        generatorState = generatorState,
        transitionerState = transitionerState,
        combinedState = combinedState
    )
}
