//
// PatternListScreen.kt
//
// Screen wrapper for PatternListView in Juggling Lab.
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
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlErrorIfNotSanitized
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.getString

@Composable
fun PatternListScreen(
    patternList: JmlPatternList,
    favoritesHashCodes: Set<Int>,
    state: PatternAnimationState,
    animationController: AnimationController,
    isPatternListEditable: Boolean,
    onIsEditableChange: (Boolean) -> Unit,
    patternListPath: Path?,
    onPathChange: (Path?) -> Unit,
    listState: LazyListState,
    onListStateChange: (LazyListState) -> Unit,
    hasLoadedPatternList: Boolean,
    onHasLoadedChange: (Boolean) -> Unit,
    localFilesDir: Path?,
    onNavigateTo: (String) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit,
    onAddToFavorites: (PatternRecord) -> Unit,
    onRemoveFromFavorites: (PatternRecord) -> Unit,
    savePatternList: () -> Unit,
    jmlStorageRepository: org.jugglinglab.core.JmlStorageRepository
) {
    val coroutineScope = rememberCoroutineScope()

    if (!hasLoadedPatternList) {
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
                    } catch (e: Throwable) {
                        onError(e)
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
                onListStateChange(LazyListState())
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
                        onListStateChange(LazyListState())
                    } catch (e: Throwable) {
                        onError(e)
                    } finally {
                        onBusyChange(false)
                    }
                }
            },
            onError = onError,
            localFilesDir = localFilesDir,
            jmlStorageRepository = jmlStorageRepository
        )
    } else {
        PatternListView(
            patternList = patternList,
            favoritesHashCodes = favoritesHashCodes,
            state = state,
            isEditable = isPatternListEditable,
            patternListPath = patternListPath,
            listState = listState,
            onCloseClick = {
                onHasLoadedChange(false)
            },
            onItemClick = { index, _ ->
                coroutineScope.launch(Dispatchers.Default) {
                    onBusyChange(true)
                    try {
                        val newPattern = patternList.getPatternForLine(index) ?: return@launch
                        newPattern.layout
                        val ap = patternList.getAnimationPrefsForLine(index)
                        animationController.restartJuggle(pattern = newPattern, prefs = ap)
                        state.addCurrentToUndoList()
                        withContext(Dispatchers.Main) {
                            onNavigateTo("Animation")
                        }
                    } catch (e: Throwable) {
                        onError(e)
                    } finally {
                        onBusyChange(false)
                    }
                }
            },
            onAddToFavorites = onAddToFavorites,
            onRemoveFromFavorites = onRemoveFromFavorites,
            onPatternListModified = savePatternList,
            onExportList = {
                coroutineScope.exportListHelper(
                    list = patternList,
                    path = patternListPath,
                    onBusyChange = onBusyChange,
                    onError = onError
                )
            },
            onSharePattern = { record ->
                coroutineScope.sharePatternHelper(
                    list = patternList,
                    record = record,
                    onBusyChange = onBusyChange,
                    onError = onError
                )
            },
            onExportPattern = { record ->
                coroutineScope.exportPatternHelper(
                    list = patternList,
                    record = record,
                    onBusyChange = onBusyChange,
                    onError = onError
                )
            },
            onDuplicateClick = {
                onIsEditableChange(true)
                onPathChange(null)
                onHasLoadedChange(true)
            },
            onSaveAsClick = { filename ->
                coroutineScope.launch {
                    if (localFilesDir != null) {
                        var finalName = filename.trim()
                        if (finalName.isNotEmpty()) {
                            if (!finalName.lowercase().endsWith(".jml")) {
                                finalName += ".jml"
                            }
                            try {
                                jlErrorIfNotSanitized(finalName)
                                val path = localFilesDir / finalName
                                if (jmlStorageRepository.exists(path)) {
                                    onError(JuggleExceptionUser(getString(Res.string.error_mobile_file_exists)))
                                } else {
                                    onPathChange(path)
                                    if (patternList.title == null) {
                                        patternList.title = finalName.removeSuffix(".jml")
                                    }
                                    savePatternList()
                                }
                            } catch (e: Throwable) {
                                onError(e)
                            }
                        }
                    } else {
                        onError(JuggleExceptionInternal(getString(Res.string.error_mobile_local_storage)))
                    }
                }
            },
            onRenameListClick = { filename ->
                coroutineScope.launch {
                    if (localFilesDir != null && patternListPath != null) {
                        var finalName = filename.trim()
                        if (finalName.isNotEmpty()) {
                            if (!finalName.lowercase().endsWith(".jml")) {
                                finalName += ".jml"
                            }
                            try {
                                jlErrorIfNotSanitized(finalName)
                                val newPath = localFilesDir / finalName
                                if (jmlStorageRepository.exists(newPath) && newPath != patternListPath) {
                                    onError(JuggleExceptionUser(getString(Res.string.error_mobile_file_exists)))
                                } else if (newPath != patternListPath) {
                                    val oldName = patternListPath.name.removeSuffix(".jml")
                                    jmlStorageRepository.renameFile(patternListPath, newPath)
                                    onPathChange(newPath)
                                    if (patternList.title == oldName) {
                                        patternList.title = finalName.removeSuffix(".jml")
                                    }
                                    savePatternList()
                                }
                            } catch (e: JuggleExceptionUser) {
                                onError(e)
                            } catch (e: Throwable) {
                                onError(
                                    JuggleExceptionInternal(
                                        getString(
                                            Res.string.error_mobile_renaming_file,
                                            e.message ?: ""
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        onError(JuggleExceptionInternal(getString(Res.string.error_mobile_local_storage)))
                    }
                }
            },
            onDeleteListClick = {
                if (patternListPath != null) {
                    coroutineScope.launch {
                        try {
                            jmlStorageRepository.delete(patternListPath)
                            patternList.clearModel()
                            patternList.title = null
                            onPathChange(null)
                            onHasLoadedChange(false)
                            onListStateChange(LazyListState())
                            onNavigateTo("FileChooser")
                        } catch (e: Throwable) {
                            onError(
                                JuggleExceptionInternal(
                                    getString(
                                        Res.string.error_mobile_deleting_file,
                                        e.message ?: ""
                                    )
                                )
                            )
                        }
                    }
                }
            }
        )
    }
}
