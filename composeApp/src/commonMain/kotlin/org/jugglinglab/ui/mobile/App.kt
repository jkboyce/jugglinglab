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
import org.jugglinglab.core.OkioJmlStorageRepository
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.core.ThemeSetting
import org.jugglinglab.generator.SiteswapGenerator
import org.jugglinglab.generator.SiteswapTransitioner
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.JmlPatternList.PatternRecord
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.notation.Pattern
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.ui.common.*
import org.jugglinglab.ui.components.JlErrorDialog
import org.jugglinglab.util.jlShareUrl
import org.jugglinglab.util.jlShareFile
import org.jugglinglab.util.buildShareUrl
import org.jugglinglab.util.decodeShareUrl
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(
    prefsRepo: StoredPreferencesRepository? = null,
    localFilesDir: Path? = null,
    startUrl: String? = null,
    startJmlContent: String? = null,
    onUrlHandled: () -> Unit = {},
    onJmlContentHandled: () -> Unit = {},
    isMigrationDialogShown: Boolean = false,
) {
    // theme settings
    val themeSetting by (prefsRepo?.themeSettingFlow ?: flowOf(ThemeSetting.SYSTEM)).collectAsState(
        initial = ThemeSetting.SYSTEM
    )
    val useDarkTheme = when (themeSetting) {
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()

    val coroutineScope = rememberCoroutineScope()

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val jmlStorageRepository = remember { OkioJmlStorageRepository() }
            val navController = rememberNavController()
            val navigateTo = { newView: String ->
                navController.navigate(newView) {
                    launchSingleTop = true
                }
            }
            var asyncErrorMessage by retain { mutableStateOf<String?>(null) }
            var startupErrorMessage by retain { mutableStateOf<String?>(null) }
            var isProcessing by retain { mutableStateOf(false) }
            var isPatternListEditable by retain { mutableStateOf(true) }
            var patternListPath by retain { mutableStateOf<Path?>(null) }
            var favoritesHashCodes by retain { mutableStateOf<Set<Int>>(emptySet()) }
            var hasLoadedFavorites by retain { mutableStateOf(false) }
            var hasLoadedPatternList by retain { mutableStateOf(false) }

            val state = retain {
                val pattern = SiteswapPattern().fromString("3").asJmlPattern()
                PatternAnimationState(pattern, AnimationPrefs())
            }

            // Onboarding walkthrough coordinator
            val walkthroughCoordinator = remember(prefsRepo, state) {
                WalkthroughCoordinator(
                    prefsRepo = prefsRepo,
                    state = state,
                    coroutineScope = coroutineScope,
                    onNavigateTo = navigateTo
                )
            }

            val onboardingCompleted by (prefsRepo?.onboardingCompletedFlow
                ?: flowOf(true)).collectAsState(
                initial = true
            )

            LaunchedEffect(onboardingCompleted, isMigrationDialogShown) {
                if (!onboardingCompleted && !isMigrationDialogShown && walkthroughCoordinator.walkthroughStep == 0) {
                    walkthroughCoordinator.startWalkthrough()
                }
            }
            val animationController = retain(state) { AnimationController(state) }
            val patternList = retain { JmlPatternList() }
            val favoritesList = retain { JmlPatternList() }
            var patternListScrollState by retain { mutableStateOf(LazyListState()) }
            val favoritesListScrollState = retain { LazyListState() }

            val saveFavoritesList = {
                val path = localFilesDir?.let { it / "Favorites.jml" }
                if (path != null) {
                    coroutineScope.launch {
                        try {
                            jmlStorageRepository.saveList(path, favoritesList)
                            favoritesHashCodes =
                                favoritesList.model.filter { it.canAnimate }.map { it.jlHashCode }
                                    .toSet()
                        } catch (e: Exception) {
                            asyncErrorMessage =
                                getString(Res.string.error_mobile_saving, e.message ?: "")
                        }
                    }
                }
            }

            val loadFavoritesList: suspend (onError: (String) -> Unit, onSuccess: () -> Unit) -> Unit =
                { onError, onSuccess ->
                    try {
                        if (localFilesDir != null) {
                            val path = localFilesDir / "Favorites.jml"
                            jmlStorageRepository.initializeFavorites(path, DEFAULT_FAVORITES_JML)
                            val pl = jmlStorageRepository.loadList(path)
                            if (pl.title == null) {
                                pl.title = "Favorites"
                            }
                            favoritesList.clearModel()
                            favoritesList.title = pl.title
                            for (i in 0 until pl.size) {
                                val item = pl.getLine(i)
                                if (item != null) {
                                    favoritesList.addLine(-1, item)
                                }
                            }
                            favoritesHashCodes = favoritesList.model.filter { it.canAnimate }
                                .map { it.jlHashCode }.toSet()
                            onSuccess()
                        } else {
                            onError(getString(Res.string.error_mobile_local_storage))
                        }
                    } catch (e: Exception) {
                        onError(
                            getString(
                                Res.string.error_mobile_loading_favorites,
                                e.message ?: ""
                            )
                        )
                    }
                }

            LaunchedEffect(Unit) {
                if (!hasLoadedFavorites) {
                    loadFavoritesList(
                        { errorMessage -> startupErrorMessage = errorMessage },
                        { hasLoadedFavorites = true }
                    )
                }
            }

            // Handle a share URL passed in at launch (Android deep-link)
            LaunchedEffect(startUrl) {
                if (startUrl != null) {
                    coroutineScope.launch(Dispatchers.Default) {
                        isProcessing = true
                        try {
                            val (pattern, prefs) = decodeShareUrl(startUrl)
                            if (pattern != null) {
                                animationController.restartJuggle(pattern = pattern, prefs = prefs)
                                state.addCurrentToUndoList()
                                withContext(Dispatchers.Main) {
                                    navController.navigate("Animation") {
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                asyncErrorMessage =
                                    getString(Res.string.error_mobile_load_shared_pattern)
                            }
                        } catch (e: Exception) {
                            asyncErrorMessage =
                                getString(Res.string.error_mobile_loading_pattern, e.message ?: "")
                        } finally {
                            isProcessing = false
                            onUrlHandled()
                        }
                    }
                }
            }

            // Handle imported JML content (Android ACTION_VIEW intents)
            LaunchedEffect(startJmlContent) {
                if (startJmlContent != null) {
                    coroutineScope.launch(Dispatchers.Default) {
                        isProcessing = true
                        try {
                            val parser = JmlParser()
                            parser.parse(startJmlContent)

                            when (parser.fileType) {
                                JmlParser.JML_PATTERN -> {
                                    val pat = JmlPattern.fromJmlNode(parser.tree!!)
                                    pat.layout
                                    animationController.restartJuggle(pattern = pat)
                                    state.addCurrentToUndoList()
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("Animation") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                                JmlParser.JML_LIST -> {
                                    val pl = JmlPatternList(parser.tree)
                                    patternList.clearModel()
                                    patternList.title = pl.title
                                    for (i in 0 until pl.size) {
                                        val item = pl.getLine(i)
                                        if (item != null) {
                                            patternList.addLine(-1, item)
                                        }
                                    }
                                    isPatternListEditable = true
                                    patternListPath = null
                                    patternListScrollState = LazyListState()
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("PatternList") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                                else -> {
                                    asyncErrorMessage = getString(Res.string.error_invalid_jml)
                                }
                            }
                        } catch (e: Exception) {
                            asyncErrorMessage = getString(
                                Res.string.error_mobile_reading_imported_file,
                                e.message ?: ""
                            )
                        } finally {
                            isProcessing = false
                            onJmlContentHandled()
                        }
                    }
                }
            }

            // remembered objects for "generator" panel
            val generator = retain { SiteswapGenerator() }
            val transitioner = retain { SiteswapTransitioner() }
            val generatorState = retain { SiteswapGeneratorState() }
            val transitionerState = retain { SiteswapTransitionerState() }
            val combinedState = retain { GeneratorControlCombinedState() }

            val savePatternList = {
                val path = patternListPath
                if (path != null) {
                    coroutineScope.launch {
                        try {
                            jmlStorageRepository.saveList(path, patternList)
                        } catch (e: Exception) {
                            asyncErrorMessage =
                                getString(Res.string.error_mobile_saving, e.message ?: "")
                        }
                    }
                }
            }

            val addToFavoritesRecord: (PatternRecord) -> Unit = { record ->
                coroutineScope.launch(Dispatchers.Default) {
                    isProcessing = true
                    try {
                        val displaySize =
                            if (JmlPatternList.BLANK_AT_END) favoritesList.model.size - 1 else favoritesList.model.size
                        var newRecord = record
                        if (!(record.notation?.equals("jml", ignoreCase = true) ?: true)) {
                            // rewrite animation parameters in canonical ordering
                            val newAnim =
                                Pattern.newPattern(record.notation).fromString(record.anim!!)
                                    .toCanonicalString()
                            newRecord = record.copy(anim = newAnim)
                        }
                        favoritesList.model.add(displaySize, newRecord)
                        saveFavoritesList()
                    } finally {
                        isProcessing = false
                    }
                }
            }

            val removeFromFavoritesRecord: (PatternRecord) -> Unit = { record ->
                coroutineScope.launch(Dispatchers.Default) {
                    isProcessing = true
                    try {
                        val index =
                            favoritesList.model.indexOfFirst { it.jlHashCode == record.jlHashCode }
                        if (index != -1) {
                            favoritesList.model.removeAt(index)
                            saveFavoritesList()
                        }
                    } finally {
                        isProcessing = false
                    }
                }
            }

            val addToFavoritesPattern: (JmlPattern, AnimationPrefs) -> Unit = { pattern, prefs ->
                coroutineScope.launch(Dispatchers.Default) {
                    isProcessing = true
                    try {
                        val tempPl = JmlPatternList()
                        tempPl.insertPattern(pattern, prefs, 0)
                        addToFavoritesRecord(tempPl.model[0])
                    } catch (e: Exception) {
                        asyncErrorMessage =
                            getString(Res.string.error_mobile_adding_favorites, e.message ?: "")
                    } finally {
                        isProcessing = false
                    }
                }
            }

            val removeFromFavoritesPattern: (JmlPattern, AnimationPrefs) -> Unit =
                { pattern, prefs ->
                    coroutineScope.launch(Dispatchers.Default) {
                        isProcessing = true
                        try {
                            val tempPl = JmlPatternList()
                            tempPl.insertPattern(pattern, prefs, 0)
                            removeFromFavoritesRecord(tempPl.model[0])
                        } catch (e: Exception) {
                            asyncErrorMessage =
                                getString(Res.string.error_mobile_saving, e.message ?: "")
                        } finally {
                            isProcessing = false
                        }
                    }
                }

            val onShare: () -> Unit = {
                coroutineScope.launch(Dispatchers.Default) {
                    isProcessing = true
                    try {
                        val url = buildShareUrl(state.pattern, state.prefs)
                        if (url.encodeToByteArray().size > 2000) {
                            asyncErrorMessage = getString(Res.string.error_mobile_pattern_too_long)
                        } else {
                            val title = state.pattern.title?.takeIf { it.isNotBlank() }
                                ?: getString(Res.string.gui_pattern).lowercase()
                            val subject = getString(Res.string.gui_mobile_share_subject, title)
                            val htmlText = getString(Res.string.gui_mobile_share_html, url, title)
                            jlShareUrl(url, subject = subject, htmlText = htmlText)
                        }
                    } catch (e: Exception) {
                        asyncErrorMessage =
                            getString(Res.string.error_mobile_sharing, e.message ?: "")
                    } finally {
                        isProcessing = false
                    }
                }
            }

            // Export the currently animated pattern as a .jml file.
            val onExport: () -> Unit = {
                coroutineScope.launch(Dispatchers.Default) {
                    isProcessing = true
                    try {
                        val title = state.pattern.title?.takeIf { it.isNotBlank() }
                            ?: getString(Res.string.gui_pattern).lowercase()
                        jlShareFile(
                            content = state.pattern.toString(),
                            filename = "$title.jml",
                            mimeType = "application/xml",
                            subject = getString(Res.string.gui_mobile_share_subject, title)
                        )
                    } catch (e: Exception) {
                        asyncErrorMessage =
                            getString(Res.string.error_mobile_exporting, e.message ?: "")
                    } finally {
                        isProcessing = false
                    }
                }
            }

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
                        NavHost(
                            navController = navController,
                            startDestination = "Info",
                            modifier = modifier
                        ) {
                            composable("Animation") {
                                AnimationViewCombined(
                                    state = state,
                                    animationController = animationController,
                                    colorScheme = MaterialTheme.colorScheme,
                                    favoritesHashCodes = favoritesHashCodes,
                                    onAddToFavorites = addToFavoritesPattern,
                                    onRemoveFromFavorites = removeFromFavoritesPattern,
                                    onShare = onShare,
                                    onExport = onExport,
                                    onBusy = { isProcessing = it }
                                )
                            }

                            composable("Notation") {
                                NotationScreen(
                                    state = state,
                                    animationController = animationController,
                                    onNavigateToAnimation = { navigateTo("Animation") },
                                    onBusyChange = { isProcessing = it },
                                    onError = { asyncErrorMessage = it }
                                )
                            }

                            composable("PatternList") {
                                PatternListScreen(
                                    patternList = patternList,
                                    favoritesHashCodes = favoritesHashCodes,
                                    state = state,
                                    animationController = animationController,
                                    isPatternListEditable = isPatternListEditable,
                                    onIsEditableChange = { isPatternListEditable = it },
                                    patternListPath = patternListPath,
                                    onPathChange = { patternListPath = it },
                                    listState = patternListScrollState,
                                    onListStateChange = { patternListScrollState = it },
                                    hasLoadedPatternList = hasLoadedPatternList,
                                    onHasLoadedChange = { hasLoadedPatternList = it },
                                    localFilesDir = localFilesDir,
                                    onNavigateTo = { navigateTo(it) },
                                    onBusyChange = { isProcessing = it },
                                    onError = { asyncErrorMessage = it },
                                    onAddToFavorites = addToFavoritesRecord,
                                    onRemoveFromFavorites = removeFromFavoritesRecord,
                                    savePatternList = savePatternList,
                                    jmlStorageRepository = jmlStorageRepository
                                )
                            }

                            composable("Favorites") {
                                FavoritesScreen(
                                    favoritesList = favoritesList,
                                    favoritesHashCodes = favoritesHashCodes,
                                    state = state,
                                    animationController = animationController,
                                    listState = favoritesListScrollState,
                                    localFilesDir = localFilesDir,
                                    patternList = patternList,
                                    onIsEditableChange = { isPatternListEditable = it },
                                    onPathChange = { patternListPath = it },
                                    onHasLoadedChange = { hasLoadedPatternList = it },
                                    onPatternListScrollStateChange = {
                                        patternListScrollState = it
                                    },
                                    onNavigateTo = { navigateTo(it) },
                                    onBusyChange = { isProcessing = it },
                                    onError = { asyncErrorMessage = it },
                                    onAddToFavorites = addToFavoritesRecord,
                                    onRemoveFromFavorites = removeFromFavoritesRecord,
                                    saveFavoritesList = saveFavoritesList,
                                    jmlStorageRepository = jmlStorageRepository
                                )
                            }

                            composable("FileChooser") {
                                FileChooserScreen(
                                    state = state,
                                    animationController = animationController,
                                    patternList = patternList,
                                    onIsEditableChange = { isPatternListEditable = it },
                                    onPathChange = { patternListPath = it },
                                    onHasLoadedChange = { hasLoadedPatternList = it },
                                    onPatternListScrollStateChange = {
                                        patternListScrollState = it
                                    },
                                    localFilesDir = localFilesDir,
                                    onNavigateTo = { navigateTo(it) },
                                    onBusyChange = { isProcessing = it },
                                    onError = { asyncErrorMessage = it },
                                    jmlStorageRepository = jmlStorageRepository
                                )
                            }

                            composable("Generator") {
                                GeneratorScreen(
                                    isBusy = isProcessing,
                                    generator = generator,
                                    transitioner = transitioner,
                                    patternList = patternList,
                                    onIsEditableChange = { isPatternListEditable = it },
                                    onPathChange = { patternListPath = it },
                                    onHasLoadedChange = { hasLoadedPatternList = it },
                                    onPatternListScrollStateChange = {
                                        patternListScrollState = it
                                    },
                                    generatorState = generatorState,
                                    transitionerState = transitionerState,
                                    combinedState = combinedState,
                                    onNavigateTo = { navigateTo(it) },
                                    onBusyChange = { isProcessing = it },
                                    onError = { asyncErrorMessage = it }
                                )
                            }

                            composable("Info") {
                                InfoScreen(
                                    themeSetting = themeSetting,
                                    prefsRepo = prefsRepo,
                                    onNavigateTo = { navigateTo(it) },
                                    onStartWalkthrough = { walkthroughCoordinator.startWalkthrough() }
                                )
                            }
                        }
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

                // Walkthrough spotlight overlay delegate
                if (walkthroughCoordinator.walkthroughStep in 1..18) {
                    WalkthroughOverlay(
                        walkthroughCoordinator = walkthroughCoordinator,
                        currentRoute = currentRoute,
                        patternListTitle = patternList.title,
                        maxW = maxW,
                        maxH = maxH
                    )
                }

                val currentAsyncErrorMessage = asyncErrorMessage
                if (currentAsyncErrorMessage != null) {
                    JlErrorDialog(
                        errorMessage = currentAsyncErrorMessage,
                        onDismiss = { asyncErrorMessage = null }
                    )
                }

                val currentStartupErrorMessage = startupErrorMessage
                if (currentStartupErrorMessage != null) {
                    JlErrorDialog(
                        errorMessage = currentStartupErrorMessage,
                        onDismiss = { kotlin.system.exitProcess(0) }
                    )
                }

                var showSpinner by remember { mutableStateOf(false) }
                LaunchedEffect(isProcessing) {
                    if (isProcessing) {
                        // 200ms delay to show the busy spinner
                        kotlinx.coroutines.delay(200)
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

private const val DEFAULT_FAVORITES_JML = """<?xml version="1.0"?>
<!DOCTYPE jml SYSTEM "file://jml.dtd">
<jml version="3">
<patternlist>
<title>Favorites</title>
</patternlist>
</jml>
"""
