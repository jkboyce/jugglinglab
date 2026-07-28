//
// Avatar.kt
//
// A drawable representation of a juggler.
//
// The base class computes the shared skeleton (hands, shoulders, elbows, waist,
// head) and draws the parts common to all avatars (torso outline + head), while
// concrete avatars override dimensions, add their own points, and draw their
// own silhouette and adornments. The hierarchy mirrors Prop / BallProp /
// RingProp: the Renderer stays ignorant of which avatar it is drawing.
//
// Avatars are purely visual. The simulation, timing, hand paths and physics are
// identical whatever avatar is selected; the layout-facing body model
// (dimensions and elbow IK) stays in the Juggler object, shared by every
// avatar.
//
// Adding a new avatar: subclass Avatar, then add one arm to newAvatar() and one
// entry to builtinAvatars. Nothing else changes. Rule of thumb: a new
// silhouette or species is a subclass; new proportions/styling of an existing
// silhouette should be a parameter of that subclass.
//
// Contracts every avatar must honor:
// - Points 0..11 are the shared skeleton (see the named constants below), in
//   the same order the classic renderer used. Avatar-specific points start at
//   index CORE_POINT_COUNT and are named privately by the subclass.
// - The occlusion plane used by the painter's algorithm is the triangle
//   LEFT_SHOULDER / RIGHT_SHOULDER / RIGHT_WAIST (see DrawObject2D); an avatar
//   may add any points, but those three define its depth.
// - Avatars are stateless after construction, so one instance can be shared by
//   any number of jugglers and by both stereo renderers. All per-frame mutable
//   state lives on the DrawObject2D being drawn.
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
import org.jugglinglab.util.toRadians
import org.jugglinglab.util.jlGetStringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import org.jetbrains.compose.resources.StringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Helper class so an avatar can paint itself without knowing about the
// Renderer's paint/anti-aliasing internals or theme colors.
// - fill = fill a closed path with the background color (used to occlude
//   objects behind the body)
// - stroke = outline a path in the line color
// - segment = draw a single line segment in the line color.

class AvatarContext(
    val fill: (Path) -> Unit,
    val stroke: (Path) -> Unit,
    val segment: (Offset, Offset) -> Unit
)

abstract class Avatar {
    // Body dimensions used for the shared skeleton. Defaults are the classic
    // stick figure's; avatars with a different frame override these.
    protected open val shoulderHW: Double get() = SHOULDER_HW
    protected open val waistHW: Double get() = WAIST_HW

    // Total number of points computePoints() writes; also sizes the point
    // buffers. Core skeleton = CORE_POINT_COUNT; subclasses add their own.
    open val pointCount: Int get() = CORE_POINT_COUNT

    // Indices of the points that enter the body's screen bounding box, used by
    // the painter's algorithm for its cheap overlap test. Hands and elbows are
    // deliberately excluded (they belong to the separately-drawn arm lines).
    open val boundsPoints: List<Int> get() = TORSO_AND_HEAD_POINTS

    // Ordered outline of the drawn figure, or null to occlude lines with the
    // classic bounding-box test. An avatar whose drawn shape differs from its
    // bounding box (e.g. a flared dress) declares its outline so lines are
    // occluded by the shape actually on screen (see DrawObject2D).
    open val silhouettePoints: List<Int>? get() = null

    //--------------------------------------------------------------------------
    // Geometry: assemble the avatar's 3D points for one juggler at `time`
    //--------------------------------------------------------------------------

