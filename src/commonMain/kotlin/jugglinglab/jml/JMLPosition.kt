//
// JMLPosition.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.getStringResource

class JMLPosition {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0
    var t: Double = 0.0
    var angle: Double = 0.0
    var juggler: Int = 0

    var previous: JMLPosition? = null
    var next: JMLPosition? = null // for doubly-linked event list

    var coordinate: Coordinate
        get() = Coordinate(x, y, z)
        set(c) {
            x = c.x
            y = c.y
            z = c.z
        }

    fun setJuggler(strjuggler: String) {
        juggler = strjuggler.toInt()
    }

    val hashCode: Int
        get() = toString().hashCode()

    //--------------------------------------------------------------------------
    //  Reader/writer methods
    //--------------------------------------------------------------------------

    @Suppress("unused")
    @Throws(JuggleExceptionUser::class)
    fun readJML(current: JMLNode, jmlvers: String?) {
        val at = current.attributes
        var tempx = 0.0
        var tempy = 0.0
        var tempz = 0.0
        var tempt = 0.0
        var tempangle = 0.0
        var jugglerstr = "1"

        try {
            for (i in 0..<at.numberOfAttributes) {
                // System.out.println("att. "+i+" = "+at.getAttributeValue(i));
                if (at.getAttributeName(i).equals("x", ignoreCase = true)) {
                    tempx = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("y", ignoreCase = true)) {
                    tempy = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("z", ignoreCase = true)) {
                    tempz = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("t", ignoreCase = true)) {
                    tempt = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("angle", ignoreCase = true)) {
                    tempangle = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("juggler", ignoreCase = true)) {
                    jugglerstr = at.getAttributeValue(i)
                }
            }
        } catch (_: NumberFormatException) {
            val message = getStringResource(Res.string.error_position_coordinate)
            throw JuggleExceptionUser(message)
        }

        coordinate = Coordinate(tempx, tempy, tempz)
        t = tempt
        angle = tempangle
        setJuggler(jugglerstr)

        if (current.numberOfChildren != 0) {
            val message = getStringResource(Res.string.error_position_subtag)
            throw JuggleExceptionUser(message)
        }
    }

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
}
