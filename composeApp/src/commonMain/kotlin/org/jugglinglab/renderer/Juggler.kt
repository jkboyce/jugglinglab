//
// Juggler.kt
//
// This class calculates the coordinates of the juggler elbows, shoulders, etc.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.jml.JmlEvent
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.JuggleExceptionInternal
import kotlin.math.*

object Juggler {
    // juggler dimensions, in centimeters
    const val SHOULDER_HW: Double = 23.0 // shoulder half-width (cm)
    const val SHOULDER_H: Double = 40.0 // throw pos. to shoulder
    const val WAIST_HW: Double = 17.0 // waist half-width
    const val WAIST_H: Double = -5.0

    // public final static double ELBOW_HW = 30;  // elbow "home"
    // public final static double ELBOW_H = 6;
    // public final static double ELBOW_SLOP = 12;
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

    // the remaining are used only for the 3d display
    // public final static double SHOULDER_RADIUS = 6;
    // public final static double ELBOW_RADIUS = 4;
    // public final static double WRIST_RADIUS = 2;

    @Suppress("LocalVariableName")
    @Throws(JuggleExceptionInternal::class)
    fun findJugglerCoordinates(pat: JmlPattern, time: Double, result: Array<Array<JlVector?>>) {
        for (juggler in 1..pat.numberOfJugglers) {
            val coord0 = Coordinate()
            val coord1 = Coordinate()
            val coord2 = Coordinate()
            pat.layout.getHandCoordinate(juggler, JmlEvent.Companion.LEFT_HAND, time, coord0)
            pat.layout.getHandCoordinate(juggler, JmlEvent.Companion.RIGHT_HAND, time, coord1)
            val lefthand = JlVector(coord0.x, coord0.z + LOWER_HAND_HEIGHT, coord0.y)
            val righthand = JlVector(coord1.x, coord1.z + LOWER_HAND_HEIGHT, coord1.y)

            pat.layout.getJugglerPosition(juggler, time, coord2)
            val angle = Math.toRadians(pat.layout.getJugglerAngle(juggler, time))
            val s = sin(angle)
            val c = cos(angle)

            val leftshoulder =
                JlVector(
                    coord2.x - SHOULDER_HW * c - SHOULDER_Y * s,
                    coord2.z + SHOULDER_H,
                    coord2.y - SHOULDER_HW * s + SHOULDER_Y * c
                )
            val rightshoulder =
                JlVector(
                    coord2.x + SHOULDER_HW * c - SHOULDER_Y * s,
                    coord2.z + SHOULDER_H,
                    coord2.y + SHOULDER_HW * s + SHOULDER_Y * c
                )
            val leftwaist =
                JlVector(
                    coord2.x - WAIST_HW * c - SHOULDER_Y * s,
                    coord2.z + WAIST_H,
                    coord2.y - WAIST_HW * s + SHOULDER_Y * c
                )
            val rightwaist =
                JlVector(
                    coord2.x + WAIST_HW * c - SHOULDER_Y * s,
                    coord2.z + WAIST_H,
                    coord2.y + WAIST_HW * s + SHOULDER_Y * c
                )
            val leftheadbottom =
                JlVector(
                    coord2.x - HEAD_HW * c - SHOULDER_Y * s,
                    coord2.z + SHOULDER_H + NECK_H,
                    coord2.y - HEAD_HW * s + SHOULDER_Y * c
                )
            val leftheadtop =
                JlVector(
                    coord2.x - HEAD_HW * c - SHOULDER_Y * s,
                    coord2.z + SHOULDER_H + NECK_H + HEAD_H,
                    coord2.y - HEAD_HW * s + SHOULDER_Y * c
                )
            val rightheadbottom =
                JlVector(
                    coord2.x + HEAD_HW * c - SHOULDER_Y * s,
                    coord2.z + SHOULDER_H + NECK_H,
                    coord2.y + HEAD_HW * s + SHOULDER_Y * c
                )
            val rightheadtop =
                JlVector(
                    coord2.x + HEAD_HW * c - SHOULDER_Y * s,
                    coord2.z + SHOULDER_H + NECK_H + HEAD_H,
                    coord2.y + HEAD_HW * s + SHOULDER_Y * c
                )

            val L = LOWER_TOTAL
            val U = UPPER_TOTAL
            val deltaL = JlVector.sub(lefthand, leftshoulder)
            var D = deltaL.length
            var leftelbow: JlVector? = null
            if (D <= (L + U)) {
                // Calculate the coordinates of the elbows
                val Lr = sqrt(
                    (4.0 * U * U * L * L - (U * U + L * L - D * D) * (U * U + L * L - D * D))
                            / (4.0 * D * D)
                )
                if (Lr.isNaN()) {
                    throw JuggleExceptionInternal("NaN in renderer 1")
                }

                var factor = sqrt(U * U - Lr * Lr) / D
                if (factor.isNaN()) {
                    throw JuggleExceptionInternal("NaN in renderer 2")
                }
                val Lxsc = JlVector.scale(factor, deltaL)
                val Lalpha = asin(deltaL.y / D)
                if (Lalpha.isNaN()) {
                    throw JuggleExceptionInternal("NaN in renderer 3")
                }
                factor = 1.0 + Lr * tan(Lalpha) / (factor * D)
                leftelbow = JlVector(
                    leftshoulder.x + Lxsc.x * factor,
                    leftshoulder.y + Lxsc.y - Lr * cos(Lalpha),
                    leftshoulder.z + Lxsc.z * factor
                )
            }

            val deltaR = JlVector.sub(righthand, rightshoulder)
            D = deltaR.length
            var rightelbow: JlVector? = null
            if (D <= (L + U)) {
                // Calculate the coordinates of the elbows
                val Rr = sqrt(
                    (4.0 * U * U * L * L - (U * U + L * L - D * D) * (U * U + L * L - D * D))
                            / (4.0 * D * D)
                )
                if (Rr.isNaN()) {
                    throw JuggleExceptionInternal("NaN in renderer 4")
                }

                var factor = sqrt(U * U - Rr * Rr) / D
                if (factor.isNaN()) {
                    throw JuggleExceptionInternal("NaN in renderer 5")
                }
                val Rxsc = JlVector.scale(factor, deltaR)
                val Ralpha = asin(deltaR.y / D)
                if (Ralpha.isNaN()) {
                    throw JuggleExceptionInternal("NaN in renderer 6")
                }
                factor = 1.0 + Rr * tan(Ralpha) / (factor * D)
                rightelbow = JlVector(
                    rightshoulder.x + Rxsc.x * factor,
                    rightshoulder.y + Rxsc.y - Rr * cos(Ralpha),
                    rightshoulder.z + Rxsc.z * factor
                )
            }

            result[juggler - 1][0] = lefthand
            result[juggler - 1][1] = righthand
            result[juggler - 1][2] = leftshoulder
            result[juggler - 1][3] = rightshoulder
            result[juggler - 1][4] = leftelbow
            result[juggler - 1][5] = rightelbow
            result[juggler - 1][6] = leftwaist
            result[juggler - 1][7] = rightwaist
            result[juggler - 1][8] = leftheadbottom
            result[juggler - 1][9] = leftheadtop
            result[juggler - 1][10] = rightheadbottom
            result[juggler - 1][11] = rightheadtop
        }
    }
}
