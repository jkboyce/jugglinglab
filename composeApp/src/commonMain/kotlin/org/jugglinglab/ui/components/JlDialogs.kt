//
// JlDialogs.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.components

import org.jugglinglab.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun JlErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.error)) },
        text = { Text(errorMessage) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.gui_ok))
            }
        },
    )
}

@Composable
fun JlStoppedDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.gui_generator_stopped_title)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.gui_ok))
            }
        },
    )
}

@Composable
fun JlActionRowDefaultsRun(
    onDefaults: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onDefaults,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(stringResource(Res.string.gui_defaults))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Button(
            onClick = onRun,
            enabled = !isBusy
        ) {
            Text(stringResource(Res.string.gui_run))
        }
    }
}

@Composable
fun JlInputDialog(
    title: String,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var textState by remember { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(Res.string.gui_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(textState) }) {
                        Text(stringResource(Res.string.gui_ok))
                    }
                }
            }
        }
    }
}

@Composable
fun JlConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(Res.string.gui_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.gui_cancel))
            }
        }
    )
}

@Suppress("DEPRECATION")
@Composable
fun JlMobileAppPromotionDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val annotatedLinkString = buildAnnotatedString {
        append("Juggling Lab is also available as a mobile app: ")

        val iosStart = length
        append("Apple iOS")
        val iosEnd = length
        addStringAnnotation(
            tag = "URL",
            annotation = "https://apps.apple.com/app/juggling-lab/id6775941862",
            start = iosStart,
            end = iosEnd
        )
        addStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            ),
            start = iosStart,
            end = iosEnd
        )

        append(" or ")

        val androidStart = length
        append("Android")
        val androidEnd = length
        addStringAnnotation(
            tag = "URL",
            annotation = "https://play.google.com/store/apps/details?id=com.jonglen7.jugglinglab",
            start = androidStart,
            end = androidEnd
        )
        addStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            ),
            start = androidStart,
            end = androidEnd
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            ClickableText(
                text = annotatedLinkString,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                onClick = { offset ->
                    annotatedLinkString.getStringAnnotations(
                        tag = "URL",
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let { annotation ->
                        try {
                            uriHandler.openUri(annotation.item)
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.gui_ok))
            }
        }
    )
}

