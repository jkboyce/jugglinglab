//
// ClassicAvatar.kt
//
// The classic Juggling Lab stick figure.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.toRadians
import kotlin.math.cos
import kotlin.math.sin

class ClassicAvatar : Avatar() {
    override fun computeObjects(
        pat: JmlPattern,
        juggler: Int,
        time: Double,
        pool: DrawObjectPool
    ): List<DrawObject2D> {
        val result = mutableListOf<DrawObject2D>()

        val pos = Coordinate()
        pat.layout.getJugglerPosition(juggler, time, pos)
        val angle = pat.layout.getJugglerAngle(juggler, time).toRadians()
        val s = sin(angle)
        val c = cos(angle)

        // 1. Head polygon
        val headObj = createHeadPolygon(juggler, pos, s, c, SHOULDER_H + NECK_H,
            HEAD_H, pool)
        result.add(headObj)

        // 2. Torso polygon (left shoulder, right shoulder, right waist, left waist)
        val leftShoulder = bodyPoint(pos, -SHOULDER_HW, SHOULDER_H, s, c)
        val rightShoulder = bodyPoint(pos, SHOULDER_HW, SHOULDER_H, s, c)
        val rightWaist = bodyPoint(pos, WAIST_HW, WAIST_H, s, c)
        val leftWaist = bodyPoint(pos, -WAIST_HW, WAIST_H, s, c)

        val torsoObj = pool.next()
        torsoObj.set3DCoordinates(
            DrawObject2D.TYPE_POLY,
            juggler,
            listOf(leftShoulder, rightShoulder, rightWaist, leftWaist),
            isClosed = true
        )
        result.add(torsoObj)

        // 3. Arm lines
        val upperArmTotal = UPPER_LENGTH + UPPER_GAP_ELBOW + UPPER_GAP_SHOULDER
        val lowerArmTotal = LOWER_LENGTH + LOWER_GAP_WRIST + LOWER_GAP_ELBOW
        createArmLines(pat, juggler, time, leftShoulder, rightShoulder,
            upperArmTotal, lowerArmTotal, pool, result)

        return result
    }

    companion object {
        // juggler body model dimensions (in centimeters)
        const val SHOULDER_HW: Double = 23.0
        const val SHOULDER_H: Double = 40.0
        const val WAIST_HW: Double = 17.0
        const val WAIST_H: Double = -5.0

        const val HEAD_HW: Double = 10.0
        const val HEAD_H: Double = 26.0
        const val NECK_H: Double = 5.0
        const val SHOULDER_Y: Double = 0.0
        const val UPPER_LENGTH: Double = 41.0
        const val LOWER_LENGTH: Double = 40.0

        const val UPPER_GAP_ELBOW: Double = 0.0
        const val UPPER_GAP_SHOULDER: Double = 0.0
        const val LOWER_GAP_WRIST: Double = 1.0
        const val LOWER_GAP_ELBOW: Double = 0.0
    }
}
