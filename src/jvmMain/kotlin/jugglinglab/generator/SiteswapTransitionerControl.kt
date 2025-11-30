//
// SiteswapTransitionerControl.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jugglinglab.JugglingLab.guistrings
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

internal class SiteswapTransitionerControl : JPanel() {

    // State lifted to class level so legacy methods (params, resetControl) can access it
    private val fromPattern = mutableStateOf("")
    private val toPattern = mutableStateOf("")

    // Multiplexing section
    private val multiplexing = mutableStateOf(false)
    private val simultaneousThrows = mutableStateOf("2")
    private val noSimultaneousCatches = mutableStateOf(true)
    private val noClusteredThrows = mutableStateOf(false)

    init {
        layout = BorderLayout()
        val composePanel = ComposePanel()

        // Set a preferred size so that pack() on the parent JFrame works correctly,
        // shrinking the window to fit the content instead of using a default large size.
        composePanel.preferredSize = Dimension(500, 450)

        composePanel.setContent {
            MaterialTheme {
                // Surface color matching the dialog background in the screenshot usually
                Surface(color = MaterialTheme.colors.surface) {
                    TransitionerContent()
                }
            }
        }
        add(composePanel, BorderLayout.CENTER)

        resetControl() // apply defaults
    }

    fun resetControl() {
        fromPattern.value = ""
        toPattern.value = ""
        multiplexing.value = false
        simultaneousThrows.value = "2"
        noSimultaneousCatches.value = true
        noClusteredThrows.value = false
    }

    val params: String
        get() {
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

    @Composable
    fun TransitionerContent() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // --- Pattern Entry Section ---
            // Using a grid-like layout for labels and fields
            PatternInputRow(
                label = guistrings.getString("from_pattern"),
                textState = fromPattern
            )

            Spacer(modifier = Modifier.height(8.dp))

            PatternInputRow(
                label = guistrings.getString("to_pattern"),
                textState = toPattern
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Swap Button
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
                Text("\u2195", color = Color.Black) // Up/Down arrow
            }

            Spacer(modifier = Modifier.height(30.dp))

            // --- Multiplexing Section ---
            Column(
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                // Main Checkbox
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
                        text = guistrings.getString("multiplexing_in_transitions"),
                        style = MaterialTheme.typography.body1
                    )
                }

                // Sub-options (Indented)
                // We use 'enabled' state based on the parent checkbox
                val enabled = multiplexing.value
                val contentColor = if (enabled) Color.Unspecified else Color.Gray

                Column(modifier = Modifier.padding(start = 32.dp)) {
                    // Simultaneous throws row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = guistrings.getString("simultaneous_throws"),
                            style = MaterialTheme.typography.body1,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = simultaneousThrows.value,
                            onValueChange = { simultaneousThrows.value = it },
                            enabled = enabled,
                            singleLine = true,
                            modifier = Modifier.width(50.dp),
                            textStyle = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.Black,
                                cursorColor = Color.Black,
                                disabledTextColor = Color.Gray,
                                disabledBorderColor = Color.LightGray
                            )
                        )
                    }

                    // Checkboxes
                    SubOptionCheckbox(
                        label = guistrings.getString("no_simultaneous_catches"),
                        state = noSimultaneousCatches,
                        enabled = enabled
                    )

                    SubOptionCheckbox(
                        label = guistrings.getString("no_clustered_throws"),
                        state = noClusteredThrows,
                        enabled = enabled
                    )
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
                modifier = Modifier.width(250.dp),
                singleLine = true,
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
}
