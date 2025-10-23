//
// Coordinate.kt
//
// Defines an (x, y, z) spatial coordinate.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Coordinate @JvmOverloads constructor(
    @JvmField var x: Double = 0.0,
    @JvmField var y: Double = 0.0,
    @JvmField var z: Double = 0.0
) {
    fun setCoordinate(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun get(index: Int): Double {
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IndexOutOfBoundsException("Invalid Coordinate index: $index")
        }
    }

    fun set(index: Int, value: Double) {
        when (index) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
            else -> throw IndexOutOfBoundsException("Invalid Coordinate index: $index")
        }
    }

    val isValid: Boolean
        get() = !(x.isNaN()
            || x.isInfinite()
            || y.isNaN()
            || y.isInfinite()
            || z.isNaN()
            || z.isInfinite())

    override fun toString(): String {
        return ("("
            + toStringRounded(x, 5)
            + ", "
            + toStringRounded(y, 5)
            + ", "
            + toStringRounded(z, 5)
            + ")")
    }

    companion object {
        fun max(coord1: Coordinate?, coord2: Coordinate?): Coordinate? {
            if (coord1 == null) {
                return coord2
            }
            if (coord2 == null) {
                return coord1
            }
            return Coordinate(
                max(coord1.x, coord2.x), max(coord1.y, coord2.y), max(coord1.z, coord2.z)
            )
        }

        fun min(coord1: Coordinate?, coord2: Coordinate?): Coordinate? {
            if (coord1 == null) {
                return coord2
            }
            if (coord2 == null) {
                return coord1
            }
            return Coordinate(
                min(coord1.x, coord2.x), min(coord1.y, coord2.y), min(coord1.z, coord2.z)
            )
        }

        fun add(coord1: Coordinate, coord2: Coordinate): Coordinate {
            return Coordinate(coord1.x + coord2.x, coord1.y + coord2.y, coord1.z + coord2.z)
        }

        fun sub(coord1: Coordinate, coord2: Coordinate): Coordinate {
            return Coordinate(coord1.x - coord2.x, coord1.y - coord2.y, coord1.z - coord2.z)
        }

        fun truncate(coord: Coordinate, epsilon: Double): Coordinate {
            val result: Coordinate = coord.copy()

            if (abs(result.x) < epsilon) {
                result.x = 0.0
            }
            if (abs(result.y) < epsilon) {
                result.y = 0.0
            }
            if (abs(result.z) < epsilon) {
                result.z = 0.0
            }
            return result
        }

        fun distance(coord1: Coordinate, coord2: Coordinate): Double {
            val dc: Coordinate = sub(coord1, coord2)
            return sqrt(dc.x * dc.x + dc.y * dc.y + dc.z * dc.z)
        }
    }
}
