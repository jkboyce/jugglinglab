//
// AnimationViewCombined.kt
//
// Combined view for the animation and ladder diagram. This is the primary
// animation view for the mobile application.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.PatternBuilder
import org.jugglinglab.prop.Prop
import org.jugglinglab.ui.common.*
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlHandleFatalException
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@Suppress("AssignedValueIsNeverRead")
@Composable
fun AnimationViewCombined(
    state: PatternAnimationState,
    animationController: AnimationController,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    favoritesHashCodes: Set<Int> = emptySet(),
    onAddToFavorites: (JmlPattern, AnimationPrefs) -> Unit = { _, _ -> },
    onRemoveFromFavorites: (JmlPattern, AnimationPrefs) -> Unit = { _, _ -> },
    onShare: () -> Unit = {},
    onExport: () -> Unit = {},
    onBusy: (Boolean) -> Unit = {}
) {
    val coordinator = LocalWalkthroughCoordinator.current
    val coroutineScope = rememberCoroutineScope()
    val ladderController = remember(state) {
        lateinit var controller: LadderDiagramController
        controller = LadderDiagramController(
            state = state,
            onMakePopup = { _, _, _ -> controller.onShowMenu() }
        )
        controller
    }

    // ladder diagram state
    var isLadderExpanded by remember { mutableStateOf(false) }
    var activeAnimationDialog by remember { mutableStateOf<AnimationViewDialog?>(null) }
    var ladderZoom by remember { mutableFloatStateOf(1f) }
    val ladderScrollState = rememberScrollState()
    var savedScrollProportion by remember { mutableStateOf<Float?>(null) }

    // Onboarding walkthrough observer effects
    val animCenter = remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(isLadderExpanded) {
        if (coordinator?.walkthroughStep == 6 && isLadderExpanded) {
            coordinator.walkthroughStep = 7
        }
    }

    LaunchedEffect(state.selectedItemHashCode) {
        if (coordinator?.walkthroughStep == 9 && state.selectedItemHashCode != 0) {
            coordinator.walkthroughStep = 10
        }
    }

    val currentLayout = animationController.currentLayout
    LaunchedEffect(animCenter.value, currentLayout, state.selectedItemHashCode, coordinator) {
        val ac = animCenter.value
        if (ac != null && currentLayout != null && state.selectedItemHashCode != 0 && coordinator != null) {
            val eventPoints = currentLayout.eventPoints.getOrNull(0)?.getOrNull(0)
            if (eventPoints != null && eventPoints.size >= 4) {
                val minX = eventPoints.take(4).minOf { it[0] }.toFloat()
                val maxX = eventPoints.take(4).maxOf { it[0] }.toFloat()
                val minY = eventPoints.take(4).minOf { it[1] }.toFloat()
                val maxY = eventPoints.take(4).maxOf { it[1] }.toFloat()
                coordinator.reportElement(
                    "anim_box",
                    Rect(
                        left = ac.left + minX,
                        top = ac.top + minY,
                        right = ac.left + maxX,
                        bottom = ac.top + maxY
                    )
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        val targetWeight = if (isLadderExpanded && !isLandscape) 1.5f else 0.001f
        val ladderWeight by animateFloatAsState(
            targetValue = targetWeight,
            label = "ladderWeight"
        )

        val targetWidthFraction = if (isLadderExpanded && isLandscape) 0.8f else 0.001f
        val ladderWidthFraction by animateFloatAsState(
            targetValue = targetWidthFraction,
            label = "ladderWidthFraction"
        )

        val isAnimating = if (isLandscape) {
            abs(ladderWidthFraction - targetWidthFraction) > 0.001f
        } else {
            abs(ladderWeight - targetWeight) > 0.001f
        }

        LaunchedEffect(ladderScrollState.maxValue) {
            if (savedScrollProportion != null && ladderScrollState.maxValue > 0) {
                val target = (savedScrollProportion!! * ladderScrollState.maxValue).roundToInt()
                ladderScrollState.scrollTo(target)
            }
        }

        LaunchedEffect(isAnimating) {
            if (!isAnimating && isLadderExpanded) {
                savedScrollProportion = null
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .safeDrawingPadding()
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimationView(
                        state = state,
                        colorScheme = colorScheme,
                        onPress = animationController::handlePress,
                        onDrag = animationController::handleDrag,
                        onRelease = animationController::handleRelease,
                        onLayoutUpdate = animationController::updateLayout,
                        onZoom = animationController::handleZoom,
                        modifier = Modifier.fillMaxSize()
                            .walkthroughTarget("anim_center")
                            .onGloballyPositioned { coords ->
                                animCenter.value = coords.boundsInRoot()
                            }
                    )

                    IconButton(
                        onClick = {
                            if (isLadderExpanded && ladderScrollState.maxValue > 0) {
                                savedScrollProportion = ladderScrollState.value.toFloat() / ladderScrollState.maxValue
                            }
                            isLadderExpanded = !isLadderExpanded
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .walkthroughTarget("anim_ladder_toggle")
                    ) {
                        if (isLadderExpanded) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(Res.string.gui_mobile_collapse)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(Res.string.gui_mobile_expand)
                            )
                        }
                    }

                    AnimationViewMenus(
                        animationController = animationController,
                        favoritesHashCodes = favoritesHashCodes,
                        onShowDialog = { activeAnimationDialog = it },
                        onAddToFavorites = onAddToFavorites,
                        onRemoveFromFavorites = onRemoveFromFavorites,
                        onShare = onShare,
                        onExport = onExport,
                        onBusy = onBusy,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp),
                        onMenuPositioned = { bounds ->
                            coordinator?.reportElement("anim_menu", bounds)
                        }
                    )
                }

                if (ladderWidthFraction > 0.01f) {
                    LadderDiagramView(
                        state = state,
                        colorScheme = colorScheme,
                        onPress = ladderController::handlePress,
                        onDrag = ladderController::handleDrag,
                        onRelease = ladderController::handleRelease,
                        onLayoutUpdate = ladderController::onLayoutUpdate,
                        zoom = ladderZoom,
                        onZoomChange = { ladderZoom = it },
                        scrollState = ladderScrollState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(ladderWidthFraction, matchHeightConstraintsFirst = true)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .safeDrawingPadding()
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimationView(
                        state = state,
                        colorScheme = colorScheme,
                        onPress = animationController::handlePress,
                        onDrag = animationController::handleDrag,
                        onRelease = animationController::handleRelease,
                        onLayoutUpdate = animationController::updateLayout,
                        onZoom = animationController::handleZoom,
                        modifier = Modifier.fillMaxSize()
                            .walkthroughTarget("anim_center")
                            .onGloballyPositioned { coords ->
                                animCenter.value = coords.boundsInRoot()
                            }
                    )

                    IconButton(
                        onClick = {
                            if (isLadderExpanded && ladderScrollState.maxValue > 0) {
                                savedScrollProportion = ladderScrollState.value.toFloat() / ladderScrollState.maxValue
                            }
                            isLadderExpanded = !isLadderExpanded
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .walkthroughTarget("anim_ladder_toggle")
                    ) {
                        if (isLadderExpanded) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(Res.string.gui_mobile_collapse)
                            )
                        } else {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(Res.string.gui_mobile_expand)
                            )
                        }
                    }

                    AnimationViewMenus(
                        animationController = animationController,
                        favoritesHashCodes = favoritesHashCodes,
                        onShowDialog = { activeAnimationDialog = it },
                        onAddToFavorites = onAddToFavorites,
                        onRemoveFromFavorites = onRemoveFromFavorites,
                        onShare = onShare,
                        onExport = onExport,
                        onBusy = onBusy,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp),
                        onMenuPositioned = { bounds ->
                            coordinator?.reportElement("anim_menu", bounds)
                        }
                    )
                }

                if (ladderWeight > 0.1f) {
                    LadderDiagramView(
                        state = state,
                        colorScheme = colorScheme,
                        onPress = ladderController::handlePress,
                        onDrag = ladderController::handleDrag,
                        onRelease = ladderController::handleRelease,
                        onLayoutUpdate = ladderController::onLayoutUpdate,
                        zoom = ladderZoom,
                        onZoomChange = { ladderZoom = it },
                        scrollState = ladderScrollState,
                        modifier = Modifier.weight(ladderWeight)
                    )
                }
            }
        }
    }

    AnimationViewDialogs(
        activeDialog = activeAnimationDialog,
        onDismissRequest = { activeAnimationDialog = null },
        onConfirmPrefs = { newPrefs ->
            activeAnimationDialog = null
            coroutineScope.launch(Dispatchers.Default) {
                onBusy(true)
                try {
                    withContext(Dispatchers.Main) {
                        animationController.restartJuggle(prefs = newPrefs)
                    }
                } finally {
                    onBusy(false)
                }
            }
        },
        onConfirmTiming = { scale ->
            activeAnimationDialog = null
            coroutineScope.launch(Dispatchers.Default) {
                onBusy(true)
                try {
                    val newPat = state.pattern.withScaledTime(scale)
                    withContext(Dispatchers.Main) {
                        animationController.restartJuggle(pattern = newPat)
                        state.addCurrentToUndoList()
                    }
                } finally {
                    onBusy(false)
                }
            }
        },
        onConfirmTitle = { newTitle ->
            activeAnimationDialog = null
            coroutineScope.launch(Dispatchers.Default) {
                onBusy(true)
                try {
                    val rec = PatternBuilder.fromJmlPattern(state.pattern)
                    rec.setTitleString(newTitle.takeIf { it.isNotBlank() })
                    val newPat = JmlPattern.fromPatternBuilder(rec)
                    withContext(Dispatchers.Main) {
                        animationController.restartJuggle(pattern = newPat, coldRestart = false)
                        state.addCurrentToUndoList()
                    }
                } finally {
                    onBusy(false)
                }
            }
        }
    )

    LadderDiagramDialogs(
        controller = ladderController,
        onDismissRequest = ladderController::onDismissDialog
    )
}

