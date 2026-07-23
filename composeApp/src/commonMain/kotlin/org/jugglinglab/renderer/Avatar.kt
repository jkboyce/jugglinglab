//
// Avatar.kt
//
// A drawable representation of a juggler (stick figure, female figure, ...).
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
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.composeapp.generated.resources.*
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    protected open val shoulderHW: Double get() = Juggler.SHOULDER_HW
    protected open val waistHW: Double get() = Juggler.WAIST_HW

    // Total number of points computePoints() writes; also sizes the point
    // buffers. Core skeleton = CORE_POINT_COUNT; subclasses add their own.
    open val pointCount: Int get() = CORE_POINT_COUNT

    // Indices of the points that enter the body's screen bounding box, used by
    // the painter's algorithm for its cheap overlap test. Hands and elbows are
    // deliberately excluded (they belong to the separately-drawn arm lines).
    open val boundsPoints: List<Int> get() = TORSO_AND_HEAD_POINTS

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
            leftHandCoord.x, leftHandCoord.z + Juggler.LOWER_HAND_HEIGHT, leftHandCoord.y
        )
        val righthand = JlVector(
            rightHandCoord.x, rightHandCoord.z + Juggler.LOWER_HAND_HEIGHT, rightHandCoord.y
        )

        pat.layout.getJugglerPosition(juggler, time, pos)
        val angle = pat.layout.getJugglerAngle(juggler, time).toRadians()
        val s = sin(angle)
        val c = cos(angle)

        val neckTop = Juggler.SHOULDER_H + Juggler.NECK_H
        val headTop = neckTop + Juggler.HEAD_H

        out[LEFT_HAND] = lefthand
        out[RIGHT_HAND] = righthand
        out[LEFT_SHOULDER] = bodyPoint(pos, -shoulderHW, Juggler.SHOULDER_H, s, c)
        out[RIGHT_SHOULDER] = bodyPoint(pos, shoulderHW, Juggler.SHOULDER_H, s, c)
        out[LEFT_ELBOW] = Juggler.elbow(out[LEFT_SHOULDER]!!, lefthand)
        out[RIGHT_ELBOW] = Juggler.elbow(out[RIGHT_SHOULDER]!!, righthand)
        out[LEFT_WAIST] = bodyPoint(pos, -waistHW, Juggler.WAIST_H, s, c)
        out[RIGHT_WAIST] = bodyPoint(pos, waistHW, Juggler.WAIST_H, s, c)
        out[LEFT_HEAD_BOTTOM] = bodyPoint(pos, -Juggler.HEAD_HW, neckTop, s, c)
        out[LEFT_HEAD_TOP] = bodyPoint(pos, -Juggler.HEAD_HW, headTop, s, c)
        out[RIGHT_HEAD_BOTTOM] = bodyPoint(pos, Juggler.HEAD_HW, neckTop, s, c)
        out[RIGHT_HEAD_TOP] = bodyPoint(pos, Juggler.HEAD_HW, headTop, s, c)

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
            pos.x + side * c - Juggler.SHOULDER_Y * s,
            pos.z + h,
            pos.y + side * s + Juggler.SHOULDER_Y * c
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

        // The default avatar's id: the classic stick figure. First in the registry.
        const val DEFAULT: String = "default"

        // Registry of selectable avatars, mirroring Prop.builtinProps. The
        // first entry is the default.
        val builtinAvatars: List<String> = listOf(
            DEFAULT,
            "female"
        )

        val builtinAvatarsStringResources: List<StringResource> = listOf(
            Res.string.gui_avatar_default,
            Res.string.gui_avatar_female
        )

        // Factory, mirroring Prop.newProp(). Adding a new avatar means adding a
        // subclass and one arm here.
        @Throws(JuggleExceptionUser::class)
        fun newAvatar(type: String): Avatar = when (type.lowercase()) {
            DEFAULT -> DefaultAvatar()
            "female" -> FemaleAvatar()
            else -> {
                val message = jlGetStringResource(Res.string.error_unrecognized_avatar, type)
                throw JuggleExceptionUser(message)
            }
        }

        // Build the per-juggler avatar map from a spec such as "default",
        // "female", or "default,female". Multiple ids are assigned cyclically by
        // juggler number (jugglers 1,3,5 -> first id, 2,4,6 -> second, ...), so a
        // passing pattern can mix figures. A pure-default spec yields an empty
        // map, so every juggler falls back to the renderer's default figure and
        // existing patterns are unchanged.
        @Throws(JuggleExceptionUser::class)
        fun avatarMap(spec: String, numberOfJugglers: Int): Map<Int, Avatar> {
            val ids = spec.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (ids.isEmpty() || ids.all { it == DEFAULT }) return emptyMap()
            val instances = ids.distinct().associateWith { newAvatar(it) }
            return (1..numberOfJugglers).associateWith { instances.getValue(ids[(it - 1) % ids.size]) }
        }
    }
}
