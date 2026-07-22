//
// AnimationPrefsControl.kt
//
// Composable UI for the animation preferences dialog.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.ui.components.*
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.ParameterList
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlToStringRounded
import org.jugglinglab.util.jlIsDesktop
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun AnimationPrefsControl(
    initialPrefs: AnimationPrefs,
    onConfirm: (AnimationPrefs) -> Unit
) {
    val paramsWithUi = if (jlIsDesktop) {
        listOf(
            "width", "height", "fps", "slowdown", "border",
            "showground", "avatar", "startpaused", "mousepause", "stereo",
            "catchsound", "bouncesound"
        )
    } else {
        listOf(
            "slowdown", "showground", "avatar", "startpaused", "stereo",
            "catchsound", "bouncesound"
        )
    }

    // Extra parameters not covered by UI fields
    val initialManualSettings = try {
        val pl = ParameterList(initialPrefs.toString())
        for (param in paramsWithUi) {
            pl.removeParameter(param)
        }
        pl.toString()
    } catch (_: Exception) {
        ""
    }

    // State holders
    var width by remember { mutableStateOf(initialPrefs.width.toString()) }
    var height by remember { mutableStateOf(initialPrefs.height.toString()) }
    var fps by remember { mutableStateOf(jlToStringRounded(initialPrefs.fps, 2)) }
    var slowdown by remember { mutableStateOf(jlToStringRounded(initialPrefs.slowdown, 2)) }
    var border by remember { mutableStateOf(initialPrefs.borderPixels.toString()) }
    var showGround by remember { mutableIntStateOf(initialPrefs.showGround) }
    var avatar by remember { mutableStateOf(initialPrefs.avatar) }
    var startPaused by remember { mutableStateOf(initialPrefs.startPaused) }
    var mousePause by remember { mutableStateOf(initialPrefs.mousePause) }
    var stereo by remember { mutableStateOf(initialPrefs.stereo) }
    var catchSound by remember { mutableStateOf(initialPrefs.catchSound) }
    var bounceSound by remember { mutableStateOf(initialPrefs.bounceSound) }
    var manualSettings by remember { mutableStateOf(initialManualSettings) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun fillStateFromPrefs(prefs: AnimationPrefs, manualStr: String = "") {
        width = prefs.width.toString()
        height = prefs.height.toString()
        fps = jlToStringRounded(prefs.fps, 2)
        slowdown = jlToStringRounded(prefs.slowdown, 2)
        border = prefs.borderPixels.toString()
        showGround = prefs.showGround
        avatar = prefs.avatar
        startPaused = prefs.startPaused
        mousePause = prefs.mousePause
        stereo = prefs.stereo
        catchSound = prefs.catchSound
        bounceSound = prefs.bounceSound
        manualSettings = manualStr
    }

    // Helper for creating the return object
    fun tryCreatePrefs() {
        // Validate and set numeric fields
        fun parseDouble(valStr: String, name: String): Double {
            return valStr.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: throw NumberFormatException(name)
        }

        fun parseInt(valStr: String, name: String): Int {
            return valStr.toIntOrNull()?.takeIf { it >= 0 }
                ?: throw NumberFormatException(name)
        }

        var newPrefs = try {
            AnimationPrefs(
                width = parseInt(width, "width"),
                height = parseInt(height, "height"),
                fps = parseDouble(fps, "fps"),
                slowdown = parseDouble(slowdown, "slowdown"),
                borderPixels = parseInt(border, "border"),
                showGround = showGround,
                startPaused = startPaused,
                mousePause = mousePause,
                stereo = stereo,
                catchSound = catchSound,
                bounceSound = bounceSound,
                avatar = avatar
            )
        } catch (e: Exception) {
            errorMessage = jlGetStringResource(Res.string.error_number_format, e.message)
            return
        }

        if (manualSettings.isNotBlank()) {
            try {
                // merge the current object state with the manual string
                newPrefs = AnimationPrefs.fromString("$newPrefs;$manualSettings")
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
            .onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    tryCreatePrefs()
                    true
                } else {
                    false
                }
            }
    ) {
        // Number inputs section
        if ("width" in paramsWithUi) {
            JlPrefsInputRow(width, { width = it }, stringResource(Res.string.gui_width))
        }
        if ("height" in paramsWithUi) {
            JlPrefsInputRow(height, { height = it }, stringResource(Res.string.gui_height))
        }
        if ("fps" in paramsWithUi) {
            JlPrefsInputRow(fps, { fps = it }, stringResource(Res.string.gui_frames_per_second))
        }
        if ("slowdown" in paramsWithUi) {
            JlPrefsInputRow(slowdown, { slowdown = it }, stringResource(Res.string.gui_slowdown_factor))
        }
        if ("border" in paramsWithUi) {
            JlPrefsInputRow(border, { border = it }, stringResource(Res.string.gui_border__pixels_))
        }

        if ("showground" in paramsWithUi) {
            Spacer(modifier = Modifier.height(4.dp))

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
                        modifier = Modifier.width(80.dp).height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
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
                            DropdownMenuItem(
                                text = { Text(text = label) },
                                onClick = {
                                    showGround = value
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.gui_prefs_show_ground),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Juggler avatar dropdown (Male / Female). Options mirror the built-in
        // avatar registry; add a new row here when registering a new avatar.
        if ("avatar" in paramsWithUi) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                val options = listOf(
                    "male" to stringResource(Res.string.gui_avatar_male),
                    "female" to stringResource(Res.string.gui_avatar_female)
                )
                val selectedText = options.find { it.first == avatar }?.second ?: ""

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.width(100.dp).height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
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
                            DropdownMenuItem(
                                text = { Text(text = label) },
                                onClick = {
                                    avatar = value
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.gui_juggler_avatar),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Checkboxes section
        if ("startpaused" in paramsWithUi) {
            JlCheckbox(
                checked = startPaused,
                onCheckedChange = { startPaused = it },
                label = stringResource(Res.string.gui_start_paused)
            )
        }
        if ("mousepause" in paramsWithUi) {
            JlCheckbox(
                checked = mousePause,
                onCheckedChange = { mousePause = it },
                label = stringResource(Res.string.gui_pause_on_mouse_away)
            )
        }
        if ("stereo" in paramsWithUi) {
            JlCheckbox(
                checked = stereo,
                onCheckedChange = { stereo = it },
                label = stringResource(Res.string.gui_stereo_display)
            )
        }
        if ("catchsound" in paramsWithUi) {
            JlCheckbox(
                checked = catchSound,
                onCheckedChange = { catchSound = it },
                label = stringResource(Res.string.gui_catch_sounds)
            )
        }
        if ("bouncesound" in paramsWithUi) {
            JlCheckbox(
                checked = bounceSound,
                onCheckedChange = { bounceSound = it },
                label = stringResource(Res.string.gui_bounce_sounds)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Manual Settings section
        Text(
            stringResource(Res.string.gui_manual_settings),
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedTextField(
            value = manualSettings,
            onValueChange = { manualSettings = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Button section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { fillStateFromPrefs(AnimationPrefs(), "") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(Res.string.gui_defaults))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { tryCreatePrefs() }) {
                Text(stringResource(Res.string.gui_ok))
            }
        }

        if (errorMessage != null) {
            JlErrorDialog(errorMessage = errorMessage!!, onDismiss = { errorMessage = null })
        }
    }
}
