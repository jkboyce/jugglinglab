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
import kotlin.math.max
import kotlin.math.min

class PatternAnimationState(
    initialPattern: JMLPattern,
    initialPrefs: AnimationPrefs
) {
    //--------------------------------------------------------------------------
    // State variables
    //--------------------------------------------------------------------------

    var pattern: JMLPattern by mutableStateOf(initialPattern)
    var prefs: AnimationPrefs by mutableStateOf(initialPrefs)
    var time: Double by mutableStateOf(initialPattern.loopStartTime)
    var isPaused: Boolean by mutableStateOf(initialPrefs.startPaused)
    //var clock: Long by mutableStateOf(0L)
    var cameraAngle: List<Double> by mutableStateOf(defaultCameraAngle())  // radians
    var zoom: Double by mutableStateOf(1.0)
    var selectedItemHashCode: Int by mutableStateOf(0)

    //--------------------------------------------------------------------------
    // Helper to update the state and notify listeners
    //--------------------------------------------------------------------------

    fun update(
        pattern: JMLPattern? = null,
        prefs: AnimationPrefs? = null,
        time: Double? = null,
        isPaused: Boolean? = null,
        cameraAngle: List<Double>? = null,
        zoom: Double? = null,
        selectedItemHashCode: Int? = null
    ) {
        if (pattern != null) this.pattern = pattern
        if (prefs != null) this.prefs = prefs
        if (time != null) this.time = time
        if (isPaused != null) this.isPaused = isPaused
        if (cameraAngle != null) this.cameraAngle = cameraAngle
        if (zoom != null) this.zoom = zoom
        if (selectedItemHashCode != null) this.selectedItemHashCode = selectedItemHashCode
        
        if (pattern != null) {
            onPatternChange.forEach { it() }
        }
        if (prefs != null) {
            onPrefsChange.forEach { it() }
        }
        if (time != null) {
            onTimeChange.forEach { it() }
        }
        if (isPaused != null) {
            onIsPausedChange.forEach { it() }
        }
        if (cameraAngle != null) {
            onCameraAngleChange.forEach { it() }
        }
        if (zoom != null) {
            onZoomChange.forEach { it() }
        }
        if (selectedItemHashCode != null) {
            onSelectedItemHashChange.forEach { it() }
        }
    }

    // callbacks
    val onPatternChange = mutableListOf<() -> Unit>()
    val onPrefsChange = mutableListOf<() -> Unit>()
    val onTimeChange = mutableListOf<() -> Unit>()
    val onIsPausedChange  = mutableListOf<() -> Unit>()
    val onCameraAngleChange = mutableListOf<() -> Unit>()
    val onZoomChange = mutableListOf<() -> Unit>()
    val onSelectedItemHashChange = mutableListOf<() -> Unit>()
    val onNewPatternUndo = mutableListOf<() -> Unit>()

    fun addListener(
        onPatternChange: (() -> Unit)? = null,
        onPrefsChange: (() -> Unit)? = null,
        onTimeChange: (() -> Unit)? = null,
        onIsPausedChanged: (() -> Unit)? = null,
        onCameraAngleChange: (() -> Unit)? = null,
        onZoomChange: (() -> Unit)? = null,
        onSelectedItemHashChange: (() -> Unit)? = null,
        onNewPatternUndo: (() -> Unit)? = null
    ) {
        if (onPatternChange != null) this.onPatternChange.add(onPatternChange)
        if (onPrefsChange != null) this.onPrefsChange.add(onPrefsChange)
        if (onTimeChange != null) this.onTimeChange.add(onTimeChange)
        if (onIsPausedChanged != null) this.onIsPausedChange.add(onIsPausedChanged)
        if (onCameraAngleChange != null) this.onCameraAngleChange.add(onCameraAngleChange)
        if (onZoomChange != null) this.onZoomChange.add(onZoomChange)
        if (onSelectedItemHashChange != null) this.onSelectedItemHashChange.add(onSelectedItemHashChange)
        if (onNewPatternUndo != null) this.onNewPatternUndo.add(onNewPatternUndo)
    }

    fun removeAllListeners() {
        onPatternChange.clear()
        onPrefsChange.clear()
        onTimeChange.clear()
        onIsPausedChange.clear()
        onCameraAngleChange.clear()
        onZoomChange.clear()
        onSelectedItemHashChange.clear()
    }

    // Return the default camera angle for the current pattern.

    fun defaultCameraAngle(): List<Double> {
        val ca = DoubleArray(2)
        if (prefs.defaultCameraAngle != null) {
            ca[0] = Math.toRadians(prefs.defaultCameraAngle!![0])
            val theta = min(179.9999, max(0.0001, prefs.defaultCameraAngle!![1]))
            ca[1] = Math.toRadians(theta)
        } else {
            if (pattern.numberOfJugglers == 1) {
                ca[0] = Math.toRadians(0.0)
                ca[1] = Math.toRadians(90.0)
            } else {
                ca[0] = Math.toRadians(340.0)
                ca[1] = Math.toRadians(70.0)
            }
        }
        return ca.toList()
    }

    //--------------------------------------------------------------------------
    // Undo list to support undo/redo for pattern edits
    //--------------------------------------------------------------------------

    val undoList: MutableList<JMLPattern> = mutableListOf(initialPattern)
    var undoIndex: Int = 0

    // Add the current pattern to the undo list. See View for other undo-related
    // methods.

    fun addCurrentToUndoList() {
        ++undoIndex
        undoList.add(undoIndex, pattern)
        while (undoIndex + 1 < undoList.size) {
            undoList.removeAt(undoIndex + 1)
        }
        onNewPatternUndo.forEach { it() }
    }
}
