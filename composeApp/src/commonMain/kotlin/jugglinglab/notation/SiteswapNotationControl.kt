//
// SiteswapNotationControl.kt
//
// Composable UI for the Siteswap Notation control.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.prop.Prop
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
import androidx.compose.ui.unit.dp

@Composable
fun SiteswapNotationControl(
    onConfirm: (String) -> Unit
) {
    // --- Constants and Data Sources (Mirrored from Swing Companion Object) ---
    val builtinHandsNames = listOf("inside", "outside", "half", "Mills")
    val builtinHandsStrings = listOf(
        "(10)(32.5).",
        "(32.5)(10).",
        "(32.5)(10).(10)(32.5).",
        "(-25)(2.5).(25)(-2.5).(-25)(0)."
    )
    val builtinBodyNames = listOf("line", "feed", "backtoback", "sidetoside", "circles")
    val builtinBodyStrings = listOf(
        "<(90).|(270,-125).|(90,125).|(270,-250).|(90,250).|(270,-375).>",
        "<(90,75).|(270,-75,50).|(270,-75,-50).|(270,-75,150).|(270,-75,-150).>",
        "<(270,35).|(90,-35).|(0,0,35).|(180,0,-35).>",
        "<(0).|(0,100).|(0,-100).|(0,200).|(0,-200).|(0,300).>",
        "(0,75,0)...(90,0,75)...(180,-75,0)...(270,0,-75)..."
    )

    // Helper to get display strings for Dropdowns
    @Composable
    fun getLabel(resourceKey: String): String {
        // In a real app, map these keys to stringResource(Res.string.x).
        // Using placeholders based on the provided Swing code strings.
        return when(resourceKey) {
            "MHNHands_name_default" -> "default"
            "MHNHands_name_custom" -> "custom"
            "MHNHands_name_inside" -> "inside"
            "MHNHands_name_outside" -> "outside"
            "MHNHands_name_half" -> "half"
            "MHNHands_name_Mills" -> "Mills"
            "MHNBody_name_default" -> "default"
            "MHNBody_name_custom" -> "custom"
            "MHNBody_name_line" -> "line"
            "MHNBody_name_feed" -> "feed"
            "MHNBody_name_backtoback" -> "backtoback"
            "MHNBody_name_sidetoside" -> "sidetoside"
            "MHNBody_name_circles" -> "circles"
            else -> resourceKey.removePrefix("Prop_name_") // Fallback for props
        }
    }

    // --- State Variables ---
    var pattern by remember { mutableStateOf("") }
    var beatsPerSecond by remember { mutableStateOf("") }
    var dwellBeats by remember { mutableStateOf("") }

    // Hand Movement State
    var handParams by remember { mutableStateOf("") }
    var handDropdownIndex by remember { mutableStateOf(0) } // 0 = default

    // Body Movement State
    var bodyParams by remember { mutableStateOf("") }
    var bodyDropdownIndex by remember { mutableStateOf(0) } // 0 = default

    // Prop State
    var propIndex by remember { mutableStateOf(0) } // 0 = ball (usually)

    // Manual Settings
    var manualSettings by remember { mutableStateOf("") }

    fun resetControl() {
        pattern = "3"
        beatsPerSecond = ""
        dwellBeats = "1.3" // Default based on screenshot
        handParams = ""
        handDropdownIndex = 0
        bodyParams = ""
        bodyDropdownIndex = 0
        propIndex = 0
        manualSettings = ""
    }

    // Initialize defaults once
    LaunchedEffect(Unit) {
        resetControl()
    }

    fun buildParams(): String {
        val sb = StringBuilder()
        sb.append("pattern=")
        sb.append(pattern)

        // Prop (Assuming Prop.builtinProps exists or using local logic)
        if (propIndex != 0 && propIndex < Prop.builtinProps.size) {
            sb.append(";prop=")
            sb.append(Prop.builtinProps[propIndex].lowercase())
        }

        // Dwell
        if (dwellBeats.isNotEmpty() && dwellBeats != "1.3") {
            sb.append(";dwell=")
            sb.append(dwellBeats)
        }

        // BPS
        if (beatsPerSecond.isNotEmpty()) {
            sb.append(";bps=")
            sb.append(beatsPerSecond)
        }

        // Hands
        if (handParams.isNotEmpty()) {
            sb.append(";hands=")
            sb.append(handParams)
        }

        // Body
        if (bodyParams.isNotEmpty()) {
            sb.append(";body=")
            sb.append(bodyParams)
        }

        // Manual
        if (manualSettings.isNotEmpty()) {
            sb.append(";")
            sb.append(manualSettings)
        }

        // Title Logic (simplified from Swing to just construct the parameter string)
        // In the full system, ParameterList parsing handles the title logic
        // based on these parameters.

        return sb.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Allow scrolling for small screens
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pattern
        LabelledInputRow(
            label = "Pattern", // stringResource(Res.string.Pattern)
            value = pattern,
            onValueChange = { pattern = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Beats per second
        LabelledInputRow(
            label = "Beats per second", // stringResource(Res.string.Beats_per_second)
            value = beatsPerSecond,
            onValueChange = { beatsPerSecond = it },
            width = 100.dp // Smaller width for numeric inputs
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Dwell beats
        LabelledInputRow(
            label = "Dwell beats", // stringResource(Res.string.Dwell_beats)
            value = dwellBeats,
            onValueChange = { dwellBeats = it },
            width = 100.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Hand Movement Complex Control
        MovementControlRow(
            label = "Hand movement",
            dropdownItems = listOf("MHNHands_name_default") +
                builtinHandsNames.map { "MHNHands_name_$it" } +
                listOf("MHNHands_name_custom"),
            selectedIndex = handDropdownIndex,
            textValue = handParams,
            onDropdownChange = { index ->
                handDropdownIndex = index
                when (index) {
                    0 -> handParams = "" // Default
                    builtinHandsNames.size + 1 -> {} // Custom, do nothing
                    else -> handParams = builtinHandsStrings[index - 1]
                }
            },
            onTextChange = { text ->
                handParams = text
                // Logic: if text empty -> default, else -> custom
                handDropdownIndex = if (text.isEmpty()) 0 else (builtinHandsNames.size + 1)
            },
            getLabel = { getLabel(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Body Movement Complex Control
        MovementControlRow(
            label = "Body movement",
            dropdownItems = listOf("MHNBody_name_default") +
                builtinBodyNames.map { "MHNBody_name_$it" } +
                listOf("MHNBody_name_custom"),
            selectedIndex = bodyDropdownIndex,
            textValue = bodyParams,
            onDropdownChange = { index ->
                bodyDropdownIndex = index
                when (index) {
                    0 -> bodyParams = "" // Default
                    builtinBodyNames.size + 1 -> {} // Custom
                    else -> bodyParams = builtinBodyStrings[index - 1]
                }
            },
            onTextChange = { text ->
                bodyParams = text
                bodyDropdownIndex = if (text.isEmpty()) 0 else (builtinBodyNames.size + 1)
            },
            getLabel = { getLabel(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Prop Type Dropdown
        // Construct prop list similar to Swing
        val propItems = Prop.builtinProps.map { "Prop_name_" + it.lowercase() }

        DropdownRow(
            label = "Prop type",
            items = propItems,
            selectedIndex = propIndex,
            onIndexChange = { propIndex = it },
            getLabel = { getLabel(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Manual Settings (Full width styling based on screenshot/Swing)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Manual settings",
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                style = MaterialTheme.typography.body1
            )
            OutlinedTextField(
                value = manualSettings,
                onValueChange = { manualSettings = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.body1
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
private fun LabelledInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    width: androidx.compose.ui.unit.Dp = 250.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.width(130.dp), // Matched visual alignment
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.body1
        )
        Spacer(modifier = Modifier.width(10.dp))

        // Alignment shim to keep fields left-aligned in the remaining space
        Box(modifier = Modifier.width(250.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(width).height(56.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.body1
            )
        }
    }
}

@Suppress("SameParameterValue")
@Composable
private fun DropdownRow(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    getLabel: @Composable (String) -> String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.width(130.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.body1
        )
        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.width(250.dp)) {
            SimpleDropdown(
                items = items,
                selectedIndex = selectedIndex,
                onIndexChange = onIndexChange,
                getLabel = getLabel,
                modifier = Modifier.width(160.dp) // Approximate width from screenshot
            )
        }
    }
}

@Composable
private fun MovementControlRow(
    label: String,
    dropdownItems: List<String>,
    selectedIndex: Int,
    textValue: String,
    onDropdownChange: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    getLabel: @Composable (String) -> String
) {
    // This mimics the Swing layout: Label, Dropdown, then text field on next "visual" line (or offset)
    // The screenshot shows: Label [Dropdown]
    //                       [TextField]

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Row 1: Label + Dropdown
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                modifier = Modifier.width(130.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.body1
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.width(250.dp)) {
                SimpleDropdown(
                    items = dropdownItems,
                    selectedIndex = selectedIndex,
                    onIndexChange = onDropdownChange,
                    getLabel = getLabel,
                    modifier = Modifier.width(160.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Indented Text Field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.width(140.dp)) // Offset to align under inputs
            Box(modifier = Modifier.width(250.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body1
                )
            }
        }
    }
}

@Composable
private fun SimpleDropdown(
    items: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    getLabel: @Composable (String) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(50.dp) // Match height of standard inputs roughly
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
                text = if (selectedIndex in items.indices) getLabel(items[selectedIndex]) else "",
                style = MaterialTheme.typography.body1
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
                    Text(text = getLabel(item))
                }
            }
        }
    }
}

