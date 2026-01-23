//
// PatternAnimationState.kt
//
// This class contains all the state variables needed to draw a frame of
// the pattern animation, including the ladder diagram.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.jml.JmlPattern
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min

class PatternAnimationState(
    initialPattern: JmlPattern,
    initialPrefs: AnimationPrefs
) {
    //--------------------------------------------------------------------------
    // State variables
    //--------------------------------------------------------------------------

    var pattern: JmlPattern by mutableStateOf(initialPattern)
    var prefs: AnimationPrefs by mutableStateOf(initialPrefs)
    var time: Double by mutableStateOf(initialPattern.loopStartTime)
    var isPaused: Boolean by mutableStateOf(initialPrefs.startPaused)
    var cameraAngle: List<Double> by mutableStateOf(initialCameraAngle())  // radians
    var zoom: Double by mutableStateOf(1.0)
    var selectedItemHashCode: Int by mutableStateOf(0)
    var propForPath: List<Int> by mutableStateOf(initialPattern.initialPropForPath)
    var fitToFrame: Boolean by mutableStateOf(true)
    var showAxes: Boolean by mutableStateOf(false)
    var draggingPosition: Boolean by mutableStateOf(false)
    var draggingPositionZ: Boolean by mutableStateOf(false)
    var draggingPositionAngle: Boolean by mutableStateOf(false)
    var message: String by mutableStateOf("")

    //--------------------------------------------------------------------------
    // Helper to update the state and notify listeners
    //--------------------------------------------------------------------------

    fun update(
        pattern: JmlPattern? = null,
        prefs: AnimationPrefs? = null,
        time: Double? = null,
        isPaused: Boolean? = null,
        cameraAngle: List<Double>? = null,
        zoom: Double? = null,
        selectedItemHashCode: Int? = null,
        propForPath: List<Int>? = null,
        fitToFrame: Boolean? = null,
        showAxes: Boolean? = null,
        draggingPosition: Boolean? = null,
        draggingPositionZ: Boolean? = null,
        draggingPositionAngle: Boolean? = null,
        message: String? = null,
    ) {
        if (pattern != null) this.pattern = pattern
        if (prefs != null) this.prefs = prefs
        if (time != null) this.time = time
        if (isPaused != null) this.isPaused = isPaused
        if (cameraAngle != null) this.cameraAngle = cameraAngle
        if (zoom != null) this.zoom = zoom
        if (selectedItemHashCode != null) this.selectedItemHashCode = selectedItemHashCode
        if (propForPath != null) this.propForPath = propForPath
        if (fitToFrame != null) this.fitToFrame = fitToFrame
        if (showAxes != null) this.showAxes = showAxes
        if (draggingPosition != null) this.draggingPosition = draggingPosition
        if (draggingPositionZ != null) this.draggingPositionZ = draggingPositionZ
        if (draggingPositionAngle != null) this.draggingPositionAngle = draggingPositionAngle
        if (message != null) this.message = message

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
        if (propForPath != null) {
            onPropForPathChange.forEach { it() }
        }
        if (fitToFrame != null) {
            onFitToFrameChange.forEach { it() }
        }
        if (showAxes != null) {
            onShowAxesChange.forEach { it() }
        }
        if (draggingPosition != null) {
            onDraggingPositionChange.forEach { it() }
        }
        if (draggingPositionZ != null) {
            onDraggingPositionZChange.forEach { it() }
        }
        if (draggingPositionAngle != null) {
            onDraggingPositionAngleChange.forEach { it() }
        }
        if (message != null) {
            onMessageChange.forEach { it() }
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
    val onPropForPathChange = mutableListOf<() -> Unit>()
    val onFitToFrameChange = mutableListOf<() -> Unit>()
    val onShowAxesChange = mutableListOf<() -> Unit>()
    val onDraggingPositionChange = mutableListOf<() -> Unit>()
    val onDraggingPositionZChange = mutableListOf<() -> Unit>()
    val onDraggingPositionAngleChange = mutableListOf<() -> Unit>()
    val onMessageChange = mutableListOf<() -> Unit>()
    val onNewPatternUndo = mutableListOf<() -> Unit>()

    fun addListener(
        onPatternChange: (() -> Unit)? = null,
        onPrefsChange: (() -> Unit)? = null,
        onTimeChange: (() -> Unit)? = null,
        onIsPausedChanged: (() -> Unit)? = null,
        onCameraAngleChange: (() -> Unit)? = null,
        onZoomChange: (() -> Unit)? = null,
        onSelectedItemHashChange: (() -> Unit)? = null,
        onPropForPathChange: (() -> Unit)? = null,
        onFitToFrameChange: (() -> Unit)? = null,
        onShowAxesChange: (() -> Unit)? = null,
        onDraggingPositionChange: (() -> Unit)? = null,
        onDraggingPositionZChange: (() -> Unit)? = null,
        onDraggingPositionAngleChange: (() -> Unit)? = null,
        onMessageChange: (() -> Unit)? = null,
        onNewPatternUndo: (() -> Unit)? = null
    ) {
        if (onPatternChange != null) this.onPatternChange.add(onPatternChange)
        if (onPrefsChange != null) this.onPrefsChange.add(onPrefsChange)
        if (onTimeChange != null) this.onTimeChange.add(onTimeChange)
        if (onIsPausedChanged != null) this.onIsPausedChange.add(onIsPausedChanged)
        if (onCameraAngleChange != null) this.onCameraAngleChange.add(onCameraAngleChange)
        if (onZoomChange != null) this.onZoomChange.add(onZoomChange)
        if (onSelectedItemHashChange != null) this.onSelectedItemHashChange.add(onSelectedItemHashChange)
        if (onPropForPathChange != null) this.onPropForPathChange.add(onPropForPathChange)
        if (onFitToFrameChange != null) this.onFitToFrameChange.add(onFitToFrameChange)
        if (onShowAxesChange != null) this.onShowAxesChange.add(onShowAxesChange)
        if (onDraggingPositionChange != null) this.onDraggingPositionChange.add(onDraggingPositionChange)
        if (onDraggingPositionZChange != null) this.onDraggingPositionZChange.add(onDraggingPositionZChange)
        if (onDraggingPositionAngleChange != null) this.onDraggingPositionAngleChange.add(onDraggingPositionAngleChange)
        if (onMessageChange != null) this.onMessageChange.add(onMessageChange)
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
        onPropForPathChange.clear()
        onFitToFrameChange.clear()
        onShowAxesChange.clear()
        onDraggingPositionChange.clear()
        onDraggingPositionZChange.clear()
        onDraggingPositionAngleChange.clear()
        onMessageChange.clear()
        onNewPatternUndo.clear()
    }

    // Return the initial camera angle for the current pattern.

    fun initialCameraAngle(): List<Double> {
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

    // Advance the path-to-prop mapping after a cycle through the pattern.
    // After pattern.periodWithProps times through this, the props return to
    // their initial assignments.

    fun advancePropForPath() {
        val paths = pattern.numberOfPaths
        val newPropForPath = IntArray(paths)
        val inversePathPerm = pattern.pathPermutation!!.inverse
        for (i in 0..<paths) {
            newPropForPath[inversePathPerm.map(i + 1) - 1] = propForPath[i]
        }
        update(propForPath = newPropForPath.toList())
    }

    //--------------------------------------------------------------------------
    // Undo list to support undo/redo for pattern edits
    //--------------------------------------------------------------------------

    val undoList: MutableList<JmlPattern> = mutableListOf(initialPattern)
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
