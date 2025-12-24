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
    val juggler: Int = 1,
) : Comparable<JMLPosition> {
    val coordinate: Coordinate
        get() = Coordinate(x, y, z)

    fun writeJML(wr: Appendable) {
        wr.append("<position x=\"${jlToStringRounded(x, 4)}\"")
        wr.append(" y=\"${jlToStringRounded(y, 4)}\"")
        wr.append(" z=\"${jlToStringRounded(z, 4)}\"")
        wr.append(" t=\"${jlToStringRounded(t, 4)}\"")
        wr.append(" angle=\"${jlToStringRounded(angle, 4)}\"")
        wr.append(" juggler=\"${juggler}\"/>\n")
    }

    private val cachedToString: String by lazy {
        val sb = StringBuilder()
        writeJML(sb)
        sb.toString()
    }

    override fun toString(): String = cachedToString

    private val cachedHashCode: Int by lazy {
        toString().hashCode()
    }

    fun jlHashCode(): Int = cachedHashCode

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

    //--------------------------------------------------------------------------
    // Constructing JMLPositions
    //--------------------------------------------------------------------------

    companion object {
        @Suppress("unused")
        @Throws(JuggleExceptionUser::class)
        fun fromJMLNode(
            current: JMLNode,
            loadingJmlVersion: String = JMLDefs.CURRENT_JML_VERSION
        ): JMLPosition {
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

            if (current.children.isNotEmpty()) {
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
