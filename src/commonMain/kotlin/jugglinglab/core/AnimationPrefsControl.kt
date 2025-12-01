//
// AnimationPrefsControl.kt
//
// Composable UI for the animation preferences dialog.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.generated.resources.*
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.getStringResource
import jugglinglab.util.jlToStringRounded
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun AnimationPrefsControl(
    initialPrefs: AnimationPrefs,
    onConfirm: (AnimationPrefs) -> Unit,
    onCancel: () -> Unit
) {
    // Extra parameters not covered by UI fields
    val initialManualSettings = try {
        val pl = ParameterList(initialPrefs.toString())
        val paramsRemove = listOf(
            "width", "height", "fps", "slowdown", "border",
            "showground", "startpaused", "mousepause", "stereo",
            "catchsound", "bouncesound"
        )
        for (param in paramsRemove) {
            pl.removeParameter(param)
        }
        pl.toString()
    } catch (_: Exception) { "" }

    // State holders
    var width by remember { mutableStateOf(initialPrefs.width.toString()) }
    var height by remember { mutableStateOf(initialPrefs.height.toString()) }
    var fps by remember { mutableStateOf(jlToStringRounded(initialPrefs.fps, 2)) }
    var slowdown by remember { mutableStateOf(jlToStringRounded(initialPrefs.slowdown, 2)) }
    var border by remember { mutableStateOf(initialPrefs.border.toString()) }
    var showGround by remember { mutableStateOf(initialPrefs.showGround) }
    var startPaused by remember { mutableStateOf(initialPrefs.startPause) }
    var mousePause by remember { mutableStateOf(initialPrefs.mousePause) }
    var stereo by remember { mutableStateOf(initialPrefs.stereo) }
    var catchSound by remember { mutableStateOf(initialPrefs.catchSound) }
    var bounceSound by remember { mutableStateOf(initialPrefs.bounceSound) }
    var manualSettings by remember { mutableStateOf(initialManualSettings) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Helper for creating the return object
    fun tryCreatePrefs() {
        val newPrefs = AnimationPrefs(initialPrefs)

        // Validate and set numeric fields
        fun parseDouble(valStr: String, name: String): Double {
            return valStr.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: throw NumberFormatException(name)
        }
        fun parseInt(valStr: String, name: String): Int {
            return valStr.toIntOrNull()?.takeIf { it >= 0 }
                ?: throw NumberFormatException(name)
        }

        try {
            newPrefs.width = parseInt(width, "width")
            newPrefs.height = parseInt(height, "height")
            newPrefs.fps = parseDouble(fps, "fps")
            newPrefs.slowdown = parseDouble(slowdown, "slowdown")
            newPrefs.border = parseInt(border, "border")
        } catch (e: Exception) {
            errorMessage = getStringResource(Res.string.error_number_format, e.message)
            return
        }

        newPrefs.showGround = showGround
        newPrefs.startPause = startPaused
        newPrefs.mousePause = mousePause
        newPrefs.stereo = stereo
        newPrefs.catchSound = catchSound
        newPrefs.bounceSound = bounceSound

        // Process manual settings
        if (manualSettings.isNotBlank()) {
            try {
                // We effectively merge the current object state with the manual string
                newPrefs.fromString("$newPrefs;$manualSettings")
            } catch (jeu: JuggleExceptionUser) {
                errorMessage = jeu.message
                return
            }
        }

        onConfirm(newPrefs)
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(IntrinsicSize.Max) // Fit width to content
            .verticalScroll(rememberScrollState())
    ) {
        // Number inputs section
        PrefsInputRow(width, { width = it }, stringResource(Res.string.gui_width))
        PrefsInputRow(height, { height = it }, stringResource(Res.string.gui_height))
        PrefsInputRow(fps, { fps = it }, stringResource(Res.string.gui_frames_per_second))
        PrefsInputRow(slowdown, { slowdown = it }, stringResource(Res.string.gui_slowdown_factor))
        PrefsInputRow(border, { border = it }, stringResource(Res.string.gui_border__pixels_))

        Spacer(modifier = Modifier.height(4.dp))

        // Dropdown section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            // Use a Box to simulate the dropdown behavior
            var expanded by remember { mutableStateOf(false) }
            val options = listOf(
                AnimationPrefs.GROUND_AUTO to stringResource(Res.string.gui_prefs_show_ground_auto),
                AnimationPrefs.GROUND_ON to stringResource(Res.string.gui_prefs_show_ground_yes),
                AnimationPrefs.GROUND_OFF to stringResource(Res.string.gui_prefs_show_ground_no)
            )
            val selectedText = options.find { it.first == showGround }?.second ?: ""

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.width(80.dp).height(32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedText, style = MaterialTheme.typography.body2, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(onClick = {
                            showGround = value
                            expanded = false
                        }) {
                            Text(text = label)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(Res.string.gui_prefs_show_ground),
                style = MaterialTheme.typography.body1
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Checkboxes section
        PrefsCheckbox(
            startPaused,
            { startPaused = it },
            stringResource(Res.string.gui_start_paused)
        )
        PrefsCheckbox(
            mousePause,
            { mousePause = it },
            stringResource(Res.string.gui_pause_on_mouse_away)
        )
        PrefsCheckbox(
            stereo,
            { stereo = it },
            stringResource(Res.string.gui_stereo_display)
        )
        PrefsCheckbox(
            catchSound,
            { catchSound = it },
            stringResource(Res.string.gui_catch_sounds)
        )
        PrefsCheckbox(
            bounceSound,
            { bounceSound = it },
            stringResource(Res.string.gui_bounce_sounds)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Manual Settings section
        Text("Manual settings", style = MaterialTheme.typography.body1)
        OutlinedTextField(
            value = manualSettings,
            onValueChange = { manualSettings = it },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Button section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
            ) {
                Text(stringResource(Res.string.gui_cancel), color = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { tryCreatePrefs() }) {
                Text(stringResource(Res.string.gui_ok))
            }
        }

        if (errorMessage != null) {
            // Error dialog in case of a problem
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(errorMessage!!) },
                confirmButton = {
                    Button(onClick = { errorMessage = null }) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

// Helper Composable for [TextField] [Label] rows

@Composable
private fun PrefsInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(80.dp).height(48.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.Black,
                cursorColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.body1)
    }
}

// Helper Composable for Checkboxes

@Composable
private fun PrefsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.body1)
    }
}
