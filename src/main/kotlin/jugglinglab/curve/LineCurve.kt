//
// LineCurve.kt
//
// This curve describes a sequence of linear trajectories through space.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.curve

import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import kotlin.math.max
import kotlin.math.min

class LineCurve : Curve() {
  private var n: Int = 0  // number of line segments
  private lateinit var a: Array<DoubleArray>
  private lateinit var b: Array<DoubleArray>  // line coefficients
  private lateinit var durations: DoubleArray  // durations of segments

  @Throws(JuggleExceptionInternal::class)
  override fun calcCurve() {
    n = numpoints - 1
    if (n < 1) {
      throw JuggleExceptionInternal("LineCurve error 1")
    }

    a = Array(n) { DoubleArray(3) }
    b = Array(n) { DoubleArray(3) }
    durations = DoubleArray(n)
    for (i in 0..<n) {
      durations[i] = times[i + 1] - times[i]
      if (durations[i] <= 0.0) {
        throw JuggleExceptionInternal("LineCurve error 2")
      }
    }

    val x = DoubleArray(n + 1)

    for (i in 0..2) {
      for (j in 0..<(n + 1)) {
        x[j] = positions[j].get(i)
      }

      // now solve for line coefficients
      for (j in 0..<n) {
        a[j][i] = x[j]
        b[j][i] = (x[j + 1] - x[j]) / durations[j]
      }
    }
  }

  override fun getCoordinate(time: Double, newPosition: Coordinate) {
    var time = time
    if (time < times[0] || time > times[n]) {
      return
    }

    var i: Int
    i = 0
    while (i < n) {
      if (time <= times[i + 1]) {
        break
      }
      i++
    }
    if (i == n) {
      i = n - 1
    }

    time -= times[i]
    newPosition.setCoordinate(
      a[i][0] + time * b[i][0], a[i][1] + time * b[i][1], a[i][2] + time * b[i][2]
    )
  }

  override fun getMax2(time1: Double, time2: Double): Coordinate? {
    if (time2 < times[0] || time1 > times[n]) {
      return null
    }

    val tlow = max(times[0], time1)
    val thigh = min(times[n], time2)
    var result: Coordinate? = check(null, tlow, true)
    result = check(result, thigh, true)

    for (i in 0..n) {
      if (times[i] in tlow..thigh) {
        result = check(result, times[i], true)
      }
      if (i != n) {
        val tlowtemp = max(tlow, times[i])
        val thightemp = min(thigh, times[i + 1])

        if (tlowtemp < thightemp) {
          result = check(result, tlowtemp, true)
          result = check(result, thightemp, true)
        }
      }
    }

    return result
  }

  override fun getMin2(time1: Double, time2: Double): Coordinate? {
    if ((time2 < times[0]) || (time1 > times[n])) {
      return null
    }

    val tlow = max(times[0], time1)
    val thigh = min(times[n], time2)
    var result: Coordinate? = check(null, tlow, false)
    result = check(result, thigh, false)

    for (i in 0..n) {
      if (times[i] in tlow..thigh) {
        result = check(result, times[i], false)
      }
      if (i != n) {
        val tlowtemp = max(tlow, times[i])
        val thightemp = min(thigh, times[i + 1])

        if (tlowtemp < thightemp) {
          result = check(result, tlowtemp, false)
          result = check(result, thightemp, false)
        }
      }
    }

    return result
  }
}
