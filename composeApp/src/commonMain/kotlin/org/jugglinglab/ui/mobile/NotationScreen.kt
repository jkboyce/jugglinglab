//
// NotationScreen.kt
//
// Screen wrapper for SiteswapNotationControl in Juggling Lab.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.ui.common.*
import org.jugglinglab.util.ParameterList
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.jugglinglab.notation.SequencePreviewBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NotationScreen(
    state: PatternAnimationState,
    animationController: AnimationController,
    onNavigateToAnimation: () -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val initialParams = if (state.pattern.basePatternNotation.equals("siteswap", ignoreCase = true)) {
        state.pattern.basePatternConfig
    } else null

    var isBasePatternEdited by remember { mutableStateOf(false) }
    LaunchedEffect(state.pattern) {
        isBasePatternEdited = withContext(Dispatchers.Default) {
            state.pattern.hasBasePattern && state.pattern.isBasePatternEdited
        }
    }

    var finiteSequence by remember { mutableStateOf(state.previewWindow != null) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().toggleable(value = finiteSequence,
            role = Role.Checkbox, onValueChange = { finiteSequence = it }).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = finiteSequence, onCheckedChange = null)
            Text("Play once (finite sequence)")
        }
        Text("Invalid loops preview up to the first conflict. Use the beat slider below the animation.", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        Box(modifier = Modifier.weight(1f)) {
        SiteswapNotationControl(
            initialParams = initialParams,
            isBasePatternEdited = isBasePatternEdited,
            onConfirm = { parameterString ->
                coroutineScope.launch(Dispatchers.Default) {
                    onBusyChange(true)
                    try {
                        val preview = if (finiteSequence) SequencePreviewBuilder.build(parameterString, finite = true) else null
                        val normal = if (preview == null) try {
                            SiteswapPattern().fromParameters(ParameterList(parameterString)).asJmlPattern().also { it.layout }
                        } catch (e: org.jugglinglab.util.JuggleExceptionUser) {
                            null
                        } else null
                        val resolvedPreview = preview ?: if (normal == null) SequencePreviewBuilder.build(parameterString, finite = false) else null
                        val newPattern = normal ?: resolvedPreview!!.pattern
                        animationController.restartJuggle(pattern = newPattern)
                        state.previewWindow = resolvedPreview?.window
                        if (resolvedPreview != null) {
                            state.update(time = resolvedPreview.window.start, isPaused = true, message = "")
                        }
                        state.addCurrentToUndoList()
                        withContext(Dispatchers.Main) {
                            onNavigateToAnimation()
                        }
                    } catch (e: Throwable) {
                        onError(e)
                    } finally {
                        onBusyChange(false)
                    }
                }
            }
        )
        }
    }
}