//------------------------------------------------------------------------------
// Dropdown menu for animation-related functions
//------------------------------------------------------------------------------

@Composable
private fun AnimationViewMenus(
    animationController: AnimationController,
    favoritesHashCodes: Set<Int>,
    onShowDialog: (AnimationViewDialog) -> Unit,
    onAddToFavorites: (JmlPattern, AnimationPrefs) -> Unit,
    onRemoveFromFavorites: (JmlPattern, AnimationPrefs) -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    onBusy: (Boolean) -> Unit = {},
    onMenuPositioned: ((Rect) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val state = animationController.state
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isColorPropsMenuExpanded by remember { mutableStateOf(false) }

    val inFavorites = remember(state.pattern, state.prefs, favoritesHashCodes) {
        val tempPl = JmlPatternList()
        tempPl.insertPattern(state.pattern, state.prefs, 0)
        favoritesHashCodes.contains(tempPl.model[0].jlHashCode)
    }

    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { isMenuExpanded = true },
                modifier = Modifier.onGloballyPositioned { coords ->
                    onMenuPositioned?.invoke(coords.boundsInRoot())
                }
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.gui_mobile_menu)
                )
            }

            if (inFavorites) {
                IconButton(onClick = { /* onRemoveFromFavorites(state.pattern, state.prefs) */ }) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(Res.string.gui_mobile_remove_from_favorites),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val layout = animationController.currentLayout
            val showRestart = (state.zoom != 1.0) ||
                (state.cameraAngle != state.initialCameraAngle()) ||
                (state.selectedItemHashCode != 0 && layout != null &&
                (layout.eventPoints.isNotEmpty() || layout.posPoints.isNotEmpty()))

            if (showRestart) {
                IconButton(onClick = { animationController.restartJuggle() }) {
                    Icon(
                        Icons.Default.ZoomOutMap,
                        contentDescription = stringResource(Res.string.gui_restart)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false }
        ) {
            Text(
                text = state.pattern.title ?: stringResource(Res.string.gui_pattern),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_undo)) },
                enabled = (state.undoIndex > 0),
                onClick = {
                    isMenuExpanded = false
                    if (state.undoIndex > 0) {
                        try {
                            --state.undoIndex
                            animationController.restartJuggle(
                                pattern = state.undoList[state.undoIndex],
                                coldRestart = false
                            )
                        } catch (jeu: JuggleExceptionUser) {
                            // pattern was animated before so user error should not occur
                            jlHandleFatalException(JuggleExceptionInternal(jeu.message ?: ""))
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_redo)) },
                enabled = (state.undoIndex < state.undoList.size - 1),
                onClick = {
                    isMenuExpanded = false
                    if (state.undoIndex < state.undoList.size - 1) {
                        try {
                            ++state.undoIndex
                            animationController.restartJuggle(
                                pattern = state.undoList[state.undoIndex],
                                coldRestart = false
                            )
                        } catch (jeu: JuggleExceptionUser) {
                            // pattern was animated before so user error should not occur
                            jlHandleFatalException(JuggleExceptionInternal(jeu.message ?: ""))
                        }
                    }
                }
            )
            /*
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_restart)) },
                onClick = {
                    isMenuExpanded = false
                    animationController.restartJuggle()
                }
            )*/
            DropdownMenuItem(
                text = {
                    Text(
                        if (state.isPaused) stringResource(Res.string.gui_mobile_unpause)
                        else stringResource(Res.string.gui_mobile_pause)
                    )
                },
                onClick = {
                    isMenuExpanded = false
                    state.update(isPaused = !state.isPaused)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_animation_preferences___)) },
                onClick = {
                    isMenuExpanded = false
                    onShowDialog(AnimationViewDialog.ChangeAnimationPrefs(state.prefs))
                }
            )
            HorizontalDivider()
            if (inFavorites) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gui_mobile_remove_from_favorites)) },
                    onClick = {
                        isMenuExpanded = false
                        onRemoveFromFavorites(state.pattern, state.prefs)
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
                        isMenuExpanded = false
                        onAddToFavorites(state.pattern, state.prefs)
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
                    isMenuExpanded = false
                    onShare()
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
                    isMenuExpanded = false
                    onExport()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_change_title___)) },
                onClick = {
                    isMenuExpanded = false
                    onShowDialog(AnimationViewDialog.ChangeTitle(state.pattern.title ?: ""))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_change_overall_timing___)) },
                onClick = {
                    isMenuExpanded = false
                    onShowDialog(AnimationViewDialog.ChangeTiming)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_color_props)) },
                enabled = state.pattern.isColorable,
                onClick = {
                    isMenuExpanded = false
                    isColorPropsMenuExpanded = true
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_swap_hands)) },
                onClick = {
                    isMenuExpanded = false
                    state.update(pattern = state.pattern.withInvertedXaxis(false))
                    state.addCurrentToUndoList()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_flip_pattern_in_x)) },
                onClick = {
                    isMenuExpanded = false
                    state.update(pattern = state.pattern.withInvertedXaxis())
                    state.addCurrentToUndoList()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_flip_pattern_in_time)) },
                onClick = {
                    isMenuExpanded = false
                    state.update(pattern = state.pattern.withInvertedTime())
                    state.addCurrentToUndoList()
                }
            )
        }

        //----------------------------------------------------------------------
        // Color Props sub-menu
        //----------------------------------------------------------------------

        DropdownMenu(
            expanded = isColorPropsMenuExpanded,
            onDismissRequest = { isColorPropsMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_pcmenu_mixed)) },
                onClick = {
                    isColorPropsMenuExpanded = false
                    coroutineScope.launch(Dispatchers.Default) {
                        onBusy(true)
                        try {
                            val newPat = state.pattern.withPropColors("mixed")
                            withContext(Dispatchers.Main) {
                                animationController.restartJuggle(
                                    pattern = newPat,
                                    coldRestart = false
                                )
                                state.addCurrentToUndoList()
                            }
                        } finally {
                            onBusy(false)
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gui_pcmenu_orbits)) },
                onClick = {
                    isColorPropsMenuExpanded = false
                    coroutineScope.launch(Dispatchers.Default) {
                        onBusy(true)
                        try {
                            val newPat = state.pattern.withPropColors("orbits")
                            withContext(Dispatchers.Main) {
                                animationController.restartJuggle(
                                    pattern = newPat,
                                    coldRestart = false
                                )
                                state.addCurrentToUndoList()
                            }
                        } finally {
                            onBusy(false)
                        }
                    }
                }
            )
            HorizontalDivider()
            val outlineColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black
            for ((i, colorName) in Prop.colorNames.withIndex()) {
                DropdownMenuItem(
                    text = { Text(stringResource(Prop.colorStringResources[i])) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Prop.colorValues[i], CircleShape)
                                .border(1.dp, outlineColor, CircleShape)
                        )
                    },
                    onClick = {
                        isColorPropsMenuExpanded = false
                        coroutineScope.launch(Dispatchers.Default) {
                            onBusy(true)
                            try {
                                val newPat = state.pattern.withPropColors("{$colorName}")
                                withContext(Dispatchers.Main) {
                                    animationController.restartJuggle(
                                        pattern = newPat,
                                        coldRestart = false
                                    )
                                    state.addCurrentToUndoList()
                                }
                            } finally {
                                onBusy(false)
                            }
                        }
                    }
                )
            }
        }
    }
}
