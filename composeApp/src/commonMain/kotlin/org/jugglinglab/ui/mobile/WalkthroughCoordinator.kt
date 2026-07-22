//
// WalkthroughCoordinator.kt
//
// Standalone coordinator for the mobile onboarding walkthrough. Decouples step
// definitions, state transitions, and element coordinate reporting.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.util.jlIsWeb
import org.jugglinglab.util.jlIsTouchInterface
import org.jugglinglab.util.jlGetStringResource
import org.jetbrains.compose.resources.StringResource
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
            val resPair = step.second ?: return null
            val resId = if (jlIsTouchInterface) resPair.first else (resPair.second ?: resPair.first)
            var text = jlGetStringResource(resId)
            if (jlIsWeb && step.first == "file_my_lists") {
                text += jlGetStringResource(Res.string.gui_walkthrough_web_note)
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

private val WALKTHROUGH_STEPS: List<Pair<String, Pair<StringResource, StringResource?>?>> = listOf(
    Pair("", null),  // Step 0 placeholder
    Pair("nav_info", Pair(Res.string.gui_walkthrough_step1, null)),
    Pair("info_pattern_list", Pair(Res.string.gui_walkthrough_step2, null)),
    Pair("info_favorites", Pair(Res.string.gui_walkthrough_step3, null)),
    Pair(
        "anim_center",
        Pair(Res.string.gui_walkthrough_step4_touch, Res.string.gui_walkthrough_step4_mouse)
    ),
    Pair("anim_menu", Pair(Res.string.gui_walkthrough_step5, null)),
    Pair("anim_ladder_toggle", Pair(Res.string.gui_walkthrough_step6, null)),
    Pair(
        "ladder_center",
        Pair(Res.string.gui_walkthrough_step7_touch, Res.string.gui_walkthrough_step7_mouse)
    ),
    Pair(
        "ladder_center",
        Pair(Res.string.gui_walkthrough_step8_touch, Res.string.gui_walkthrough_step8_mouse)
    ),
    Pair("ladder_transition", Pair(Res.string.gui_walkthrough_step9, null)),
    Pair("anim_box", Pair(Res.string.gui_walkthrough_step10, null)),
    Pair(
        "ladder_transition",
        Pair(Res.string.gui_walkthrough_step11_touch, Res.string.gui_walkthrough_step11_mouse)
    ),
    Pair(
        "ladder_event",
        Pair(Res.string.gui_walkthrough_step12_touch, Res.string.gui_walkthrough_step12_mouse)
    ),
    Pair("anim_ladder_toggle", Pair(Res.string.gui_walkthrough_step13, null)),
    Pair("info_pattern_list", Pair(Res.string.gui_walkthrough_step14, null)),
    Pair("file_my_lists", Pair(Res.string.gui_walkthrough_step15, null)),
    Pair("file_how_to_juggle", Pair(Res.string.gui_walkthrough_step16, null)),
    Pair(
        "pattern_list_line",
        Pair(Res.string.gui_walkthrough_step17_touch, Res.string.gui_walkthrough_step17_mouse)
    ),
    Pair("pattern_list_close", Pair(Res.string.gui_walkthrough_step18, null)),
    Pair("", Pair(Res.string.gui_walkthrough_step19, null))
)


