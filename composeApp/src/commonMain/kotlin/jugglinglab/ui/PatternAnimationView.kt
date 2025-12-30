//
// PatternAnimationView.kt
//
// This is a Composable UI displaying the main juggler animation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.PatternAnimationState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
fun PatternAnimationView(
    state: PatternAnimationState,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.pattern) {
        // This block runs every time 'state.pattern' changes
        println("Pattern changed to: ${state.pattern.title}")
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // draw the animation frame here
    }
}
