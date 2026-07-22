//
// Main.kt
//
// Entry point for the Juggling Lab WebAssembly application.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("unused", "UnusedVariable")

package org.jugglinglab

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.ui.mobile.App
import org.jugglinglab.util.crashReporter
import org.jugglinglab.util.CrashReporter
import org.jugglinglab.util.prewarmWasmStringResources
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.browser.localStorage
import kotlinx.browser.document
import kotlinx.datetime.Clock
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // keep Clock reference to prevent compiler pruning/DCE of kotlinx-datetime library
    val dummyClock = Clock.System

    crashReporter = object : CrashReporter {
        override fun recordThrowable(throwable: Throwable, pattern: JmlPattern?) {
            println("Exception caught: ${throwable.message}")
            throwable.printStackTrace()
        }
    }

    val fakeFileSystem = org.jugglinglab.util.jlFileSystem as FakeFileSystem
    val localFilesDir = "/data".toPath()
    if (!fakeFileSystem.exists(localFilesDir)) {
        fakeFileSystem.createDirectory(localFilesDir)
    }

    val dataStore = LocalStorageDataStore()
    val themeRepo = StoredPreferencesRepository(dataStore)

    val host = document.getElementById("compose-holder") as HTMLElement
    ComposeViewport(viewportContainer = host) {
        var isReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            try {
                // pre-warm the cache so that jlGetStringResource runs synchronously
                prewarmWasmStringResources()
            } catch (e: Exception) {
                println("Failed to pre-warm resource cache: ${e.message}")
            }
            isReady = true
            hideLoadingScreen()
        }
        if (isReady) {
            val href = kotlinx.browser.window.location.href
            val startUrl =
                if (href.contains("?") && href.substringAfter("?").isNotEmpty()) href else null

            // add Hebrew fonts; often missing in browser sandbox
            val webFontFamily = androidx.compose.ui.text.font.FontFamily(
                org.jetbrains.compose.resources.Font(
                    Res.font.noto_sans_hebrew_regular,
                    androidx.compose.ui.text.font.FontWeight.Normal
                ),
                org.jetbrains.compose.resources.Font(
                    Res.font.noto_sans_hebrew_bold,
                    androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
            val defaultTypography = androidx.compose.material3.MaterialTheme.typography
            val webTypography = remember(webFontFamily) {
                androidx.compose.material3.Typography(
                    displayLarge = defaultTypography.displayLarge.copy(fontFamily = webFontFamily),
                    displayMedium = defaultTypography.displayMedium.copy(fontFamily = webFontFamily),
                    displaySmall = defaultTypography.displaySmall.copy(fontFamily = webFontFamily),
                    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = webFontFamily),
                    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = webFontFamily),
                    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = webFontFamily),
                    titleLarge = defaultTypography.titleLarge.copy(fontFamily = webFontFamily),
                    titleMedium = defaultTypography.titleMedium.copy(fontFamily = webFontFamily),
                    titleSmall = defaultTypography.titleSmall.copy(fontFamily = webFontFamily),
                    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = webFontFamily),
                    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = webFontFamily),
                    bodySmall = defaultTypography.bodySmall.copy(fontFamily = webFontFamily),
                    labelLarge = defaultTypography.labelLarge.copy(fontFamily = webFontFamily),
                    labelMedium = defaultTypography.labelMedium.copy(fontFamily = webFontFamily),
                    labelSmall = defaultTypography.labelSmall.copy(fontFamily = webFontFamily)
                )
            }

            // switch page to RTL layout if necessary
            val lang = kotlinx.browser.window.navigator.language.lowercase()
            val isRtl = lang.startsWith("he") || lang.startsWith("ar")
            val webLayoutDirection = if (isRtl) {
                document.documentElement?.setAttribute("dir", "rtl")
                androidx.compose.ui.unit.LayoutDirection.Rtl
            } else {
                document.documentElement?.setAttribute("dir", "ltr")
                androidx.compose.ui.unit.LayoutDirection.Ltr
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides webLayoutDirection
            ) {
                App(
                    prefsRepo = themeRepo,
                    localFilesDir = localFilesDir,
                    startUrl = startUrl,
                    typography = webTypography
                )
            }
        }
    }
}

class LocalStorageDataStore : DataStore<Preferences> {
    private val stateFlow: MutableStateFlow<Preferences>

    init {
        val themeVal = localStorage.getItem("theme_setting")?.toIntOrNull()
        val onboardingVal = localStorage.getItem("onboarding_completed")?.toBoolean()

        val initialPrefs = emptyPreferences().toMutablePreferences().apply {
            if (themeVal != null) {
                val themeKey =
                    androidx.datastore.preferences.core.intPreferencesKey("theme_setting")
                this[themeKey] = themeVal
            }
            if (onboardingVal != null) {
                val onboardingKey =
                    androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")
                this[onboardingKey] = onboardingVal
            }
        }
        stateFlow = MutableStateFlow(initialPrefs)
    }

    override val data: Flow<Preferences> = stateFlow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val current = stateFlow.value
        val newPrefs = transform(current)
        stateFlow.value = newPrefs

        val themeKey = androidx.datastore.preferences.core.intPreferencesKey("theme_setting")
        val onboardingKey =
            androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

        val themeVal = newPrefs[themeKey]
        if (themeVal != null) {
            localStorage.setItem("theme_setting", themeVal.toString())
        } else {
            localStorage.removeItem("theme_setting")
        }

        val onboardingVal = newPrefs[onboardingKey]
        if (onboardingVal != null) {
            localStorage.setItem("onboarding_completed", onboardingVal.toString())
        } else {
            localStorage.removeItem("onboarding_completed")
        }

        return newPrefs
    }
}

@JsFun(
    """() => {
        const loader = document.getElementById('loading-screen');
        if (loader) {
            loader.style.opacity = '0';
            setTimeout(() => { loader.style.visibility = 'hidden'; }, 500);
        }
    }"""
)
private external fun hideLoadingScreen()
