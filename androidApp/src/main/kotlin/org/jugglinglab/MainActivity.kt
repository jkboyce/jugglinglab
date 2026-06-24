//
// MainActivity.kt
//
// Juggling Lab is an open-source application for creating and animating
// juggling patterns. https://jugglinglab.org
//
// This is the entry point into Juggling Lab as an Android application.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab

import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.ui.mobile.App
import org.jugglinglab.ui.components.JlErrorDialog
import org.jugglinglab.util.AndroidContext
import org.jugglinglab.util.CrashReporter
import org.jugglinglab.util.crashReporter
import org.jugglinglab.util.JuggleExceptionInternal
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

class MainActivity : ComponentActivity() {
    // Class-level so it can be updated by onNewIntent() when the app is already
    // running and a share link is tapped (requires launchMode="singleTask").
    private var startUrl by mutableStateOf<String?>(null)
    private var startJmlContent by mutableStateOf<String?>(null)

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        startUrl = intent.data?.toString()
            ?.takeIf { it.contains("jugglinglab.org/anim") }

        if (intent.action == android.content.Intent.ACTION_VIEW && startUrl == null) {
            intent.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        startJmlContent = stream.reader().readText()
                    }
                } catch (e: Throwable) {
                    println("Error reading JML file from intent: ${e.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Set up Firebase crash logging/reporting
        crashReporter = object : CrashReporter {
            override fun recordThrowable(throwable: Throwable, message: String?) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                message?.let { crashlytics.log(it) }  // Adds custom breadcrumb context
                if (throwable is JuggleExceptionInternal) {
                    val pat = throwable.pattern
                    if (pat != null) {
                        crashlytics.setCustomKey("jml_pattern", pat.toString())
                    }
                    val wrapped = throwable.wrapped
                    if (wrapped != null) {
                        crashlytics.setCustomKey("wrapped_exception", wrapped.toString())
                        crashlytics.recordException(wrapped)
                    } else {
                        crashlytics.recordException(throwable)
                    }
                } else {
                    crashlytics.recordException(throwable)
                }
            }
        }

        // Provide the application context to platform functions that need it
        // (e.g. jlShareUrl).
        AndroidContext.set(this)

        val localFilesDir = applicationContext.filesDir.absolutePath.toPath()
        val dataStore = getDataStore(applicationContext)
        val themeRepo = StoredPreferencesRepository(dataStore)

        // Extract share URL or file content from the launch intent
        handleIntent(intent)

        var isInitializing by mutableStateOf(true)
        var migrationError by mutableStateOf<String?>(null)
        var migrationSuccessCount by mutableStateOf<Int?>(null)

        lifecycleScope.launch(Dispatchers.IO) {
            val favFile = localFilesDir / "Favorites.jml"
            if (!FileSystem.SYSTEM.exists(favFile)) {
                when (val result = tryMigrateFavorites()) {
                    is MigrationResult.Success -> {
                        try {
                            FileSystem.SYSTEM.write(favFile) {
                                writeUtf8(result.content)
                            }
                            migrationSuccessCount = result.numPatterns
                        } catch (e: Exception) {
                            migrationError = "Error writing file: ${e.message}"
                        }
                    }

                    is MigrationResult.NotFound -> {
                        // no preferences from older app, do nothing
                        // App.kt will create empty Favorites.jml when it starts
                    }

                    is MigrationResult.Error -> {
                        migrationError = result.errorCode
                    }
                }
            }
            isInitializing = false
        }

        setContent {
            val themeSetting by themeRepo.themeSettingFlow.collectAsState(
                initial = org.jugglinglab.core.ThemeSetting.SYSTEM
            )
            val useDarkTheme = when (themeSetting) {
                org.jugglinglab.core.ThemeSetting.LIGHT -> false
                org.jugglinglab.core.ThemeSetting.DARK -> true
                org.jugglinglab.core.ThemeSetting.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            androidx.compose.runtime.DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        androidx.activity.SystemBarStyle.dark(
                            android.graphics.Color.argb(
                                0x80,
                                0x1b,
                                0x1b,
                                0x1b
                            )
                        )
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.argb(
                                0xe6,
                                0xFF,
                                0xFF,
                                0xFF
                            ), android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                        )
                    }
                )
                onDispose {}
            }

            if (migrationError != null) {
                JlErrorDialog(
                    errorMessage = migrationError ?: "Unknown Error",
                    onDismiss = { kotlin.system.exitProcess(0) }
                )
            } else if (isInitializing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (migrationSuccessCount != null) {
                    AlertDialog(
                        onDismissRequest = { migrationSuccessCount = null },
                        title = { Text("Welcome") },
                        text = {
                            Text(
                                """
                                Welcome to the new Juggling Lab Android app!

                                Your patterns in the previous app ($migrationSuccessCount total) have been moved into a new Favorites list.
                                """.trimIndent()
                            )
                        },
                        confirmButton = {
                            Button(onClick = { migrationSuccessCount = null }) {
                                Text("OK")
                            }
                        }
                    )
                }
                App(
                    prefsRepo = themeRepo,
                    localFilesDir = localFilesDir,
                    startUrl = startUrl,
                    startJmlContent = startJmlContent,
                    onUrlHandled = { startUrl = null },
                    onJmlContentHandled = { startJmlContent = null },
                    isMigrationDialogShown = migrationSuccessCount != null
                )
            }
        }
    }

    // Called by Android instead of creating a new Activity when launchMode is
    // "singleTask" and the app is already running. Update startUrl so the
    // running App composable reacts via LaunchedEffect(startUrl).

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    companion object {
        private var dataStoreInstance: DataStore<Preferences>? = null

        /**
         * Return a singleton instance of the DataStore. DataStore instances must be
         * singletons to avoid "multiple DataStores active for the same file" errors.
         */
        fun getDataStore(context: Context): DataStore<Preferences> {
            return dataStoreInstance ?: synchronized(this) {
                dataStoreInstance ?: PreferenceDataStoreFactory.createWithPath(
                    produceFile = {
                        File(
                            context.filesDir,
                            "theme.preferences_pb"
                        ).absolutePath.toPath()
                    }
                ).also { dataStoreInstance = it }
            }
        }
    }
}
