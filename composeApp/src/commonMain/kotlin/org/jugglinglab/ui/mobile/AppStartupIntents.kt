//
// AppStartupIntents.kt
//
// Composable component handling startup intents and lifecycle side effects.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.util.decodeShareUrl
import org.jugglinglab.util.jlIsWeb
import org.jugglinglab.util.JuggleExceptionUser
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString

@Composable
fun AppStartupIntents(
    viewModel: AppViewModel,
    navController: NavController,
    navigateTo: (String) -> Unit,
    walkthroughCoordinator: WalkthroughCoordinator,
    onboardingCompleted: Boolean,
    isMigrationDialogShown: Boolean,
    startUrl: String?,
    startJmlContent: String?,
    onUrlHandled: () -> Unit,
    onJmlContentHandled: () -> Unit,
    onError: (Throwable) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Load favorites at startup
    LaunchedEffect(Unit) {
        if (!viewModel.hasLoadedFavorites) {
            viewModel.loadFavoritesList {
                viewModel.hasLoadedFavorites = true
            }
        }
    }

    // Launch walkthrough
    LaunchedEffect(onboardingCompleted, isMigrationDialogShown) {
        if (!jlIsWeb && !onboardingCompleted && !isMigrationDialogShown && walkthroughCoordinator.walkthroughStep == 0) {
            walkthroughCoordinator.startWalkthrough()
        }
    }

    // Handle a share URL passed in at launch (Android deep-link)
    LaunchedEffect(startUrl) {
        if (startUrl != null) {
            if (!startUrl.contains("?") || startUrl.substringAfter("?").isEmpty()) {
                onUrlHandled()
                return@LaunchedEffect
            }
            coroutineScope.launch(Dispatchers.Default) {
                viewModel.isProcessing = true
                try {
                    val (pattern, prefs) = decodeShareUrl(startUrl)
                    viewModel.animationController.restartJuggle(
                        pattern = pattern,
                        prefs = prefs
                    )
                    viewModel.state.addCurrentToUndoList()
                    while (!isNavGraphSet(navController)) {
                        delay(50.milliseconds)
                    }
                    withContext(Dispatchers.Main) {
                        navigateTo("Animation")
                    }
                } catch (e: JuggleExceptionUser) {
                    val message = getString(
                        Res.string.error_mobile_loading_pattern,
                        e.message ?: ""
                    )
                    onError(JuggleExceptionUser(message))
                } catch (e: Throwable) {
                    onError(e)
                } finally {
                    viewModel.isProcessing = false
                    onUrlHandled()
                }
            }
        }
    }

    // Handle imported JML content (Android ACTION_VIEW intents)
    LaunchedEffect(startJmlContent) {
        if (startJmlContent != null) {
            coroutineScope.launch(Dispatchers.Default) {
                viewModel.isProcessing = true
                try {
                    val parser = JmlParser()
                    parser.parse(startJmlContent)

                    when (parser.fileType) {
                        JmlParser.JML_PATTERN -> {
                            val pat = JmlPattern.fromJmlNode(parser.tree!!)
                            pat.layout
                            viewModel.animationController.restartJuggle(pattern = pat)
                            viewModel.state.addCurrentToUndoList()
                            while (!isNavGraphSet(navController)) {
                                delay(50.milliseconds)
                            }
                            withContext(Dispatchers.Main) {
                                navigateTo("Animation")
                            }
                        }

                        JmlParser.JML_LIST -> {
                            val pl = JmlPatternList(parser.tree)
                            viewModel.patternList.clearModel()
                            viewModel.patternList.title = pl.title
                            for (i in 0 until pl.size) {
                                val item = pl.getLine(i)
                                if (item != null) {
                                    viewModel.patternList.addLine(-1, item)
                                }
                            }
                            viewModel.isPatternListEditable = true
                            viewModel.patternListPath = null
                            viewModel.hasLoadedPatternList = true
                            viewModel.patternListScrollState = LazyListState()
                            while (!isNavGraphSet(navController)) {
                                delay(50.milliseconds)
                            }
                            withContext(Dispatchers.Main) {
                                navigateTo("PatternList")
                            }
                        }

                        else -> {
                            val message = getString(Res.string.error_invalid_jml)
                            onError(JuggleExceptionUser(message))
                        }
                    }
                } catch (e: JuggleExceptionUser) {
                    val message = getString(
                        Res.string.error_mobile_reading_imported_file,
                        e.message ?: ""
                    )
                    onError(JuggleExceptionUser(message))
                } catch (e: Throwable) {
                    onError(e)
                } finally {
                    viewModel.isProcessing = false
                    onJmlContentHandled()
                }
            }
        }
    }
}

private fun isNavGraphSet(navController: NavController): Boolean {
    return try {
        navController.graph
        true
    } catch (_: IllegalStateException) {
        false
    }
}
