//
// SiteswapGeneratorControl.kt
//
// Composable UI for the Siteswap Generator control.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SiteswapGeneratorControl(
    onConfirm: (String) -> Unit
) {
    // --- State Variables ---

    // Top Row
    var balls by remember { mutableStateOf("") }
    var maxThrow by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }

    // Column 1: Settings
    var jugglersIndex by remember { mutableStateOf(0) } // 0..5 maps to 1..6 jugglers
    var rhythmAsync by remember { mutableStateOf(true) } // true = async, false = sync
    var compositionIndex by remember { mutableStateOf(0) } // 0=all, 1=non-obvious, 2=prime

    // Column 2: Find Options
    var groundState by remember { mutableStateOf(true) }
    var excitedState by remember { mutableStateOf(true) }
    var transitionThrows by remember { mutableStateOf(false) }
    var patternRotations by remember { mutableStateOf(false) }
    var jugglerPermutations by remember { mutableStateOf(false) }
    var connectedPatterns by remember { mutableStateOf(true) }
    var symmetricPatterns by remember { mutableStateOf(false) }

    // Multiplexing
    var multiplexing by remember { mutableStateOf(false) }
    var simultaneousThrows by remember { mutableStateOf("") }
    var noSimultaneousCatches by remember { mutableStateOf(true) }
    var noClusteredThrows by remember { mutableStateOf(false) }
    var trueMultiplexing by remember { mutableStateOf(false) }

    // Bottom Section
    var excludeExpressions by remember { mutableStateOf("") }
    var includeExpressions by remember { mutableStateOf("") }
    var passingDelay by remember { mutableStateOf("") }

    // --- Logic Helpers ---
    val isPassing = jugglersIndex > 0 // 0 index = 1 juggler

    // Enablement Logic
    val passingDelayEnabled = isPassing && groundState && !excitedState
    val jugglerPermutationsEnabled = isPassing && groundState && excitedState
    val transitionThrowsEnabled = excitedState

    fun resetControl() {
        balls = "5"
        maxThrow = "7"
        period = "5"
        jugglersIndex = 0
        rhythmAsync = true
        compositionIndex = 0 // "all"

        groundState = true
        excitedState = true
        transitionThrows = false
        patternRotations = false
        jugglerPermutations = false
        connectedPatterns = true
        symmetricPatterns = false

        multiplexing = false
        simultaneousThrows = "2"
        noSimultaneousCatches = true
        noClusteredThrows = false
        trueMultiplexing = false

        excludeExpressions = ""
        includeExpressions = ""
        passingDelay = "0"
    }

    // Initialize defaults
    LaunchedEffect(Unit) {
        resetControl()
    }

    fun buildParams(): String {
        val sb = StringBuilder(256)

        // Basic Params
        val maxThrowVal = if (maxThrow.trim().isEmpty()) "-" else maxThrow
        val periodVal = if (period.trim().isEmpty()) "-" else period

        sb.append(balls).append(" ").append(maxThrowVal).append(" ").append(periodVal)

        // Rhythm
        if (!rhythmAsync) {
            sb.append(" -s")
        }

        // Jugglers
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

        // Compositions
        // 0 = all (-f), 1 = non-obvious (default), 2 = prime (-prime)
        when (compositionIndex) {
            0 -> sb.append(" -f")
            2 -> sb.append(" -prime")
        }

        // Find Options
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

        // Multiplexing
        if (multiplexing && simultaneousThrows.isNotEmpty()) {
            sb.append(" -m ").append(simultaneousThrows)
            if (!noSimultaneousCatches) sb.append(" -mf")
            if (noClusteredThrows) sb.append(" -mc")
            if (trueMultiplexing) sb.append(" -mt")
        }

        // Include/Exclude
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
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Top Row: Balls, Max Throw, Period ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactInput(label = "Balls", value = balls, onValueChange = { balls = it })
            CompactInput(label = "Max. throw", value = maxThrow, onValueChange = { maxThrow = it })
            CompactInput(label = "Period", value = period, onValueChange = { period = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Split Section: Settings (Left) vs Find (Right) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // LEFT COLUMN
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            ) {
                // Jugglers
                Text(text = "Jugglers", style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = (1..6).map { it.toString() },
                    selectedIndex = jugglersIndex,
                    onIndexChange = { jugglersIndex = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rhythm (Converted to Dropdown)
                Text(text = "Rhythm", style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = listOf("async", "sync"),
                    selectedIndex = if (rhythmAsync) 0 else 1,
                    onIndexChange = { rhythmAsync = (it == 0) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Compositions
                Text(text = "Compositions", style = MaterialTheme.typography.body1)
                Spacer(modifier = Modifier.height(4.dp))
                StyledDropdown(
                    items = listOf("all", "non-obvious", "none (prime only)"),
                    selectedIndex = compositionIndex,
                    onIndexChange = { compositionIndex = it }
                )
            }

            // RIGHT COLUMN (Find)
            Column(modifier = Modifier.weight(1.3f)) {
                Text(text = "Find", style = MaterialTheme.typography.body1)
                CompactCheckbox("ground state patterns", groundState) { groundState = it }
                CompactCheckbox("excited state patterns", excitedState) { excitedState = it }
                CompactCheckbox("transition throws", transitionThrows, enabled = transitionThrowsEnabled) { transitionThrows = it }
                CompactCheckbox("pattern rotations", patternRotations) { patternRotations = it }

                // Indented / Separated Passing Options
                Spacer(modifier = Modifier.height(4.dp))
                CompactCheckbox("juggler permutations", jugglerPermutations, enabled = jugglerPermutationsEnabled, color = Color.Gray) { jugglerPermutations = it }
                CompactCheckbox("connected patterns only", connectedPatterns, enabled = isPassing, color = Color.Gray) { connectedPatterns = it }
                CompactCheckbox("symmetric patterns only", symmetricPatterns, enabled = isPassing, color = Color.Gray) { symmetricPatterns = it }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Multiplexing Section ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { multiplexing = !multiplexing }
            ) {
                Checkbox(checked = multiplexing, onCheckedChange = { multiplexing = it })
                Text("Multiplexing", style = MaterialTheme.typography.body1)
            }

            // Sub-options
            val mxColor = if (multiplexing) Color.Unspecified else Color.Gray
            Column(modifier = Modifier.padding(start = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("simultaneous throws", color = mxColor, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = simultaneousThrows,
                        onValueChange = { simultaneousThrows = it },
                        enabled = multiplexing,
                        modifier = Modifier
                            .width(50.dp)
                            .height(50.dp)
                            .padding(PaddingValues(0.dp)),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
                        // Fix for input clipping
                        //contentPadding = PaddingValues(0.dp)
                    )
                }
                CompactCheckbox("no simultaneous catches", noSimultaneousCatches, enabled = multiplexing) { noSimultaneousCatches = it }
                CompactCheckbox("no clustered throws", noClusteredThrows, enabled = multiplexing) { noClusteredThrows = it }
                CompactCheckbox("true multiplexing only", trueMultiplexing, enabled = multiplexing) { trueMultiplexing = it }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Bottom Inputs (Aligned & Centered) ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlignedInputRow(label = "Exclude these expressions", value = excludeExpressions, onValueChange = { excludeExpressions = it })
            Spacer(modifier = Modifier.height(8.dp))
            AlignedInputRow(label = "Include these expressions", value = includeExpressions, onValueChange = { includeExpressions = it })
            Spacer(modifier = Modifier.height(8.dp))
            // Passing communication delay
            AlignedInputRow(
                label = "Passing communication delay",
                value = passingDelay,
                onValueChange = { passingDelay = it },
                enabled = passingDelayEnabled,
                inputWidth = 60.dp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Buttons (Defaults / Run) ---
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { resetControl() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
            ) {
                Text("Defaults", color = Color.Black)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { onConfirm(buildParams()) }
            ) {
                Text("Run")
            }
        }
    }
}

// --- Helper Composables ---

@Composable
private fun CompactInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
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
                .padding(PaddingValues(0.dp)),
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
            // Padding fix for top inputs
            //contentPadding = PaddingValues(0.dp)
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
            color = if (enabled) color else Color.Gray
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
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.body2,
            color = if (enabled) Color.Unspecified else Color.Gray,
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
            // Padding fix for vertical centering
            //contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
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
            .fillMaxWidth() // Enforces identical width logic in the column
            .height(40.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .clickable { expanded = true }
            .padding(horizontal = 8.dp),
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
