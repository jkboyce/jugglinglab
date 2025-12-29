//
// LadderDiagramView.kt
//
// This is a Composable UI displaying the ladder diagram that can
// accompany the main juggler animation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.core.PatternAnimationState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LadderDiagramView(
    state: PatternAnimationState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // draw the animation frame here
    }
}
