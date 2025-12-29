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

class PatternAnimationState(initialPattern: JMLPattern, initialPrefs: AnimationPrefs) {
    var pattern: JMLPattern by mutableStateOf(initialPattern)
    var prefs: AnimationPrefs by mutableStateOf(initialPrefs)

    // Animation State
    var time: Double by mutableStateOf(0.0)
    var isPaused: Boolean by mutableStateOf(false)

    // View State
    var cameraAngle: DoubleArray by mutableStateOf(doubleArrayOf(0.0, 90.0)) // [theta, phi]
    var zoom: Double by mutableStateOf(1.0)

    // Selection State
    var selectedItemHash: Int by mutableStateOf(0)

    // Helper to update pattern (and handle undo history if needed)
    fun updatePattern(newPattern: JMLPattern) {
        pattern = newPattern
        // Trigger layout recalculation if necessary
        // pattern.layout 
    }
}
