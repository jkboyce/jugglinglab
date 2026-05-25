//
// AnimationViewDialogs.kt
//
// Compose dialogs for the animation view.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.ui.common.*
import org.jugglinglab.ui.components.JlInputDialog
import org.jugglinglab.util.jlParseFiniteDouble
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun AnimationViewDialogs(
    activeDialog: AnimationViewDialog?,
    onDismissRequest: () -> Unit,
    onConfirmPrefs: (AnimationPrefs) -> Unit,
    onConfirmTiming: (Double) -> Unit,
    onConfirmTitle: (String) -> Unit
) {
    when (activeDialog) {
        is AnimationViewDialog.ChangeAnimationPrefs -> {
            Dialog(onDismissRequest = onDismissRequest) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    AnimationPrefsControl(
                        initialPrefs = activeDialog.currentPrefs,
                        onConfirm = onConfirmPrefs
                    )
                }
            }
        }
        is AnimationViewDialog.ChangeTiming -> {
            ChangeTimingDialog(
                onConfirm = onConfirmTiming,
                onDismissRequest = onDismissRequest
            )
        }
        is AnimationViewDialog.ChangeTitle -> {
            JlInputDialog(
                title = "Change title",
                initialText = activeDialog.currentTitle,
                onConfirm = onConfirmTitle,
                onDismissRequest = onDismissRequest
            )
        }
        null -> {}
    }
}

@Composable
fun ChangeTimingDialog(
    onConfirm: (Double) -> Unit,
    onDismissRequest: () -> Unit
) {
    var textState by remember { mutableStateOf("100") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.gui_change_timing),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(text = stringResource(Res.string.gui_rescale_percentage))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = textState,
                        onValueChange = {
                            textState = it
                            isError = false
                        },
                        isError = isError,
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(Res.string.gui_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        try {
                            val scale = jlParseFiniteDouble(textState) / 100.0
                            if (scale > 0.0) {
                                onConfirm(scale)
                            } else {
                                isError = true
                            }
                        } catch (_: Exception) {
                            isError = true
                        }
                    }) {
                        Text(stringResource(Res.string.gui_ok))
                    }
                }
            }
        }
    }
}

sealed class AnimationViewDialog {
    data class ChangeAnimationPrefs(val currentPrefs: AnimationPrefs) : AnimationViewDialog()
    data object ChangeTiming : AnimationViewDialog()
    data class ChangeTitle(val currentTitle: String) : AnimationViewDialog()
}
