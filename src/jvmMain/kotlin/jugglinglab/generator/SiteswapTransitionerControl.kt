//
// SiteswapTransitionerControl.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import jugglinglab.generated.resources.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteswapTransitionerControl(onConfirm: (String) -> Unit) {
    // State lifted to class level so legacy methods (params, resetControl) can access it
    val fromPattern = mutableStateOf("")
    val toPattern = mutableStateOf("")

    // Multiplexing section
    val multiplexing = mutableStateOf(false)
    val simultaneousThrows = mutableStateOf("2")
    val noSimultaneousCatches = mutableStateOf(true)
    val noClusteredThrows = mutableStateOf(false)

    fun resetControl() {
        fromPattern.value = ""
        toPattern.value = ""
        multiplexing.value = false
        simultaneousThrows.value = "2"
        noSimultaneousCatches.value = true
        noClusteredThrows.value = false
    }

    resetControl()

    fun params(): String {
        val sb = StringBuilder(256)
        var fromPat = fromPattern.value
        if (fromPat.trim().isEmpty()) {
            fromPat = "-"
        }
        var toPat = toPattern.value
        if (toPat.trim().isEmpty()) {
            toPat = "-"
        }
        sb.append(fromPat).append(" ").append(toPat)
        if (multiplexing.value && simultaneousThrows.value.isNotEmpty()) {
            sb.append(" -m ").append(simultaneousThrows.value)
            if (!noSimultaneousCatches.value) {
                sb.append(" -mf")
            }
            if (noClusteredThrows.value) {
                sb.append(" -mc")
            }
        }
        return sb.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // pattern entry section
        PatternInputRow(
            label = stringResource(Res.string.gui_from_pattern),
            textState = fromPattern
        )

        Spacer(modifier = Modifier.height(8.dp))

        PatternInputRow(
            label = stringResource(Res.string.gui_to_pattern),
            textState = toPattern
        )

        Spacer(modifier = Modifier.height(8.dp))

        // swap button
        Button(
            onClick = {
                val temp = fromPattern.value
                fromPattern.value = toPattern.value
                toPattern.value = temp
            },
            modifier = Modifier.width(60.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
        ) {
            Text(text = "\u2195",  // up/down arrow
                color = Color.Black,
                fontSize = 25.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // multiplexing section
        Column(
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            // main checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Checkbox(
                    checked = multiplexing.value,
                    onCheckedChange = { multiplexing.value = it },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.gui_multiplexing_in_transitions),
                    style = MaterialTheme.typography.body1
                )
            }

            // bub-options (indented)
            // we use 'enabled' state based on the parent checkbox
            val enabled = multiplexing.value
            val contentColor = if (enabled) Color.Unspecified else Color.Gray

            Column(modifier = Modifier.padding(start = 32.dp)) {
                // Simultaneous throws row
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
                        value = simultaneousThrows.value,
                        onValueChange = { simultaneousThrows.value = it },
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
                    state = noSimultaneousCatches,
                    enabled = enabled
                )

                SubOptionCheckbox(
                    label = stringResource(Res.string.gui_no_clustered_throws),
                    state = noClusteredThrows,
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
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)
            ) {
                Text("Defaults", color = Color.Black)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { onConfirm(params()) }
            ) {
                Text("Run")
            }
        }
    }
}

@Composable
private fun PatternInputRow(label: String, textState: MutableState<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp), // Fixed width for alignment
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.body1
        )
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedTextField(
            value = textState.value,
            onValueChange = { textState.value = it },
            modifier = Modifier.width(250.dp).height(56.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.body1
        )
    }
}

@Composable
private fun SubOptionCheckbox(label: String, state: MutableState<Boolean>, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = state.value,
            onCheckedChange = { if (enabled) state.value = it },
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
