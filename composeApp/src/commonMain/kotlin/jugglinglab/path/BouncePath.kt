//
// BouncePath.kt
//
// This path type describes a path that bounces one or more times off the floor.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.path

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.*
import kotlin.math.*

class BouncePath : Path() {
    private var bx: Double = 0.0
    private var cx: Double = 0.0
    private var by: Double = 0.0
    private var cy: Double = 0.0
    private lateinit var az: DoubleArray
    private lateinit var bz: DoubleArray
    private lateinit var cz: DoubleArray
    private lateinit var endtime: DoubleArray
    private var bounces: Int = BOUNCES_DEF
    private var forced: Boolean = FORCED_DEF // true -> forced throw
    private var hyper: Boolean = HYPER_DEF // true -> same type of catch (lift/forced) as throw
    private var bounceplane: Double = BOUNCEPLANE_DEF
    private var bouncefrac: Double = BOUNCEFRAC_DEF
    private var g: Double = G_DEF
    private var bouncefracsqrt: Double = 0.0
    private var numbounces: Int = 0 // actual number of bounces (<= this.bounces)

    @Throws(JuggleExceptionUser::class)
    override fun initPath(config: String?) {
        // default bounce characteristics
        var bounces: Int = BOUNCES_DEF
        var forced: Boolean = FORCED_DEF
        var hyper: Boolean = HYPER_DEF
        var bounceplane: Double = BOUNCEPLANE_DEF
        var bouncefrac: Double = BOUNCEFRAC_DEF
        var g: Double = G_DEF

        // now parse for edits to the above variables
        val pl = ParameterList(config)
        for (i in 0..<pl.numberOfParameters) {
            val pname = pl.getParameterName(i)
            val pvalue = pl.getParameterValue(i)

            if (pname.equals("bounces", ignoreCase = true)) {
                try {
                    bounces = pvalue.toInt()
                } catch (_: NumberFormatException) {
                    val message = getStringResource(Res.string.error_number_format, "bounces")
                    throw JuggleExceptionUser(message)
                }
            } else if (pname.equals("forced", ignoreCase = true)) {
                forced = pvalue.toBoolean()
            } else if (pname.equals("hyper", ignoreCase = true)) {
                hyper = pvalue.toBoolean()
            } else if (pname.equals("bounceplane", ignoreCase = true)) {
                try {
                    bounceplane = jlParseFiniteDouble(pvalue)
                } catch (_: NumberFormatException) {
                    val message = getStringResource(Res.string.error_number_format, "bounceplane")
                    throw JuggleExceptionUser(message)
                }
            } else if (pname.equals("bouncefrac", ignoreCase = true)) {
                try {
                    bouncefrac = jlParseFiniteDouble(pvalue)
                } catch (_: NumberFormatException) {
                    val message = getStringResource(Res.string.error_number_format, "bouncefrac")
                    throw JuggleExceptionUser(message)
                }
            } else if (pname.equals("g", ignoreCase = true)) {
                try {
                    g = jlParseFiniteDouble(pvalue)
                } catch (_: NumberFormatException) {
                    val message = getStringResource(Res.string.error_number_format, "g")
                    throw JuggleExceptionUser(message)
                }
            } else {
                val message = getStringResource(Res.string.error_path_badmod)
                throw JuggleExceptionUser("$message: '$pname'")
            }
        }

        this.bounces = bounces
        this.forced = forced
        this.hyper = hyper
        this.bounceplane = bounceplane
        this.bouncefrac = bouncefrac
        try {
            this.bouncefracsqrt = sqrt(bouncefrac)
        } catch (_: ArithmeticException) {
            this.bouncefracsqrt = 1.0
        }
        this.g = g

        this.az = DoubleArray(bounces + 1)
        this.bz = DoubleArray(bounces + 1)
        this.cz = DoubleArray(bounces + 1)
        this.endtime = DoubleArray(bounces + 1)
        for (i in 0..bounces) {
            az[i] = -0.5 * g
        }
    }

