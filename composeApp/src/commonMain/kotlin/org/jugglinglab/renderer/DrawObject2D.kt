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

import org.jugglinglab.util.Coordinate
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
    var sequenceNumber: Int = 0
    var label: String? = null

    // Global 3D coordinates (set before projection)
    val coords3D: MutableList<JlVector> = mutableListOf()

    // Projected screen coordinates (x, y = pixels; z = depth for sorting)
    val coords2D: MutableList<JlVector> = mutableListOf()

    // Cached plane normal, used for polygons
    val planeNormal: JlVector = JlVector()

    // Bounding box in screen coordinates
    var bbLeft: Float = 0f
    var bbTop: Float = 0f
    var bbRight: Float = 0f
    var bbBottom: Float = 0f

    // For coverage calculations
    var covering: MutableList<DrawObject2D> = mutableListOf()
    var drawn: Boolean = false

    // Prepare 3D coordinate buffers without allocating memory if capacity exists
    fun prepare3DCoordinates(
        type: Type,
        number: Int,
        pointsCount: Int,
        isClosed: Boolean = true
    ) {
        this.type = type
        this.number = number
        this.isClosed = isClosed
        this.numPoints = pointsCount
        ensureCapacity(numPoints)
    }

    fun ensureCapacity(points: Int) {
        while (coords3D.size < points) {
            coords3D.add(JlVector())
            coords2D.add(JlVector())
        }
    }

    // Set 3D global coordinate for 1 point from a Coordinate (e.g. a prop)
    fun set3DCoordinates(
        type: Type,
        number: Int,
        coord: Coordinate,
        isClosed: Boolean = true
    ) {
        prepare3DCoordinates(type, number, 1, isClosed)
        JlVector.fromCoordinate(coord, coords3D[0])
    }

    // Set 3D global coordinates for 2 points (e.g. a line segment)
    fun set3DCoordinates(
        type: Type,
        number: Int,
        p1: JlVector,
        p2: JlVector,
        isClosed: Boolean = true
    ) {
        prepare3DCoordinates(type, number, 2, isClosed)
        coords3D[0].set(p1)
        coords3D[1].set(p2)
    }

    // Set 3D global coordinates for arbitrary list of points; used only for
    // testing
    fun set3DCoordinates(
        type: Type,
        number: Int,
        points: List<JlVector>,
        isClosed: Boolean = true
    ) {
        prepare3DCoordinates(type, number, points.size, isClosed)
        for (i in 0..<numPoints) {
            coords3D[i].set(points[i])
        }
    }

    // Find bounding box in screen coordinates
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

        if (type == Type.POLY) {
            // unrelated to bounding box but this is a convenient place
            // to recalculate when coords change
            polyPlaneNormal(planeNormal)
        }
    }

    // Determine covering relationship between `this` and `obj`. Return values:
    //  1: `this` is in front of `obj`
    // -1: `obj` is in front of `this`
    //  0: Neither covers the other
    //
    // This function is skew-symmetric, i.e.:
    // a.compareCovering(b) == -b.compareCovering(a)

    fun compareCovering(obj: DrawObject2D): Int {
        // Bounding box overlap check; (x,y) = (0,0) is top left edge
        if (bbRight <= obj.bbLeft || bbLeft >= obj.bbRight ||
            bbBottom <= obj.bbTop || bbTop >= obj.bbBottom
        ) {
            return 0
        }

        return when (type) {
            Type.PROP -> when (obj.type) {
                Type.PROP -> isPropCoveringProp(this, obj)
                Type.POLY -> isPropCoveringPoly(this, obj)
                Type.LINE -> isPropCoveringLine(this, obj)
            }

            Type.POLY -> when (obj.type) {
                Type.PROP -> -isPropCoveringPoly(obj, this)
                Type.POLY -> isPolyCoveringPoly(this, obj)
                Type.LINE -> isPolyCoveringLine(this, obj)
            }

            Type.LINE -> when (obj.type) {
                Type.PROP -> -isPropCoveringLine(obj, this)
                Type.POLY -> -isPolyCoveringLine(obj, this)
                Type.LINE -> 0
            }
        }
    }

    fun isCovering(obj: DrawObject2D): Boolean = compareCovering(obj) > 0

    private fun polyDepthAtPoint(x: Double, y: Double): Double {
        val normal = planeNormal
        if (kotlin.math.abs(normal.z) < 1e-4) {
            return coords2D[0].z
        }
        val base = coords2D[0]
        return base.z - (normal.x * (x - base.x) + normal.y * (y - base.y)) / normal.z
    }

    private fun polyPlaneNormal(result: JlVector) {
        if (numPoints < 3) {
            result.x = 0.0; result.y = 0.0; result.z = 1.0
            return
        }
        vectorProduct(coords2D[0], coords2D[1], coords2D[2], result)
    }

    companion object {
        private const val SILHOUETTE_SAMPLES = 16
        private const val EPSILON = 0.01

        // Helpers for coverage calculations

        private fun isPropCoveringProp(prop1: DrawObject2D, prop2: DrawObject2D): Int {
            return when {
                prop1.coords2D[0].z < prop2.coords2D[0].z -> 1
                prop1.coords2D[0].z > prop2.coords2D[0].z -> -1
                else -> 0
            }
        }

        private fun isPropCoveringPoly(prop: DrawObject2D, poly: DrawObject2D): Int {
            val normal = poly.planeNormal
            if (normal.z == 0.0) return 0
            val base = poly.coords2D[0]
            val z = base.z - (normal.x * (prop.coords2D[0].x - base.x) +
                    normal.y * (prop.coords2D[0].y - base.y)) / normal.z
            return when {
                prop.coords2D[0].z < z -> 1
                prop.coords2D[0].z > z -> -1
                else -> 0
            }
        }

        private fun isPropCoveringLine(prop: DrawObject2D, line: DrawObject2D): Int {
            if (line.numPoints < 2 || prop.numPoints < 1) return 0

            val px = prop.coords2D[0].x
            val py = prop.coords2D[0].y
            val pz = prop.coords2D[0].z

            val x1 = line.coords2D[0].x
            val y1 = line.coords2D[0].y

            val dx = line.coords2D[1].x - x1
            val dy = line.coords2D[1].y - y1
            val lenSq = dx * dx + dy * dy

            // Find parameter t of closest point on 2D line segment
            val t = if (lenSq == 0.0) {
                0.0
            } else {
                (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0.0, 1.0)
            }

            val closestX = x1 + t * dx
            val closestY = y1 + t * dy

            // Check if closest point falls inside prop bounding box
            if (closestX < prop.bbLeft || closestX > prop.bbRight ||
                closestY < prop.bbTop || closestY > prop.bbBottom
            ) {
                return 0
            }

            // Interpolated line depth at t
            val z1 = line.coords2D[0].z
            val dz = line.coords2D[1].z - z1
            val lineZ = z1 + t * dz

            return when {
                pz < lineZ -> 1  // prop closer (smaller z) -> prop covers line
                pz > lineZ -> -1  // line closer (smaller z) -> line covers prop
                else -> 0
            }
        }

        private fun isPolyCoveringPoly(poly1: DrawObject2D, poly2: DrawObject2D): Int {
            for (i in 0..<poly1.numPoints) {
                val pt = poly1.coords2D[i]
                if (isPolyContainingPoint(poly2, pt.x, pt.y)) {
                    val depthObj = poly2.polyDepthAtPoint(pt.x, pt.y)
                    val cmp = when {
                        pt.z < depthObj - EPSILON -> 1
                        pt.z > depthObj + EPSILON -> -1
                        else -> 0
                    }
                    if (cmp != 0) return cmp
                }
            }

            for (i in 0..<poly2.numPoints) {
                val pt = poly2.coords2D[i]
                if (isPolyContainingPoint(poly1, pt.x, pt.y)) {
                    val depthThis = poly1.polyDepthAtPoint(pt.x, pt.y)
                    val cmp = when {
                        depthThis < pt.z - EPSILON -> 1
                        depthThis > pt.z + EPSILON -> -1
                        else -> 0
                    }
                    if (cmp != 0) return cmp
                }
            }

            return 0
        }

        private fun isPolyCoveringLine(poly: DrawObject2D, line: DrawObject2D): Int {
            if (line.numPoints < 2) return 0
            val normal = poly.planeNormal
            if (normal.z == 0.0) return 0
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
                if (!isPolyContainingPoint(poly, x, y)) continue
                val zb = base.z - (normal.x * (x - base.x) + normal.y * (y - base.y)) / normal.z
                margin += (z0 + dz * t) - zb
                insideCount++
            }
            if (insideCount == 0) return 0
            // when depths are equal, bias in favor of poly covers line;
            // otherwise we can get flickering artifacts due to numerical
            // inaccuracy in the depth calculations
            return if (margin > -EPSILON) 1 else -1
        }

        private fun isPolyContainingPoint(poly: DrawObject2D, x: Double, y: Double): Boolean {
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

        private fun vectorProduct(
            v1: JlVector,
            v2: JlVector,
            v3: JlVector,
            result: JlVector
        ): JlVector {
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

//------------------------------------------------------------------------------
// Object pool to reuse DrawObject2D objects
//------------------------------------------------------------------------------

class DrawObjectPool(
    val objects: MutableList<DrawObject2D> = mutableListOf()
) : Iterable<DrawObject2D> {
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

    override fun iterator(): Iterator<DrawObject2D> = object : Iterator<DrawObject2D> {
        private var cur = 0
        override fun hasNext(): Boolean = cur < activeCount
        override fun next(): DrawObject2D {
            if (!hasNext()) throw NoSuchElementException("Index $cur out of active bounds ($activeCount)")
            return objects[cur++]
        }
    }
}
