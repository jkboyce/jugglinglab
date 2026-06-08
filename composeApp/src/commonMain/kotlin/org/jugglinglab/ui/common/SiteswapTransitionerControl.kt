//
// SiteswapTransitionerControl.kt
//
// Composable UI for the siteswap transitioner control.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.ui.components.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteswapTransitionerControl(
    onConfirm: (String) -> Unit,
    isBusy: Boolean = false,
    state: SiteswapTransitionerState = remember { SiteswapTransitionerState() }
) {
    // state variables for control
    var fromPattern by state.fromPattern
    var toPattern by state.toPattern
    var multiplexing by state.multiplexing
    var simultaneousThrows by state.simultaneousThrows
    var noSimultaneousCatches by state.noSimultaneousCatches
    var noClusteredThrows by state.noClusteredThrows

    // Focus requester retained across recompositions
    val focusRequester = remember { FocusRequester() }

    fun resetControl() {
        state.reset()
    }

    LaunchedEffect(state) {
        if (state.fromPattern.value.isEmpty() && state.toPattern.value.isEmpty()) {
            resetControl()
        }
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
            // pattern entry section
            JlWidthAlignedInputRow(
                labelWidth = 100.dp, inputWidth = 250.dp,
                label = stringResource(Res.string.gui_from_pattern),
                value = fromPattern,
                onValueChange = { fromPattern = it },
                modifier = Modifier.focusRequester(focusRequester)
            )

            // swap button row, aligned to match the structure of PatternInputRow
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp),
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap patterns",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            JlWidthAlignedInputRow(
                labelWidth = 100.dp, inputWidth = 250.dp,
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
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // sub-options (indented)
                if (multiplexing) {
                    Column(modifier = Modifier.padding(start = 32.dp)) {
                        // simultaneous throws row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.gui_simultaneous_throws),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = simultaneousThrows,
                                onValueChange = { simultaneousThrows = it },
                                singleLine = true,
                                modifier = Modifier.width(50.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center)
                            )
                        }

                        // checkboxes
                        JlCheckbox(
                            label = stringResource(Res.string.gui_no_simultaneous_catches),
                            checked = noSimultaneousCatches,
                            onCheckedChange = { noSimultaneousCatches = it }
                        )

                        JlCheckbox(
                            label = stringResource(Res.string.gui_no_clustered_throws),
                            checked = noClusteredThrows,
                            onCheckedChange = { noClusteredThrows = it }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // action buttons (Defaults / Run)
        JlActionRowDefaultsRun(
            onDefaults = { resetControl() },
            onRun = { onConfirm(params()) },
            isBusy = isBusy
        )
    }
}

class SiteswapTransitionerState {
    val fromPattern = mutableStateOf("")
    val toPattern = mutableStateOf("")
    val multiplexing = mutableStateOf(false)
    val simultaneousThrows = mutableStateOf("2")
    val noSimultaneousCatches = mutableStateOf(true)
    val noClusteredThrows = mutableStateOf(false)

    fun reset() {
        fromPattern.value = ""
        toPattern.value = ""
        multiplexing.value = false
        simultaneousThrows.value = "2"
        noSimultaneousCatches.value = true
        noClusteredThrows.value = false
    }
}