    @Throws(JuggleExceptionInternal::class)
    override fun calcPath() {
        val start = startCoord
        val end = endCoord
        if (start == null || end == null) {
            return
        }

        for (n in bounces downTo 1) {
            val root = DoubleArray(4)
            val liftcatch = BooleanArray(4)
            val numroots = solveBounceEquation(n, duration, root, liftcatch)

            /*
            System.out.println(numroots + " roots found with " + n + " bounces");
            for (int i = 0; i < numroots; i++)
                System.out.println("   v0["+i+"] = "+root[i]+" -- "+(liftcatch[i]?"lift catch":"forced catch"));
            */
            if (numroots == 0) {
                continue  // no solution -> go to the next fewer number of bounces
            }

            // Select which root to use. First try to get the forced and hyper values as
            // desired. If no solution, try to get forced, then try to get hyper as desired.
            var choseroot = false
            var v0 = root[0]  // default
            for (i in 0..<numroots) {
                if (forced xor (root[i] < 0)) continue
                if (hyper xor liftcatch[i] xor forced) continue
                v0 = root[i]
                choseroot = true
                break
            }
            if (!choseroot) {
                for (i in 0..<numroots) {
                    if (forced xor (root[i] < 0)) continue
                    v0 = root[i]
                    choseroot = true
                    break
                }
            }
            if (!choseroot) {
                for (i in 0..<numroots) {
                    if (hyper xor liftcatch[i] xor (root[i] < 0)) continue
                    v0 = root[i]
                    break
                }
            }
            numbounces = n

            // set the remaining path variables based on our solution for
            // `numbounces` and `v0`
            bz[0] = v0
            cz[0] = start.z
            endtime[0] = if (az[0] < 0) {
                (-v0 - sqrt(v0 * v0 - 4 * az[0] * (cz[0] - bounceplane))) / (2 * az[0])
            } else {
                (-v0 + sqrt(v0 * v0 - 4 * az[0] * (cz[0] - bounceplane))) / (2 * az[0])
            }
            var vrebound = (-v0 - 2 * az[0] * endtime[0]) * bouncefracsqrt

            for (i in 1..n) {
                bz[i] = vrebound - 2 * az[i] * endtime[i - 1]
                cz[i] = bounceplane - az[i] * endtime[i - 1] * endtime[i - 1] -
                    bz[i] * endtime[i - 1]
                endtime[i] = endtime[i - 1] - vrebound / az[i]
                vrebound *= bouncefracsqrt
            }
            endtime[n] = duration  // fix this assignment from the above loop

            // finally do the x and y coordinates -- these are simple
            cx = start.x
            bx = (end.x - start.x) / duration
            cy = start.y
            by = (end.y - start.y) / duration
            return
        }

        throw JuggleExceptionInternal("No root found in BouncePath")
    }

    // The next method does the real work of figuring out the object's path.
    // It solves a polynomial equation to determine the values of `v0` (upward-
    // directed velocity) that achieve the given number of bounces and total
    // duration.
    //
    // Inputs:
    //     n -- number of bounces
    //     duration -- time from throw to catch
    // Outputs:
    //     numroots -- function return value, number of valid solutions found
    //     root[] -- solution(s) for v0
    //     liftcatch[] -- whether the solution corresponds to a "lift" catch

