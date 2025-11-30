//
// AnimationPrefsDialog.kt
//
// This is the dialog box that allows the user to set animation preferences.
// The dialog does not display when the dialog box is constructed, but when
// getPrefs() is called.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

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
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jugglinglab.generated.resources.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.getStringResource
import jugglinglab.util.jlToStringRounded
import java.awt.Dimension
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane

class AnimationPrefsDialog(private val parentFrame: JFrame?) : JDialog(
    parentFrame,
    getStringResource(Res.string.gui_animation_preferences),
    true
) {
    private var result: AnimationPrefs? = null

    init {
        // Basic dialog setup
        isResizable = false
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
    }

    fun getPrefs(oldPrefs: AnimationPrefs): AnimationPrefs {
        // Calculate the "manual settings" string (extra parameters not covered by UI)
        val manualSettingsInitial = try {
            val pl = ParameterList(oldPrefs.toString())
            val paramsRemove = listOf(
                "width", "height", "fps", "slowdown", "border",
                "showground", "stereo", "startpaused", "mousepause",
                "catchsound", "bouncesound"
            )
            for (param in paramsRemove) {
                pl.removeParameter(param)
            }
            pl.toString()
        } catch (jeu: JuggleExceptionUser) {
            handleFatalException(JuggleExceptionInternal("Anim Prefs Dialog error: " + jeu.message))
            ""
        }

        // Setup the Compose content
        val composePanel = ComposePanel()
        composePanel.setContent {
            MaterialTheme {
                AnimationPrefsContent(
                    initialPrefs = oldPrefs,
                    initialManualSettings = manualSettingsInitial,
                    onConfirm = { newPrefs ->
                        result = newPrefs
                        isVisible = false
                    },
                    onCancel = {
                        result = null
                        isVisible = false
                    }
                )
            }
        }

        contentPane = composePanel
        pack()
        // Ensure reasonable size if pack is too small, though pack usually works fine with Compose
        if (width < 300) size = Dimension(350, 600)

        setLocationRelativeTo(parentFrame)

        // This blocks until isVisible = false is called in the callbacks above
        isVisible = true

        return result ?: oldPrefs
    }
}

@Composable
fun AnimationPrefsContent(
    initialPrefs: AnimationPrefs,
    initialManualSettings: String,
    onConfirm: (AnimationPrefs) -> Unit,
    onCancel: () -> Unit
) {
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

    // Helper for creating the return object
    fun tryCreatePrefs() {
        try {
            val newPrefs = AnimationPrefs(initialPrefs)

            // Validate and set numeric fields
            // We use helper functions to throw exceptions that match the original logic
            // or we can use the original logic's approach of ignoring invalid inputs
            // but showing an error dialog is better user experience.

            fun parseDouble(valStr: String, name: String): Double {
                return valStr.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: throw NumberFormatException(name)
            }

            fun parseInt(valStr: String, name: String): Int {
                return valStr.toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw NumberFormatException(name)
            }

            try { newPrefs.width = parseInt(width, "width") } catch(_: Exception) { showError("width") ; return }
            try { newPrefs.height = parseInt(height, "height") } catch(_: Exception) { showError("height") ; return }
            try { newPrefs.fps = parseDouble(fps, "fps") } catch(_: Exception) { showError("fps") ; return }
            try { newPrefs.slowdown = parseDouble(slowdown, "slowdown") } catch(_: Exception) { showError("slowdown") ; return }
            try { newPrefs.border = parseInt(border, "border") } catch(_: Exception) { showError("border") ; return }

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
                    JOptionPane.showMessageDialog(null, jeu.message, "Error", JOptionPane.ERROR_MESSAGE)
                    return
                }
            }

            onConfirm(newPrefs)

        } catch (e: Exception) {
            // Fallback catch-all
            JOptionPane.showMessageDialog(null, e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(IntrinsicSize.Max) // Fit width to content
            .verticalScroll(rememberScrollState())
    ) {
        // --- Number Inputs Section ---
        // Layout: [TextField] [Label]

        PrefsInputRow(width, { width = it }, getStringResource(Res.string.gui_width))
        PrefsInputRow(height, { height = it }, getStringResource(Res.string.gui_height))
        PrefsInputRow(fps, { fps = it }, getStringResource(Res.string.gui_frames_per_second))
        PrefsInputRow(slowdown, { slowdown = it }, getStringResource(Res.string.gui_slowdown_factor))
        PrefsInputRow(border, { border = it }, getStringResource(Res.string.gui_border__pixels_))

        Spacer(modifier = Modifier.height(4.dp))

        // --- Dropdown Section ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            // Use a Box to simulate the dropdown behavior
            var expanded by remember { mutableStateOf(false) }
            val options = listOf(
                AnimationPrefs.GROUND_AUTO to getStringResource(Res.string.gui_prefs_show_ground_auto),
                AnimationPrefs.GROUND_ON to getStringResource(Res.string.gui_prefs_show_ground_yes),
                AnimationPrefs.GROUND_OFF to getStringResource(Res.string.gui_prefs_show_ground_no)
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
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
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
            Text(getStringResource(Res.string.gui_prefs_show_ground), style = MaterialTheme.typography.body1)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Checkboxes Section ---
        PrefsCheckbox(startPaused, { startPaused = it }, getStringResource(Res.string.gui_start_paused))
        PrefsCheckbox(mousePause, { mousePause = it }, getStringResource(Res.string.gui_pause_on_mouse_away))
        PrefsCheckbox(stereo, { stereo = it }, getStringResource(Res.string.gui_stereo_display))
        PrefsCheckbox(catchSound, { catchSound = it }, getStringResource(Res.string.gui_catch_sounds))
        PrefsCheckbox(bounceSound, { bounceSound = it }, getStringResource(Res.string.gui_bounce_sounds))

        Spacer(modifier = Modifier.height(12.dp))

        // --- Manual Settings ---
        Text("Manual settings", style = MaterialTheme.typography.body1)
        OutlinedTextField(
            value = manualSettings,
            onValueChange = { manualSettings = it },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
            ) {
                Text(getStringResource(Res.string.gui_cancel), color = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { tryCreatePrefs() }) {
                Text(getStringResource(Res.string.gui_ok))
            }
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
            modifier = Modifier.width(80.dp).height(48.dp), // Fixed width to match screenshot look
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
            modifier = Modifier.size(20.dp) // Slightly smaller compact look
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.body1)
    }
}

// Helper for error dialogs (bridging to Swing for the modal error popup)
private fun showError(fieldName: String) {
    val template = getStringResource(Res.string.error_number_format, fieldName)
    JOptionPane.showMessageDialog(null, template, "Error", JOptionPane.ERROR_MESSAGE)
}