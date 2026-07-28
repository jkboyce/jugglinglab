//
// Avatar.kt
//
// A drawable representation of a juggler.
//
// Avatars compute the 3D geometry of a juggler (head, torso, skirt, arms, etc.)
// for a given frame as a list of DrawObject2D elements (convex polygons and
// lines).
//
// Avatars are stateless after construction, so one instance can be shared by
// any number of jugglers and by both stereo renderers.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.jml.JmlEvent
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

abstract class Avatar {
    @Throws(JuggleExceptionInternal::class)
    abstract fun computeObjects(
        pat: JmlPattern,
        juggler: Int,
        time: Double,
        pool: DrawObjectPool
    ): List<DrawObject2D>

    companion object {
        // Registry of selectable avatars
        val builtinAvatars: List<String> = listOf(
            "classic",
            "female"
        )

        val builtinAvatarsStringResources: List<StringResource> = listOf(
            Res.string.gui_avatar_classic,
            Res.string.gui_avatar_female
        )

        @Throws(JuggleExceptionUser::class)
        fun newAvatar(type: String): Avatar = when (type.lowercase()) {
            "classic" -> ClassicAvatar()
            "female" -> FemaleAvatar()
            else -> {
                val message = jlGetStringResource(Res.string.error_unrecognized_avatar, type)
                throw JuggleExceptionUser(message)
            }
        }

        // Build the per-juggler avatar map from a spec such as "classic",
        // "female", or "classic,female". Multiple ids are assigned cyclically by
        // juggler number (jugglers 1,3,5 -> first id, 2,4,6 -> second, ...), so a
        // passing pattern can mix figures. A pure-default spec yields an empty
        // map, so every juggler falls back to the renderer's default figure and
        // existing patterns are unchanged.

        @Throws(JuggleExceptionUser::class)
        fun avatarMap(spec: String, numberOfJugglers: Int): Map<Int, Avatar> {
            val ids = spec.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (ids.isEmpty() || ids.all { it == AnimationPrefs.AVATAR_DEF }) return emptyMap()
            val instances = ids.distinct().associateWith { newAvatar(it) }
            return (1..numberOfJugglers).associateWith { instances.getValue(ids[(it - 1) % ids.size]) }
        }

        //----------------------------------------------------------------------
        // Avatar-independent geometry for pattern layout
        //----------------------------------------------------------------------

        // Distance from rotational center of body to juggling plane (cm)
        const val JUGGLE_PLANE_OFFSET: Double = 30.0

        // Viewpoint clearance geometry
        const val HAND_CLEARANCE_OUTWARD: Double = 5.0
        const val HAND_CLEARANCE_INWARD: Double = 5.0

        val bodyClearanceMin = Coordinate(
            -ClassicAvatar.SHOULDER_HW,
            -ClassicAvatar.SHOULDER_HW,
            0.0
        )
        val bodyClearanceMax = Coordinate(
            ClassicAvatar.SHOULDER_HW,
            ClassicAvatar.SHOULDER_HW,
            ClassicAvatar.SHOULDER_H + ClassicAvatar.NECK_H + ClassicAvatar.HEAD_H
        )
        val handClearanceMin = Coordinate(-HAND_CLEARANCE_INWARD, 0.0, -1.0)
        val handClearanceMax = Coordinate(HAND_CLEARANCE_OUTWARD, 0.0, 1.0)

        //----------------------------------------------------------------------
        // Convenience methods for Avatar implementations
        //----------------------------------------------------------------------

        internal fun bodyPoint(
            pos: Coordinate,
            side: Double,
            h: Double,
            s: Double,
            c: Double
        ) = JlVector(
            pos.x + side * c - ClassicAvatar.SHOULDER_Y * s,
            pos.z + h,
            pos.y + side * s + ClassicAvatar.SHOULDER_Y * c
        )

        // Head polygon tables, precomputed once and shared
        internal const val POLYSIDES = 40
        internal val headCos = DoubleArray(POLYSIDES) { cos(it.toDouble() * 2.0 * PI / POLYSIDES) }
        internal val headSin = DoubleArray(POLYSIDES) { sin(it.toDouble() * 2.0 * PI / POLYSIDES) }

        internal fun createHeadPolygon(
            juggler: Int,
            pos: Coordinate,
            s: Double,
            c: Double,
            headBottom: Double,
            headHeight: Double,
            pool: DrawObjectPool
        ): DrawObject2D {
            val headCenterH = headBottom + headHeight / 2.0
            val headRadiusH = headHeight / 2.0

            val headPoints = ArrayList<JlVector>(POLYSIDES)
            for (j in 0..<POLYSIDES) {
                val side = ClassicAvatar.HEAD_HW * headCos[j]
                val h = headCenterH + headRadiusH * headSin[j]
                headPoints.add(bodyPoint(pos, side, h, s, c))
            }

            val obj = pool.next()
            obj.set3DCoordinates(DrawObject2D.TYPE_POLY, juggler, headPoints, isClosed = true)
            return obj
        }

        internal fun createArmLines(
            pat: JmlPattern,
            juggler: Int,
            time: Double,
            leftShoulder: JlVector,
            rightShoulder: JlVector,
            upperArmLength: Double,
            lowerArmLength: Double,
            pool: DrawObjectPool,
            out: MutableList<DrawObject2D>
        ) {
            val leftHandCoord = Coordinate()
            val rightHandCoord = Coordinate()
            pat.layout.getHandCoordinate(juggler, JmlEvent.LEFT_HAND, time, leftHandCoord)
            pat.layout.getHandCoordinate(juggler, JmlEvent.RIGHT_HAND, time, rightHandCoord)

            val lefthand = JlVector(
                leftHandCoord.x,
                leftHandCoord.z,
                leftHandCoord.y
            )
            val righthand = JlVector(
                rightHandCoord.x,
                rightHandCoord.z,
                rightHandCoord.y
            )

            val leftElbow = elbow(leftShoulder, lefthand, upperArmLength, lowerArmLength)
            val rightElbow = elbow(rightShoulder, righthand, upperArmLength, lowerArmLength)

            if (leftElbow == null) {
                val obj = pool.next()
                obj.set3DCoordinates(
                    DrawObject2D.TYPE_LINE,
                    juggler,
                    listOf(leftShoulder, lefthand)
                )
                out.add(obj)
            } else {
                val obj1 = pool.next()
                obj1.set3DCoordinates(
                    DrawObject2D.TYPE_LINE,
                    juggler,
                    listOf(leftShoulder, leftElbow)
                )
                out.add(obj1)

                val obj2 = pool.next()
                obj2.set3DCoordinates(DrawObject2D.TYPE_LINE, juggler, listOf(leftElbow, lefthand))
                out.add(obj2)
            }

            if (rightElbow == null) {
                val obj = pool.next()
                obj.set3DCoordinates(
                    DrawObject2D.TYPE_LINE,
                    juggler,
                    listOf(rightShoulder, righthand)
                )
                out.add(obj)
            } else {
                val obj1 = pool.next()
                obj1.set3DCoordinates(
                    DrawObject2D.TYPE_LINE,
                    juggler,
                    listOf(rightShoulder, rightElbow)
                )
                out.add(obj1)

                val obj2 = pool.next()
                obj2.set3DCoordinates(
                    DrawObject2D.TYPE_LINE,
                    juggler,
                    listOf(rightElbow, righthand)
                )
                out.add(obj2)
            }
        }

        // Two-bone inverse kinematics: given a shoulder and hand position
        // (render space), return the elbow position — or null when the hand is
        // beyond arm's reach (the arm is then drawn as a straight shoulder-to-
        // hand line).

        @Suppress("LocalVariableName", "UnnecessaryVariable")
        @Throws(JuggleExceptionInternal::class)
        fun elbow(
            shoulder: JlVector,
            hand: JlVector,
            upperArmLength: Double,
            lowerArmLength: Double
        ): JlVector? {
            val U = upperArmLength
            val L = lowerArmLength
            val delta = JlVector.sub(hand, shoulder)
            val D = delta.length
            if (D > (U + L)) return null

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
}
