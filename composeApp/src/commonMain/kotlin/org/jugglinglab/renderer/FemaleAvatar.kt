//
// FemaleAvatar.kt
//
// The female juggler avatar: a slimmer frame, a flared dress (torso + skirt
// polys), and a side-swept ponytail.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.toRadians
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FemaleAvatar : Avatar() {
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
        val neckTop = SHOULDER_H + NECK_H
        addHeadPolygon(juggler, pos, s, c, neckTop, HEAD_H, 2 * HEAD_HW, HEAD_Y, pool)

        // 2. Ponytail polygon
        val polyPoints = PONYTAIL_LOCAL_POINTS.map { pt ->
            bodyPoint(pos, pt.x, pt.y, pt.z, s, c)
        }

        val ponyObj = pool.next()
        ponyObj.set3DCoordinates(
            DrawObject2D.Type.POLY,
            juggler,
            polyPoints,
            isClosed = true
        )

        // 3. Torso polygon (unclosed at waist edge)
        val leftShoulder = bodyPoint(pos, -SHOULDER_HW, BODY_Y, SHOULDER_H, s, c)
        val rightShoulder = bodyPoint(pos, SHOULDER_HW, BODY_Y, SHOULDER_H, s, c)
        val rightDressWaist = bodyPoint(pos, DRESS_WAIST_HW, BODY_Y, DRESS_WAIST_H, s, c)
        val leftDressWaist = bodyPoint(pos, -DRESS_WAIST_HW, BODY_Y, DRESS_WAIST_H, s, c)

        val torsoObj = pool.next()
        torsoObj.set3DCoordinates(
            DrawObject2D.Type.POLY,
            juggler,
            listOf(rightDressWaist, rightShoulder, leftShoulder, leftDressWaist),
            isClosed = false
        )

        // 4. Skirt polygon (unclosed at waist edge)
        val leftHem = bodyPoint(pos, -HEM_HW, BODY_Y, HEM_H, s, c)
        val rightHem = bodyPoint(pos, HEM_HW, BODY_Y, HEM_H, s, c)

        val skirtObj = pool.next()
        skirtObj.set3DCoordinates(
            DrawObject2D.Type.POLY,
            juggler,
            listOf(rightDressWaist, rightHem, leftHem, leftDressWaist),
            isClosed = false
        )

        // 5. Arm lines
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
        const val SHOULDER_HW: Double = 18.0
        const val SHOULDER_H: Double = 40.0
        const val DRESS_WAIST_HW: Double = 11.0
        const val DRESS_WAIST_H: Double = 10.0
        const val HEM_HW: Double = 34.0
        const val HEM_H: Double = -44.0
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

        const val PONYTAIL_SIDE: Double = 9.0
        const val PONYTAIL_BACK: Double = 5.0
        const val PONYTAIL_ANCHOR_H: Double = 0.9
        const val PONYTAIL_TIP_SIDE: Double = 13.0
        const val PONYTAIL_TIP_BACK: Double = 9.0
        const val PONYTAIL_TIP_H: Double = 0.2

        // Precompute the ponytail polygon once, at load time.
        // It's modelled as a smooth 3D Bezier curve with rounded tip.

        private val PONYTAIL_LOCAL_POINTS: List<JlVector> = run {
            val neckTop = SHOULDER_H + NECK_H
            val anchorH = neckTop + PONYTAIL_ANCHOR_H * HEAD_H
            val tipH = neckTop + PONYTAIL_TIP_H * HEAD_H

            val sideA = PONYTAIL_SIDE
            val backA = PONYTAIL_BACK
            val sideT = PONYTAIL_TIP_SIDE
            val backT = PONYTAIL_TIP_BACK

            val dSide = sideT - sideA
            val dBack = backT - backA
            val dH = tipH - anchorH

            val len = sqrt(dSide * dSide + dBack * dBack + dH * dH).coerceAtLeast(1.0)
            val horizLen = sqrt(dSide * dSide + dBack * dBack).coerceAtLeast(0.001)
            val bulgeMag = 0.34 * len
            val wSide = -dBack / horizLen * bulgeMag
            val wBack = dSide / horizLen * bulgeMag

            val anchor = JlVector(sideA, -backA, anchorH)
            val tip = JlVector(sideT, -backT, tipH)

            val c11 = JlVector(
                sideA + 0.15 * dSide + wSide,
                -backA - 0.15 * dBack - wBack,
                anchorH + 0.15 * dH
            )
            val c12 = JlVector(
                sideT + 0.4 * wSide,
                -backT - 0.4 * wBack,
                tipH
            )
            val c21 = JlVector(
                sideT - 0.4 * wSide,
                -backT + 0.4 * wBack,
                tipH
            )
            val c22 = JlVector(
                sideA + 0.15 * dSide - wSide,
                -backA - 0.15 * dBack + wBack,
                anchorH + 0.15 * dH
            )

            val points = ArrayList<JlVector>(32)
            val samples = 16
            for (i in 0..samples) {
                val u = i.toDouble() / samples
                points.add(cubicBezier3D(anchor, c11, c12, tip, u))
            }
            for (i in 1..<samples) {
                val u = i.toDouble() / samples
                points.add(cubicBezier3D(tip, c21, c22, anchor, u))
            }
            points
        }

        private fun cubicBezier3D(
            p0: JlVector, p1: JlVector, p2: JlVector, p3: JlVector, u: Double
        ): JlVector {
            val omt = 1.0 - u
            val f0 = omt * omt * omt
            val f1 = 3.0 * omt * omt * u
            val f2 = 3.0 * omt * u * u
            val f3 = u * u * u
            return JlVector(
                f0 * p0.x + f1 * p1.x + f2 * p2.x + f3 * p3.x,
                f0 * p0.y + f1 * p1.y + f2 * p2.y + f3 * p3.y,
                f0 * p0.z + f1 * p1.z + f2 * p2.z + f3 * p3.z
            )
        }
    }
}