    private fun solveBounceEquation(
        n: Int,
        duration: Double,
        root: DoubleArray,
        liftcatch: BooleanArray
    ): Int {
        var f1 = 1.0
        repeat(n) {
            f1 *= bouncefracsqrt
        }
        val k = (if (bouncefracsqrt == 1.0)
            2 * n.toDouble()
        else
            1 + f1 + 2 * bouncefracsqrt * (1 - f1 / bouncefracsqrt) / (1 - bouncefracsqrt))
        val u = 2 * g * (startCoord!!.z - bounceplane)
        val l = 2 * g * (endCoord!!.z - bounceplane)
        val f2 = f1 * f1
        val c = u - l / f2
        val kk = k * k
        val gt = g * duration

        // We are solving the following equation for v0 (the throw velocity), where
        // the constants are as defined above:
        //
        // gt = v0 + k*sqrt(v0^2+u) +- f1*sqrt(v0^2+c)
        //
        // The plus sign on the last term corresponds to a lift catch, and v0 > 0
        // corresponds to a lift (upward) throw. When this equation is converted to a
        // polynomial in the usual way, the result is quartic:
        //
        // c4*v0^4 + c3*v0^3 + c2*v0^2 + c1*v0 + c0 = 0
        //
        // When there is only one bounce, c4=0 always and we reduce to a cubic.
        val coef = DoubleArray(5)
        coef[4] = 1 + kk * kk + f2 * f2 - 2 * kk - 2 * f2 - 2 * kk * f2
        coef[3] = -4 * gt + 4 * f2 * gt + 4 * kk * gt
        coef[2] = ((6 * gt * gt + 2 * kk * kk * u + 2 * f2 * f2 * c) - 2 * f2 * c -
            2 * f2 * gt * gt - 2 * kk * gt * gt - 2 * kk * u - 2 * kk * f2 * c - 2 * kk * f2 * u)
        coef[1] = -4 * gt * gt * gt + 4 * f2 * gt * c + 4 * kk * gt * u
        coef[0] = ((gt * gt * gt * gt + kk * kk * u * u + f2 * f2 * c * c) - 2 * gt * gt * f2 * c -
            2 * kk * gt * gt * u - 2 * kk * f2 * u * c)

        val realroot = DoubleArray(4)
        val numrealroots: Int

        if (n > 1) {
            // more than one bounce, need to solve the quartic case
            for (i in 0..3) {
                coef[i] /= coef[4]
            }
            numrealroots = findRealRootsPolynomial(coef, 4, realroot)
            // numrealroots = findRealRootsQuartic(coef[0], coef[1], coef[2], coef[3], realroot);
        } else {
            // A single bounce, which reduces to a cubic polynomial (coef[4]=0)
            for (i in 0..2) {
                coef[i] /= coef[3]
            }
            numrealroots = findRealRootsPolynomial(coef, 3, realroot)
            // numrealroots = findRealRootsCubic(coef[0], coef[1], coef[2], realroot);
        }

        // Check whether the roots found are physical; due to the way the
        // equation was converted into a polynomial, nonphysical extra solutions
        // with (v0^2+c) < 0 are generated. Filter these out.
        var numroots = 0
        for (i in 0..<numrealroots) {
            val v0 = realroot[i]
            if (v0 * v0 + c >= 0) {
                root[numroots] = v0
                liftcatch[numroots] = ((gt - v0 - k * sqrt(v0 * v0 + u)) > 0)
                ++numroots
                /*
                double lhs = gt - v0 - k*Math.sqrt(v0*v0+u);
                double rhs = f1 * Math.sqrt(v0*v0+c);
                System.out.println("Root v0 = "+v0+" -- lhs = "+lhs+", rhs = "+rhs);
                */
            }
        }
        return numroots
    }

    override val type = "Bounce"

    override val minDuration: Double
        get() {
            if (bounces == 1 && hyper && forced) {
                // single hyperforce bounce is the only one with zero min duration
                return 0.0
            }

            var dlower = 0.0
            var dupper = 1.0
            while (!isFeasibleDuration(dupper)) {
                dlower = dupper
                dupper *= 2.0
            }
            while (dupper - dlower > 0.0001) {
                val davg = 0.5 * (dlower + dupper)
                if (isFeasibleDuration(davg)) {
                    dupper = davg
                } else {
                    dlower = davg
                }
            }
            return dupper
        }

    private fun isFeasibleDuration(duration: Double): Boolean {
        val root = DoubleArray(4)
        val liftcatch = BooleanArray(4)
        val numroots = solveBounceEquation(bounces, duration, root, liftcatch)

        for (i in 0..<numroots) {
            if (forced xor (root[i] < 0)) continue
            if (hyper xor liftcatch[i] xor forced) continue
            return true
        }
        return false
    }

    override val parameterDescriptors
        get() = listOf(
            ParameterDescriptor(
                "bounces",
                ParameterDescriptor.TYPE_INT,
                null,
                BOUNCES_DEF,
                bounces
            ),
            ParameterDescriptor(
                "forced",
                ParameterDescriptor.TYPE_BOOLEAN,
                null,
                FORCED_DEF,
                forced
            ),
            ParameterDescriptor(
                "hyper",
                ParameterDescriptor.TYPE_BOOLEAN,
                null,
                HYPER_DEF,
                hyper
            ),
            ParameterDescriptor(
                "bounceplane",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                BOUNCEPLANE_DEF,
                bounceplane
            ),
            ParameterDescriptor(
                "bouncefrac",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                BOUNCEFRAC_DEF,
                bouncefrac
            ),
            ParameterDescriptor(
                "g",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                G_DEF,
                g
            ),
        )

    override val startVelocity
        get() = Coordinate(bx, by, bz[0])

    override val endVelocity
        get() = Coordinate(bx, by, bz[numbounces] + 2 * az[numbounces] * (endTime - startTime))

