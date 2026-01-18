//
// SiteswapTransitionerControl.kt
//
// Composable UI for the siteswap transitioner control.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.composeapp.generated.resources.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteswapTransitionerControl(
    onConfirm: (String) -> Unit
) {
    // state variables for control
    var fromPattern by remember { mutableStateOf("") }
    var toPattern by remember { mutableStateOf("") }
    var multiplexing by remember { mutableStateOf(false) }
    var simultaneousThrows by remember { mutableStateOf("2") }
    var noSimultaneousCatches by remember { mutableStateOf(true) }
    var noClusteredThrows by remember { mutableStateOf(false) }

    // Focus requester retained across recompositions
    val focusRequester = remember { FocusRequester() }

    fun resetControl() {
        fromPattern = ""
        toPattern = ""
        multiplexing = false
        simultaneousThrows = "2"
        noSimultaneousCatches = true
        noClusteredThrows = false
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun params(): String {
        val sb = StringBuilder()
        var fromPat = fromPattern
        if (fromPat.trim().isEmpty()) {
            fromPat = "-"
        }
        var toPat = toPattern
        if (toPat.trim().isEmpty()) {
            toPat = "-"
        }
        sb.append(fromPat).append(" ").append(toPat)
        if (multiplexing && simultaneousThrows.isNotEmpty()) {
            sb.append(" -m ").append(simultaneousThrows)
            if (!noSimultaneousCatches) {
                sb.append(" -mf")
            }
            if (noClusteredThrows) {
                sb.append(" -mc")
            }
        }
        return sb.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            //.verticalScroll(rememberScrollState()),
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
        // pattern entry section
        PatternInputRow(
            label = stringResource(Res.string.gui_from_pattern),
            value = fromPattern,
            onValueChange = { fromPattern = it },
            modifier = Modifier.focusRequester(focusRequester)
        )

        // swap button row, aligned to match the structure of PatternInputRow
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(100.dp))
            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.width(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val temp = fromPattern
                        fromPattern = toPattern
                        toPattern = temp
                    },
                    modifier = Modifier.width(60.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Swap patterns",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Black
                    )
                }
            }
        }

        PatternInputRow(
            label = stringResource(Res.string.gui_to_pattern),
            value = toPattern,
            onValueChange = { toPattern = it }
        )

        Spacer(modifier = Modifier.height(40.dp))

        // multiplexing section
        Column(
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            // main checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { multiplexing = !multiplexing }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = multiplexing,
                    onCheckedChange = { multiplexing = it },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.gui_multiplexing_in_transitions),
                    style = MaterialTheme.typography.body1
                )
            }

            // sub-options (indented)
            // we use 'enabled' state based on the parent checkbox
            val enabled = multiplexing
            val contentColor = if (enabled) Color.Unspecified else Color.Gray

            Column(modifier = Modifier.padding(start = 32.dp)) {
                // simultaneous throws row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.gui_simultaneous_throws),
                        style = MaterialTheme.typography.body1,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = simultaneousThrows,
                        onValueChange = { simultaneousThrows = it },
                        enabled = enabled,
                        singleLine = true,
                        modifier = Modifier.width(50.dp).height(56.dp),
                        textStyle = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.Black,
                            cursorColor = Color.Black,
                            disabledTextColor = Color.Gray,
                            disabledBorderColor = Color.LightGray
                        )
                    )
                }

                // checkboxes
                SubOptionCheckbox(
                    label = stringResource(Res.string.gui_no_simultaneous_catches),
                    checked = noSimultaneousCatches,
                    onCheckedChange = { noSimultaneousCatches = it },
                    enabled = enabled
                )

                SubOptionCheckbox(
                    label = stringResource(Res.string.gui_no_clustered_throws),
                    checked = noClusteredThrows,
                    onCheckedChange = { noClusteredThrows = it },
                    enabled = enabled
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // action buttons (Defaults / Run)
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

@Composable
private fun PatternInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp), // fixed width for alignment
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.body1
        )
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(250.dp).height(56.dp).then(modifier),
            singleLine = true,
            textStyle = MaterialTheme.typography.body1
        )
    }
}

@Composable
private fun SubOptionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clickable { onCheckedChange(!checked) }
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
            style = MaterialTheme.typography.body1,
            color = if (enabled) Color.Unspecified else Color.Gray
        )
    }
}
