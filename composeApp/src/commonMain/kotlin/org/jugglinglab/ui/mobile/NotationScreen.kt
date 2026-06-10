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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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

    SiteswapNotationControl(
        initialParams = initialParams,
        isBasePatternEdited = state.pattern.hasBasePattern && state.pattern.isBasePatternEdited,
        onConfirm = { parameterString ->
            coroutineScope.launch(Dispatchers.Default) {
                onBusyChange(true)
                try {
                    val parameterList = ParameterList(parameterString)
                    val newPattern = SiteswapPattern().fromParameters(parameterList).asJmlPattern()
                    newPattern.layout
                    animationController.restartJuggle(pattern = newPattern)
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
