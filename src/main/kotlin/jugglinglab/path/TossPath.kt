//
// TossPath.kt
//
// This path type describes an ordinary trajectory through the air.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.path

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.*
import java.text.MessageFormat
import kotlin.math.max
import kotlin.math.min

class TossPath : Path() {
    private var bx: Double = 0.0
    private var cx: Double = 0.0
    private var by: Double = 0.0
    private var cy: Double = 0.0
    private var az: Double = 0.0
    private var bz: Double = 0.0
    private var cz: Double = 0.0
    private var g: Double = G_DEF

    @Throws(JuggleExceptionUser::class)
    override fun initPath(st: String?) {
        var g: Double = G_DEF

        // parse for edits to the above variables
        val pl = ParameterList(st)
        for (i in 0..<pl.numberOfParameters) {
            val pname = pl.getParameterName(i)
            val pvalue = pl.getParameterValue(i)

            if (pname.equals("g", ignoreCase = true)) {
                try {
                    g = jlParseDouble(pvalue)
                } catch (_: NumberFormatException) {
                    val template = errorstrings.getString("Error_number_format")
                    val arguments = arrayOf<Any?>("g")
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            } else {
                throw JuggleExceptionUser(
                    errorstrings.getString("Error_path_badmod") + ": '" + pname + "'"
                )
            }
        }
        this.g = g
        az = -0.5 * g
    }

    @Throws(JuggleExceptionInternal::class)
    override fun calcPath() {
        val start = startCoord
        val end = endCoord
        if (start == null || end == null) {
            throw JuggleExceptionInternal("Error in parabolic path: endpoints not set")
        }

        val t = duration
        cx = start.x
        bx = (end.x - start.x) / t
        cy = start.y
        by = (end.y - start.y) / t
        cz = start.z
        bz = (end.z - start.z) / t - az * t
    }

    override val type = "Toss"

    override val minDuration = 0.0

    override val parameterDescriptors
        get() = arrayOf(
            ParameterDescriptor("g", ParameterDescriptor.TYPE_FLOAT, null, G_DEF, g)
        )

    override val startVelocity
        get() = Coordinate(bx, by, bz)

    override val endVelocity
        get() = Coordinate(bx, by, bz + 2 * az * duration)

    override fun getCoordinate(time: Double, newPosition: Coordinate) {
        if (time in startTime..endTime) {
            val t = time - startTime
            newPosition.setCoordinate(cx + bx * t, cy + by * t, cz + t * (bz + az * t))
        }
    }

    override fun getMax2(time1: Double, time2: Double): Coordinate {
        val tlow = max(startTime, time1)
        val thigh = min(endTime, time2)

        var result: Coordinate? = check(null, tlow, true)
        result = check(result, thigh, true)

        if (az < 0) {
            val te = -bz / (2 * az) + startTime
            if (tlow < te && te < thigh) {
                result = check(result, te, true)
            }
        }
        return result
    }

    override fun getMin2(time1: Double, time2: Double): Coordinate {
        val tlow = max(startTime, time1)
        val thigh = min(endTime, time2)

        var result: Coordinate? = check(null, tlow, false)
        result = check(result, thigh, false)

        if (az > 0) {
            val te = -by / (2 * az) + startTime
            if (tlow < te && te < thigh) {
                result = check(result, te, false)
            }
        }
        return result
    }

    companion object {
        const val G_DEF: Double = 980.0  // using CGS units
    }
}
