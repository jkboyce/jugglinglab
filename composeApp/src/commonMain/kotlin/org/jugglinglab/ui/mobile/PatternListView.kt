//
// PatternListView.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("SimplifyBooleanWithConstants")

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.JmlPatternList.PatternRecord
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okio.Path
import org.jetbrains.compose.resources.stringResource

@Suppress("KotlinConstantConditions")
@Composable
fun PatternListView(
    patternList: JmlPatternList,
    state: PatternAnimationState,
    modifier: Modifier = Modifier,
    favoritesHashCodes: Set<Int> = emptySet(),
    isEditable: Boolean = true,
    isFavoritesList: Boolean = false,
    patternListPath: Path? = null,
    listState: LazyListState = rememberLazyListState(),
    onItemClick: (Int, PatternRecord) -> Unit,
    onDuplicateClick: () -> Unit = {},
    onSaveAsClick: (String) -> Unit = {},
    onRenameListClick: (String) -> Unit = {},
    onDeleteListClick: () -> Unit = {},
    onDeleteFavoritesAndQuitClick: () -> Unit = {},
    onAddToFavorites: (PatternRecord) -> Unit = {},
    onRemoveFromFavorites: (PatternRecord) -> Unit = {},
    onPatternListModified: () -> Unit = {},
    onExportList: () -> Unit = {},
    onSharePattern: (PatternRecord) -> Unit = {},
    onExportPattern: (PatternRecord) -> Unit = {},
    onCloseClick: (() -> Unit)? = null
) {

    Column(modifier = modifier.fillMaxSize()) {
        var showMainMenu by remember { mutableStateOf(false) }
        var activeDialog by remember { mutableStateOf<PatternListDialog?>(null) }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (onCloseClick != null) 4.dp else 16.dp,
                    end = 12.dp,
                    top = 2.dp,
                    bottom = 2.dp
                ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (onCloseClick != null) {
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.walkthroughTarget("pattern_list_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            val heading = patternListPath?.name?.removeSuffix(".jml")
                ?: patternList.title?.let { if (isEditable) "$it (unsaved)" else it }
                ?: if (isEditable) stringResource(
                    Res.string.gui_mobile_pattern_list_unsaved,
                    stringResource(Res.string.gui_plwindow_default_window_title)
                ) else stringResource(Res.string.gui_plwindow_default_window_title)
            Text(
                text = heading,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                IconButton(onClick = { showMainMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.gui_mobile_menu),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                DropdownMenu(
                    expanded = showMainMenu,
                    onDismissRequest = { showMainMenu = false }
                ) {
                    Text(
                        text = heading,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider()

                    if (isEditable && patternListPath == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.gui_save_jml_as___)) },
                            onClick = {
                                showMainMenu = false
                                activeDialog =
                                    PatternListDialog.SaveAs(heading.removeSuffix(" (unsaved)"))
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.gui_duplicate)) },
                            onClick = {
                                showMainMenu = false
                                onDuplicateClick()
                            }
                        )
                    }
                    if (isEditable && patternListPath != null && !isFavoritesList) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.gui_mobile_rename___)) },
                            onClick = {
                                showMainMenu = false
                                activeDialog = PatternListDialog.RenameList(
                                    patternListPath.name.removeSuffix(".jml")
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.gui_mobile_export_as_jml___)) },
                        onClick = {
                            showMainMenu = false
                            onExportList()
                        }
                    )
                    if (patternListPath != null && !isFavoritesList) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.gui_mobile_delete_list)) },
                            onClick = {
                                showMainMenu = false
                                activeDialog = PatternListDialog.DeleteList(patternListPath.name)
                            }
                        )
                    }

                    if (Constants.TEST_MOBILE_APP && isFavoritesList) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("DELETE FAVORITES AND QUIT") },
                            onClick = {
                                showMainMenu = false
                                onDeleteFavoritesAndQuitClick()
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                val displaySize = patternList.model.size

                items(displaySize) { index ->
                    val record = patternList.model[index]
                    val inFavorites = record.canAnimate && record.jlHashCode != 0 &&
                        favoritesHashCodes.contains(record.jlHashCode)
                    val hasFavoriteStar = inFavorites && !isFavoritesList

                    PatternListItem(
                        index = index,
                        record = record,
                        hasFavoriteStar = hasFavoriteStar,
                        inFavorites = inFavorites,
                        patternList = patternList,
                        state = state,
                        isEditable = isEditable,
                        modifier = Modifier.walkthroughTarget(
                            key = "pattern_list_line",
                            condition = index == 3
                        ),
                        onClick = { onItemClick(index, record) },
                        onPatternListModified = onPatternListModified,
                        onShowDialog = { activeDialog = it },
                        onAddToFavorites = onAddToFavorites,
                        onRemoveFromFavorites = onRemoveFromFavorites,
                        onSharePattern = onSharePattern,
                        onExportPattern = onExportPattern
                    )
                }
            }
        }

        PatternListDialogs(
            activeDialog = activeDialog,
            onDismissRequest = { activeDialog = null },
            onRenameList = { newFilename ->
                activeDialog = null
                onRenameListClick(newFilename)
            },
            onSaveAs = { newFilename ->
                activeDialog = null
                onSaveAsClick(newFilename)
            },
            onDeleteList = {
                activeDialog = null
                onDeleteListClick()
            },
            onInsertText = { index, text ->
                activeDialog = null
                patternList.addLine(index, PatternRecord(text, null, null, null, null, null, null))
                onPatternListModified()
            },
            onChangeDisplayText = { index, text ->
                activeDialog = null
                val updatedRecord = patternList.model[index].copy(display = text)
                patternList.model[index] = updatedRecord
                onPatternListModified()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PatternListItem(
    index: Int,
    record: PatternRecord,
    hasFavoriteStar: Boolean,
    inFavorites: Boolean,
    patternList: JmlPatternList,
    state: PatternAnimationState,
    isEditable: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPatternListModified: () -> Unit,
    onShowDialog: (PatternListDialog) -> Unit,
    onAddToFavorites: (PatternRecord) -> Unit,
    onRemoveFromFavorites: (PatternRecord) -> Unit,
    onSharePattern: (PatternRecord) -> Unit,
    onExportPattern: (PatternRecord) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val isBlankLine = JmlPatternList.BLANK_AT_END && index == patternList.model.size - 1
    val canAnimate = record.canAnimate

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = record.display.ifEmpty { " " },
                modifier = Modifier.weight(1f),
                fontFamily = if (canAnimate) FontFamily.Monospace else FontFamily.SansSerif,
                fontStyle = if (canAnimate) FontStyle.Normal else FontStyle.Italic,
                fontWeight = if (canAnimate) FontWeight.Normal else FontWeight.Bold,
                fontSize = if (canAnimate) 20.sp else 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
                style = androidx.compose.ui.text.TextStyle(
                    textIndent = androidx.compose.ui.text.style.TextIndent(restLine = if (canAnimate) 32.sp else 0.sp)
                )
            )

            if (hasFavoriteStar) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (canAnimate) {
                Text(
                    text = record.display.trim(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
            }
            if (isEditable && !isBlankLine) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_move_up)) },
                    onClick = {
                        showMenu = false
                        if (index > 0) {
                            val prev = patternList.model[index - 1]
                            patternList.model[index - 1] = record
                            patternList.model[index] = prev
                            onPatternListModified()
                        }
                    },
                    enabled = index > 0
                )
                val displaySize =
                    if (JmlPatternList.BLANK_AT_END) patternList.model.size - 1 else patternList.model.size
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_move_down)) },
                    onClick = {
                        showMenu = false
                        if (index < displaySize - 1) {
                            val next = patternList.model[index + 1]
                            patternList.model[index + 1] = record
                            patternList.model[index] = next
                            onPatternListModified()
                        }
                    },
                    enabled = index < displaySize - 1
                )
            }

            if (canAnimate) {
                if (isEditable && !isBlankLine) {
                    HorizontalDivider()
                }
                if (inFavorites) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.gui_mobile_remove_from_favorites)) },
                        onClick = {
                            showMenu = false
                            onRemoveFromFavorites(record)
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = stringResource(Res.string.gui_mobile_remove_from_favorites)
                            )
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.gui_mobile_add_to_favorites)) },
                        onClick = {
                            showMenu = false
                            onAddToFavorites(record)
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = stringResource(Res.string.gui_mobile_favorites)
                            )
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_share)) },
                    onClick = {
                        showMenu = false
                        onSharePattern(record)
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.gui_mobile_share)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_export_as_jml___)) },
                    onClick = {
                        showMenu = false
                        onExportPattern(record)
                    }
                )
            }

            if (isEditable) {
                if (!isBlankLine || canAnimate) {
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_insert_current_pattern)) },
                    onClick = {
                        showMenu = false
                        patternList.insertPattern(state.pattern, state.prefs, index)
                        onPatternListModified()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_plpopup_insert_text___)) },
                    onClick = {
                        showMenu = false
                        onShowDialog(PatternListDialog.InsertText(index))
                    }
                )
                if (!isBlankLine) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.gui_mobile_plpopup_change_display_text___)) },
                        onClick = {
                            showMenu = false
                            onShowDialog(PatternListDialog.ChangeDisplayText(index, record.display))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.gui_mobile_plpopup_remove_line)) },
                        onClick = {
                            showMenu = false
                            patternList.model.removeAt(index)
                            onPatternListModified()
                        }
                    )
                }
            }
        }
    }
}

// Insert a pattern into the pattern list.
//
// If `index` is unspecified, insert onto the end of the list.

fun JmlPatternList.insertPattern(
    pattern: JmlPattern,
    prefs: AnimationPrefs,
    index: Int = -1
) {
    val display = pattern.title
    var animprefs: String? = prefs.toString()
    if (animprefs!!.isEmpty()) {
        animprefs = null
    }
    var notation: String? = "jml"
    var anim: String? = null
    var patnode = pattern.rootNode!!.findNode("pattern")
    val infonode = patnode!!.findNode("info")

    if (pattern.hasBasePattern && !pattern.isBasePatternEdited) {
        // add as base pattern instead of JML
        notation = pattern.basePatternNotation
        anim = pattern.basePatternConfig
        patnode = null
    }

    val rec = PatternRecord.create(display, animprefs, notation, anim, patnode, infonode)
    val displaySize =
        if (JmlPatternList.BLANK_AT_END) model.size - 1 else model.size
    model.add(if (index == -1) displaySize else index, rec)
}
