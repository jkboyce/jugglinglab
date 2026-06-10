//
// JmlFileChooser.kt
//
// View for selecting and loading a JML file.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import okio.Path

@OptIn(ExperimentalResourceApi::class)
@Composable
fun JmlFileChooser(
    onPatternLoaded: (JmlPattern) -> Unit,
    onPatternListLoaded: (JmlPatternList, Boolean, Path?) -> Unit,
    onNewListClick: () -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    localFilesDir: Path? = null,
    jmlStorageRepository: org.jugglinglab.core.JmlStorageRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val localFiles = remember(localFilesDir) {
        val list = mutableListOf<Path>()
        if (localFilesDir != null && jmlStorageRepository.exists(localFilesDir)) {
            val paths = jmlStorageRepository.listFiles(localFilesDir)
            for (p in paths) {
                if (p.name.endsWith(".jml", ignoreCase = true) && p.name != "Favorites.jml") {
                    list.add(p)
                }
            }
        }
        list.sortedBy { it.name.lowercase() }
    }

    val staticFilesBasic = remember {
        listOf(
            "basic_how to.jml" to "How to Juggle",
            "basic_solo.jml" to "Solo Patterns",
            "basic_passing.jml" to "Passing Patterns",
            "basic_siteswaps.jml" to "Common Siteswaps",
        )
    }

    val staticFilesOther = remember {
        listOf(
            "Alanz_3BallBounce V 2Edit.jml" to "Alan's 3 Ball Bounce",
            "Alanz_Multiplex etcetera.jml" to "Alan's Multiplex Etcetera",
            "Alanz_Some Patterns Without 3's.jml" to "Alan's Patterns Without 3's",
            "Alanz_Synchronous Favorites.jml" to "Alan's Synchronous Favorites",
            "hss_2JugglersAsymmetric.jml" to "HSS: 2 Jugglers Asymmetric",
            "hss_2JugglersSymmetric.jml" to "HSS: 2 Jugglers Symmetric",
            "hss_2UnequalPassers.jml" to "HSS: 2 Unequal Passers",
            "hss_3JugglersAsymmetric.jml" to "HSS: 3 Jugglers Asymmetric",
            "hss_3JugglersSymmetric.jml" to "HSS: 3 Jugglers Symmetric",
            "hss_PrechacWeaves.jml" to "HSS: Prechac Weaves",
            "hss_TwoHandedPatterns.jml" to "HSS: Two Handed Patterns",
            "Are you God.jml" to "Are you God?",
            "Omnikrabundi_FunWithJugglingLab.jml" to "Fun with Juggling Lab",
            "arham_stupid jugging lab patterns.jml" to "Arham: Stupid Juggling Lab Patterns",
            "jboyce_Juggling Lab demo.jml" to "Juggling Lab Demo",
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.gui_mobile_nav_filechooser),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val onStaticFileClick: (String, String) -> Unit = { filename, displayName ->
            if (!isBusy) {
                isBusy = true
                coroutineScope.launch {
                    try {
                        val bytes = Res.readBytes("files/$filename")
                        val text = bytes.decodeToString()

                        val parser = JmlParser()
                        parser.parse(text)

                        when (parser.fileType) {
                            JmlParser.JML_PATTERN -> {
                                val pat = JmlPattern.fromJmlNode(parser.tree!!)
                                pat.layout
                                onPatternLoaded(pat)
                            }

                            JmlParser.JML_LIST -> {
                                val pl = JmlPatternList(parser.tree)
                                if (pl.title == null) {
                                    pl.title = displayName
                                }
                                onPatternListLoaded(pl, false, null)
                            }

                            else -> {
                                onError(JuggleExceptionInternal("Invalid JML file type"))
                            }
                        }
                    } catch (e: Throwable) {
                        val message = if (e is JuggleExceptionUser) e.message
                            ?: "Unknown User Error" else e.message ?: "Unknown Error"
                        onError(JuggleExceptionInternal("Error reading file: $message"))
                    } finally {
                        isBusy = false
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                item {
                    FileChooserHeader(
                        text = "My lists",
                        modifier = Modifier.walkthroughTarget("file_my_lists")
                    )
                }

                items(localFiles) { path ->
                    val displayName = path.name.removeSuffix(".jml")
                    FileChooserItem(
                        displayName = displayName,
                        isFavorites = displayName == "Favorites",
                        onClick = {
                            if (isBusy) return@FileChooserItem
                            isBusy = true
                            coroutineScope.launch {
                                try {
                                    val text = jmlStorageRepository.readFileText(path)
                                    val parser = JmlParser()
                                    parser.parse(text)

                                    when (parser.fileType) {
                                        JmlParser.JML_PATTERN -> {
                                            val pat = JmlPattern.fromJmlNode(parser.tree!!)
                                            pat.layout
                                            onPatternLoaded(pat)
                                        }

                                        JmlParser.JML_LIST -> {
                                            val pl = JmlPatternList(parser.tree)
                                            if (pl.title == null) {
                                                pl.title = displayName
                                            }
                                            onPatternListLoaded(pl, true, path)
                                        }

                                        else -> {
                                            onError(JuggleExceptionUser("Invalid JML file type"))
                                        }
                                    }
                                } catch (e: Throwable) {
                                    val message = if (e is JuggleExceptionUser) e.message
                                        ?: "Unknown User Error" else e.message ?: "Unknown Error"
                                    onError(JuggleExceptionUser("Error reading file: $message"))
                                } finally {
                                    isBusy = false
                                }
                            }
                        }
                    )
                }

                item {
                    FileChooserItem(
                        displayName = "<" + stringResource(Res.string.gui_new_pattern_list) + ">",
                        fontStyle = FontStyle.Italic,
                        onClick = onNewListClick
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (staticFilesBasic.isNotEmpty()) {
                    item {
                        FileChooserHeader("Pattern library", showDividerAbove = true)
                    }
                }

                items(staticFilesBasic) { (filename, displayName) ->
                    FileChooserItem(
                        displayName = displayName,
                        modifier = Modifier.walkthroughTarget(
                            key = "file_how_to_juggle",
                            condition = displayName == "How to Juggle"
                        ),
                        onClick = { onStaticFileClick(filename, displayName) }
                    )
                }

                if (staticFilesBasic.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                if (staticFilesOther.isNotEmpty()) {
                    item {
                        FileChooserHeader(
                            "Other",
                            showDividerAbove = localFiles.isNotEmpty() || staticFilesBasic.isNotEmpty()
                        )
                    }
                }

                items(staticFilesOther) { (filename, displayName) ->
                    FileChooserItem(
                        displayName = displayName,
                        onClick = { onStaticFileClick(filename, displayName) }
                    )
                }

                if (staticFilesOther.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (isBusy) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun FileChooserHeader(
    text: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showDividerAbove) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileChooserItem(
    displayName: String,
    modifier: Modifier = Modifier,
    isFavorites: Boolean = false,
    fontStyle: FontStyle = FontStyle.Normal,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontStyle = fontStyle,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isFavorites) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorites",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
