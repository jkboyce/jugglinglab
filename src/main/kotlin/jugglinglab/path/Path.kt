//
// Path.kt
//
// This is the base class for all Path types in Juggling Lab. A Path describes
// the movement of an object from one point in spacetime to another.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.path

import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor

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
        this.startTime = time
    }

    fun setEnd(position: Coordinate, time: Double) {
        endCoord = position
        this.endTime = time
    }

    @Throws(JuggleExceptionUser::class)
    abstract fun initPath(st: String?)

    // Must be called after above path parameters are set, before querying for
    // path coordinates
    @Throws(JuggleExceptionInternal::class)
    abstract fun calcPath()

    // Utility method
    @Suppress("unused")
    fun translateTime(deltat: Double) {
        this.startTime += deltat
        this.endTime += deltat
    }

    //--------------------------------------------------------------------------
    // Querying properties of the path
    //--------------------------------------------------------------------------

    // String indicating the type of path
    abstract val type: String

    val duration: Double
        get() = (this.endTime - this.startTime)

    // Minimum duration between `startCoord` and `endCoord`, for a path of the
    // given type
    abstract val minDuration: Double

    // Parameters for defining the path in the UI (e.g., EditLadderDiagram)
    abstract val parameterDescriptors: Array<ParameterDescriptor>

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
        get() = getMax2(this.startTime, this.endTime)
    val min: Coordinate?
        get() = getMin2(this.startTime, this.endTime)

    // Path max/min over [time1, time2], but clipped to `null` when the time is
    // out of range
    fun getMax(time1: Double, time2: Double): Coordinate? {
        if (time2 < this.startTime || time1 > this.endTime) return null
        return getMax2(time1, time2)
    }
    fun getMin(time1: Double, time2: Double): Coordinate? {
        if (time2 < this.startTime || time1 > this.endTime) return null
        return getMin2(time1, time2)
    }

    // Utility for getMax2/getMin2
    protected fun check(result: Coordinate?, t: Double, findmax: Boolean): Coordinate {
        val loc = Coordinate(0.0, 0.0, 0.0)
        getCoordinate(t, loc)

        val res = if (result == null) {
            loc
        } else if (findmax) {
            Coordinate.max(result, loc)
        } else {
            Coordinate.min(result, loc)
        }
        return res!!
    }

    companion object {
        // List of the built-in path types
        @JvmField
        val builtinPaths: Array<String> = arrayOf<String>("Toss", "Bounce")

        // Factory method to create paths
        @JvmStatic
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
