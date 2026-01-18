//
// Curve.kt
//
// This type describes a path through 3D space, used to model hand movements as
// well as juggler positions/angles.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("EmptyRange")

package jugglinglab.curve

import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal

abstract class Curve {
    // The curve is defined by `numpoints` control points
    protected var numpoints: Int = 0

    // Coordinate at each control point; the curve is expected to match each
    // coordinate at its corresponding time
    protected lateinit var positions: Array<Coordinate>

    // Time at each control point
    protected lateinit var times: DoubleArray

    // Velocity at each control point
    // - For line curves, velocities are ignored
    // - For spline curves, velocities are used to calculate spline coefficients
    protected lateinit var velocities: Array<Coordinate?>

    //--------------------------------------------------------------------------
    // Methods to define the Curve
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    fun setCurve(times: DoubleArray, positions: Array<Coordinate>, velocities: Array<Coordinate?>) {
        numpoints = times.size
        this.times = times
        this.positions = positions
        this.velocities = velocities

        if (numpoints != positions.size || numpoints != velocities.size) {
            throw JuggleExceptionInternal("Curve error 1")
        }
    }

    // Calculate the curve; this is called after setting curve parameters but
    // before any calls to getCoordinate()
    @Throws(JuggleExceptionInternal::class)
    abstract fun calcCurve()

    // Utility method
    @Suppress("unused")
    fun translateTime(deltat: Double) {
        for (i in 0..<numpoints) {
            times[i] += deltat
        }
    }

    //--------------------------------------------------------------------------
    // Querying properties of the curve
    //--------------------------------------------------------------------------

    val startTime: Double
        get() = times[0]

    val endTime: Double
        get() = times[numpoints - 1]

    @Suppress("unused")
    val duration: Double
        get() = (times[numpoints - 1] - times[0])

    // Calculated curve coordinate at time `time`
    abstract fun getCoordinate(time: Double, newPosition: Coordinate)

    //--------------------------------------------------------------------------
    // Max and min coordinates over a range of times, used for layout
    //--------------------------------------------------------------------------

    // Curve max/min coordinates over time interval [time1, time2]
    protected abstract fun getMax2(time1: Double, time2: Double): Coordinate?
    protected abstract fun getMin2(time1: Double, time2: Double): Coordinate?

    // Max/min over the entire curve duration
    val max: Coordinate?
        get() = getMax2(times[0], times[numpoints - 1])
    val min: Coordinate?
        get() = getMin2(times[0], times[numpoints - 1])

    // Path max/min over [time1, time2], but clipped to `null` when the time is
    // out of range
    fun getMax(time1: Double, time2: Double): Coordinate? {
        return when {
            time2 < this.startTime || time1 > this.endTime -> null
            else -> getMax2(time1, time2)
        }
    }
    fun getMin(time1: Double, time2: Double): Coordinate? {
        return when {
            time2 < this.startTime || time1 > this.endTime -> null
            else -> getMin2(time1, time2)
        }
    }

    // Utility for getMax2/getMin2
    protected fun check(result: Coordinate?, t: Double, findmax: Boolean): Coordinate {
        val loc = Coordinate()
        getCoordinate(t, loc)

        return when {
            result == null -> loc
            findmax -> Coordinate.max(result, loc)!!
            else -> Coordinate.min(result, loc)!!
        }
    }

    companion object {
        // implemented types
        @Suppress("unused")
        const val CURVE_SPLINE: Int = 1
        const val CURVE_LINE: Int = 2
    }
}
