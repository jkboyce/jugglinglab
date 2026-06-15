//
// FavoritesScreen.kt
//
// Screen wrapper for Juggling Lab user favorites lists.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.JmlPatternList.PatternRecord
import org.jugglinglab.ui.common.*
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.jlExitProcess
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.getString

@Composable
fun FavoritesScreen(
    favoritesList: JmlPatternList,
    favoritesHashCodes: Set<Int>,
    state: PatternAnimationState,
    animationController: AnimationController,
    listState: LazyListState,
    localFilesDir: Path?,
    patternList: JmlPatternList,
    onIsEditableChange: (Boolean) -> Unit,
    onPathChange: (Path?) -> Unit,
    onHasLoadedChange: (Boolean) -> Unit,
    onPatternListScrollStateChange: (LazyListState) -> Unit,
    onNavigateTo: (String) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit,
    onAddToFavorites: (PatternRecord) -> Unit,
    onRemoveFromFavorites: (PatternRecord) -> Unit,
    saveFavoritesList: () -> Unit,
    jmlStorageRepository: org.jugglinglab.core.JmlStorageRepository
) {
    val coroutineScope = rememberCoroutineScope()
 
    PatternListView(
        patternList = favoritesList,
        favoritesHashCodes = favoritesHashCodes,
        state = state,
        isEditable = true,
        isFavoritesList = true,
        patternListPath = localFilesDir?.let { it / "Favorites.jml" },
        listState = listState,
        onItemClick = { index, _ ->
            coroutineScope.launch {
                onBusyChange(true)
                try {
                    val newPat = withContext(Dispatchers.Default) {
                        val newPat = favoritesList.getPatternForLine(index)
                        newPat?.layout
                        newPat
                    } ?: return@launch
                    val ap = favoritesList.getAnimationPrefsForLine(index)
                    animationController.restartJuggle(pattern = newPat, prefs = ap)
                    state.addCurrentToUndoList()
                    onNavigateTo("Animation")
                } catch (e: Throwable) {
                    onError(e)
                } finally {
                    onBusyChange(false)
                }
            }
        },
        onAddToFavorites = onAddToFavorites,
        onRemoveFromFavorites = onRemoveFromFavorites,
        onPatternListModified = saveFavoritesList,
        onExportList = {
            coroutineScope.exportListHelper(
                list = favoritesList,
                path = localFilesDir?.let { it / "Favorites.jml" },
                onBusyChange = onBusyChange,
                onError = onError
            )
        },
        onSharePattern = { record ->
            coroutineScope.sharePatternHelper(
                list = favoritesList,
                record = record,
                onBusyChange = onBusyChange,
                onError = onError
            )
        },
        onExportPattern = { record ->
            coroutineScope.exportPatternHelper(
                list = favoritesList,
                record = record,
                onBusyChange = onBusyChange,
                onError = onError
            )
        },
        onDuplicateClick = {
            patternList.clearModel()
            patternList.title = "Favorites copy"
            for (i in 0 until favoritesList.size) {
                val item = favoritesList.getLine(i)
                if (item != null) {
                    patternList.addLine(-1, item)
                }
            }
            onIsEditableChange(true)
            onPathChange(null)
            onHasLoadedChange(true)
            onPatternListScrollStateChange(LazyListState())
            onNavigateTo("PatternList")
        },
        onDeleteFavoritesAndQuitClick = {
            if (localFilesDir != null) {
                coroutineScope.launch {
                    try {
                        val path = localFilesDir / "Favorites.jml"
                        if (jmlStorageRepository.exists(path)) {
                            jmlStorageRepository.delete(path)
                        }
                        jlExitProcess(0)
                    } catch (e: Throwable) {
                        val message = getString(
                            Res.string.error_mobile_deleting_favorites,
                            e.message ?: ""
                        )
                        onError(JuggleExceptionInternal(message))
                    }
                }
            } else {
                jlExitProcess(0)
            }
        }
    )
}