    override fun getCoordinate(time: Double, newPosition: Coordinate) {
        if (time in startTime..endTime) {
            val t = time - startTime
            var zpos = 0.0
            for (i in 0..numbounces) {
                if (t < endtime[i] || i == numbounces) {
                    zpos = cz[i] + t * (bz[i] + az[i] * t)
                    break
                }
            }
            newPosition.setCoordinate(cx + bx * t, cy + by * t, zpos)
        }
    }

    override fun getMax2(time1: Double, time2: Double): Coordinate? {
        val tlow = max(startTime, time1)
        val thigh = min(endTime, time2)

        var result: Coordinate? = check(null, tlow, true)
        result = check(result, thigh, true)
        if (az[0] < 0) {
            val te = -bz[0] / (2 * az[0]) + startTime
            if (tlow < te && te < min(thigh, startTime + endtime[0])) {
                result = check(result, te, true)
            }
        }
        if (az[numbounces] < 0) {
            val te = -bz[numbounces] / (2 * az[numbounces]) + startTime
            if (max(tlow, startTime + endtime[numbounces - 1]) < te && te < thigh) {
                result = check(result, te, true)
            }
        }
        if (tlow < (startTime + endtime[0]) && (startTime + endtime[0]) < thigh) {
            result = check(result, startTime + endtime[0], true)
        }
        for (i in 1..<numbounces) {
            if (az[i] < 0) {
                val te = -bz[i] / (2 * az[i]) + startTime
                if (max(tlow, startTime + endtime[i - 1]) < te &&
                    te < min(thigh, startTime + endtime[i])
                ) {
                    result = check(result, te, true)
                }
            }
            if (tlow < (startTime + endtime[i]) && (startTime + endtime[i]) < thigh) {
                result = check(result, startTime + endtime[i], true)
            }
        }
        return result
    }

    override fun getMin2(time1: Double, time2: Double): Coordinate? {
        val tlow = max(startTime, time1)
        val thigh = min(endTime, time2)

        var result: Coordinate? = check(null, tlow, false)
        result = check(result, thigh, false)
        if (az[0] > 0) {
            val te = -bz[0] / (2 * az[0]) + startTime
            if (tlow < te && te < min(thigh, startTime + endtime[0])) {
                result = check(result, te, false)
            }
        }
        if (az[numbounces] > 0) {
            val te = -bz[numbounces] / (2 * az[numbounces]) + startTime
            if (max(tlow, startTime + endtime[numbounces - 1]) < te && te < thigh) {
                result = check(result, te, false)
            }
        }
        if (tlow < (startTime + endtime[0]) && (startTime + endtime[0]) < thigh) {
            result = check(result, startTime + endtime[0], false)
        }
        for (i in 1..<numbounces) {
            if (az[i] > 0) {
                val te = -bz[i] / (2 * az[i]) + startTime
                if (max(tlow, startTime + endtime[i - 1]) < te &&
                    te < min(thigh, startTime + endtime[i])
                ) {
                    result = check(result, te, false)
                }
            }
            if (tlow < (startTime + endtime[i]) && (startTime + endtime[i]) < thigh) {
                result = check(result, startTime + endtime[i], false)
            }
        }
        return result
    }

    // Find the volume (on a scale of 0 to 1) of any bounces off the floor during
    // interval [time1, time2].
    //
    // The animator doesn't adjust the volume so for now treat it as yes/no.

    fun getBounceVolume(time1: Double, time2: Double): Double {
        if (time2 < startTime || time1 > endTime) {
            return 0.0
        }
        val t1 = time1 - startTime
        val t2 = time2 - startTime

        for (i in 0..<numbounces) {
            if (t1 < endtime[i]) {
                return if (t2 > endtime[i]) 1.0 else 0.0
            }
        }
        return 0.0
    }

