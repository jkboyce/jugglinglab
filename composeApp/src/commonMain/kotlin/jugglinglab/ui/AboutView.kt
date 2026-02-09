//
// AboutView.kt
//
// Composable for the contents of the Juggling Lab About box.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.util.jlGetAboutBoxPlatform
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutView(onCloseRequest: () -> Unit) {
    // Set height to IntrinsicSize.Min so the Row height is defined by its children.
    // This allows the Image to stretch to match the height of the Column.
    Row(modifier = Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
        Image(
            painter = painterResource(Res.drawable.about),
            contentDescription = "Juggling Lab Logo",
            // Crop ensures the image fills the height without distortion
            contentScale = ContentScale.Crop,
            // Fill the height determined by the text column
            modifier = Modifier.fillMaxHeight()
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text("Juggling Lab", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(Res.string.gui_version, Constants.VERSION),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.gui_copyright_message, Constants.YEAR),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.gui_gpl_message),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                jlGetAboutBoxPlatform(),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onCloseRequest,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.gui_ok))
            }
        }
    }
}
