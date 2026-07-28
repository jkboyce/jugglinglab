//
// DrawObject2D.kt
//
// A single 2D drawable element (prop, line, or convex polygon) produced by the
// Renderer for one frame, plus the painter's-algorithm depth test used to
// sort elements back-to-front.
//
// Types:
// TYPE_PROP (1): Single coordinate point (center of prop).
// TYPE_POLY (2): Convex polygon of N coordinates (head, torso, skirt, etc.).
// TYPE_LINE (3): Line segment of 2 coordinates (arms, ground grid).
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.roundToInt

class DrawObject2D {
    var type: Int = 0
    var number: Int = 0 // path or juggler number (ground lines use 0)
    var isClosed: Boolean = true
    var numPoints: Int = 0

    // Global 3D coordinates (set before projection)
    val coords3D: MutableList<JlVector> = mutableListOf()

    // Projected screen coordinates (x, y = pixels; z = depth for sorting)
    val coords2D: MutableList<JlVector> = mutableListOf()

    var bbLeft: Float = 0f
    var bbTop: Float = 0f
    var bbRight: Float = 0f
    var bbBottom: Float = 0f
    var covering: MutableList<DrawObject2D> = mutableListOf()
    var drawn: Boolean = false

    private val tempv: JlVector = JlVector()
    private val fillPath: Path = Path()
    private val strokePath: Path = Path()

    fun ensureCapacity(points: Int) {
        while (coords3D.size < points) {
            coords3D.add(JlVector())
            coords2D.add(JlVector())
        }
    }

    // Set 3D global coordinates without allocating memory if capacity exists
    fun set3DCoordinates(
        type: Int,
        number: Int,
        points: List<JlVector>,
        isClosed: Boolean = true
    ) {
        this.type = type
        this.number = number
        this.isClosed = isClosed
        this.numPoints = points.size

        ensureCapacity(numPoints)
        for (i in 0..<numPoints) {
            coords3D[i].set(points[i])
        }
    }

    // Screen bounding box over coords2D
    fun computeBounds() {
        if (numPoints == 0) return

        var xmin = coords2D[0].x.roundToInt()
        var xmax = xmin
        var ymin = coords2D[0].y.roundToInt()
        var ymax = ymin

        for (k in 1..<numPoints) {
            val x = coords2D[k].x.roundToInt()
            val y = coords2D[k].y.roundToInt()
            if (x < xmin) xmin = x
            if (x > xmax) xmax = x
            if (y < ymin) ymin = y
            if (y > ymax) ymax = y
        }

        if (type == TYPE_LINE) {
            val left = xmin.toFloat()
            val top = ymin.toFloat()
            bbLeft = left
            bbTop = top
            bbRight = max(left + 1f, xmax.toFloat())
            bbBottom = max(top + 1f, ymax.toFloat())
        } else {
            bbLeft = (xmin + 1).toFloat()
            bbTop = (ymin + 1).toFloat()
            bbRight = xmax.toFloat()
            bbBottom = ymax.toFloat()
        }
    }

    fun draw(ctx: DrawObjectContext) {
        when (type) {
            TYPE_POLY -> {
                if (numPoints < 3) return

                fillPath.rewind()
                fillPath.moveTo(coords2D[0].x.toFloat(), coords2D[0].y.toFloat())
                for (i in 1..<numPoints) {
                    fillPath.lineTo(coords2D[i].x.toFloat(), coords2D[i].y.toFloat())
                }
                fillPath.close()
                ctx.fill(fillPath)

                if (isClosed) {
                    ctx.stroke(fillPath)
                } else {
                    strokePath.rewind()
                    strokePath.moveTo(coords2D[0].x.toFloat(), coords2D[0].y.toFloat())
                    for (i in 1..<numPoints) {
                        strokePath.lineTo(coords2D[i].x.toFloat(), coords2D[i].y.toFloat())
                    }
                    ctx.stroke(strokePath)
                }
            }

            TYPE_LINE -> {
                if (numPoints >= 2) {
                    val p1 = Offset(coords2D[0].x.toFloat(), coords2D[0].y.toFloat())
                    val p2 = Offset(coords2D[1].x.toFloat(), coords2D[1].y.toFloat())
                    ctx.segment(p1, p2)
                }
            }
        }
    }