    // Template method: computes the shared skeleton into out[0..11], then lets
    // the subclass add its own points at out[12..] via computeExtraPoints().
    // Note the axis swap: points are in render space (y up, z depth) while the
    // layout speaks world space (z up, y depth) — see JlVector.fromCoordinate.
    @Throws(JuggleExceptionInternal::class)
    fun computePoints(pat: JmlPattern, juggler: Int, time: Double, out: Array<JlVector?>) {
        val leftHandCoord = Coordinate()
        val rightHandCoord = Coordinate()
        val pos = Coordinate()
        pat.layout.getHandCoordinate(juggler, JmlEvent.LEFT_HAND, time, leftHandCoord)
        pat.layout.getHandCoordinate(juggler, JmlEvent.RIGHT_HAND, time, rightHandCoord)
        val lefthand = JlVector(
            leftHandCoord.x, leftHandCoord.z + LOWER_HAND_HEIGHT, leftHandCoord.y
        )
        val righthand = JlVector(
            rightHandCoord.x, rightHandCoord.z + LOWER_HAND_HEIGHT, rightHandCoord.y
        )

        pat.layout.getJugglerPosition(juggler, time, pos)
        val angle = pat.layout.getJugglerAngle(juggler, time).toRadians()
        val s = sin(angle)
        val c = cos(angle)

        val neckTop = SHOULDER_H + NECK_H
        val headTop = neckTop + HEAD_H
        val upperArmTotal = UPPER_LENGTH + UPPER_GAP_ELBOW + UPPER_GAP_SHOULDER
        val lowerArmTotal = LOWER_LENGTH + LOWER_GAP_WRIST + LOWER_GAP_ELBOW

        out[LEFT_HAND] = lefthand
        out[RIGHT_HAND] = righthand
        out[LEFT_SHOULDER] = bodyPoint(pos, -shoulderHW, SHOULDER_H, s, c)
        out[RIGHT_SHOULDER] = bodyPoint(pos, shoulderHW, SHOULDER_H, s, c)
        out[LEFT_ELBOW] = elbow(
            shoulder = out[LEFT_SHOULDER]!!,
            hand = lefthand,
            upperArmLength = upperArmTotal,
            lowerArmLength = lowerArmTotal
        )
        out[RIGHT_ELBOW] = elbow(
            shoulder = out[RIGHT_SHOULDER]!!,
            hand = righthand,
            upperArmLength = upperArmTotal,
            lowerArmLength = lowerArmTotal
        )
        out[LEFT_WAIST] = bodyPoint(pos, -waistHW, WAIST_H, s, c)
        out[RIGHT_WAIST] = bodyPoint(pos, waistHW, WAIST_H, s, c)
        out[LEFT_HEAD_BOTTOM] = bodyPoint(pos, -HEAD_HW, neckTop, s, c)
        out[LEFT_HEAD_TOP] = bodyPoint(pos, -HEAD_HW, headTop, s, c)
        out[RIGHT_HEAD_BOTTOM] = bodyPoint(pos, HEAD_HW, neckTop, s, c)
        out[RIGHT_HEAD_TOP] = bodyPoint(pos, HEAD_HW, headTop, s, c)

        computeExtraPoints(pos, s, c, out)
    }

    // Hook: subclasses add their own points (hips, hem, ponytail, tail, ...) at
    // indices >= CORE_POINT_COUNT. `pos` is the juggler position (world space),
    // `s`/`c` are sin/cos of the juggler's facing angle.
    protected open fun computeExtraPoints(
        pos: Coordinate, s: Double, c: Double, out: Array<JlVector?>
    ) {
    }

    // A point `side` cm along the shoulder axis at height `h` above the throw
    // position, in the juggler's rotated frame.
    protected fun bodyPoint(pos: Coordinate, side: Double, h: Double, s: Double, c: Double) =
        JlVector(
            pos.x + side * c - SHOULDER_Y * s,
            pos.z + h,
            pos.y + side * s + SHOULDER_Y * c
        )

    //--------------------------------------------------------------------------
    // Drawing (screen space; all points already projected into body.coord)
    //--------------------------------------------------------------------------

    // Draw the whole figure. Default = torso + head; avatars with adornments
    // (e.g. a ponytail that can sit in front of or behind the head) override
    // this to control the drawing order.
    open fun drawBody(body: DrawObject2D, ctx: AvatarContext) {
        drawTorso(body, ctx)
        drawHead(body, ctx)
    }

    // Each avatar defines its own torso silhouette (trapezoid, dress, ...) as a
    // path over the projected points in body.coord. The path is closed, filled
    // and stroked by drawTorso().
    protected abstract fun buildTorsoPath(body: DrawObject2D, path: Path)

    protected fun drawTorso(body: DrawObject2D, ctx: AvatarContext) {
        val path = body.scratchPath(TORSO_PATH)
        path.rewind()
        buildTorsoPath(body, path)
        path.close()
        ctx.fill(path)
        ctx.stroke(path)
    }

    // The head is identical for every avatar: an outlined oval, or a vertical
    // line when seen edge-on. (Becomes `open` the day an avatar needs its own.)
    protected fun drawHead(body: DrawObject2D, ctx: AvatarContext) {
        val lHeadBx = body.coord[LEFT_HEAD_BOTTOM].x
        val lHeadBy = body.coord[LEFT_HEAD_BOTTOM].y
        val lHeadTy = body.coord[LEFT_HEAD_TOP].y
        val rHeadBx = body.coord[RIGHT_HEAD_BOTTOM].x
        val rHeadBy = body.coord[RIGHT_HEAD_BOTTOM].y

        if (abs(rHeadBx - lHeadBx) > 2.0) {
            val headPath = body.scratchPath(HEAD_PATH)
            headPath.rewind()
            for (j in 0..<POLYSIDES) {
                val hx = (0.5 * (lHeadBx + rHeadBx + headCos[j] * (rHeadBx - lHeadBx))).roundToInt()
                val hy = (0.5 * (lHeadBy + lHeadTy + headSin[j] * (lHeadBy - lHeadTy))
                        + (hx - lHeadBx) * (rHeadBy - lHeadBy) / (rHeadBx - lHeadBx)).roundToInt()

                if (j == 0) headPath.moveTo(hx.toFloat(), hy.toFloat())
                else headPath.lineTo(hx.toFloat(), hy.toFloat())
            }
            headPath.close()
            ctx.fill(headPath)
            ctx.stroke(headPath)
        } else {
            val h =
                sqrt((lHeadBy - lHeadTy) * (lHeadBy - lHeadTy) + (rHeadBy - lHeadBy) * (rHeadBy - lHeadBy))
            val hx = (0.5 * (lHeadBx + rHeadBx)).toFloat()
            val hy1 = (0.5 * (lHeadTy + rHeadBy + h)).toFloat()
            val hy2 = (0.5 * (lHeadTy + rHeadBy - h)).toFloat()
            ctx.segment(Offset(hx, hy1), Offset(hx, hy2))
        }
    }

