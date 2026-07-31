//
// ClassicAvatar.kt
//
// The classic Juggling Lab stick figure.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.core.Constants
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.toRadians
import kotlin.math.cos
import kotlin.math.sin

class ClassicAvatar : Avatar() {
    override fun addObjectsToPool(
        juggler: Int,
        pat: JmlPattern,
        time: Double,
        pool: DrawObjectPool
    ) {
        val pos = Coordinate()
        pat.layout.getJugglerPosition(juggler, time, pos)
        val angle = pat.layout.getJugglerAngle(juggler, time).toRadians()
        val s = sin(angle)
        val c = cos(angle)

        // 1. Head polygon
        addHeadPolygon(juggler, pos, s, c, SHOULDER_H + NECK_H, HEAD_H,
            2 * HEAD_HW, HEAD_Y, pool)

        // 2. Torso polygon
        val torsoObj = pool.next()
        torsoObj.prepare3DCoordinates(
            type = DrawObject2D.Type.POLY,
            number = juggler,
            pointsCount = 4,
            isClosed = true
        )
        val leftShoulder = bodyPoint(pos, -SHOULDER_HW, BODY_Y, SHOULDER_H, s, c, torsoObj.coords3D[0])
        val rightShoulder = bodyPoint(pos, SHOULDER_HW, BODY_Y, SHOULDER_H, s, c, torsoObj.coords3D[1])
        bodyPoint(pos, WAIST_HW, BODY_Y, WAIST_H, s, c, torsoObj.coords3D[2])
        bodyPoint(pos, -WAIST_HW, BODY_Y, WAIST_H, s, c, torsoObj.coords3D[3])

        if (Constants.DEBUG_DRAWING) {
            torsoObj.label = "Juggler $juggler torso"
        }

        // 3. Arm lines
        addArmLines(
            juggler = juggler,
            pat = pat,
            time = time,
            leftShoulder = leftShoulder,
            rightShoulder = rightShoulder,
            upperArmLength = UPPER_ARM_LENGTH,
            lowerArmLength = LOWER_ARM_LENGTH,
            upperGapElbow = UPPER_GAP_ELBOW,
            upperGapShoulder = UPPER_GAP_SHOULDER,
            lowerGapWrist = LOWER_GAP_WRIST,
            lowerGapElbow = LOWER_GAP_ELBOW,
            pool = pool
        )
    }

    companion object {
        // juggler body model dimensions (in centimeters)
        const val SHOULDER_HW: Double = 23.0
        const val SHOULDER_H: Double = 40.0
        const val WAIST_HW: Double = 17.0
        const val WAIST_H: Double = -5.0
        const val BODY_Y: Double = 0.0

        const val HEAD_HW: Double = 10.0
        const val HEAD_H: Double = 26.0
        const val NECK_H: Double = 5.0
        const val HEAD_Y: Double = 0.0

        const val UPPER_ARM_LENGTH: Double = 41.0
        const val LOWER_ARM_LENGTH: Double = 40.0
        const val UPPER_GAP_SHOULDER: Double = 0.0
        const val UPPER_GAP_ELBOW: Double = 0.0
        const val LOWER_GAP_ELBOW: Double = 0.0
        const val LOWER_GAP_WRIST: Double = 1.0
    }
}
