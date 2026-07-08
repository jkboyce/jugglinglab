//
// WalkthroughCoordinator.kt
//
// Standalone coordinator for the mobile onboarding walkthrough. Decouples step
// definitions, state transitions, and element coordinate reporting.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.util.jlIsWeb
import org.jugglinglab.util.jlIsTouchInterface
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WalkthroughCoordinator(
    private val prefsRepo: StoredPreferencesRepository?,
    private val state: PatternAnimationState,
    private val coroutineScope: CoroutineScope,
    private val onNavigateTo: (String) -> Unit,
    private val onResetAnimationToDefault: () -> Unit = {},
    private val onResetPatternListState: () -> Unit = {}
) {
    var walkthroughStep by mutableIntStateOf(0)
    val walkthroughPositions = mutableStateMapOf<String, Rect>()
    var rootBounds by mutableStateOf<Rect?>(null)

    val activeStepData: Pair<String, String>?
        get() {
            val step = WALKTHROUGH_STEPS.getOrNull(walkthroughStep) ?: return null
            var text = step.second
            if (!jlIsTouchInterface) {
                text = text.replace("long press", "right click")
                text = text.replace("Long press", "Right click")
                text = text.replace("a two finger pinch gesture", "the scroll wheel")
                text = text.replace("a pinch gesture", "the scroll wheel")
            }
            if (jlIsWeb && step.first == "file_my_lists") {
                text += "\n\nImportant: Pattern lists in the web application are not saved between browser sessions."
            }
            return Pair(step.first, text)
        }

    fun startWalkthrough() {
        walkthroughStep = 1
    }

    fun handleOkClick() {
        when (walkthroughStep) {
            2 -> {
                if (jlIsWeb) {
                    onResetAnimationToDefault()
                    onNavigateTo("Animation")
                    walkthroughStep = 4
                } else {
                    walkthroughStep = 3
                }
            }

            3 -> {
                onResetAnimationToDefault()
                onNavigateTo("Animation")
                walkthroughStep = 4
            }

            11 -> {
                state.update(selectedItemHashCode = 0)
                walkthroughStep = 12
            }

            13 -> {
                onResetPatternListState()
                onNavigateTo("Info")
                walkthroughStep = 14
            }

            17 -> {
                walkthroughStep = 18
            }

            18 -> {
                // Interactive close button step. Handled reactively.
            }

            19 -> {
                if (!jlIsWeb) {
                    coroutineScope.launch {
                        prefsRepo?.saveOnboardingCompleted(true)
                    }
                }
                onNavigateTo("Info")
                state.update(selectedItemHashCode = 0)
                walkthroughPositions.clear()
                walkthroughStep = 0
            }

            else -> {
                walkthroughStep += 1
            }
        }
    }

    fun handleSkipClick() {
        if (!jlIsWeb) {
            coroutineScope.launch {
                prefsRepo?.saveOnboardingCompleted(true)
            }
        }
        state.update(selectedItemHashCode = 0)
        walkthroughPositions.clear()
        walkthroughStep = 0
    }

    fun reportElement(key: String, rect: Rect) {
        walkthroughPositions[key] = rect
    }
}

/**
 * CompositionLocal key to access the global [WalkthroughCoordinator].
 */
val LocalWalkthroughCoordinator = staticCompositionLocalOf<WalkthroughCoordinator?> { null }

/**
 * A declarative Modifier that automatically tracks coordinates of a Composable layout node
 * and reports them to the current [WalkthroughCoordinator] if active.
 */
@Composable
fun Modifier.walkthroughTarget(key: String, condition: Boolean = true): Modifier {
    val coordinator = LocalWalkthroughCoordinator.current
    if (coordinator == null || coordinator.walkthroughStep == 0 || !condition) return this
    return this.onGloballyPositioned { coords ->
        coordinator.reportElement(key, coords.boundsInRoot())
    }
}

private val WALKTHROUGH_STEPS = listOf(
    Pair("", ""),  // Step 0 placeholder
    Pair(
        "nav_info",
        "Welcome to Juggling Lab! This is the home screen – come back here by selecting this icon. (Please do so to advance.)"
    ),
    Pair(
        "info_pattern_list",
        "Go here to open juggling patterns to view."
    ),
    Pair(
        "info_favorites",
        "Go here to see your favorited patterns."
    ),
    Pair(
        "anim_center",
        "This is the animation view. Tap to pause/unpause, drag to move the camera, and use a two finger pinch gesture to zoom in/out."
    ),
    Pair(
        "anim_menu",
        "Select this to access the pattern menu. (Please do so to advance.)"
    ),
    Pair(
        "anim_ladder_toggle",
        "Select this to open the ladder diagram view. (Please do so to advance.)"
    ),
    Pair(
        "ladder_center",
        "This is the ladder diagram. Drag the time tracker up and down to cue the time. The ladder is also zoomable with a pinch gesture."
    ),
    Pair(
        "ladder_center",
        "A long press inside the ladder diagram accesses editing features."
    ),
    Pair(
        "ladder_transition",
        "This represents a transition, in this case a throw. Select this to highlight it. (Please do so to advance.)"
    ),
    Pair(
        "anim_box",
        "When the transition is selected, this box represents its location. Drag the box to change the event's location."
    ),
    Pair(
        "ladder_transition",
        "Long press on the transition to access more editing features."
    ),
    Pair(
        "ladder_event",
        "This represents an event. Drag it up and down to change its time, or long press for additional features."
    ),
    Pair(
        "anim_ladder_toggle",
        "Select this to close the ladder diagram. (Please do so to advance.)"
    ),
    Pair(
        "info_pattern_list",
        "Select the Pattern List icon to open a list of patterns. (Please do so to advance.)"
    ),
    Pair(
        "file_my_lists",
        "This is the pattern list opening screen. This section at the top is where your user-created pattern lists go. Create as many as you want and share them with others!"
    ),
    Pair(
        "file_how_to_juggle",
        "These are the pattern lists that come with Juggling Lab. Select this one to open it. (Please do so to advance.)"
    ),
    Pair(
        "pattern_list_line",
        "Each line is a separate pattern. Select one to launch an animation, or long press for additional options. The menu at the upper right accesses features for the entire list."
    ),
    Pair(
        "pattern_list_close",
        "Select this to close a pattern list and load a new one. (Please do so to advance.)"
    ),
    Pair(
        "",
        "This completes the tour. We hope you enjoy Juggling Lab!"
    )
)
