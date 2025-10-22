//
// Path.kt
//
// This is the base class for all Path types in Juggling Lab. A Path describes
// the movement of an object during the time between throw and catch.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.path

import jugglinglab.JugglingLab
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import java.util.*

abstract class Path {
  var startTime: Double = 0.0
    protected set
  var endTime: Double = 0.0
    protected set

  @JvmField
  protected var startCoord: Coordinate? = null

  @JvmField
  protected var endCoord: Coordinate? = null

  fun setStart(position: Coordinate?, time: Double) {
    startCoord = position
    this.startTime = time
  }

  fun setEnd(position: Coordinate?, time: Double) {
    endCoord = position
    this.endTime = time
  }

  val duration: Double
    get() = (this.endTime - this.startTime)

  open val minDuration: Double
    // Minimum duration is nonzero for certain throw types, e.g., a double
    get() = 0.0

  fun translateTime(deltat: Double) {
    this.startTime += deltat
    this.endTime += deltat
  }

  val max: Coordinate?
    // For screen layout.
    get() = getMax2(this.startTime, this.endTime)

  val min: Coordinate?
    get() = getMin2(this.startTime, this.endTime)

  fun getMax(begin: Double, end: Double): Coordinate? {
    if (end < this.startTime || begin > this.endTime) return null
    return getMax2(begin, end)
  }

  fun getMin(begin: Double, end: Double): Coordinate? {
    if (end < this.startTime || begin > this.endTime) {
      return null
    }
    return getMin2(begin, end)
  }

  // Utility for getMax/getMin.

  protected fun check(result: Coordinate?, t: Double, findmax: Boolean): Coordinate? {
    var result = result
    val loc = Coordinate(0.0, 0.0, 0.0)
    getCoordinate(t, loc)
    result = if (findmax) {
      Coordinate.max(result, loc)
    } else {
      Coordinate.min(result, loc)
    }
    return result
  }

  // string indicating the type of path
  abstract val type: String?

  // used for defining the path in the UI (EditLadderDiagram)
  abstract fun getParameterDescriptors(): Array<ParameterDescriptor?>?

  // defines the path from a config string
  @Throws(JuggleExceptionUser::class)
  abstract fun initPath(st: String?)

  @Throws(JuggleExceptionInternal::class)
  abstract fun calcPath()

  // for hand layout purposes, only valid after calcPath()
  abstract fun getStartVelocity(): Coordinate?

  abstract fun getEndVelocity(): Coordinate?

  // only valid after calcPath()
  abstract fun getCoordinate(time: Double, newPosition: Coordinate?)

  // for hand layout, only valid after calcPath()
  protected abstract fun getMax2(begin: Double, end: Double): Coordinate?

  protected abstract fun getMin2(begin: Double, end: Double): Coordinate?

  companion object {
    @JvmField
    val errorstrings: ResourceBundle? = JugglingLab.errorstrings

    // the built-in path types
    @JvmField
    val builtinPaths: Array<String?> = arrayOf<String?>("Toss", "Bounce")

    // Create a new path of the given type.
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
