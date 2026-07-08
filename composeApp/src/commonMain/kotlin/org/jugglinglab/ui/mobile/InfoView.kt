//
// InfoView.kt
//
// Information view for the mobile application, intended as the initial screen
// when launching the app.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import org.jugglinglab.core.ThemeSetting
import org.jugglinglab.util.jlCurrentVersion
import org.jugglinglab.util.jlIsAndroid
import org.jugglinglab.util.jlIsLandscape
import org.jugglinglab.util.jlIsWeb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoView(
    themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    onThemeChange: (ThemeSetting) -> Unit = {},
    onNavClick: (String) -> Unit = {},
    onStartWalkthrough: (() -> Unit)? = null,
    onOpenJmlClick: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 30.dp)
    ) {
        val isLandscape = jlIsLandscape()

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    InfoHeader(uriHandler, onStartWalkthrough)
                    Spacer(modifier = Modifier.weight(1f))
                    ThemeSelector(themeSetting, onThemeChange)
                }
                Spacer(modifier = Modifier.width(16.dp))
                NavGrid(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = 16.dp),
                    onNavClick = onNavClick,
                    onOpenJmlClick = onOpenJmlClick
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                InfoHeader(uriHandler, onStartWalkthrough)
                Spacer(modifier = Modifier.height(16.dp))
                NavGrid(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onNavClick = onNavClick,
                    onOpenJmlClick = onOpenJmlClick
                )
                Spacer(modifier = Modifier.height(16.dp))
                ThemeSelector(themeSetting, onThemeChange)
            }
        }
    }
}

@Composable
private fun InfoHeader(
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onStartWalkthrough: (() -> Unit)? = null
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = stringResource(Res.string.gui_mobile_juggling_lab_logo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(72.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(Res.string.gui_mobile_juggling_lab),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = stringResource(Res.string.gui_version, jlCurrentVersion),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (jlIsAndroid) {
                stringResource(Res.string.gui_copyright_message, Constants.YEAR) + ". " +
                        stringResource(Res.string.gui_gpl_message) +
                        ". " + stringResource(Res.string.gui_mobile_android_leads)
            } else {
                stringResource(Res.string.gui_copyright_message, Constants.YEAR) + ". " +
                        stringResource(Res.string.gui_gpl_message) + "."
            },
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "jugglinglab.org",
                modifier = Modifier.clickable {
                    try {
                        uriHandler.openUri("https://jugglinglab.org")
                    } catch (_: Throwable) {
                        // no reasonable response for connection errors etc.
                    }
                },
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                style = MaterialTheme.typography.bodyLarge
            )
            if (onStartWalkthrough != null) {
                Spacer(modifier = Modifier.width(32.dp))
                Text(
                    text = "Take a Tour",
                    modifier = Modifier.clickable { onStartWalkthrough.invoke() },
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun NavGrid(
    modifier: Modifier = Modifier,
    onNavClick: (String) -> Unit,
    onOpenJmlClick: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            NavButton(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.gui_mobile_info_animation),
                icon = Icons.Default.Animation
            ) { onNavClick("Animation") }
            NavButton(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.gui_mobile_info_pattern_entry),
                icon = Icons.Default.Edit
            ) { onNavClick("Notation") }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            NavButton(
                modifier = Modifier.weight(1f).walkthroughTarget("info_pattern_list"),
                title = stringResource(Res.string.gui_mobile_info_pattern_list),
                icon = Icons.AutoMirrored.Filled.FormatListBulleted
            ) { onNavClick("PatternList") }
            NavButton(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.gui_mobile_info_generator),
                icon = Icons.Default.Build
            ) { onNavClick("Generator") }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (jlIsWeb) {
                NavButton(
                    modifier = Modifier.weight(1f),
                    title = "Open JML File",
                    icon = Icons.Default.FileOpen
                ) { onOpenJmlClick() }
            } else {
                NavButton(
                    modifier = Modifier.weight(1f).walkthroughTarget("info_favorites"),
                    title = stringResource(Res.string.gui_mobile_info_favorites),
                    icon = Icons.Default.Star
                ) { onNavClick("Favorites") }
            }
            NavButton(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.gui_mobile_info_library),
                icon = Icons.Default.LocalLibrary
            ) { onNavClick("FileChooser") }
        }
    }
}

@Composable
private fun NavButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.padding(bottom = 8.dp).width(36.dp).height(36.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    themeSetting: ThemeSetting,
    onThemeChange: (ThemeSetting) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        SingleChoiceSegmentedButtonRow {
            val options = listOf(ThemeSetting.SYSTEM, ThemeSetting.LIGHT, ThemeSetting.DARK)
            val labels = listOf(
                stringResource(Res.string.gui_mobile_theme_system),
                stringResource(Res.string.gui_mobile_theme_light),
                stringResource(Res.string.gui_mobile_theme_dark)
            )

            options.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    ),
                    onClick = { onThemeChange(option) },
                    selected = themeSetting == option
                ) {
                    Text(labels[index])
                }
            }
        }
    }
}
