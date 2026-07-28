//
// DrawObject2D.kt
//
// A single 2D drawable element (prop, line, or convex polygon) produced by the
// Renderer for one frame, plus the painter's-algorithm depth test used to sort
// elements back-to-front.
//
// Types:
// Type.PROP: Single coordinate point (center of prop).
// Type.POLY: Convex polygon of N coordinates (head, torso, etc.).
// Type.LINE: Line segment of 2 coordinates (arms, ground grid).
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.roundToInt

class DrawObject2D {
    enum class Type {
        PROP,
        POLY,
        LINE
    }

    var type: Type = Type.PROP
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
        type: Type,
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

        if (type == Type.LINE) {
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
            Type.PROP -> {
                // Props are drawn separately by Renderer
            }

            Type.POLY -> {
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

            Type.LINE -> {
                if (numPoints >= 2) {
                    val p1 = Offset(coords2D[0].x.toFloat(), coords2D[0].y.toFloat())
                    val p2 = Offset(coords2D[1].x.toFloat(), coords2D[1].y.toFloat())
                    ctx.segment(p1, p2)
                }
            }
        }
    }

    fun isCovering(obj: DrawObject2D): Boolean {
        // Bounding box overlap check; (x,y) = (0,0) is top left edge
        if (bbRight <= obj.bbLeft || bbLeft >= obj.bbRight ||
            bbBottom <= obj.bbTop || bbTop >= obj.bbBottom
        ) {
            return false
        }

        return when (type) {
            Type.PROP -> when (obj.type) {
                Type.PROP -> (coords2D[0].z < obj.coords2D[0].z)
                Type.POLY -> {
                    obj.polyPlaneNormal(tempv)
                    if (tempv.z == 0.0) {
                        false
                    } else {
                        val base = obj.coords2D[0]
                        val z = base.z - (tempv.x * (coords2D[0].x - base.x) +
                                tempv.y * (coords2D[0].y - base.y)) / tempv.z
                        (coords2D[0].z < z)
                    }
                }
                Type.LINE -> (isPropCoveringLine(this, obj) == 1)
            }

            Type.POLY -> when (obj.type) {
                Type.PROP -> {
                    polyPlaneNormal(tempv)
                    if (tempv.z == 0.0) {
                        false
                    } else {
                        val base = coords2D[0]
                        val z = base.z - (tempv.x * (obj.coords2D[0].x - base.x) +
                                tempv.y * (obj.coords2D[0].y - base.y)) / tempv.z
                        (z < obj.coords2D[0].z)
                    }
                }
                Type.POLY -> {
                    var sumA = 0.0
                    for (i in 0..<numPoints) sumA += coords2D[i].z
                    val meanA = sumA / numPoints

                    var sumB = 0.0
                    for (i in 0..<obj.numPoints) sumB += obj.coords2D[i].z
                    val meanB = sumB / obj.numPoints

                    (meanA < meanB)
                }
                Type.LINE -> (isPolyCoveringLine(this, obj) == 1)
            }

            Type.LINE -> when (obj.type) {
                Type.PROP -> (isPropCoveringLine(obj, this) == -1)
                Type.POLY -> (isPolyCoveringLine(obj, this) == -1)
                Type.LINE -> false
            }
        }
    }

    private fun polyPlaneNormal(result: JlVector) {
        if (numPoints < 3) {
            result.x = 0.0; result.y = 0.0; result.z = 1.0
            return
        }
        vectorProduct(coords2D[0], coords2D[1], coords2D[2], result)
    }

    private fun isPropCoveringLine(prop: DrawObject2D, line: DrawObject2D): Int {
        if (line.numPoints < 2 || prop.numPoints < 1) return 0

        val px = prop.coords2D[0].x
        val py = prop.coords2D[0].y
        val pz = prop.coords2D[0].z

        val x1 = line.coords2D[0].x
        val y1 = line.coords2D[0].y
        val z1 = line.coords2D[0].z

        val x2 = line.coords2D[1].x
        val y2 = line.coords2D[1].y
        val z2 = line.coords2D[1].z

        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy

        // Find parameter t of closest point on 2D line segment
        val t = if (lenSq == 0.0) 0.0 else (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0.0, 1.0)

        val closestX = x1 + t * dx
        val closestY = y1 + t * dy

        // Check if closest point falls inside prop bounding box
        if (closestX < prop.bbLeft || closestX > prop.bbRight ||
            closestY < prop.bbTop || closestY > prop.bbBottom
        ) {
            return 0
        }

        // Interpolated line depth at t
        val lineZ = z1 + t * (z2 - z1)

        return when {
            pz < lineZ -> 1   // prop closer (smaller z) -> prop covers line
            pz > lineZ -> -1   // line closer (smaller z) -> line covers prop
            else -> 0
        }
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
        return if (margin < 0.0) -1 else 1
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
