//
// SplineCurve.kt
//
// Curve described by a sequence of cubic splines, matched to the positions and
// (optional) velocities defined at the end of each spline. The end velocity
// of spline `i` is guaranteed to match the start velocity of spline `i + 1`.
//
// If the velocities at the endpoints are defined (not null), the curve will
// match those velocities precisely. For velocities in the middle, the curve
// will match the *directions* of those velocities, but not their magnitudes.
// Any of the velocities may be null, in which case the spline will choose a
// velocity using one of three optimization goals.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.curve

import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.ndarray.data.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SplineCurve : Curve() {
    private var n: Int = 0  // number of spline segments
    private lateinit var a: Array<DoubleArray>
    private lateinit var b: Array<DoubleArray>
    private lateinit var c: Array<DoubleArray>
    private lateinit var d: Array<DoubleArray>  // spline coefficients

    // Calculate the coefficients a, b, c, d for each portion of the spline path.
    // To solve for these four unknowns, we use four boundary conditions: the
    // position at both endpoints, and the velocity at both endpoints.
    //
    // When the hand is making a throw or softcatch, the velocities are known and
    // given by the velocity of the object being thrown/caught. When the hand is
    // making a "natural" catch, we want the spline velocity to match the
    // direction of the landing object's velocity. All remaining unknowns in
    // the velocities must be assigned, which we do via one of three techniques
    // described below.

    @Throws(JuggleExceptionInternal::class)
    override fun calcCurve() {
        n = numpoints - 1
        if (n < 1) {
            throw JuggleExceptionInternal("SplineCurve error 1")
        }

        a = Array(n) { DoubleArray(3) }
        b = Array(n) { DoubleArray(3) }
        c = Array(n) { DoubleArray(3) }
        d = Array(n) { DoubleArray(3) }
        val durations = DoubleArray(n)
        for (i in 0..<n) {
            durations[i] = times[i + 1] - times[i]
            if (durations[i] <= 0) {
                throw JuggleExceptionInternal("SplineCurve error 2")
            }
        }

        // copy the velocity array so we can modify it
        val vel = velocities.map { it?.copy() }.toTypedArray()

        if (vel[0] != null && vel[n] != null) {
            findvelsEdgesKnown(n, durations, positions, vel)
        } else {
            findvelsEdgesUnknown(n, durations, positions, vel)
        }

        // now that we have all velocities, solve for spline coefficients
        for (i in 0..<n) {
            val t = durations[i]

            for (j in 0..2) {
                val xi0 = positions[i][j]
                val xi1 = positions[i + 1][j]
                val vi0 = vel[i]!![j]
                val vi1 = vel[i + 1]!![j]

                a[i][j] = xi0
                b[i][j] = vi0
                c[i][j] = (3 * (xi1 - xi0) - (vi1 + 2 * vi0) * t) / (t * t)
                d[i][j] = (-2 * (xi1 - xi0) + (vi1 + vi0) * t) / (t * t * t)
                // System.out.println("a="+a[i][j]+", b="+b[i][j]+", c="+c[i][j]+", d="+d[i][j]+"\n");
            }
        }
    }

    override fun getCoordinate(time: Double, newPosition: Coordinate) {
        if (time !in times[0]..times[n]) return

        var i = 0
        while (i < n) {
            if (time <= times[i + 1]) break
            ++i
        }
        i = min(i, n - 1)

        val t = time - times[i]
        newPosition.setCoordinate(
            a[i][0] + t * (b[i][0] + t * (c[i][0] + t * d[i][0])),
            a[i][1] + t * (b[i][1] + t * (c[i][1] + t * d[i][1])),
            a[i][2] + t * (b[i][2] + t * (c[i][2] + t * d[i][2]))
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

                    for (index in 0..2) {
                        if (abs(d[i][index]) > 1.0e-6) {
                            val k = c[i][index] * c[i][index] - 3 * b[i][index] * d[i][index]
                            if (k > 0) {
                                val te = times[i] + (-c[i][index] - sqrt(k)) / (3 * d[i][index])
                                if (te in tlowtemp..thightemp) {
                                    result = check(result, te, true)
                                }
                            }
                        } else if (c[i][index] < 0) {
                            val te = times[i] - b[i][index] / (2 * c[i][index])
                            if (te in tlowtemp..thightemp) {
                                result = check(result, te, true)
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    override fun getMin2(time1: Double, time2: Double): Coordinate? {
        if (time2 < times[0] || time1 > times[n]) {
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

                    for (index in 0..2) {
                        if (abs(d[i][index]) > 1.0e-6) {
                            val k = c[i][index] * c[i][index] - 3 * b[i][index] * d[i][index]
                            if (k > 0) {
                                val te = times[i] + (-c[i][index] + sqrt(k)) / (3 * d[i][index])
                                if (te in tlowtemp..thightemp) {
                                    result = check(result, te, false)
                                }
                            }
                        } else if (c[i][index] > 0) {
                            val te = times[i] - b[i][index] / (2 * c[i][index])
                            if (te in tlowtemp..thightemp) {
                                result = check(result, te, false)
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    @Suppress("KotlinConstantConditions")
    companion object {
        // These are the three techniques to assign velocities:
        //    "MINIMIZE_RMSACCEL" minimizes the rms acceleration of the hand
        //    "CONTINUOUS_ACCEL" makes the hand acceleration a continuous function
        //    "MINIMIZE_RMSVEL" minimizes the rms velocity of the hand
        const val MINIMIZE_RMSACCEL: Int = 0
        const val CONTINUOUS_ACCEL: Int = 1
        const val MINIMIZE_RMSVEL: Int = 2

        // Selected method
        const val SPLINE_LAYOUT_METHOD: Int = MINIMIZE_RMSACCEL

        // The next method assigns velocities at the intermediate times from known
        // velocities at the endpoints, and positions at all times.
        //
        // Inputs:
        //    n is the number of spline segments -- (n-1) is the number of unknown
        //          interior velocities v[1]...v[n-1]
        //    t[] is the duration of each segment, dimension n
        //    x[] is the position at each segment endpoint, dimension (n+1)
        //    v[] is the velocity at each segment endpoint, dimension (n+1)
        //
        // Outputs:
        //    v[1]...v[n-1]
        //
        // v[0] and v[n] are assumed to be initialized to known endpoints. The
        // interior velocities v[1]...v[n-1] may be initialized to non-null values,
        // to indicate there is a natural catch at that time (v[i] is the catch
        // velocity). For a natural catch the hand velocity is constrained to be
        // parallel to the catch velocity, at the time of catch.
        //
        // For the minimization techniques, the calculus problem reduces to
        // solving a system of linear equations of the form A.v = b, where v
        // is a column vector of velocities and Lagrange multipliers.

        @Throws(JuggleExceptionInternal::class)
        private fun findvelsEdgesKnown(
            n: Int,
            t: DoubleArray,
            x: Array<Coordinate>,
            v: Array<Coordinate?>
        ) {
            if (n < 2) return

            var numcatches = 0
            for (i in 1..<n) {
                if (v[i] != null) {
                    ++numcatches
                }
            }

            // In this case we put all three axes into one big matrix, and solve once.
            //
            // Number of variables in linear solve:
            //    3 for each interior velocity v[1]...v[n-1]
            //    2 for each natural catch (Lagrange multipliers for constraints)
            val dim = 3 * (n - 1) + 2 * numcatches

            val m = mk.zeros<Double>(dim, dim)
            val b = mk.zeros<Double>(dim)

            for (axis in 0..2) {
                val v0 = v[0]!![axis]
                val vn = v[n]!![axis]

                for (i in 0..<n - 1) {
                    val xi0 = x[i][axis]
                    val xi1 = x[i + 1][axis]
                    val xi2 = x[i + 2][axis]
                    val index = i + axis * (n - 1)

                    when (SPLINE_LAYOUT_METHOD) {
                        MINIMIZE_RMSACCEL, CONTINUOUS_ACCEL -> {
                            // cases end up being identical
                            m[index, index] = 2 / t[i] + 2 / t[i + 1]
                            val offdiag1 = (if (i == n - 2) 0.0 else 1 / t[i + 1])
                            if (index < 3 * (n - 1) - 1) {
                                m[index, index + 1] = offdiag1
                                m[index + 1, index] = offdiag1
                            }

                            b[index] = 3 * (xi2 - xi1) / (t[i + 1] * t[i + 1]) + 3 * (xi1 - xi0) /
                                (t[i] * t[i])
                            if (i == 0) {
                                b[index] = b[index] - v0 / t[0]
                            }
                            if (i == (n - 2)) {
                                b[index] = b[index] - vn / t[n - 1]
                            }
                        }
                        MINIMIZE_RMSVEL -> {
                            m[index, index] = 4 * (t[i] + t[i + 1])
                            val offdiag2 = (if (i == n - 2) 0.0 else -t[i + 1])
                            if (index < 3 * (n - 1) - 1) {
                                m[index, index + 1] = offdiag2
                                m[index + 1, index] = offdiag2
                            }

                            b[index] = 3 * (xi2 - xi0)
                            if (i == 0) {
                                b[index] = b[index] + v0 * t[0]
                            }
                            if (i == (n - 2)) {
                                b[index] = b[index] + vn * t[n - 1]
                            }
                        }
                    }
                }
            }

            // Now we apply the "natural throwing" constraint, that the hand
            // velocity must be parallel to the catch velocity at the time of catch.
            // We implement this constraint by requiring the cross product between
            // v[] and the catch velocity to be zero. This is three separate
            // constraints (one for each spatial dimension), however they are not
            // linearly independent so we only need to apply two. We select the two
            // to retain based on the components of catch velocity.
            //
            // The constraints are implemented using Lagrange multipliers, two per
            // specified catch velocity.
            var i = 0
            var catchnum = 0
            while (i < n - 1) {
                if (v[i + 1] == null) {
                    ++i
                    continue
                }

                val index = 3 * (n - 1) + 2 * catchnum
                val ci0 = v[i + 1]!![0]  // components of catch velocity
                val ci1 = v[i + 1]!![1]
                val ci2 = v[i + 1]!![2]

                val largeaxis = when {
                    abs(ci1) >= max(abs(ci0), abs(ci2)) -> 1
                    abs(ci2) >= max(abs(ci0), abs(ci1)) -> 2
                    else -> 0
                }

                when (largeaxis) {
                    0 -> {
                        m[index, i] = ci2
                        m[i, index] = ci2
                        m[index + 1, i] = ci1
                        m[i, index + 1] = ci1
                        m[index + 1, i + (n - 1)] = -ci0
                        m[i + (n - 1), index + 1] = -ci0
                        m[index, i + 2 * (n - 1)] = -ci0
                        m[i + 2 * (n - 1), index] = -ci0
                    }
                    1 -> {
                        m[index + 1, i] = ci1
                        m[i, index + 1] = ci1
                        m[index, i + (n - 1)] = ci2
                        m[i + (n - 1), index] = ci2
                        m[index + 1, i + (n - 1)] = -ci0
                        m[i + (n - 1), index + 1] = -ci0
                        m[index, i + 2 * (n - 1)] = -ci1
                        m[i + 2 * (n - 1), index] = -ci1
                    }
                    2 -> {
                        m[index + 1, i] = ci2
                        m[i, index + 1] = ci2
                        m[index, i + (n - 1)] = ci2
                        m[i + (n - 1), index] = ci2
                        m[index, i + 2 * (n - 1)] = -ci1
                        m[i + 2 * (n - 1), index] = -ci1
                        m[index + 1, i + 2 * (n - 1)] = -ci0
                        m[i + 2 * (n - 1), index + 1] = -ci0
                    }
                }

                ++catchnum
                ++i
            }

            try {
                val solution = mk.linalg.solve(m, b)
                for (i in 0..<n - 1) {
                    v[i + 1] = Coordinate(
                        solution[i],
                        solution[i + (n - 1)],
                        solution[i + 2 * (n - 1)]
                    )
                }
            } catch (_: Exception) { // multik can throw different exception types
                throw JuggleExceptionInternal("Multik exception in findvelsEdgesKnown()")
            }
        }

        // The next method, like the one above, assigns velocities at the spline
        // endpoints. The difference is that here the endpoint velocities v[0] and
        // v[n] are not known but are assigned, with the constraint that v[n] = v[0].
        //
        // There are no catch velocity constraints to consider here, since if there
        // were catches for the hand there would be throws as well -- and the edge
        // velocities would be known. Because there is no catch velocity constraint,
        // we can solve each axis independently.
        //
        // The matrix A is close to tridiagonal, except for nonzero elements in the
        // upper-right and lower-left corners. Since A is close to tridiagonal, we
        // use the Woodbury formula which allows us to solve a few auxiliary
        // tridiagonal problems and then combine the results to solve the full
        // problem. See pg. 77 from Numerical Recipes in C, first edition.

        @Suppress("LocalVariableName")
        @Throws(JuggleExceptionInternal::class)
        private fun findvelsEdgesUnknown(
            n: Int,
            t: DoubleArray,
            x: Array<Coordinate>,
            v: Array<Coordinate?>
        ) {
            if (n < 1) return

            val Adiag = DoubleArray(n)  // v[0]...v[n-1]
            val Aoffd = DoubleArray(n)  // A is symmetric
            var Acorner = 0.0  // nonzero element in UR/LL corners of A
            val b = DoubleArray(n)

            for (i in 0..<n) {
                v[i] = Coordinate()
            }

            // Here we can solve each axis independently, and combine the results
            for (axis in 0..2) {
                val xn0 = x[n][axis]
                val xnm1 = x[n - 1][axis]

                for (i in 0..<n) {
                    val xi0 = x[i][axis]
                    val xi1 = x[i + 1][axis]
                    val xim1 = if (i == 0) 0.0 else x[i - 1][axis]

                    when (SPLINE_LAYOUT_METHOD) {
                        MINIMIZE_RMSACCEL, CONTINUOUS_ACCEL -> {
                            if (i == 0) {
                                Adiag[i] = 2 / t[n - 1] + 2 / t[0]
                                Acorner = 1 / t[n - 1]
                                b[i] = 3 * (xi1 - xi0) / (t[0] * t[0]) + 3 * (xn0 - xnm1) /
                                        (t[n - 1] * t[n - 1])
                            } else {
                                Adiag[i] = 2 / t[i - 1] + 2 / t[i]
                                b[i] = 3 * (xi1 - xi0) / (t[i] * t[i]) + 3 * (xi0 - xim1) /
                                        (t[i - 1] * t[i - 1])
                            }
                            Aoffd[i] = 1 / t[i]  // not used for i = n - 1
                        }

                        MINIMIZE_RMSVEL -> {
                            if (i == 0) {
                                Adiag[i] = 4 * (t[n - 1] + t[0])
                                Acorner = -t[n - 1]
                                b[i] = 3 * (xn0 - xnm1 + xi1 - xi0)
                            } else {
                                Adiag[i] = 4 * (t[i - 1] + t[i])
                                b[i] = 3 * (xi1 - xim1)
                            }
                            Aoffd[i] = -t[i]
                        }
                    }
                }

                val vel = DoubleArray(n) { v[it]!![axis] }

                // Woodbury's formula: First solve the problem ignoring A's nonzero corners
                tridag(Aoffd, Adiag, Aoffd, b, vel, n)

                if (n > 2) { // need to deal with nonzero corners?
                    // solve a few auxiliary problems
                    val z1 = DoubleArray(n)
                    b[0] = Acorner
                    for (i in 1..<n) {
                        b[i] = 0.0
                    }
                    tridag(Aoffd, Adiag, Aoffd, b, z1, n)

                    val z2 = DoubleArray(n)
                    b[n - 1] = Acorner
                    for (i in 0..<n - 1) {
                        b[i] = 0.0
                    }
                    tridag(Aoffd, Adiag, Aoffd, b, z2, n)

                    // calculate a 2x2 matrix H
                    var H00 = 1 + z2[0]
                    var H01 = -z2[n - 1]
                    var H10 = -z1[0]
                    var H11 = 1 + z1[n - 1]
                    val det = H00 * H11 - H01 * H10
                    H00 /= det
                    H01 /= det
                    H10 /= det
                    H11 /= det

                    // use Woodbury's formula to adjust the velocities
                    val m0 = H00 * vel[n - 1] + H01 * vel[0]
                    val m1 = H10 * vel[n - 1] + H11 * vel[0]
                    for (i in 0..<n) {
                        vel[i] -= (z1[i] * m0 + z2[i] * m1)
                    }
                }

                for (i in 0..<n) {
                    v[i]!![axis] = vel[i]
                }

                /*
                // do the matrix multiply to check the answer
                System.out.println("Final result RHS:");
                for (int i = 0; i < n; i++) {
                  double res = v[i] * Adiag[i];
                  if (i != (n-1))
                      res += v[i+1] * Aoffd[i];
                  if (i > 0)
                      res += v[i-1] * Aoffd[i-1];
                  if ((i == 0) && (n > 2))
                      res += Acorner * v[n-1];
                  if ((i == (n-1)) && (n > 2))
                      res += Acorner * v[0];
                  System.out.println("  rhs["+i+"] = "+res);
                }
                */
            }

            v[n] = v[0]?.copy()  // v[n] = v[0]
        }

        // The following method is adapted from Numerical Recipes. It solves the
        // linear system A.u = r where A is tridiagonal. a[] is the subdiagonal,
        // b[] the diagonal, c[] the superdiagonal. All arrays are indexed from 0.
        // Only the array u[] is changed.

        @Throws(JuggleExceptionInternal::class)
        private fun tridag(
            a: DoubleArray,
            b: DoubleArray,
            c: DoubleArray,
            r: DoubleArray,
            u: DoubleArray,
            n: Int
        ) {
            if (b[0] == 0.0) {
                throw JuggleExceptionInternal("Error 1 in tridag()")
            }

            var bet = b[0]
            val gam = DoubleArray(n)

            u[0] = r[0] / bet
            for (j in 1..<n) {
                gam[j] = c[j - 1] / bet
                bet = b[j] - a[j - 1] * gam[j]
                if (bet == 0.0) {
                    throw JuggleExceptionInternal("Error 2 in tridag()")
                }
                u[j] = (r[j] - a[j - 1] * u[j - 1]) / bet
            }
            for (j in (n - 1) downTo 1) {
                u[j - 1] -= gam[j] * u[j]
            }
        }
    }
}