    companion object {
        // Shared juggler body model dimensions (in centimeters)
        const val SHOULDER_HW: Double = 23.0 // shoulder half-width (cm)
        const val SHOULDER_H: Double = 40.0 // throw pos. to shoulder
        const val WAIST_HW: Double = 17.0 // waist half-width
        const val WAIST_H: Double = -5.0

        const val HEAD_HW: Double = 10.0 // head half-width
        const val HEAD_H: Double = 26.0 // head height
        const val NECK_H: Double = 5.0 // neck height
        const val SHOULDER_Y: Double = 0.0
        const val UPPER_LENGTH: Double = 41.0
        const val LOWER_LENGTH: Double = 40.0

        const val UPPER_GAP_ELBOW: Double = 0.0
        const val UPPER_GAP_SHOULDER: Double = 0.0
        const val LOWER_GAP_WRIST: Double = 1.0
        const val LOWER_GAP_ELBOW: Double = 0.0
        const val LOWER_HAND_HEIGHT: Double = 0.0

        const val HAND_OUT: Double = 5.0
        const val HAND_IN: Double = 5.0

        // Distance from rotational center of body to juggling plane (cm)
        const val JUGGLE_PLANE_OFFSET: Double = 30.0

        // Viewpoint clearance geometry used by LaidoutPattern.
        // - `body` is relative to the juggler's unrotated position
        // - `hand` is relative to the hand's position
        val bodyClearanceMin = Coordinate(-SHOULDER_HW, -SHOULDER_HW, 0.0)
        val bodyClearanceMax = Coordinate(SHOULDER_HW, SHOULDER_HW, SHOULDER_H + NECK_H + HEAD_H)
        val handClearanceMin = Coordinate(-HAND_IN, 0.0, -1.0)
        val handClearanceMax = Coordinate(HAND_OUT, 0.0, 1.0)

        // The shared skeleton: indices into the point buffers, identical in 3D
        // (computePoints output) and 2D (DrawObject2D.coord after projection).
        // Same order as the classic renderer's 12-slot jugglerVec.
        const val LEFT_HAND = 0
        const val RIGHT_HAND = 1
        const val LEFT_SHOULDER = 2
        const val RIGHT_SHOULDER = 3
        const val LEFT_ELBOW = 4 // null in the buffer when out of reach
        const val RIGHT_ELBOW = 5 // null in the buffer when out of reach
        const val LEFT_WAIST = 6
        const val RIGHT_WAIST = 7
        const val LEFT_HEAD_BOTTOM = 8
        const val LEFT_HEAD_TOP = 9
        const val RIGHT_HEAD_BOTTOM = 10
        const val RIGHT_HEAD_TOP = 11
        const val CORE_POINT_COUNT = 12

        // Bounding-box points of the shared skeleton (torso + head).
        val TORSO_AND_HEAD_POINTS: List<Int> = listOf(
            LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_WAIST, RIGHT_WAIST,
            LEFT_HEAD_BOTTOM, LEFT_HEAD_TOP, RIGHT_HEAD_BOTTOM, RIGHT_HEAD_TOP
        )

        // Scratch-path slots on DrawObject2D used by the shared drawing code.
        // Subclasses use slots >= FIRST_FREE_PATH for their own adornments.
        protected const val TORSO_PATH = 0
        protected const val HEAD_PATH = 1
        const val FIRST_FREE_PATH = 2

        // Head polygon tables, precomputed once and shared (read-only).
        private const val POLYSIDES = 40 // # sides in polygon for head
        private val headCos = DoubleArray(POLYSIDES) { cos(it.toDouble() * 2.0 * PI / POLYSIDES) }
        private val headSin = DoubleArray(POLYSIDES) { sin(it.toDouble() * 2.0 * PI / POLYSIDES) }

        // Registry of selectable avatars.
        val builtinAvatars: List<String> = listOf(
            "classic",
            "female"
        )

        val builtinAvatarsStringResources: List<StringResource> = listOf(
            Res.string.gui_avatar_classic,
            Res.string.gui_avatar_female
        )

        // Factory method to create an avatar.
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