    fun isCovering(obj: DrawObject2D): Boolean {
        // Bounding box overlap check
        if (bbRight <= obj.bbLeft || bbLeft >= obj.bbRight ||
            bbBottom <= obj.bbTop || bbTop >= obj.bbBottom
        ) {
            return false
        }

        when (type) {
            TYPE_PROP -> when (obj.type) {
                TYPE_PROP -> return (coords2D[0].z < obj.coords2D[0].z)
                TYPE_POLY -> {
                    obj.polyPlaneNormal(tempv)
                    if (tempv.z == 0.0) return false
                    val base = obj.coords2D[0]
                    val z = base.z - (tempv.x * (coords2D[0].x - base.x) + tempv.y * (coords2D[0].y - base.y)) / tempv.z
                    return (coords2D[0].z < z)
                }
                TYPE_LINE -> return (isPolyCoveringLine(this, obj) == 1)
            }

            TYPE_POLY -> when (obj.type) {
                TYPE_PROP -> {
                    polyPlaneNormal(tempv)
                    if (tempv.z == 0.0) return false
                    val base = coords2D[0]
                    val z = base.z - (tempv.x * (obj.coords2D[0].x - base.x) + tempv.y * (obj.coords2D[0].y - base.y)) / tempv.z
                    return (z < obj.coords2D[0].z)
                }
                TYPE_POLY -> {
                    var sumA = 0.0
                    for (i in 0..<numPoints) sumA += coords2D[i].z
                    val meanA = sumA / numPoints

                    var sumB = 0.0
                    for (i in 0..<obj.numPoints) sumB += obj.coords2D[i].z
                    val meanB = sumB / obj.numPoints

                    return (meanA < meanB)
                }
                TYPE_LINE -> return (isPolyCoveringLine(this, obj) == 1)
            }

            TYPE_LINE -> when (obj.type) {
                TYPE_PROP, TYPE_POLY -> return (isPolyCoveringLine(obj, this) == -1)
                TYPE_LINE -> return false
            }
        }
        return false
    }

    private fun polyPlaneNormal(result: JlVector) {
        if (numPoints < 3) {
            result.x = 0.0; result.y = 0.0; result.z = 1.0
            return
        }
        vectorProduct(coords2D[0], coords2D[1], coords2D[2], result)
    }

    private fun isPolyCoveringLine(poly: DrawObject2D, line: DrawObject2D): Int {
        poly.polyPlaneNormal(tempv)
        if (tempv.z == 0.0) return 0
        val base = poly.coords2D[0]
        val x0 = line.coords2D[0].x
        val y0 = line.coords2D[0].y
        val z0 = line.coords2D[0].z
        val dx = line.coords2D[1].x - x0
        val dy = line.coords2D[1].y - y0
        val dz = line.coords2D[1].z - z0

        var insideCount = 0
        var margin = 0.0
        for (i in 0..SILHOUETTE_SAMPLES) {
            val t = i.toDouble() / SILHOUETTE_SAMPLES
            val x = x0 + dx * t
            val y = y0 + dy * t
            if (!insidePoly(poly, x, y)) continue
            val zb = base.z - (tempv.x * (x - base.x) + tempv.y * (y - base.y)) / tempv.z
            margin += (z0 + dz * t) - zb
            insideCount++
        }
        if (insideCount == 0) return 0
        return if (margin / insideCount < SLOP) -1 else 1
    }

    private fun insidePoly(poly: DrawObject2D, x: Double, y: Double): Boolean {
        var odd = false
        var j = poly.numPoints - 1
        for (i in 0..<poly.numPoints) {
            val a = poly.coords2D[i]
            val b = poly.coords2D[j]
            if ((a.y > y) != (b.y > y) &&
                x < a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
            ) {
                odd = !odd
            }
            j = i
        }
        return odd
    }

    companion object {
        const val TYPE_PROP: Int = 1
        const val TYPE_POLY: Int = 2
        const val TYPE_LINE: Int = 3

        private const val SLOP: Double = 3.0
        private const val SILHOUETTE_SAMPLES = 16

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
    }
}

// Object pool to reuse DrawObject2D objects.

class DrawObjectPool(val objects: MutableList<DrawObject2D> = mutableListOf()) {
    private var index = 0

    fun reset() {
        index = 0
    }

    fun next(): DrawObject2D {
        if (index >= objects.size) {
            objects.add(DrawObject2D())
        }
        return objects[index++]
    }

    val activeCount: Int
        get() = index
}

// Helper class for painting.

class DrawObjectContext(
    val fill: (Path) -> Unit,
    val stroke: (Path) -> Unit,
    val segment: (Offset, Offset) -> Unit
)
