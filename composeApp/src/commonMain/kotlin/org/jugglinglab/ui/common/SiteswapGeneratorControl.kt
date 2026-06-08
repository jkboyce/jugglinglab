//
// SiteswapGeneratorControl.kt
//
// Composable UI for the Siteswap Generator control.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.ui.components.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteswapGeneratorControl(
    onConfirm: (String) -> Unit,
    isBusy: Boolean = false,
    state: SiteswapGeneratorState = remember { SiteswapGeneratorState() }
) {
    // State Variables
    var balls by state.balls
    var maxThrow by state.maxThrow
    var period by state.period
    // column 1: settings
    var jugglersIndex by state.jugglersIndex // 0..5 maps to 1..6 jugglers
    var rhythmAsync by state.rhythmAsync // true = async, false = sync
    var compositionIndex by state.compositionIndex // 0=all, 1=non-obvious, 2=prime
    // multiplexing: 0="none", 1="2", 2="3", 3="4"
    var multiplexingIndex by state.multiplexingIndex
    // column 2: filters
    var groundState by state.groundState
    var excitedState by state.excitedState
    var transitionThrows by state.transitionThrows
    var patternRotations by state.patternRotations
    var jugglerPermutations by state.jugglerPermutations
    var connectedPatterns by state.connectedPatterns
    var symmetricPatterns by state.symmetricPatterns
    var noSqueezeCatches by state.noSqueezeCatches
    var noClusteredThrows by state.noClusteredThrows
    var trueMultiplexing by state.trueMultiplexing
    // bottom
    var excludeExpressions by state.excludeExpressions
    var includeExpressions by state.includeExpressions
    var passingDelay by state.passingDelay

    // logic helpers
    val isPassing = jugglersIndex > 0 // 0 index = 1 juggler
    val isMultiplexing = multiplexingIndex > 0 // 0 is "none"

    // enablement logic
    val passingDelayEnabled = isPassing && groundState && !excitedState
    val jugglerPermutationsEnabled = isPassing && groundState && excitedState
    val transitionThrowsEnabled = excitedState

    val multiplexingOptions =
        listOf(stringResource(Res.string.gui_multiplexing_none), "2", "3", "4")

    fun resetControl() {
        state.reset()
    }

    LaunchedEffect(state) {
        // initialize defaults on first launch only
        if (state.balls.value.isEmpty() && state.maxThrow.value.isEmpty() && state.period.value.isEmpty()) {
            resetControl()
        }
    }

    fun params(): String {
        val sb = StringBuilder()
        val maxThrowVal = if (maxThrow.trim().isEmpty()) "-" else maxThrow
        val periodVal = if (period.trim().isEmpty()) "-" else period
        sb.append(balls).append(" ").append(maxThrowVal).append(" ").append(periodVal)
        if (!rhythmAsync) {
            sb.append(" -s")
        }
        val jugglerCount = jugglersIndex + 1
        if (jugglerCount > 1) {
            sb.append(" -j ").append(jugglerCount)
            if (passingDelayEnabled && passingDelay.isNotEmpty()) {
                sb.append(" -d ").append(passingDelay).append(" -l 1")
            }
            if (jugglerPermutationsEnabled) {
                if (jugglerPermutations) sb.append(" -jp")
            } else {
                sb.append(" -jp")
            }
            if (connectedPatterns) sb.append(" -cp")
            if (symmetricPatterns) sb.append(" -sym")
        }
        // 0 = all (-f), 1 = non-obvious (default), 2 = prime (-prime)
        when (compositionIndex) {
            0 -> sb.append(" -f")
            2 -> sb.append(" -prime")
        }
        if (groundState && !excitedState) {
            sb.append(" -g")
        } else if (!groundState && excitedState) {
            sb.append(" -ng")
        }
        if (!transitionThrowsEnabled || !transitionThrows) {
            sb.append(" -se")
        }
        if (patternRotations) {
            sb.append(" -rot")
        }
        if (isMultiplexing) {
            val simultThrows = multiplexingOptions[multiplexingIndex]
            sb.append(" -m ").append(simultThrows)
            if (!noSqueezeCatches) sb.append(" -mf")
            if (noClusteredThrows) sb.append(" -mc")
            if (trueMultiplexing) sb.append(" -mt")
        }
        if (excludeExpressions.isNotEmpty()) {
            sb.append(" -x ").append(excludeExpressions)
        }
        if (includeExpressions.isNotEmpty()) {
            sb.append(" -i ").append(includeExpressions)
        }
        sb.append(" -n")
        return sb.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    onConfirm(params())
                    true
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Row: Balls, Max Throw, Period
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                JlCompactInput(
                    label = stringResource(Res.string.gui_balls),
                    value = balls,
                    onValueChange = { balls = it },
                    modifier = Modifier.weight(1f)
                )
                JlCompactInput(
                    label = stringResource(Res.string.gui_max__throw),
                    value = maxThrow,
                    onValueChange = { maxThrow = it },
                    modifier = Modifier.weight(1f)
                )
                JlCompactInput(
                    label = stringResource(Res.string.gui_period),
                    value = period,
                    onValueChange = { period = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings 2x2 Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                // LEFT COLUMN (Rhythm, Multiplexing)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Rhythm
                    Text(
                        text = stringResource(Res.string.gui_rhythm),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JlStringDropdown(
                        items = listOf(
                            stringResource(Res.string.gui_asynch),
                            stringResource(Res.string.gui_synch)
                        ),
                        selectedIndex = if (rhythmAsync) 0 else 1,
                        onIndexChange = { rhythmAsync = (it == 0) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Multiplexing (Dropdown)
                    Text(
                        text = stringResource(Res.string.gui_multiplexing),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JlStringDropdown(
                        items = multiplexingOptions,
                        selectedIndex = multiplexingIndex,
                        onIndexChange = { multiplexingIndex = it }
                    )
                }

                Spacer(modifier = Modifier.width(30.dp))

                // RIGHT COLUMN (Jugglers, Compositions)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Jugglers
                    Text(
                        text = stringResource(Res.string.gui_jugglers),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JlStringDropdown(
                        items = (1..6).map { it.toString() },
                        selectedIndex = jugglersIndex,
                        onIndexChange = { jugglersIndex = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compositions
                    Text(
                        text = stringResource(Res.string.gui_compositions),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JlStringDropdown(
                        items = listOf(
                            stringResource(Res.string.gui_all),
                            stringResource(Res.string.gui_non_obvious),
                            stringResource(Res.string.gui_none__prime_only_)
                        ),
                        selectedIndex = compositionIndex,
                        onIndexChange = { compositionIndex = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Filters Section
            Column(modifier = Modifier.fillMaxWidth()) {
                /*
                Text(
                    text = stringResource(Res.string.gui_find),
                    style = MaterialTheme.typography.bodyLarge
                )*/
                JlCheckbox(
                    stringResource(Res.string.gui_ground_state_patterns),
                    groundState
                ) { groundState = it }
                JlCheckbox(
                    stringResource(Res.string.gui_excited_state_patterns),
                    excitedState
                ) { excitedState = it }
                if (transitionThrowsEnabled) {
                    JlCheckbox(
                        stringResource(Res.string.gui_transition_throws),
                        transitionThrows
                    ) { transitionThrows = it }
                }
                JlCheckbox(
                    stringResource(Res.string.gui_pattern_rotations),
                    patternRotations
                ) { patternRotations = it }

                // Passing options
                if (isPassing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (jugglerPermutationsEnabled) {
                        JlCheckbox(
                            stringResource(Res.string.gui_juggler_permutations),
                            jugglerPermutations
                        ) { jugglerPermutations = it }
                    }
                    JlCheckbox(
                        stringResource(Res.string.gui_connected_patterns),
                        connectedPatterns
                    ) { connectedPatterns = it }
                    JlCheckbox(
                        stringResource(Res.string.gui_symmetric_patterns),
                        symmetricPatterns
                    ) { symmetricPatterns = it }
                }

                // Multiplexing checkboxes
                if (isMultiplexing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    JlCheckbox(
                        stringResource(Res.string.gui_no_simultaneous_catches),
                        noSqueezeCatches
                    ) { noSqueezeCatches = it }
                    JlCheckbox(
                        stringResource(Res.string.gui_no_clustered_throws),
                        noClusteredThrows
                    ) { noClusteredThrows = it }
                    JlCheckbox(
                        stringResource(Res.string.gui_true_multiplexing),
                        trueMultiplexing
                    ) { trueMultiplexing = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Bottom Inputs (Aligned & Centered Block) ---
            // We use a Box with center alignment to center the group,
            // but inside the column, rows naturally align left.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    JlWidthAlignedInputRow(
                        labelWidth = 220.dp, inputWidth = 200.dp,
                        label = stringResource(Res.string.gui_exclude_these_throws),
                        value = excludeExpressions,
                        onValueChange = { excludeExpressions = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    JlWidthAlignedInputRow(
                        labelWidth = 220.dp, inputWidth = 200.dp,
                        label = stringResource(Res.string.gui_include_these_throws),
                        value = includeExpressions,
                        onValueChange = { includeExpressions = it })
                    if (passingDelayEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Passing communication delay (Left aligned within the group)
                        JlWidthAlignedInputRow(
                            labelWidth = 220.dp,
                            label = stringResource(Res.string.gui_passing_communication_delay),
                            value = passingDelay,
                            onValueChange = { passingDelay = it },
                            inputWidth = 60.dp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons (Defaults / Run)
        JlActionRowDefaultsRun(
            onDefaults = { resetControl() },
            onRun = { onConfirm(params()) },
            isBusy = isBusy
        )
    }
}

class SiteswapGeneratorState {
    val balls = mutableStateOf("")
    val maxThrow = mutableStateOf("")
    val period = mutableStateOf("")
    val jugglersIndex = mutableIntStateOf(0)
    val rhythmAsync = mutableStateOf(true)
    val compositionIndex = mutableIntStateOf(0)
    val multiplexingIndex = mutableIntStateOf(0)
    val groundState = mutableStateOf(true)
    val excitedState = mutableStateOf(true)
    val transitionThrows = mutableStateOf(false)
    val patternRotations = mutableStateOf(false)
    val jugglerPermutations = mutableStateOf(false)
    val connectedPatterns = mutableStateOf(true)
    val symmetricPatterns = mutableStateOf(false)
    val noSqueezeCatches = mutableStateOf(true)
    val noClusteredThrows = mutableStateOf(false)
    val trueMultiplexing = mutableStateOf(true)
    val excludeExpressions = mutableStateOf("")
    val includeExpressions = mutableStateOf("")
    val passingDelay = mutableStateOf("")

    fun reset() {
        balls.value = "5"
        maxThrow.value = "7"
        period.value = "5"
        jugglersIndex.intValue = 0
        rhythmAsync.value = true
        multiplexingIndex.intValue = 0
        compositionIndex.intValue = 0
        groundState.value = true
        excitedState.value = true
        transitionThrows.value = false
        patternRotations.value = false
        jugglerPermutations.value = false
        connectedPatterns.value = true
        symmetricPatterns.value = false
        noSqueezeCatches.value = true
        noClusteredThrows.value = false
        trueMultiplexing.value = true
        excludeExpressions.value = ""
        includeExpressions.value = ""
        passingDelay.value = "0"
    }
}
