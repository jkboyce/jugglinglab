//
// JlVector.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.renderer

import jugglinglab.util.Coordinate
import kotlin.math.sqrt

class JlVector {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0

    constructor()

    constructor(xpos: Double, ypos: Double, zpos: Double) {
        x = xpos
        y = ypos
        z = zpos
    }

    val length: Double
        get() = sqrt(x * x + y * y + z * z)

    fun transform(m: JlMatrix): JlVector {
        val newx = x * m.m00 + y * m.m01 + z * m.m02 + m.m03
        val newy = x * m.m10 + y * m.m11 + z * m.m12 + m.m13
        val newz = x * m.m20 + y * m.m21 + z * m.m22 + m.m23
        return JlVector(newx, newy, newz)
    }

    companion object {
        fun add(a: JlVector, b: JlVector): JlVector {
            return JlVector(a.x + b.x, a.y + b.y, a.z + b.z)
        }

        fun sub(a: JlVector, b: JlVector): JlVector {
            return JlVector(a.x - b.x, a.y - b.y, a.z - b.z)
        }

        fun scale(f: Double, a: JlVector): JlVector {
            return JlVector(f * a.x, f * a.y, f * a.z)
        }

        fun fromCoordinate(c: Coordinate, result: JlVector): JlVector {
            result.x = c.x
            result.y = c.z
            result.z = c.y
            return result
        }
    }
}
