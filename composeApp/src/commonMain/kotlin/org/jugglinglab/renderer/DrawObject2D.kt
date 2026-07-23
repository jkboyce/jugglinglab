//
// DrawObject2D.kt
//
// A single 2D drawable element (prop, juggler body, or line) produced by the
// Renderer for one frame, plus the painter's-algorithm "covering" test used to
// sort the elements back-to-front.
//
// This class is generic: it holds a growable list of projected points, a screen
// bounding box computed from whichever point indices the avatar declares, and
// reusable scratch paths — but it knows nothing about any particular avatar.
//
// Occlusion contract for TYPE_BODY objects: the depth plane is the triangle
// LEFT_SHOULDER / RIGHT_SHOULDER / RIGHT_WAIST (Avatar's shared skeleton), and
// body-vs-body ordering compares the summed depth of the shoulders and waist.
// The screen bounding box is computed from the avatar's declared boundsPoints.
// An avatar may also declare silhouettePoints; lines are then occluded by the
// drawn outline itself instead of the bounding box.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DrawObject2D {
    var type: Int = 0
    var number: Int = 0 // path or juggler number (ground lines use 0)
    var avatar: Avatar? = null // the avatar that draws a TYPE_BODY element

    // Projected screen coordinates (x, y = pixels; z = depth for sorting).
    // coord[i] is always the projection of the avatar's 3D point i. Grown on
    // demand: props use 1 slot, lines 2, bodies avatar.pointCount.
    var coord: MutableList<JlVector> = MutableList(2) { JlVector() }

    var bbLeft: Float = 0f
    var bbTop: Float = 0f
    var bbRight: Float = 0f
    var bbBottom: Float = 0f
    var covering: MutableList<DrawObject2D> = mutableListOf()
    var drawn: Boolean = false
    var tempv: JlVector = JlVector()

    // Reusable Path objects for whatever the avatar draws (torso, head, hair,
    // ...). Slots are allocated lazily and reused across frames; see the
    // scratch-path constants in Avatar for the shared slots.
    private val scratchPaths: MutableList<Path> = mutableListOf()

    fun ensureCapacity(points: Int) {
        while (coord.size < points) {
            coord.add(JlVector())
        }
    }

    fun scratchPath(i: Int): Path {
        while (scratchPaths.size <= i) {
            scratchPaths.add(Path())
        }
        return scratchPaths[i]
    }

    // Screen bounding box over the given point indices, with the same rounding
    // the classic renderer used for the juggler body.
    fun computeBounds(indices: List<Int>) {
        var xmin = coord[indices[0]].x.roundToInt()
        var xmax = xmin
        var ymin = coord[indices[0]].y.roundToInt()
        var ymax = ymin

        for (k in 1..<indices.size) {
            val x = coord[indices[k]].x.roundToInt()
            val y = coord[indices[k]].y.roundToInt()
            if (x < xmin) xmin = x
            if (x > xmax) xmax = x
            if (y < ymin) ymin = y
            if (y > ymax) ymax = y
        }
        bbLeft = (xmin + 1).toFloat()
        bbTop = (ymin + 1).toFloat()
        bbRight = xmax.toFloat()
        bbBottom = ymax.toFloat()
    }

    // Screen bounding box for a two-point line (arms, ground grid).
    fun computeLineBounds() {
        val x1 = coord[0].x.roundToInt().toFloat()
        val y1 = coord[0].y.roundToInt().toFloat()
        val x2 = coord[1].x.roundToInt().toFloat()
        val y2 = coord[1].y.roundToInt().toFloat()
        val left = min(x1, x2)
        val top = min(y1, y2)
        val right = max(x1, x2)
        val bottom = max(y1, y2)
        bbLeft = left
        bbTop = top
        bbRight = max(left + 1f, right)
        bbBottom = max(top + 1f, bottom)
    }

    fun isCovering(obj: DrawObject2D): Boolean {
        // Check for bounding box overlap
        if (bbRight <= obj.bbLeft || bbLeft >= obj.bbRight ||
            bbBottom <= obj.bbTop || bbTop >= obj.bbBottom
        ) {
            return false
        }

        when (type) {
            TYPE_PROP -> when (obj.type) {
                TYPE_PROP -> return (coord[0].z < obj.coord[0].z)
                TYPE_BODY -> {
                    obj.bodyPlaneNormal(tempv)
                    if (tempv.z == 0.0) return false
                    val base = obj.coord[PLANE_A]
                    val z = base.z -
                            (tempv.x * (coord[0].x - base.x) + tempv.y * (coord[0].y - base.y)) / tempv.z
                    return (coord[0].z < z)
                }

                TYPE_LINE -> return (isBoxCoveringLine(this, obj) == 1)
            }

            TYPE_BODY -> when (obj.type) {
                TYPE_PROP -> {
                    bodyPlaneNormal(tempv)
                    if (tempv.z == 0.0) return false
                    val base = coord[PLANE_A]
                    val z = base.z -
                            (tempv.x * (obj.coord[0].x - base.x) + tempv.y * (obj.coord[0].y - base.y)) / tempv.z
                    return (z < obj.coord[0].z)
                }

                TYPE_BODY -> {
                    var d = 0.0
                    for (i in TORSO_DEPTH_POINTS) d += (coord[i].z - obj.coord[i].z)
                    return (d < 0.0)
                }

                TYPE_LINE -> return (isBoxCoveringLine(this, obj) == 1)
            }

            TYPE_LINE -> when (obj.type) {
                TYPE_PROP, TYPE_BODY -> return (isBoxCoveringLine(obj, this) == -1)
                TYPE_LINE -> return false
            }
        }
        return false
    }

    // Normal of this body's occlusion plane (see contract in file header).
    private fun bodyPlaneNormal(result: JlVector) {
        vectorProduct(coord[PLANE_A], coord[PLANE_B], coord[PLANE_C], result)
    }

    private fun isBoxCoveringLine(box: DrawObject2D, line: DrawObject2D): Int {
        // A body that declares its drawn outline is compared as that shape.
        if (box.type == TYPE_BODY) {
            box.avatar?.silhouettePoints?.let { return isSilhouetteCoveringLine(box, it, line) }
        }

        // The reference point/plane of `box`: a body uses its occlusion plane,
        // a prop is treated as a screen-parallel plane through its center.
        val base: JlVector
        if (box.type == TYPE_BODY) {
            box.bodyPlaneNormal(tempv)
            base = box.coord[PLANE_A]
        } else {
            tempv.x = 0.0
            tempv.y = 0.0
            tempv.z = 1.0
            base = box.coord[0]
        }

        if (tempv.z == 0.0) return 0

        var endinbb = false
        for (i in 0..1) {
            val x = line.coord[i].x
            val y = line.coord[i].y
            if (contains(box, (x + 0.5).toFloat(), (y + 0.5).toFloat())) {
                val zb =
                    (base.z - (tempv.x * (x - base.x) + tempv.y * (y - base.y)) / tempv.z)
                if (line.coord[i].z < (zb - SLOP)) return -1
                endinbb = true
            }
        }
        if (endinbb) return 1

        var intersection = false
        // Check top/bottom edges of bbox
        for (i in 0..1) {
            val x = (if (i == 0) box.bbLeft else box.bbRight).toDouble()
            if (x < min(line.coord[0].x, line.coord[1].x) || x > max(line.coord[0].x, line.coord[1].x)) continue
            if (line.coord[1].x == line.coord[0].x) continue

            val y =
                line.coord[0].y + (line.coord[1].y - line.coord[0].y) * (x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x)
            if (y < box.bbTop || y > box.bbBottom) continue

            intersection = true
            val zb = (base.z - (tempv.x * (x - base.x) + tempv.y * (y - base.y)) / tempv.z)
            val zl =
                (line.coord[0].z + (line.coord[1].z - line.coord[0].z) * (x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x))
            if (zl < (zb - SLOP)) return -1
        }

        // Check left/right edges of bbox
        for (i in 0..1) {
            val y = (if (i == 0) box.bbTop else box.bbBottom).toDouble()
            if (y < min(line.coord[0].y, line.coord[1].y) || y > max(line.coord[0].y, line.coord[1].y)) continue
            if (line.coord[1].y == line.coord[0].y) continue

            val x =
                line.coord[0].x + (line.coord[1].x - line.coord[0].x) * (y - line.coord[0].y) / (line.coord[1].y - line.coord[0].y)
            if (x < box.bbLeft || x > box.bbRight) continue

            intersection = true
            val zb = (base.z - (tempv.x * (x - base.x) + tempv.y * (y - base.y)) / tempv.z)
            val zl =
                (line.coord[0].z + (line.coord[1].z - line.coord[0].z) * (x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x))
            if (zl < (zb - SLOP)) return -1
        }

        return if (intersection) 1 else 0
    }

    // Verdict for a body with a declared outline: the part of the line that
    // overlaps the drawn shape decides, by its mean depth against the body
    // plane. Ties go to "in front" (arms attach to the front of the body).
    private fun isSilhouetteCoveringLine(
        box: DrawObject2D, outline: List<Int>, line: DrawObject2D
    ): Int {
        box.bodyPlaneNormal(tempv)
        if (tempv.z == 0.0) return 0
        val base = box.coord[PLANE_A]
        val x0 = line.coord[0].x
        val y0 = line.coord[0].y
        val z0 = line.coord[0].z
        val dx = line.coord[1].x - x0
        val dy = line.coord[1].y - y0
        val dz = line.coord[1].z - z0

        var inside = 0
        var margin = 0.0
        for (i in 0..SILHOUETTE_SAMPLES) {
            val t = i.toDouble() / SILHOUETTE_SAMPLES
            val x = x0 + dx * t
            val y = y0 + dy * t
            if (!insideOutline(box, outline, x, y)) continue
            val zb = base.z - (tempv.x * (x - base.x) + tempv.y * (y - base.y)) / tempv.z
            margin += (z0 + dz * t) - zb
            inside++
        }
        if (inside == 0) return 0
        return if (margin / inside < SLOP) -1 else 1
    }

    // Even-odd test of a screen point against the projected outline.
    private fun insideOutline(box: DrawObject2D, outline: List<Int>, x: Double, y: Double): Boolean {
        var odd = false
        var j = outline.size - 1
        for (i in outline.indices) {
            val a = box.coord[outline[i]]
            val b = box.coord[outline[j]]
            if ((a.y > y) != (b.y > y) &&
                x < a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
            ) {
                odd = !odd
            }
            j = i
        }
        return odd
    }

    private fun contains(box: DrawObject2D, x: Float, y: Float): Boolean {
        return x >= box.bbLeft && x < box.bbRight && y >= box.bbTop && y < box.bbBottom
    }

    fun vectorProduct(v1: JlVector, v2: JlVector, v3: JlVector, result: JlVector): JlVector {
        val ax = v2.x - v1.x
        val ay = v2.y - v1.y
        val az = v2.z - v1.z
        val bx = v3.x - v1.x
        val by = v3.y - v1.y
        val bz = v3.z - v1.z
        result.x = ay * bz - by * az
        result.y = az * bx - bz * ax
        result.z = ax * by - bx * ay
        return result
    }

    companion object {
        const val TYPE_PROP: Int = 1
        const val TYPE_BODY: Int = 2
        const val TYPE_LINE: Int = 3

        // The occlusion-plane triangle of a TYPE_BODY object (see file header).
        private const val PLANE_A = Avatar.LEFT_SHOULDER
        private const val PLANE_B = Avatar.RIGHT_SHOULDER
        private const val PLANE_C = Avatar.RIGHT_WAIST

        // Points whose summed depth orders two bodies against each other.
        private val TORSO_DEPTH_POINTS = listOf(
            Avatar.LEFT_SHOULDER, Avatar.RIGHT_SHOULDER,
            Avatar.RIGHT_WAIST, Avatar.LEFT_WAIST
        )

        private const val SLOP: Double = 3.0

        // Samples along a line for the silhouette occlusion test.
        private const val SILHOUETTE_SAMPLES = 16
    }
}
