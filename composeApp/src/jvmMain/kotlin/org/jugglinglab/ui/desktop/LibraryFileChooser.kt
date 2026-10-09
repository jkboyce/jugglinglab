//
// LibraryFileChooser.kt
//
// Compose UI component for selecting and opening built-in JML pattern lists.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.ui.common.BuiltinPatternLists
import org.jugglinglab.ui.mobile.FileChooserHeader
import org.jugglinglab.ui.mobile.FileChooserItem
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.PatternListScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryFileChooser(
    onPatternLoaded: (JmlPattern) -> Unit,
    onPatternListLoaded: (JmlPatternList) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val onFileClick: (String, String) -> Unit = { filename, displayName ->
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
                            val pat = withContext(Dispatchers.Default) {
                                val p = JmlPattern.fromJmlNode(parser.tree!!)
                                p.layout
                                p
                            }
                            onPatternLoaded(pat)
                        }

                        JmlParser.JML_LIST -> {
                            val pl = withContext(Dispatchers.Default) {
                                JmlPatternList(parser.tree)
                            }
                            if (pl.title == null) {
                                pl.title = displayName
                            }
                            onPatternListLoaded(pl)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                item {
                    FileChooserHeader(
                        text = stringResource(Res.string.gui_mobile_filechooser_pattern_library)
                    )
                }

                items(BuiltinPatternLists.basic) { entry ->
                    FileChooserItem(
                        displayName = entry.displayName,
                        onClick = { onFileClick(entry.filename, entry.displayName) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    FileChooserHeader(
                        text = stringResource(Res.string.gui_mobile_filechooser_other),
                        showDividerAbove = true
                    )
                }

                items(BuiltinPatternLists.other) { entry ->
                    FileChooserItem(
                        displayName = entry.displayName,
                        onClick = { onFileClick(entry.filename, entry.displayName) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            PatternListScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )

            if (isBusy) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
