//
// Juggler.kt
//
// The shared juggler body model: the physical dimensions (in centimeters) used
// by the pattern layout and by every avatar, plus the two-bone inverse
// kinematics for the elbows. How a juggler is *drawn* lives in the Avatar
// hierarchy (Avatar / MaleAvatar / FemaleAvatar / ...).
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.util.JuggleExceptionInternal
import kotlin.math.*

object Juggler {
    const val SHOULDER_HW: Double = 23.0 // shoulder half-width (cm)
    const val SHOULDER_H: Double = 40.0 // throw pos. to shoulder
    const val WAIST_HW: Double = 17.0 // waist half-width
    const val WAIST_H: Double = -5.0

    const val HAND_OUT: Double = 5.0 // outside width of hand
    const val HAND_IN: Double = 5.0
    const val HEAD_HW: Double = 10.0 // head half-width
    const val HEAD_H: Double = 26.0 // head height
    const val NECK_H: Double = 5.0 // neck height
    const val SHOULDER_Y: Double = 0.0
    const val PATTERN_Y: Double = 30.0
    const val UPPER_LENGTH: Double = 41.0
    const val LOWER_LENGTH: Double = 40.0

    const val LOWER_GAP_WRIST: Double = 1.0
    const val LOWER_GAP_ELBOW: Double = 0.0
    const val LOWER_HAND_HEIGHT: Double = 0.0
    const val UPPER_GAP_ELBOW: Double = 0.0
    const val UPPER_GAP_SHOULDER: Double = 0.0

    const val LOWER_TOTAL: Double = LOWER_LENGTH + LOWER_GAP_WRIST + LOWER_GAP_ELBOW
    const val UPPER_TOTAL: Double = UPPER_LENGTH + UPPER_GAP_ELBOW + UPPER_GAP_SHOULDER

    // Two-bone inverse kinematics: given a shoulder and hand position (render
    // space), return the elbow position — or null when the hand is beyond
    // arm's reach (the arm is then drawn as a straight shoulder-to-hand line).
    // Shared by every avatar, so arms bend identically whatever is drawn.

    @Suppress("LocalVariableName")
    @Throws(JuggleExceptionInternal::class)
    fun elbow(shoulder: JlVector, hand: JlVector): JlVector? {
        val L = LOWER_TOTAL
        val U = UPPER_TOTAL
        val delta = JlVector.sub(hand, shoulder)
        val D = delta.length
        if (D > (L + U)) return null

        // Perpendicular distance from the elbow to the shoulder-hand line
        // (law of cosines), then a droop toward the ground so elbows hang
        // naturally at any hand height.
        val r = sqrt(
            (4.0 * U * U * L * L - (U * U + L * L - D * D) * (U * U + L * L - D * D))
                    / (4.0 * D * D)
        )
        if (r.isNaN()) {
            throw JuggleExceptionInternal("NaN in elbow radius")
        }

        var factor = sqrt(U * U - r * r) / D
        if (factor.isNaN()) {
            throw JuggleExceptionInternal("NaN in elbow factor")
        }
        val xsc = JlVector.scale(factor, delta)
        val alpha = asin(delta.y / D)
        if (alpha.isNaN()) {
            throw JuggleExceptionInternal("NaN in elbow angle")
        }

        factor = 1.0 + r * tan(alpha) / (factor * D)
        return JlVector(
            shoulder.x + xsc.x * factor,
            shoulder.y + xsc.y - r * cos(alpha),
            shoulder.z + xsc.z * factor
        )
    }
}
