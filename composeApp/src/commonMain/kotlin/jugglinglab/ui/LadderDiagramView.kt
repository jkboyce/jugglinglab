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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.isActive

@Composable
fun LadderDiagramView(
    state: PatternAnimationState,
    modifier: Modifier = Modifier
) {
    val ladderLayoutData = remember(state.pattern, state.cameraAngle) {
        // This block runs when 'state.pattern' or 'state.cameraAngle' changes.
        // It persists across the frequent recompositions caused by the animation loop.
    }

    SideEffect {
        // Runs after every recomposition
        //controller.updateInternalState(state.pattern)
    }

    LaunchedEffect(state) {
        var lastFrameTime = withFrameNanos { it }

        while (isActive) {
            // update 'state.clock' when the next display frame is ready
            withFrameNanos { currentFrameTime ->
                // Calculate time difference in seconds
                val dt = (currentFrameTime - lastFrameTime) /
                    (1_000_000_000.0 * state.prefs.slowdown)
                lastFrameTime = currentFrameTime

                if (!state.isPaused) {
                    // Advance the simulation time
                    state.time += dt
                }

                //state.clock = currentFrameTime
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // draw the animation frame here
    }
}
