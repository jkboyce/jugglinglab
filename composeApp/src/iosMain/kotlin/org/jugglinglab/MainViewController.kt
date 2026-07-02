//
// MainViewController.kt
//
// Juggling Lab is an open-source application for creating and animating
// juggling patterns. https://jugglinglab.org
//
// This is the Kotlin entry point into Juggling Lab as an iOS application.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("unused", "FunctionName")

package org.jugglinglab

import org.jugglinglab.core.StoredPreferencesRepository
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.ui.mobile.App
import org.jugglinglab.util.CrashReporter
import org.jugglinglab.util.crashReporter
import org.jugglinglab.util.JuggleExceptionInternal
import co.touchlab.crashkios.crashlytics.CrashlyticsKotlin
import co.touchlab.crashkios.crashlytics.enableCrashlytics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException

private var startUrl by mutableStateOf<String?>(null)
private var startJmlContent by mutableStateOf<String?>(null)
private var startError by mutableStateOf<String?>(null)

fun handleUniversalLink(url: String) {
    startUrl = url
}

fun handleJmlContent(content: String) {
    startJmlContent = content
}

fun handleImportError(message: String) {
    startError = message
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController {
    // 1. Initialize CrashKiOS for unhandled crash reporting
    enableCrashlytics()

    // 2. Firebase/CrashKiOS Handled Exception Crash Reporter
    crashReporter = object : CrashReporter {
        override fun recordThrowable(throwable: Throwable, pattern: JmlPattern?) {
            if (throwable is CancellationException) return
            if (pattern != null) {
                CrashlyticsKotlin.setCustomValue("jml_pattern", pattern.toString())
            }
            if (throwable is JuggleExceptionInternal) {
                val wrapped = throwable.wrapped
                if (wrapped != null) {
                    CrashlyticsKotlin.setCustomValue("wrapped_exception", wrapped.toString())
                    CrashlyticsKotlin.sendHandledException(wrapped)
                } else {
                    CrashlyticsKotlin.sendHandledException(throwable)
                }
            } else {
                CrashlyticsKotlin.sendHandledException(throwable)
            }
            if (pattern != null) {
                CrashlyticsKotlin.setCustomValue("jml_pattern", "")
            }
        }
    }

    // 3. Resolve Document Directory for Okio Local Files and Datastore
    val fileManager = NSFileManager.defaultManager
    val documentDirectory = fileManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val documentPath = documentDirectory?.path ?: ""
    val localFilesDir = documentPath.toPath()

    // 4. Initialize DataStore
    val dataStore = createDataStore(documentPath)
    val themeRepo = StoredPreferencesRepository(dataStore)

    // 5. Invoke shared App Composable
    App(
        prefsRepo = themeRepo,
        localFilesDir = localFilesDir,
        startUrl = startUrl,
        startJmlContent = startJmlContent,
        startError = startError,
        onUrlHandled = { startUrl = null },
        onJmlContentHandled = { startJmlContent = null },
        onStartErrorHandled = { startError = null }
    )
}

private var dataStoreInstance: DataStore<Preferences>? = null

fun createDataStore(documentPath: String): DataStore<Preferences> {
    var instance = dataStoreInstance
    if (instance == null) {
        instance = PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                "$documentPath/theme.preferences_pb".toPath()
            }
        )
        dataStoreInstance = instance
    }
    return instance
}
