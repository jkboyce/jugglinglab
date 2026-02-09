//
// Path.kt
//
// This is the base class for all Path types in Juggling Lab. A Path describes
// the movement of an object from one point in spacetime to another.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.path

import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.ParameterDescriptor

abstract class Path {
    // Coordinate and time of path start
    protected var startCoord: Coordinate? = null
    protected var startTime: Double = 0.0

    // Coordinate and time of path end
    protected var endCoord: Coordinate? = null
    protected var endTime: Double = 0.0

    //--------------------------------------------------------------------------
    // Methods to define the Path
    //--------------------------------------------------------------------------

    fun setStart(position: Coordinate, time: Double) {
        startCoord = position
        startTime = time
    }

    fun setEnd(position: Coordinate, time: Double) {
        endCoord = position
        endTime = time
    }

    @Throws(JuggleExceptionUser::class)
    abstract fun initPath(config: String?)

    // Must be called after above path parameters are set, before querying for
    // path coordinates
    @Throws(JuggleExceptionInternal::class)
    abstract fun calcPath()

    // Utility method
    @Suppress("unused")
    fun translateTime(deltat: Double) {
        startTime += deltat
        endTime += deltat
    }

    //--------------------------------------------------------------------------
    // Querying properties of the path
    //--------------------------------------------------------------------------

    // String indicating the type of path
    abstract val type: String

    val duration: Double
        get() = endTime - startTime

    // Minimum duration between `startCoord` and `endCoord`, for a path of the
    // given type
    abstract val minDuration: Double

    // Parameters for defining the path in the UI (e.g., EditLadderDiagram)
    abstract val parameterDescriptors: List<ParameterDescriptor>

    // Calculated velocity at the start and end of the path, for hand layout
    abstract val startVelocity: Coordinate
    abstract val endVelocity: Coordinate

    // Calculated path coordinate at time `time`
    abstract fun getCoordinate(time: Double, newPosition: Coordinate)

    //--------------------------------------------------------------------------
    // Max and min coordinates over a range of times, used for layout
    //--------------------------------------------------------------------------

    // Path max/min coordinates over time interval [time1, time2]
    protected abstract fun getMax2(time1: Double, time2: Double): Coordinate?
    protected abstract fun getMin2(time1: Double, time2: Double): Coordinate?

    // Max/min over the entire path duration
    val max: Coordinate?
        get() = getMax2(startTime, endTime)
    val min: Coordinate?
        get() = getMin2(startTime, endTime)

    // Path max/min over [time1, time2], but clipped to `null` when the time is
    // out of range
    fun getMax(time1: Double, time2: Double): Coordinate? {
        if (time2 < startTime || time1 > endTime) return null
        return getMax2(time1, time2)
    }

    fun getMin(time1: Double, time2: Double): Coordinate? {
        if (time2 < startTime || time1 > endTime) return null
        return getMin2(time1, time2)
    }

    // Utility for getMax2/getMin2
    protected fun check(result: Coordinate?, t: Double, findmax: Boolean): Coordinate {
        val loc = Coordinate(0.0, 0.0, 0.0)
        getCoordinate(t, loc)

        val res = if (result == null) {
            loc
        } else if (findmax) {
            Coordinate.Companion.max(result, loc)
        } else {
            Coordinate.Companion.min(result, loc)
        }
        return res!!
    }

    companion object {
        // List of the built-in path types
        val builtinPaths: List<String> = listOf(
            "Toss",
            "Bounce",
        )

        // Factory method to create paths
        @Throws(JuggleExceptionUser::class)
        fun newPath(type: String): Path {
            if (type.equals("toss", ignoreCase = true)) {
                return TossPath()
            } else if (type.equals("bounce", ignoreCase = true)) {
                return BouncePath()
            }
            throw JuggleExceptionUser("Path type '$type' not recognized")
        }
    }
}
