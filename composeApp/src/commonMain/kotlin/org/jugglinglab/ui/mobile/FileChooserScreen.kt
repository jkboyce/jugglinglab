//
// FileChooserScreen.kt
//
// Screen wrapper for JmlFileChooser in Juggling Lab.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.ui.common.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path

@Composable
fun FileChooserScreen(
    state: PatternAnimationState,
    animationController: AnimationController,
    patternList: JmlPatternList,
    onIsEditableChange: (Boolean) -> Unit,
    onPathChange: (Path?) -> Unit,
    onHasLoadedChange: (Boolean) -> Unit,
    onPatternListScrollStateChange: (LazyListState) -> Unit,
    localFilesDir: Path?,
    onNavigateTo: (String) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    jmlStorageRepository: org.jugglinglab.core.JmlStorageRepository
) {
    val coroutineScope = rememberCoroutineScope()

    JmlFileChooser(
        onPatternLoaded = { pattern ->
            coroutineScope.launch(Dispatchers.Default) {
                onBusyChange(true)
                try {
                    animationController.restartJuggle(pattern = pattern)
                    state.addCurrentToUndoList()
                    withContext(Dispatchers.Main) {
                        onNavigateTo("Animation")
                    }
                } catch (e: Exception) {
                    onError(e.message)
                } finally {
                    onBusyChange(false)
                }
            }
        },
        onNewListClick = {
            patternList.clearModel()
            patternList.title = null
            onIsEditableChange(true)
            onPathChange(null)
            onHasLoadedChange(true)
            onPatternListScrollStateChange(LazyListState())
            onNavigateTo("PatternList")
        },
        onPatternListLoaded = { newPatternList, isEditable, path ->
            coroutineScope.launch(Dispatchers.Default) {
                onBusyChange(true)
                try {
                    patternList.clearModel()
                    patternList.title = newPatternList.title
                    for (i in 0 until newPatternList.size) {
                        val item = newPatternList.getLine(i)
                        if (item != null) {
                            patternList.addLine(-1, item)
                        }
                    }
                    onIsEditableChange(isEditable)
                    onPathChange(path)
                    onHasLoadedChange(true)
                    onPatternListScrollStateChange(LazyListState())
                    withContext(Dispatchers.Main) {
                        onNavigateTo("PatternList")
                    }
                } finally {
                    onBusyChange(false)
                }
            }
        },
        onError = onError,
        localFilesDir = localFilesDir,
        jmlStorageRepository = jmlStorageRepository
    )
}
