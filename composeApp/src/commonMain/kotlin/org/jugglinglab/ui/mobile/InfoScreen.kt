//
// InfoScreen.kt
//
// Screen wrapper for InfoView in Juggling Lab.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.core.ThemeSetting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun InfoScreen(
    themeSetting: ThemeSetting,
    prefsRepo: StoredPreferencesRepository?,
    onNavigateTo: (String) -> Unit,
    onStartWalkthrough: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    InfoView(
        themeSetting = themeSetting,
        onThemeChange = { newTheme ->
            coroutineScope.launch {
                prefsRepo?.saveThemeSetting(newTheme)
            }
        },
        onNavClick = onNavigateTo,
        onStartWalkthrough = onStartWalkthrough
    )
}
