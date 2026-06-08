//
// AppNavHost.kt
//
// Composable component defining the main navigation host and screen mappings.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.core.ThemeSetting
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Path

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    walkthroughCoordinator: WalkthroughCoordinator,
    themeSetting: ThemeSetting,
    prefsRepo: StoredPreferencesRepository?,
    localFilesDir: Path?,
    navigateTo: (String) -> Unit,
    handleRuntimeError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = "Info",
        modifier = modifier
    ) {
        composable("Animation") {
            AnimationViewCombined(
                state = viewModel.state,
                animationController = viewModel.animationController,
                colorScheme = MaterialTheme.colorScheme,
                favoritesHashCodes = viewModel.favoritesHashCodes,
                onAddToFavorites = { p, pr -> viewModel.addToFavoritesPattern(p, pr, coroutineScope) },
                onRemoveFromFavorites = { p, pr -> viewModel.removeFromFavoritesPattern(p, pr, coroutineScope) },
                onShare = { viewModel.onShare(coroutineScope) },
                onExport = { viewModel.onExport(coroutineScope) },
                onBusy = { viewModel.isProcessing = it },
                onError = handleRuntimeError
            )
        }

        composable("Notation") {
            NotationScreen(
                state = viewModel.state,
                animationController = viewModel.animationController,
                onNavigateToAnimation = { navigateTo("Animation") },
                onBusyChange = { viewModel.isProcessing = it },
                onError = handleRuntimeError
            )
        }

        composable("PatternList") {
            PatternListScreen(
                patternList = viewModel.patternList,
                favoritesHashCodes = viewModel.favoritesHashCodes,
                state = viewModel.state,
                animationController = viewModel.animationController,
                isPatternListEditable = viewModel.isPatternListEditable,
                onIsEditableChange = { viewModel.isPatternListEditable = it },
                patternListPath = viewModel.patternListPath,
                onPathChange = { viewModel.patternListPath = it },
                listState = viewModel.patternListScrollState,
                onListStateChange = { viewModel.patternListScrollState = it },
                hasLoadedPatternList = viewModel.hasLoadedPatternList,
                onHasLoadedChange = { hasLoaded ->
                    if (!hasLoaded) {
                        viewModel.stopGenerator()
                    }
                    viewModel.hasLoadedPatternList = hasLoaded
                },
                localFilesDir = localFilesDir,
                onNavigateTo = { navigateTo(it) },
                onBusyChange = { viewModel.isProcessing = it },
                onError = handleRuntimeError,
                onAddToFavorites = { viewModel.addToFavoritesRecord(it, coroutineScope) },
                onRemoveFromFavorites = { viewModel.removeFromFavoritesRecord(it, coroutineScope) },
                savePatternList = { viewModel.savePatternList(coroutineScope) },
                jmlStorageRepository = viewModel.jmlStorageRepository
            )
        }

        composable("Favorites") {
            FavoritesScreen(
                favoritesList = viewModel.favoritesList,
                favoritesHashCodes = viewModel.favoritesHashCodes,
                state = viewModel.state,
                animationController = viewModel.animationController,
                listState = viewModel.favoritesListScrollState,
                localFilesDir = localFilesDir,
                patternList = viewModel.patternList,
                onIsEditableChange = { viewModel.isPatternListEditable = it },
                onPathChange = { viewModel.patternListPath = it },
                onHasLoadedChange = { viewModel.hasLoadedPatternList = it },
                onPatternListScrollStateChange = {
                    viewModel.patternListScrollState = it
                },
                onNavigateTo = { navigateTo(it) },
                onBusyChange = { viewModel.isProcessing = it },
                onError = handleRuntimeError,
                onAddToFavorites = { viewModel.addToFavoritesRecord(it, coroutineScope) },
                onRemoveFromFavorites = { viewModel.removeFromFavoritesRecord(it, coroutineScope) },
                saveFavoritesList = { viewModel.saveFavoritesList(coroutineScope) },
                jmlStorageRepository = viewModel.jmlStorageRepository
            )
        }

        composable("Generator") {
            GeneratorScreen(
                isBusy = viewModel.isProcessing,
                generator = viewModel.generator,
                transitioner = viewModel.transitioner,
                patternList = viewModel.patternList,
                onIsEditableChange = { viewModel.isPatternListEditable = it },
                onPathChange = { viewModel.patternListPath = it },
                onHasLoadedChange = { viewModel.hasLoadedPatternList = it },
                onPatternListScrollStateChange = {
                    viewModel.patternListScrollState = it
                },
                generatorState = viewModel.generatorState,
                transitionerState = viewModel.transitionerState,
                combinedState = viewModel.combinedState,
                onNavigateTo = { navigateTo(it) },
                onBusyChange = { viewModel.isProcessing = it },
                onError = handleRuntimeError,
                coroutineScope = coroutineScope,
                onGeneratorJobChange = { viewModel.generatorJob = it }
            )
        }

        composable("Info") {
            InfoScreen(
                themeSetting = themeSetting,
                prefsRepo = prefsRepo,
                onNavigateTo = { route ->
                    if (route == "FileChooser") {
                        coroutineScope.launch(Dispatchers.Default) {
                            viewModel.loadStaticPatternList(
                                "basic_how to.jml",
                                "How to Juggle"
                            )
                        }
                        navigateTo("PatternList")
                    } else {
                        navigateTo(route)
                    }
                },
                onStartWalkthrough = { walkthroughCoordinator.startWalkthrough() }
            )
        }
    }
}
