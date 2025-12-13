//
// JMLPosition.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.getStringResource

data class JMLPosition(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val t: Double = 0.0,
    val angle: Double = 0.0,
    val juggler: Int = 0,
): Comparable<JMLPosition> {
    val coordinate: Coordinate
        get() = Coordinate(x, y, z)

    fun writeJML(wr: Appendable) {
        val c = this.coordinate
        wr.append(
            ("<position x=\""
                    + jlToStringRounded(c.x, 4)
                    + "\" y=\""
                    + jlToStringRounded(c.y, 4)
                    + "\" z=\""
                    + jlToStringRounded(c.z, 4)
                    + "\" t=\""
                    + jlToStringRounded(this.t, 4)
                    + "\" angle=\""
                    + jlToStringRounded(this.angle, 4)
                    + "\" juggler=\""
                    + this.juggler
                    + "\"/>\n")
        )
    }

    override fun toString(): String {
        val sb = StringBuilder()
        writeJML(sb)
        return sb.toString()
    }

    val hashCode: Int
        get() = toString().hashCode()

    override fun compareTo(other: JMLPosition): Int {
        if (t != other.t) {
            return t.compareTo(other.t)
        }
        if (juggler != other.juggler) {
            return juggler.compareTo(other.juggler)
        }
        // shouldn't get here; shouldn't have multiple positions for the same
        // juggler at a single time
        return x.compareTo(other.x)
    }

    // for doubly-linked event list during layout
    var previous: JMLPosition? = null
    var next: JMLPosition? = null

    companion object {
        @Suppress("unused")
        @Throws(JuggleExceptionUser::class)
        fun fromJMLNode(current: JMLNode, version: String?): JMLPosition {
            var tempx = 0.0
            var tempy = 0.0
            var tempz = 0.0
            var tempt = 0.0
            var tempangle = 0.0
            var jugglerstr = "1"

            try {
                for ((name, value) in current.attributes.entries) {
                    if (name.equals("x", ignoreCase = true)) {
                        tempx = jlParseFiniteDouble(value)
                    } else if (name.equals("y", ignoreCase = true)) {
                        tempy = jlParseFiniteDouble(value)
                    } else if (name.equals("z", ignoreCase = true)) {
                        tempz = jlParseFiniteDouble(value)
                    } else if (name.equals("t", ignoreCase = true)) {
                        tempt = jlParseFiniteDouble(value)
                    } else if (name.equals("angle", ignoreCase = true)) {
                        tempangle = jlParseFiniteDouble(value)
                    } else if (name.equals("juggler", ignoreCase = true)) {
                        jugglerstr = value
                    }
                }
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_position_coordinate)
                throw JuggleExceptionUser(message)
            }
            val tempjuggler = try {
                jugglerstr.toInt()
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "juggler")
                throw JuggleExceptionUser(message)
            }

            if (current.numberOfChildren != 0) {
                val message = getStringResource(Res.string.error_position_subtag)
                throw JuggleExceptionUser(message)
            }

            return JMLPosition(
                x = tempx,
                y = tempy,
                z = tempz,
                t = tempt,
                angle = tempangle,
                juggler = tempjuggler
            )
        }
    }
}
