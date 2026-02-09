//
// SiteswapNotationControl.kt
//
// Composable UI for the Siteswap Notation control.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.prop.Prop
import jugglinglab.prop.Prop.Companion.builtinPropsStringResources
import jugglinglab.util.ParameterList
import jugglinglab.util.jlGetStringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteswapNotationControl(
    onConfirm: (ParameterList) -> Unit
) {
    // State Variables
    var pattern by remember { mutableStateOf("") }
    var beatsPerSecond by remember { mutableStateOf("") }
    var dwellBeats by remember { mutableStateOf("") }
    var handParams by remember { mutableStateOf("") }
    var handDropdownIndex by remember { mutableIntStateOf(0) }
    var bodyParams by remember { mutableStateOf("") }
    var bodyDropdownIndex by remember { mutableIntStateOf(0) }
    var propIndex by remember { mutableIntStateOf(0) }
    var manualSettings by remember { mutableStateOf("") }

    // Focus requester retained across recompositions
    val focusRequester = remember { FocusRequester() }

    fun resetControl() {
        pattern = "3"
        beatsPerSecond = ""
        dwellBeats = "1.3"
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
        focusRequester.requestFocus()
    }

    fun parameterList(): ParameterList {
        val sb = StringBuilder()
        sb.append("pattern=").append(pattern)
        if (propIndex != 0 && propIndex < Prop.builtinProps.size) {
            sb.append(";prop=").append(Prop.builtinProps[propIndex].lowercase())
        }
        if (dwellBeats.isNotEmpty() && dwellBeats != "1.3") {
            sb.append(";dwell=").append(dwellBeats)
        }
        if (beatsPerSecond.isNotEmpty()) {
            sb.append(";bps=").append(beatsPerSecond)
        }
        if (handParams.isNotEmpty()) {
            sb.append(";hands=").append(handParams)
        }
        if (bodyParams.isNotEmpty()) {
            sb.append(";body=").append(bodyParams)
        }
        if (manualSettings.isNotEmpty()) {
            sb.append(";").append(manualSettings)
        }

        val pl = ParameterList(sb.toString())

        // check if we want to add a non-default title
        if (pl.getParameter("title") == null) {
            val hss = pl.getParameter("hss")
            if (hss != null) {
                val title = "oss: " + pl.getParameter("pattern") + "  hss: " + hss
                pl.addParameter("title", title)
            } else if (handDropdownIndex != 0) {
                // if hands are not default, apply a title
                val handsLabels = builtinHandsStringResources +
                    listOf(Res.string.gui_mhnhands_name_custom)
                val title = pl.getParameter("pattern") + " " +
                    jlGetStringResource(handsLabels[handDropdownIndex - 1])
                pl.addParameter("title", title)
            } else if (bodyDropdownIndex != 0) {
                // if body movement is not default, apply a title
                val bodyLabels = builtinBodyStringResources +
                    listOf(Res.string.gui_mhnbody_name_custom)
                val title = pl.getParameter("pattern") + " " +
                    jlGetStringResource(bodyLabels[bodyDropdownIndex - 1])
                pl.addParameter("title", title)
            }
        }

        return pl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()) // Allow scrolling for small screens
            .onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    onConfirm(parameterList())
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pattern
        LabelledInputRow(
            label = stringResource(Res.string.gui_pattern),
            value = pattern,
            onValueChange = { pattern = it },
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Beats per second
        LabelledInputRow(
            label = stringResource(Res.string.gui_beats_per_second),
            value = beatsPerSecond,
            onValueChange = { beatsPerSecond = it },
            width = 100.dp // Smaller width for numeric inputs
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Dwell beats
        LabelledInputRow(
            label = stringResource(Res.string.gui_dwell_beats),
            value = dwellBeats,
            onValueChange = { dwellBeats = it },
            width = 100.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Hand Movement Complex Control
        MovementControlRow(
            label = stringResource(Res.string.gui_hand_movement),
            dropdownItems = listOf(Res.string.gui_mhnhands_name_default) +
                builtinHandsStringResources +
                listOf(Res.string.gui_mhnhands_name_custom),
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
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Body Movement
        MovementControlRow(
            label = stringResource(Res.string.gui_body_movement),
            dropdownItems = listOf(Res.string.gui_mhnbody_name_default) +
                builtinBodyStringResources +
                listOf(Res.string.gui_mhnbody_name_custom),
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
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Prop Type Dropdown
        DropdownRow(
            label = stringResource(Res.string.gui_prop_type),
            items = builtinPropsStringResources,
            selectedIndex = propIndex,
            onIndexChange = { propIndex = it },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Manual Settings
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(Res.string.gui_manual_settings),
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                value = manualSettings,
                onValueChange = { manualSettings = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
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
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(stringResource(Res.string.gui_defaults), color = Color.Black)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { onConfirm(parameterList()) }
            ) {
                Text(stringResource(Res.string.gui_run))
            }
        }
    }
}

// Helper Composables

@Composable
private fun LabelledInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 250.dp,
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
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(10.dp))

        // Alignment shim to keep fields left-aligned in the remaining space
        Box(modifier = Modifier.width(250.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(width).height(56.dp).then(modifier),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Suppress("SameParameterValue")
@Composable
private fun DropdownRow(
    label: String,
    items: List<StringResource>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
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
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.width(250.dp)) {
            SimpleDropdown(
                items = items,
                selectedIndex = selectedIndex,
                onIndexChange = onIndexChange,
                modifier = Modifier.width(160.dp)
            )
        }
    }
}

@Composable
private fun MovementControlRow(
    label: String,
    dropdownItems: List<StringResource>,
    selectedIndex: Int,
    textValue: String,
    onDropdownChange: (Int) -> Unit,
    onTextChange: (String) -> Unit,
) {
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
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.width(250.dp)) {
                SimpleDropdown(
                    items = dropdownItems,
                    selectedIndex = selectedIndex,
                    onIndexChange = onDropdownChange,
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
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun SimpleDropdown(
    items: List<StringResource>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
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
                text = if (selectedIndex in items.indices) stringResource(items[selectedIndex]) else "",
                style = MaterialTheme.typography.bodyLarge
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
                    text = { Text(text = stringResource(item), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onIndexChange(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

//------------------------------------------------------------------------------
// Content for dropdowns in UI
//------------------------------------------------------------------------------

val builtinHandsNames: List<String> = listOf(
    "inside",
    "outside",
    "half",
    "Mills",
)
val builtinHandsStrings: List<String> = listOf(
    "(10)(32.5).",
    "(32.5)(10).",
    "(32.5)(10).(10)(32.5).",
    "(-25)(2.5).(25)(-2.5).(-25)(0).",
)
val builtinHandsStringResources: List<StringResource> = listOf(
    Res.string.gui_mhnhands_name_inside,
    Res.string.gui_mhnhands_name_outside,
    Res.string.gui_mhnhands_name_half,
    Res.string.gui_mhnhands_name_mills,
)

val builtinBodyNames: List<String> = listOf(
    "line",
    "feed",
    "backtoback",
    "sidetoside",
    "circles",
)
val builtinBodyStrings: List<String> = listOf(
    "<(90).|(270,-125).|(90,125).|(270,-250).|(90,250).|(270,-375).>",
    "<(90,75).|(270,-75,50).|(270,-75,-50).|(270,-75,150).|(270,-75,-150).>",
    "<(270,35).|(90,-35).|(0,0,35).|(180,0,-35).>",
    "<(0).|(0,100).|(0,-100).|(0,200).|(0,-200).|(0,300).>",
    "(0,75,0)...(90,0,75)...(180,-75,0)...(270,0,-75)...",
)
val builtinBodyStringResources: List<StringResource> = listOf(
    Res.string.gui_mhnbody_name_line,
    Res.string.gui_mhnbody_name_feed,
    Res.string.gui_mhnbody_name_backtoback,
    Res.string.gui_mhnbody_name_sidetoside,
    Res.string.gui_mhnbody_name_circles,
)