    companion object {
        const val BOUNCES_DEF: Int = 1  // number of bounces
        const val FORCED_DEF: Boolean = false
        const val HYPER_DEF: Boolean = false
        const val BOUNCEPLANE_DEF: Double = 0.0  // floor level
        const val BOUNCEFRAC_DEF: Double = 0.9
        const val G_DEF: Double = 980.0  // using CGS units

        /*
        // Find the real roots of the polynomial equation x^3 + k2*x^2 + k1*x + k0 = 0
        //
        // Algorithm adapted from Numerical Recipes in C (1st edition), page 157

        static protected int findRealRootsCubic(double k0, double k1, double k2, double[] roots) {
          double q = k2*k2/9.0 - k1/3.0;
          double r = k2*k2*k2/27.0 - k1*k2/6.0 + k0/2.0;
          double D = r*r - q*q*q;

          if (D > 0.0) {
              // one real root
              double k = Math.pow(Math.sqrt(D) + Math.abs(r), 1.0/3.0);
              roots[0] = ((r>0.0) ? -(k+q/k) : (k+q/k)) - k2/3.0;
              return 1;
          } else {
              // three real roots
              double theta = Math.acos(r / Math.sqrt(q*q*q)) / 3.0;
              double k = -2.0 * Math.sqrt(q);
              double p = 2.0 * Math.PI / 3.0;

              roots[0] = k * Math.cos(theta) - k2/3.0;
              roots[1] = k * Math.cos(theta + p) - k2/3.0;
              roots[2] = k * Math.cos(theta + 2.0*p) - k2/3.0;
              return 3;
          }
        }

        // The problem with this routine is that we don't know it will return all
        // real roots.  There may be cases where R and sqrt(A+-B) are both imaginary
        // and the imaginary parts cancel.
        static protected int findRealRootsQuartic(double k0, double k1, double k2, double k3, double[] roots) {
          // first solve ancillary cubic problem
          double m2 = -k2;
          double m1 = k1*k3 - 4.0*k0;
          double m0 = 4.0*k2*k0 - k1*k1 - k3*k3*k0;
          double[] realroots = new double[3];
          findRealRootsCubic(m0, m1, m2, realroots);

          double Rsq = 0.25*k3*k3 - k2 + realroots[0];
          if (Rsq < 0.0)
              return 0;       // no real roots

          int numroots = 0;
          double R = Math.sqrt(Rsq);
          double A = 0.75*k3*k3 - Rsq - 2.0*k2;
          double B = 0.25*(4.0*k3*k2 - 8.0*k1 - k3*k3*k3) / R;
          if ((A+B) >= 0.0) {
              roots[numroots++] = -0.25*k3 + 0.5*R + 0.5*Math.sqrt(A+B);
              roots[numroots++] = -0.25*k3 + 0.5*R - 0.5*Math.sqrt(A+B);
          }
          if ((A-B) >= 0.0) {
              roots[numroots++] = -0.25*k3 - 0.5*R + 0.5*Math.sqrt(A-B);
              roots[numroots++] = -0.25*k3 - 0.5*R - 0.5*Math.sqrt(A-B);
          }
          return numroots;
        }
        */

        // Helper to evaluate polynomials.

        private fun evalPolynomial(coef: DoubleArray, degree: Int, x: Double): Double {
            var result = coef[0]
            var term = x

            for (i in 1..<degree) {
                result += coef[i] * term
                term *= x
            }

            return (result + term)  // add on x^n term
        }

        // Utility function for bracketing the zero of a polynomial. If `val` is
        // returned then [`endpoint`, `val`] brackets a zero.

        private fun bracketOpenInterval(
            coef: DoubleArray, degree: Int, endpoint: Double, pinf: Boolean
        ): Double {
            val endpointpositive = (evalPolynomial(coef, degree, endpoint) > 0.0)
            var result = endpoint
            var adder = (if (pinf) 1.0 else -1.0)

            do {
                result += adder
                adder *= 2.0
            } while ((evalPolynomial(coef, degree, result) > 0.0) == endpointpositive)

            return result
        }

        // Find roots of polynomial by successive bisection.

        private fun findRoot(coef: DoubleArray, degree: Int, xlow: Double, xhigh: Double): Double {
            var xlow = xlow
            var xhigh = xhigh
            var val1 = evalPolynomial(coef, degree, xlow)
            val val2 = evalPolynomial(coef, degree, xhigh)
            var valtemp: Double
            var t: Double

            if (val1 * val2 > 0.0) {
                return 0.5 * (xlow + xhigh)  // should never happen!
            }

            while (abs(xlow - xhigh) > 1e-6) {
                t = 0.5 * (xlow + xhigh)
                valtemp = evalPolynomial(coef, degree, t)
                if (valtemp * val1 > 0.0) {
                    xlow = t
                    val1 = valtemp
                } else {
                    xhigh = t
                }
            }
            return xlow
        }

        // Find real roots of the polynomial expression:
        //    c0 + c1*x + c2*x^2 + ... + c(n-1)*x^(n-1) + x^n = 0
        //
        // where 'n' is the degree of the polynomial, and the x^n coefficient is always 1.0.
        // The c's are passed in as the 'coef' array

        private fun findRealRootsPolynomial(
            coef: DoubleArray,
            degree: Int,
            result: DoubleArray
        ): Int {
            // First a few special cases:
            if (degree == 0) {
                return 0
            } else if (degree == 1) {
                result[0] = -coef[0]
                return 1
            } else if (degree == 2) {
                // Quadratic formula with a=1.0
                val disc = coef[1] * coef[1] - 4.0 * coef[0]
                if (disc < 0.0) {
                    return 0
                } else if (disc == 0.0) {
                    result[0] = -0.5 * coef[1]
                    return 1
                } else {
                    val t = sqrt(disc)
                    result[0] = -0.5 * (coef[1] + t)
                    result[1] = -0.5 * (coef[1] - t)
                    return 2
                }
            } else if (degree == 3) {
                // Algorithm adapted from Numerical Recipes in C (1st edition), page 157
                val q = coef[2] * coef[2] / 9.0 - coef[1] / 3.0
                val r = coef[2] * coef[2] * coef[2] / 27.0 - coef[1] * coef[2] / 6.0 + coef[0] / 2.0
                val disc = r * r - q * q * q

                if (disc > 0.0) {
                    // one real root
                    val k = (sqrt(disc) + abs(r)).pow(1.0 / 3.0)
                    result[0] = (if (r > 0.0) -(k + q / k) else (k + q / k)) - coef[2] / 3.0
                    return 1
                } else {
                    // three real roots
                    val theta = acos(r / sqrt(q * q * q)) / 3.0
                    val k = -2.0 * sqrt(q)
                    val p = 2.0 * Math.PI / 3.0

                    result[0] = k * cos(theta) - coef[2] / 3.0
                    result[1] = k * cos(theta + p) - coef[2] / 3.0
                    result[2] = k * cos(theta + 2.0 * p) - coef[2] / 3.0
                    return 3
                }
            }

            // We have degree>=4, so the special cases don't apply. Proceed by finding
            // the extrema of our polynomial and using these to bracket each zero.
            val dcoef = DoubleArray(degree - 1)
            val extremum = DoubleArray(degree - 1)
            for (i in 0..<(degree - 1)) {
                dcoef[i] = (i + 1) * coef[i + 1] / degree.toDouble()
            }
            val numextrema: Int = findRealRootsPolynomial(dcoef, degree - 1, extremum)

            val pinfpositive = true
            val minfpositive = ((degree % 2) == 0)

            var numroots = 0

            if (numextrema == 0) {
                val zeropositive = (coef[0] > 0.0)

                if (zeropositive != pinfpositive) {
                    val endpoint2: Double = bracketOpenInterval(coef, degree, 0.0, true)
                    result[numroots++] = findRoot(coef, degree, 0.0, endpoint2)
                }
                if (zeropositive != minfpositive) {
                    val endpoint2: Double = bracketOpenInterval(coef, degree, 0.0, false)
                    result[numroots++] = findRoot(coef, degree, endpoint2, 0.0)
                }
                return numroots
            }

            // Sort the extrema using a bubble sort
            for (i in 0..<numextrema) {
                for (j in i..<numextrema) {
                    if (extremum[i] > extremum[j]) {
                        val temp = extremum[i]
                        extremum[i] = extremum[j]
                        extremum[j] = temp
                    }
                }
            }

            val extremumpositive = BooleanArray(numextrema)
            for (i in 0..<numextrema) {
                extremumpositive[i] = (evalPolynomial(coef, degree, extremum[i]) > 0.0)
            }

            if (minfpositive != extremumpositive[0]) {
                // there is a zero left of the first extremum; bracket it and find it
                val endpoint2: Double = bracketOpenInterval(coef, degree, extremum[0], false)
                result[numroots++] = findRoot(coef, degree, endpoint2, extremum[0])
            }

            for (i in 0..<(numextrema - 1)) {
                if (extremumpositive[i] != extremumpositive[i + 1]) {
                    result[numroots++] = findRoot(coef, degree, extremum[i], extremum[i + 1])
                }
            }

            if (pinfpositive != extremumpositive[numextrema - 1]) {
                // there is a zero right of the last extremum; bracket it and find it
                val endpoint2: Double =
                    bracketOpenInterval(coef, degree, extremum[numextrema - 1], true)
                result[numroots++] = findRoot(coef, degree, extremum[numextrema - 1], endpoint2)
            }

            return numroots
        }
    }
}
