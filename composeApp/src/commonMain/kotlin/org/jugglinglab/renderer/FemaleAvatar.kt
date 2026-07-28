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
        val neckTop = SHOULDER_H + NECK_H
        val headObj = createHeadPolygon(juggler, pos, s, c, neckTop,
            HEAD_H, pool)
        result.add(headObj)

        // 2. Ponytail polygon (smooth 3D Bezier curve with rounded tip)
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

        val anchor = ponytailPoint(pos, sideA, backA, anchorH, s, c)
        val tip = ponytailPoint(pos, sideT, backT, tipH, s, c)

        // Control points matching original rounded 2D Bezier geometry
        val c11 = ponytailPoint(
            pos,
            sideA + 0.15 * dSide + wSide,
            backA + 0.15 * dBack + wBack,
            anchorH + 0.15 * dH,
            s, c
        )
        val c12 = ponytailPoint(
            pos,
            sideT + 0.4 * wSide,
            backT + 0.4 * wBack,
            tipH,
            s, c
        )

        val c21 = ponytailPoint(
            pos,
            sideT - 0.4 * wSide,
            backT - 0.4 * wBack,
            tipH,
            s, c
        )
        val c22 = ponytailPoint(
            pos,
            sideA + 0.15 * dSide - wSide,
            backA + 0.15 * dBack - wBack,
            anchorH + 0.15 * dH,
            s, c
        )

        val polyPoints = ArrayList<JlVector>(32)
        val samples = 16
        for (i in 0..samples) {
            val u = i.toDouble() / samples
            polyPoints.add(cubicBezier3D(anchor, c11, c12, tip, u))
        }
        for (i in 1..<samples) {
            val u = i.toDouble() / samples
            polyPoints.add(cubicBezier3D(tip, c21, c22, anchor, u))
        }

        val ponyObj = pool.next()
        ponyObj.set3DCoordinates(
            DrawObject2D.TYPE_POLY,
            juggler,
            polyPoints,
            isClosed = true
        )
        result.add(ponyObj)

        // 3. Torso polygon (unclosed at waist edge)
        val leftShoulder = bodyPoint(pos, -SHOULDER_HW, SHOULDER_H, s, c)
        val rightShoulder = bodyPoint(pos, SHOULDER_HW, SHOULDER_H, s, c)
        val rightDressWaist = bodyPoint(pos, DRESS_WAIST_HW, DRESS_WAIST_H, s, c)
        val leftDressWaist = bodyPoint(pos, -DRESS_WAIST_HW, DRESS_WAIST_H, s, c)

        val torsoObj = pool.next()
        torsoObj.set3DCoordinates(
            DrawObject2D.TYPE_POLY,
            juggler,
            listOf(rightDressWaist, rightShoulder, leftShoulder, leftDressWaist),
            isClosed = false
        )
        result.add(torsoObj)

        // 4. Skirt polygon (unclosed at waist edge)
        val leftHem = bodyPoint(pos, -HEM_HW, HEM_H, s, c)
        val rightHem = bodyPoint(pos, HEM_HW, HEM_H, s, c)

        val skirtObj = pool.next()
        skirtObj.set3DCoordinates(
            DrawObject2D.TYPE_POLY,
            juggler,
            listOf(rightDressWaist, rightHem, leftHem, leftDressWaist),
            isClosed = false
        )
        result.add(skirtObj)

        // 5. Arm lines
        val upperArmTotal = UPPER_LENGTH + UPPER_GAP_ELBOW + UPPER_GAP_SHOULDER
        val lowerArmTotal = LOWER_LENGTH + LOWER_GAP_WRIST + LOWER_GAP_ELBOW
        createArmLines(pat, juggler, time, leftShoulder, rightShoulder,
            upperArmTotal, lowerArmTotal, pool, result)

        return result
    }

    private fun cubicBezier3D(p0: JlVector, p1: JlVector, p2: JlVector, p3: JlVector, u: Double): JlVector {
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

    private fun ponytailPoint(
        pos: Coordinate, side: Double, back: Double, h: Double, s: Double, c: Double
    ) = JlVector(
        pos.x + side * c + back * s,
        pos.z + h,
        pos.y + side * s - back * c
    )

    companion object {
        const val SHOULDER_HW: Double = 18.0
        const val SHOULDER_H: Double = 40.0
        const val DRESS_WAIST_HW: Double = 11.0
        const val DRESS_WAIST_H: Double = 10.0
        const val HEM_HW: Double = 34.0
        const val HEM_H: Double = -44.0

        const val HEAD_H: Double = 26.0
        const val NECK_H: Double = 5.0
        const val UPPER_LENGTH: Double = 41.0
        const val LOWER_LENGTH: Double = 40.0

        const val UPPER_GAP_ELBOW: Double = 0.0
        const val UPPER_GAP_SHOULDER: Double = 0.0
        const val LOWER_GAP_WRIST: Double = 1.0
        const val LOWER_GAP_ELBOW: Double = 0.0

        const val PONYTAIL_SIDE: Double = 9.0
        const val PONYTAIL_BACK: Double = 5.0
        const val PONYTAIL_ANCHOR_H: Double = 0.9
        const val PONYTAIL_TIP_SIDE: Double = 13.0
        const val PONYTAIL_TIP_BACK: Double = 9.0
        const val PONYTAIL_TIP_H: Double = 0.2
    }
}
