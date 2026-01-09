//
// SiteswapGeneratorControl.kt
//
// Composable UI for the Siteswap Generator control.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.composeapp.generated.resources.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteswapGeneratorControl(
    onConfirm: (String) -> Unit
) {
    // State Variables
    var balls by remember { mutableStateOf("") }
    var maxThrow by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    // column 1: settings
    var jugglersIndex by remember { mutableStateOf(0) } // 0..5 maps to 1..6 jugglers
    var rhythmAsync by remember { mutableStateOf(true) } // true = async, false = sync
    var compositionIndex by remember { mutableStateOf(0) } // 0=all, 1=non-obvious, 2=prime
    // multiplexing: 0="none", 1="2", 2="3", 3="4"
    var multiplexingIndex by remember { mutableStateOf(0) }
    // column 2: filters
    var groundState by remember { mutableStateOf(true) }
    var excitedState by remember { mutableStateOf(true) }
    var transitionThrows by remember { mutableStateOf(false) }
    var patternRotations by remember { mutableStateOf(false) }
    var jugglerPermutations by remember { mutableStateOf(false) }
    var connectedPatterns by remember { mutableStateOf(true) }
    var symmetricPatterns by remember { mutableStateOf(false) }
    var noSqueezeCatches by remember { mutableStateOf(true) }
    var noClusteredThrows by remember { mutableStateOf(false) }
    var trueMultiplexing by remember { mutableStateOf(true) }
    // bottom
    var excludeExpressions by remember { mutableStateOf("") }
    var includeExpressions by remember { mutableStateOf("") }
    var passingDelay by remember { mutableStateOf("") }

    // Focus requester retained across recompositions
    val focusRequester = remember { FocusRequester() }

    // logic helpers
    val isPassing = jugglersIndex > 0 // 0 index = 1 juggler
    val isMultiplexing = multiplexingIndex > 0 // 0 is "none"

    // enablement logic
    val passingDelayEnabled = isPassing && groundState && !excitedState
    val jugglerPermutationsEnabled = isPassing && groundState && excitedState
    val transitionThrowsEnabled = excitedState

    val multiplexingOptions = listOf(stringResource(Res.string.gui_multiplexing_none), "2", "3", "4")

    fun resetControl() {
        balls = "5"
        maxThrow = "7"
        period = "5"
        jugglersIndex = 0
        rhythmAsync = true
        multiplexingIndex = 0 // "none"
        compositionIndex = 0 // "all"

        groundState = true
        excitedState = true
        transitionThrows = false
        patternRotations = false
        jugglerPermutations = false
        connectedPatterns = true
        symmetricPatterns = false
        noSqueezeCatches = true
        noClusteredThrows = false
        trueMultiplexing = true

        excludeExpressions = ""
        includeExpressions = ""
        passingDelay = "0"
    }

    // Initialize defaults
    LaunchedEffect(Unit) {
        resetControl()
        focusRequester.requestFocus()
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
            .verticalScroll(rememberScrollState())
            .onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    onConfirm(params())
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Row: Balls, Max Throw, Period
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactInput(
                label = stringResource(Res.string.gui_balls),
                value = balls,
                onValueChange = { balls = it },
                modifier = Modifier.focusRequester(focusRequester)
            )
            CompactInput(label = stringResource(Res.string.gui_max__throw), value = maxThrow, onValueChange = { maxThrow = it })
            CompactInput(label = stringResource(Res.string.gui_period), value = period, onValueChange = { period = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Split Section: Settings (Left) vs Filters (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // LEFT COLUMN (Settings)
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            ) {
                // Jugglers
                Text(text = stringResource(Res.string.gui_jugglers), style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = (1..6).map { it.toString() },
                    selectedIndex = jugglersIndex,
                    onIndexChange = { jugglersIndex = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rhythm
                Text(text = stringResource(Res.string.gui_rhythm), style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = listOf(
                        stringResource(Res.string.gui_asynch),
                        stringResource(Res.string.gui_synch)
                    ),
                    selectedIndex = if (rhythmAsync) 0 else 1,
                    onIndexChange = { rhythmAsync = (it == 0) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Multiplexing (Dropdown)
                Text(text = stringResource(Res.string.gui_multiplexing), style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = multiplexingOptions,
                    selectedIndex = multiplexingIndex,
                    onIndexChange = { multiplexingIndex = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Compositions
                Text(text = stringResource(Res.string.gui_compositions), style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = listOf(
                        stringResource(Res.string.gui_all),
                        stringResource(Res.string.gui_non_obvious),
                        stringResource(Res.string.gui_none__prime_only_)
                    ),
                    selectedIndex = compositionIndex,
                    onIndexChange = { compositionIndex = it }
                )
            }

            // RIGHT COLUMN (Filters)
            Column(modifier = Modifier.weight(1.3f)) {
                Text(text = stringResource(Res.string.gui_find), style = MaterialTheme.typography.body1)
                CompactCheckbox(stringResource(Res.string.gui_ground_state_patterns), groundState) { groundState = it }
                CompactCheckbox(stringResource(Res.string.gui_excited_state_patterns), excitedState) { excitedState = it }
                CompactCheckbox(stringResource(Res.string.gui_transition_throws), transitionThrows, enabled = transitionThrowsEnabled) { transitionThrows = it }
                CompactCheckbox(stringResource(Res.string.gui_pattern_rotations), patternRotations) { patternRotations = it }

                // Passing options
                Spacer(modifier = Modifier.height(4.dp))
                val pColor = if (isPassing) Color.Unspecified else Color.LightGray
                CompactCheckbox(stringResource(Res.string.gui_juggler_permutations), jugglerPermutations, enabled = jugglerPermutationsEnabled, color = pColor) { jugglerPermutations = it }
                CompactCheckbox(stringResource(Res.string.gui_connected_patterns), connectedPatterns, enabled = isPassing, color = pColor) { connectedPatterns = it }
                CompactCheckbox(stringResource(Res.string.gui_symmetric_patterns), symmetricPatterns, enabled = isPassing, color = pColor) { symmetricPatterns = it }

                // Multiplexing checkboxes
                Spacer(modifier = Modifier.height(4.dp))
                val mxColor = if (isMultiplexing) Color.Unspecified else Color.LightGray
                CompactCheckbox(stringResource(Res.string.gui_no_simultaneous_catches), noSqueezeCatches, enabled = isMultiplexing, color = mxColor) { noSqueezeCatches = it }
                CompactCheckbox(stringResource(Res.string.gui_no_clustered_throws), noClusteredThrows, enabled = isMultiplexing, color = mxColor) { noClusteredThrows = it }
                CompactCheckbox(stringResource(Res.string.gui_true_multiplexing), trueMultiplexing, enabled = isMultiplexing, color = mxColor) { trueMultiplexing = it }
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
                AlignedInputRow(label = stringResource(Res.string.gui_exclude_these_throws), value = excludeExpressions, onValueChange = { excludeExpressions = it })
                Spacer(modifier = Modifier.height(8.dp))
                AlignedInputRow(label = stringResource(Res.string.gui_include_these_throws), value = includeExpressions, onValueChange = { includeExpressions = it })
                Spacer(modifier = Modifier.height(8.dp))
                // Passing communication delay (Left aligned within the group)
                AlignedInputRow(
                    label = stringResource(Res.string.gui_passing_communication_delay),
                    value = passingDelay,
                    onValueChange = { passingDelay = it },
                    enabled = passingDelayEnabled,
                    inputWidth = 60.dp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Buttons (Defaults / Run)
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { resetControl() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
            ) {
                Text(stringResource(Res.string.gui_defaults), color = Color.Black)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { onConfirm(params()) }
            ) {
                Text(stringResource(Res.string.gui_run))
            }
        }
    }
}

// --- Helper Composables ---

@Composable
private fun CompactInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.body2)
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .width(50.dp)
                .height(50.dp)
                .padding(PaddingValues(0.dp))
                .then(modifier),
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun CompactCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp) // Compact height for list
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = if (enabled) color else Color.LightGray
        )
    }
}

@Composable
private fun AlignedInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    inputWidth: Dp = 200.dp,
    labelWidth: Dp = 220.dp // Fixed width for alignment
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.body2,
            color = if (enabled) Color.Unspecified else Color.LightGray,
            modifier = Modifier.width(labelWidth).padding(end = 10.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .width(inputWidth)
                .height(50.dp)
                .padding(PaddingValues(horizontal = 8.dp, vertical = 0.dp)),
            singleLine = true,
            textStyle = MaterialTheme.typography.body2,
        )
    }
}

@Composable
private fun StyledDropdown(
    items: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .clickable { expanded = true }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedIndex in items.indices) items[selectedIndex] else "",
                style = MaterialTheme.typography.body2
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Dropdown",
                tint = Color.Gray
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    onClick = {
                        onIndexChange(index)
                        expanded = false
                    }
                ) {
                    Text(text = item, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}
