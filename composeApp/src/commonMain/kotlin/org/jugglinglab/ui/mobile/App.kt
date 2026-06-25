//
// App.kt
//
// Top-level Composable for the mobile application.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.core.ThemeSetting
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.ui.components.JlErrorDialog
import org.jugglinglab.ui.components.JlStoppedDialog
import org.jugglinglab.util.crashReporter
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.JuggleExceptionDone
import org.jugglinglab.util.BackHandler
import org.jugglinglab.util.backGestureHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.flowOf
import okio.Path
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun App(
    prefsRepo: StoredPreferencesRepository? = null,
    localFilesDir: Path? = null,
    startUrl: String? = null,
    startJmlContent: String? = null,
    startError: String? = null,
    onUrlHandled: () -> Unit = {},
    onJmlContentHandled: () -> Unit = {},
    onStartErrorHandled: () -> Unit = {},
    isMigrationDialogShown: Boolean = false,
) {
    // Theme Settings

    val themeSetting by (prefsRepo?.themeSettingFlow ?: flowOf(ThemeSetting.SYSTEM)).collectAsState(
        initial = ThemeSetting.SYSTEM
    )
    val useDarkTheme = when (themeSetting) {
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()

    // Coroutine Scope & ViewModel Setup

    val coroutineScope = rememberCoroutineScope()
    val viewModel = retain(localFilesDir) {
        AppViewModel(localFilesDir)
    }

    // Navigation Setup

    val navController = rememberNavController()
    val customBackStack = remember { mutableStateListOf("Info") }
    var isBackNavigating by remember { mutableStateOf(false) }

    val navigateTo = { newView: String ->
        isBackNavigating = false
        val targetRoute = if (newView == "FileChooser") {
            viewModel.hasLoadedPatternList = false
            "PatternList"
        } else {
            newView
        }
        if (navController.currentDestination?.route != targetRoute) {
            if (customBackStack.isEmpty() || customBackStack.last() != targetRoute) {
                customBackStack.add(targetRoute)
                if (customBackStack.size > 20) {
                    customBackStack.removeAt(0)
                }
            }
            navController.navigate(targetRoute) {
                popUpTo(navController.currentDestination?.route ?: "Info") {
                    inclusive = true
                }
            }
        }
    }

    val goBack = {
        if (customBackStack.size > 1) {
            isBackNavigating = true
            customBackStack.removeAt(customBackStack.lastIndex)
            val previousRoute = customBackStack.last()
            navController.navigate(previousRoute) {
                popUpTo(navController.currentDestination?.route ?: "Info") {
                    inclusive = true
                }
            }
        }
    }

    BackHandler(enabled = customBackStack.size > 1, onBack = goBack)

    // Error Handling

    val handleRuntimeError: (Throwable) -> Unit = { t ->
        when (t) {
            is JuggleExceptionDone -> {
                viewModel.asyncStoppedMessage = t.message
            }

            is JuggleExceptionUser -> {
                viewModel.asyncErrorMessage = t.message
            }

            else -> {
                viewModel.asyncErrorMessage = "Internal error: ${t.message}"
                crashReporter.recordThrowable(
                    throwable = t,
                    pattern = viewModel.state.pattern
                )
            }
        }
    }
    viewModel.onError = handleRuntimeError

    LaunchedEffect(startError) {
        if (startError != null) {
            handleRuntimeError(JuggleExceptionUser(startError))
            onStartErrorHandled()
        }
    }

    // Walkthrough Coordination

    val walkthroughCoordinator = remember(prefsRepo, viewModel.state) {
        WalkthroughCoordinator(
            prefsRepo = prefsRepo,
            state = viewModel.state,
            coroutineScope = coroutineScope,
            onNavigateTo = navigateTo,
            onResetAnimationToDefault = {
                try {
                    val defaultPattern = SiteswapPattern().fromString("3").asJmlPattern()
                    viewModel.animationController.restartJuggle(
                        pattern = defaultPattern,
                        prefs = AnimationPrefs()
                    )
                    viewModel.animationController.restartJuggle()  // deselect events
                } catch (e: Exception) {
                    handleRuntimeError(e)
                }
            },
            onResetPatternListState = {
                viewModel.hasLoadedPatternList = false
                viewModel.patternList.clearModel()
                viewModel.patternList.title = null
            }
        )
    }

    val onboardingCompleted by (prefsRepo?.onboardingCompletedFlow ?: flowOf(true)).collectAsState(
        initial = true
    )

    // Platform Intents & Lifecycle Startup Handlers

    AppStartupIntents(
        viewModel = viewModel,
        navigateTo = navigateTo,
        walkthroughCoordinator = walkthroughCoordinator,
        onboardingCompleted = onboardingCompleted,
        isMigrationDialogShown = isMigrationDialogShown,
        startUrl = startUrl,
        startJmlContent = startJmlContent,
        onUrlHandled = onUrlHandled,
        onJmlContentHandled = onJmlContentHandled,
        onError = handleRuntimeError
    )

    // Visual Hierarchy

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize()
                .backGestureHandler(enabled = customBackStack.size > 1, onBack = goBack),
            color = MaterialTheme.colorScheme.background
        ) {
            CompositionLocalProvider(LocalWalkthroughCoordinator provides walkthroughCoordinator) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .onGloballyPositioned { coords ->
                            walkthroughCoordinator.rootBounds = coords.boundsInRoot()
                        }
                ) {
                    val maxH = maxHeight
                    val maxW = maxWidth
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route ?: "Info"
                    val isLandscape = maxW > maxH

                    // Composable for the navigation buttons
                    val views = listOf(
                        Triple(
                            "Info",
                            Icons.Default.Home,
                            stringResource(Res.string.gui_mobile_nav_info)
                        ),
                        Triple(
                            "Animation",
                            Icons.Default.Animation,
                            stringResource(Res.string.gui_mobile_nav_animation)
                        ),
                        Triple(
                            "Notation",
                            Icons.Default.Edit,
                            stringResource(Res.string.gui_mobile_nav_notation)
                        ),
                        Triple(
                            "PatternList",
                            Icons.AutoMirrored.Filled.FormatListBulleted,
                            stringResource(Res.string.gui_mobile_nav_pattern_list)
                        ),
                        Triple(
                            "Generator",
                            Icons.Default.Build,
                            stringResource(Res.string.gui_mobile_nav_generator)
                        ),
                        Triple(
                            "Favorites",
                            Icons.Default.Star,
                            stringResource(Res.string.gui_mobile_favorites)
                        )
                    )
                    val navButtons = remember {
                        movableContentOf<String> { activeRoute ->
                            views.forEach { (viewName, icon, description) ->
                                val isSelected = activeRoute == viewName
                                IconButton(
                                    onClick = { navigateTo(viewName) },
                                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.walkthroughTarget(
                                        key = if (viewName == "Info") "nav_info" else "nav_favorites",
                                        condition = viewName == "Info" || viewName == "Favorites"
                                    )
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = description
                                    )
                                }
                            }
                        }
                    }

                    // Composable for the main content area
                    val mainContent = remember {
                        movableContentOf<Modifier> { modifier ->
                            AppNavHost(
                                navController = navController,
                                viewModel = viewModel,
                                walkthroughCoordinator = walkthroughCoordinator,
                                themeSetting = themeSetting,
                                prefsRepo = prefsRepo,
                                localFilesDir = localFilesDir,
                                navigateTo = navigateTo,
                                handleRuntimeError = handleRuntimeError,
                                isBackNavigating = isBackNavigating,
                                modifier = modifier
                            )
                        }
                    }

                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.fillMaxHeight().navigationBarsPadding(),
                                verticalArrangement = Arrangement.SpaceEvenly
                            ) {
                                navButtons(currentRoute)
                            }
                            mainContent(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            mainContent(Modifier.weight(1f).fillMaxWidth())
                            Row(
                                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                navButtons(currentRoute)
                            }
                        }
                    }

                    // Overlays of various kinds

                    // Walkthrough spotlight overlay delegate
                    if (walkthroughCoordinator.walkthroughStep in 1..19) {
                        WalkthroughOverlay(
                            walkthroughCoordinator = walkthroughCoordinator,
                            currentRoute = currentRoute,
                            patternListTitle = viewModel.patternList.title,
                            hasLoadedPatternList = viewModel.hasLoadedPatternList,
                            maxW = maxW,
                            maxH = maxH
                        )
                    }

                    val currentAsyncStoppedMessage = viewModel.asyncStoppedMessage
                    if (currentAsyncStoppedMessage != null) {
                        JlStoppedDialog(
                            message = currentAsyncStoppedMessage,
                            onDismiss = { viewModel.asyncStoppedMessage = null }
                        )
                    }

                    val currentAsyncErrorMessage = viewModel.asyncErrorMessage
                    if (currentAsyncErrorMessage != null) {
                        JlErrorDialog(
                            errorMessage = currentAsyncErrorMessage,
                            onDismiss = { viewModel.asyncErrorMessage = null }
                        )
                    }

                    var showSpinner by remember { mutableStateOf(false) }
                    LaunchedEffect(viewModel.isProcessing) {
                        if (viewModel.isProcessing) {
                            // 200ms delay to show the busy spinner
                            kotlinx.coroutines.delay(200.milliseconds)
                            showSpinner = true
                        } else {
                            showSpinner = false
                        }
                    }
                    if (showSpinner) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
