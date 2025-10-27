//
// JMLPosition.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.parseDouble
import jugglinglab.util.toStringRounded
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

class JMLPosition {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0
    @JvmField var t: Double = 0.0
    @JvmField var angle: Double = 0.0
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
                    tempx = parseDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("y", ignoreCase = true)) {
                    tempy = parseDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("z", ignoreCase = true)) {
                    tempz = parseDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("t", ignoreCase = true)) {
                    tempt = parseDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("angle", ignoreCase = true)) {
                    tempangle = parseDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("juggler", ignoreCase = true)) {
                    jugglerstr = at.getAttributeValue(i)
                }
            }
        } catch (_: NumberFormatException) {
            throw JuggleExceptionUser(errorstrings.getString("Error_position_coordinate"))
        }

        coordinate = Coordinate(tempx, tempy, tempz)
        t = tempt
        angle = tempangle
        setJuggler(jugglerstr)

        if (current.numberOfChildren != 0) {
            throw JuggleExceptionUser(errorstrings.getString("Error_position_subtag"))
        }
    }

    @Throws(IOException::class)
    fun writeJML(wr: PrintWriter) {
        val c = this.coordinate
        wr.println(
            ("<position x=\""
                    + toStringRounded(c.x, 4)
                    + "\" y=\""
                    + toStringRounded(c.y, 4)
                    + "\" z=\""
                    + toStringRounded(c.z, 4)
                    + "\" t=\""
                    + toStringRounded(this.t, 4)
                    + "\" angle=\""
                    + toStringRounded(this.angle, 4)
                    + "\" juggler=\""
                    + this.juggler
                    + "\"/>")
        )
    }

    override fun toString(): String {
        val sw = StringWriter()
        try {
            writeJML(PrintWriter(sw))
        } catch (_: IOException) {
        }
        return sw.toString()
    }
}
