//
// AppViewModel.kt
//
// Shared ViewModel / State Holder for the mobile application.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.OkioJmlStorageRepository
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.generator.SiteswapGenerator
import org.jugglinglab.generator.SiteswapTransitioner
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.JmlPatternList.PatternRecord
import org.jugglinglab.notation.Pattern
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.ui.common.AnimationController
import org.jugglinglab.ui.common.SiteswapGeneratorState
import org.jugglinglab.ui.common.SiteswapTransitionerState
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.buildShareUrl
import org.jugglinglab.util.jlShareFile
import org.jugglinglab.util.jlShareUrl
import org.jugglinglab.util.jlSanitizeFilename
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import okio.Path
import org.jetbrains.compose.resources.getString

class AppViewModel(
    val localFilesDir: Path?
) {
    var onError: ((Throwable) -> Unit)? = null

    //--------------------------------------------------------------------------
    // App State Variables
    //--------------------------------------------------------------------------

    // local storage
    val jmlStorageRepository = OkioJmlStorageRepository()

    // animation
    val state = run {
        val pattern = SiteswapPattern().fromString("3").asJmlPattern()
        PatternAnimationState(pattern, AnimationPrefs())
    }
    val animationController = AnimationController(state)

    // pattern list and favorites list
    val patternList = JmlPatternList()
    val favoritesList = JmlPatternList()
    var isPatternListEditable by mutableStateOf(true)
    var patternListPath by mutableStateOf<Path?>(null)
    var favoritesHashCodes by mutableStateOf<Set<Int>>(emptySet())
    var hasLoadedFavorites by mutableStateOf(false)
    var hasLoadedPatternList by mutableStateOf(false)
    var patternListScrollState by mutableStateOf(LazyListState())
    val favoritesListScrollState = LazyListState()

    // generator
    val generator = SiteswapGenerator()
    val transitioner = SiteswapTransitioner()
    val generatorState = SiteswapGeneratorState()
    val transitionerState = SiteswapTransitionerState()
    val combinedState = GeneratorControlCombinedState()
    var generatorJob: Job? = null

    // display messages and busy spinner
    var asyncStoppedMessage by mutableStateOf<String?>(null)
    var asyncErrorMessage by mutableStateOf<String?>(null)
    var isProcessing by mutableStateOf(false)

    //--------------------------------------------------------------------------
    // Helper Methods
    //--------------------------------------------------------------------------

    fun stopGenerator() {
        generatorJob?.let {
            it.cancel()
            generatorJob = null
        }
    }

    fun saveFavoritesList(scope: CoroutineScope) {
        val path = localFilesDir?.let { it / "Favorites.jml" }
        if (path != null) {
            scope.launch {
                try {
                    jmlStorageRepository.saveList(path, favoritesList)
                    favoritesHashCodes =
                        favoritesList.model.filter { it.canAnimate }.map { it.jlHashCode }
                            .toSet()
                } catch (e: Throwable) {
                    val message = getString(Res.string.error_mobile_saving, e.message ?: "")
                    onError?.invoke(JuggleExceptionInternal(message))
                }
            }
        }
    }

    suspend fun loadFavoritesList(onSuccess: () -> Unit) {
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
                onError?.invoke(JuggleExceptionInternal(getString(Res.string.error_mobile_local_storage)))
            }
        } catch (e: Throwable) {
            val message = getString(Res.string.error_mobile_loading_favorites, e.message ?: "")
            onError?.invoke(JuggleExceptionInternal(message))
        }
    }

    suspend fun loadStaticPatternList(filename: String, displayName: String) {
        isProcessing = true
        try {
            val bytes = Res.readBytes("files/$filename")
            val text = bytes.decodeToString()

            val parser = JmlParser()
            parser.parse(text)

            if (parser.fileType == JmlParser.JML_LIST) {
                val pl = JmlPatternList(parser.tree)
                patternList.clearModel()
                patternList.title = pl.title ?: displayName
                for (i in 0 until pl.size) {
                    val item = pl.getLine(i)
                    if (item != null) {
                        patternList.addLine(-1, item)
                    }
                }
                isPatternListEditable = false
                patternListPath = null
                hasLoadedPatternList = true
                patternListScrollState = LazyListState()
            }
        } catch (e: Throwable) {
            //val message = getString(Res.string.error_mobile_reading_imported_file, e.message ?: "")
            onError?.invoke(e)
        } finally {
            isProcessing = false
        }
    }

    fun savePatternList(scope: CoroutineScope) {
        val path = patternListPath
        if (path != null) {
            scope.launch {
                try {
                    jmlStorageRepository.saveList(path, patternList)
                } catch (e: Throwable) {
                    //val message = getString(Res.string.error_mobile_saving, e.message ?: "")
                    onError?.invoke(e)
                }
            }
        }
    }

    fun addToFavoritesRecord(record: PatternRecord, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
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
                saveFavoritesList(scope)
            } catch (e: Throwable) {
                onError?.invoke(e)
            } finally {
                isProcessing = false
            }
        }
    }

    fun removeFromFavoritesRecord(record: PatternRecord, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            isProcessing = true
            try {
                val index =
                    favoritesList.model.indexOfFirst { it.jlHashCode == record.jlHashCode }
                if (index != -1) {
                    favoritesList.model.removeAt(index)
                    saveFavoritesList(scope)
                }
            } catch (e: Throwable) {
                onError?.invoke(e)
            } finally {
                isProcessing = false
            }
        }
    }

    fun addToFavoritesPattern(pattern: JmlPattern, prefs: AnimationPrefs, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            isProcessing = true
            try {
                val tempPl = JmlPatternList()
                tempPl.insertPattern(pattern, prefs, 0)
                addToFavoritesRecord(tempPl.model[0], scope)
            } catch (e: Throwable) {
                //val message = getString(Res.string.error_mobile_adding_favorites, e.message ?: "")
                onError?.invoke(e)
            } finally {
                isProcessing = false
            }
        }
    }

    fun removeFromFavoritesPattern(pattern: JmlPattern, prefs: AnimationPrefs, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            isProcessing = true
            try {
                val tempPl = JmlPatternList()
                tempPl.insertPattern(pattern, prefs, 0)
                removeFromFavoritesRecord(tempPl.model[0], scope)
            } catch (e: Throwable) {
                // val message = getString(Res.string.error_mobile_saving, e.message ?: "")
                onError?.invoke(e)
            } finally {
                isProcessing = false
            }
        }
    }

    fun onShare(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
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
            } catch (e: Throwable) {
                //val message = getString(Res.string.error_mobile_sharing, e.message ?: "")
                onError?.invoke(e)
            } finally {
                isProcessing = false
            }
        }
    }

    // Export the currently animated pattern as a .jml file.
    fun onExport(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            isProcessing = true
            try {
                val title = state.pattern.title?.takeIf { it.isNotBlank() }
                    ?: getString(Res.string.gui_pattern).lowercase()
                jlShareFile(
                    content = state.pattern.toString(),
                    filename = jlSanitizeFilename("$title.jml"),
                    mimeType = "application/xml",
                    subject = getString(Res.string.gui_mobile_share_subject, title)
                )
            } catch (e: Throwable) {
                //val message = getString(Res.string.error_mobile_exporting, e.message ?: "")
                onError?.invoke(e)
            } finally {
                isProcessing = false
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
