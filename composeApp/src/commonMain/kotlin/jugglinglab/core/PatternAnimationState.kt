//
// PatternAnimationState.kt
//
// This class contains all the state variables needed to draw a frame of
// the pattern animation, including the ladder diagram.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jugglinglab.jml.JMLPattern

class PatternAnimationState(
    initialPattern: JMLPattern,
    initialPrefs: AnimationPrefs
) {
    var pattern: JMLPattern by mutableStateOf(initialPattern)
    var prefs: AnimationPrefs by mutableStateOf(initialPrefs)

    // Animation State
    var time: Double by mutableStateOf(0.0)
    var isPaused: Boolean by mutableStateOf(false)
    //var clock: Long by mutableStateOf(0L)

    // View State
    var cameraAngle: List<Double> by mutableStateOf(listOf(0.0, 90.0))
    var zoom: Double by mutableStateOf(1.0)

    // Selection State
    var selectedItemHash: Int by mutableStateOf(0)

    // Helper to update the state
    fun updateState(
        pattern: JMLPattern?,
        prefs: AnimationPrefs?,
        time: Double?,
        isPaused: Boolean?,
        cameraAngle: List<Double>?,
        zoom: Double?,
        selectedItemHash: Int?
    ) {
        if (pattern != null) this.pattern = pattern
        if (prefs != null) this.prefs = prefs
        if (time != null) this.time = time
        if (isPaused != null) this.isPaused = isPaused
        if (cameraAngle != null) this.cameraAngle = cameraAngle
        if (zoom != null) this.zoom = zoom
        if (selectedItemHash != null) this.selectedItemHash = selectedItemHash
        
        if (pattern != null) {
            for (callback in onPatternChange) callback()
        }
        if (prefs != null) {
            for (callback in onPrefsChange) callback()
        }
        if (time != null) {
            for (callback in onTimeChange) callback()
        }
        if (isPaused != null) {
            for (callback in onIsPausedChange) callback()
        }
        if (cameraAngle != null) {
            for (callback in onCameraAngleChange) callback()
        }
        if (zoom != null) {
            for (callback in onZoomChange) callback()
        }
        if (selectedItemHash != null) {
            for (callback in onSelectedItemHashChange) callback()
        }
    }

    //--------------------------------------------------------------------------
    // Callbacks when there are changes – target removal
    //--------------------------------------------------------------------------

    val onPatternChange = mutableListOf<() -> Unit>()
    val onPrefsChange = mutableListOf<() -> Unit>()
    val onTimeChange = mutableListOf<() -> Unit>()
    val onIsPausedChange  = mutableListOf<() -> Unit>()
    val onCameraAngleChange = mutableListOf<() -> Unit>()
    val onZoomChange = mutableListOf<() -> Unit>()
    val onSelectedItemHashChange = mutableListOf<() -> Unit>()

    fun addListener(
        onPatternChange: (() -> Unit)?,
        onPrefsChange: (() -> Unit)?,
        onTimeChange: (() -> Unit)?,
        onIsPausedChanged: (() -> Unit)?,
        onCameraAngleChange: (() -> Unit)?,
        onZoomChange: (() -> Unit)?,
        onSelectedItemHashChange: (() -> Unit)?
    ) {
        if (onPatternChange != null) this.onPatternChange.add(onPatternChange)
        if (onPrefsChange != null) this.onPrefsChange.add(onPrefsChange)
        if (onTimeChange != null) this.onTimeChange.add(onTimeChange)
        if (onIsPausedChanged != null) this.onIsPausedChange.add(onIsPausedChanged)
        if (onCameraAngleChange != null) this.onCameraAngleChange.add(onCameraAngleChange)
        if (onZoomChange != null) this.onZoomChange.add(onZoomChange)
        if (onSelectedItemHashChange != null) this.onSelectedItemHashChange.add(onSelectedItemHashChange)
    }

    fun clearListeners() {
        onPatternChange.clear()
        onPrefsChange.clear()
        onTimeChange.clear()
        onIsPausedChange.clear()
        onCameraAngleChange.clear()
        onZoomChange.clear()
        onSelectedItemHashChange.clear()
    }
}
