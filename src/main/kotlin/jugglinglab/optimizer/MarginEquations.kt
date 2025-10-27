//
// MarginEquations.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.optimizer

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.core.Constants
import jugglinglab.jml.*
import jugglinglab.util.*
import java.util.*
import kotlin.math.abs

class MarginEquations() {
    // number of variables in margin equations
    var varsNum: Int = 0
    // corresponding JMLEvents, one per variable
    lateinit var varsEvents: MutableList<JMLEvent>
    // current values of variables
    lateinit var varsValues: DoubleArray
    // minimum values of variables
    lateinit var varsMin: DoubleArray
    // maximum values of variables
    lateinit var varsMax: DoubleArray
    // number of distinct margin equations
    var marginsNum: Int = 0
    // array of linear equations
    lateinit var marginsEqs: MutableList<LinearEquation>

    constructor(pat: JMLPattern) : this() {
        findeqs(pat)
    }

    // returns current value of a given margin equation
    fun getMargin(eqn: Int): Double {
        var m = 0.0
        for (i in 0..<varsNum) {
            m += marginsEqs[eqn].coef(i) * varsValues[i]
        }

        return abs(m) + marginsEqs[eqn].constant()
    }

    /*
    val margin: Double
        // Find minimum value of all margins together.
        get() {
            if (marginsNum == 0) {
                return -100.0
            }

            var minmargin = getMargin(0)
            for (i in 1..<marginsNum) {
                minmargin = min(minmargin, getMargin(i))
            }
            return minmargin
        }
     */

    @Suppress("UnnecessaryVariable")
    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    private fun findeqs(pat: JMLPattern) {
        if (Constants.DEBUG_OPTIMIZE) {
            println("finding margin equations")
        }
        if (pat.numberOfJugglers > 1) {
            throw JuggleExceptionUser(errorstrings.getString("Error_optimizer_no_passing"))
        }
        if (pat.isBouncePattern()) {
            throw JuggleExceptionUser(errorstrings.getString("Error_optimizer_no_bouncing"))
        }

        // Step 1: Lay out the pattern. This generates two things we need, the pattern event
        // list and the pattern pathlink list.
        pat.layoutPattern()
        val events = pat.eventList
        val pathlinks = pat.pathLinks

        // Step 2: Figure out the variables in the margin equations. Find the master events
        // in the pattern, in particular the ones that are throws or catches. The x-coordinate
        // of each will be a free variable in our equations.
        val variableEvents = ArrayList<JMLEvent>()

        var maxValue = 0.0
        var g = 980.0 // cm per second^2

        var ev = events
        while (ev != null) {
            if (ev.isMaster) {
                for (tr in ev.transitions()) {
                    val type = tr.transType
                    if (type == JMLTransition.TRANS_THROW || type == JMLTransition.TRANS_CATCH ||
                        type == JMLTransition.TRANS_SOFTCATCH || type == JMLTransition.TRANS_GRABCATCH
                    ) {
                        ++varsNum
                        variableEvents.add(ev)
                        val coord = ev.localCoordinate
                        if (abs(coord.x) > maxValue) maxValue = abs(coord.x)

                        if (type == JMLTransition.TRANS_THROW) {
                            val pl = ParameterList(tr.mod)
                            val gparam = pl.getParameter("g")
                            if (gparam != null) {
                                try {
                                    g = parseDouble(gparam)
                                } catch (_: NumberFormatException) {
                                }
                            }
                        }
                        break
                    }
                }
            }
            ev = ev.getNext()
        }
        if (Constants.DEBUG_OPTIMIZE) {
            println("   number of variables = $varsNum")
            println("   maxValue = $maxValue")
            println("   g = $g")
        }

        // Step 3: Set up the arrays containing the current values of our variables, their
        // minimum and maximum allowed values, and corresponding JMLEvents
        varsEvents = ArrayList<JMLEvent>(varsNum)
        varsValues = DoubleArray(varsNum)
        varsMin = DoubleArray(varsNum)
        varsMax = DoubleArray(varsNum)

        for (i in 0..<varsNum) {
            ev = variableEvents[i]
            val coord = ev.localCoordinate
            val type = ev.getTransition(0).transType

            varsEvents.add(ev)
            varsValues[i] = coord.x
            // optimization won't move events to the other side of the body
            if (varsValues[i] > 0) {
                varsMin[i] = 0.1 * maxValue
                varsMax[i] = maxValue

                if (type == JMLTransition.TRANS_THROW) {
                    varsMax[i] *= 0.9
                }
            } else {
                varsMin[i] = -maxValue
                varsMax[i] = -0.1 * maxValue

                if (type == JMLTransition.TRANS_THROW) {
                    varsMin[i] *= 0.9
                }
            }
            if (Constants.DEBUG_OPTIMIZE) {
                println("   variable $i min = ${varsMin[i]}, max = ${varsMax[i]}")
            }
        }

        // Step 4: Find the maximum radius of props in the pattern, used in the margin
        // calculation below
        var propradius = 0.0
        for (i in 0..<pat.numberOfProps) {
            val thisprop = 0.5 * pat.getProp(i + 1).getWidth()
            if (thisprop > propradius) {
                propradius = thisprop
            }
        }
        if (Constants.DEBUG_OPTIMIZE) {
            println("   propradius = $propradius")
        }

        // Step 5: Identify the "master pathlinks", the non-hand pathlinks starting on
        // master events. Put them into a linear array for convenience
        var masterplNum = 0
        val masterpl: MutableList<PathLink> = ArrayList<PathLink>()
        for (pathlink in pathlinks) {
            for (pl in pathlink) {
                if (!pl!!.isInHand && pl.startEvent.isMaster) {
                    ++masterplNum
                    masterpl.add(pl)
                }
            }
        }
        if (Constants.DEBUG_OPTIMIZE) {
            println("   number of master pathlinks = $masterplNum")
        }

        // Step 6: Figure out all distinct potential collisions in the pattern, and the
        // equation determining throw error margin for each one.
        //
        // Find all pathlink pairs (P1, P2) such that:
        // * P1 and P2 both represent paths through the air (not in the hands)
        // * P1 and P2 are either both air paths, or both bounced paths
        // * P1 starts on a master event
        // * P2 does not start on the same event as P1
        // * P2 starts no earlier than P1
        // * if P1 and P2 start at the same time, then P2 ends no earlier than P1
        // * if P1 and P2 start and end at the same time, then P2 is not from a smaller juggler number
        // than P1
        // * if P1 and P2 start and end at the same time, and are from the same juggler, then P1 is from
        // the right hand
        // * P1 and P2 can collide (t_same is defined and occurs when both are in the air)
        var symDelay = -1.0
        var symSwitchdelay = false
        for (sym in pat.symmetries()) {
            when (sym.getType()) {
                JMLSymmetry.TYPE_DELAY -> symDelay = sym.delay
                JMLSymmetry.TYPE_SWITCHDELAY -> symSwitchdelay = true
                JMLSymmetry.TYPE_SWITCH -> throw JuggleExceptionUser(errorstrings.getString("Error_no_optimize_switch"))
            }
        }

        val eqns = ArrayList<DoubleArray>()

        if (Constants.DEBUG_OPTIMIZE) {
            println("potential collisions:")
        }
        for (i in 0..<masterplNum) {
            for (j in 0..<masterplNum) {
                val mpl1 = masterpl[i]
                val mpl2 = masterpl[j]

                // enumerate all of the ways that mpl2 could collide with mpl1.
                val mpl1Start = mpl1.startEvent.getT()
                val mpl1End = mpl1.endEvent.getT()
                val mpl2Start = mpl2.startEvent.getT()
                val mpl2End = mpl2.endEvent.getT()
                var delay = 0.0
                var invertMpl2 = false

                do {
                    var canCollide = true

                    // implement the criteria described above
                    if (delay == 0.0 && mpl1.startEvent === mpl2.startEvent) {
                        canCollide = false
                    }
                    if (mpl1Start > (mpl2Start + delay)) {
                        canCollide = false
                    } else if (mpl1Start == (mpl2Start + delay)) {
                        if (mpl1End > (mpl2End + delay)) {
                            canCollide = false
                        } else if (mpl1End == (mpl2End + delay)) {
                            if (mpl1.startEvent.getJuggler() > mpl2.startEvent.getJuggler()) {
                                canCollide = false
                            } else if (mpl1.startEvent.getJuggler() == mpl2.startEvent.getJuggler()) {
                                if (mpl1.startEvent.getHand() == HandLink.LEFT_HAND) {
                                    canCollide = false
                                }
                            }
                        }
                    }

                    var tsame = -1.0
                    val tsameDenom = (mpl2Start + mpl2End + 2 * delay) - (mpl1Start + mpl1End)
                    if (tsameDenom == 0.0) {
                        canCollide = false
                    }

                    if (canCollide) {
                        tsame =
                            ((mpl2Start + delay) * (mpl2End + delay) - mpl1Start * mpl1End) / tsameDenom

                        if (tsame !in mpl1Start..mpl1End || tsame < (mpl2Start + delay) || tsame > (mpl2End + delay)) {
                            canCollide = false
                        }
                    }

                    if (canCollide) {
                        // We have another potential collision in the pattern, and a new margin equation.
                        //
                        // The error margin associated with a potential collision is a linear function
                        // of the x-coordinates of throw and catch points, for each of the two arcs
                        // (4 coordinates in all):
                        //
                        // margin = sum_i {coef_i * x_i} + coef_varsNum

                        val coefs = DoubleArray(varsNum + 1)

                        // Calculate the angular margin of error (in radians) with the relations:
                        //
                        // margin * v_y1 * (tsame - t_t1) + margin * v_y2 * (tsame - t_t2)
                        // = (horizontal distance btwn arcs at time t_same) - 2 * propradius
                        // = abs(
                        //     (x_t1 * (t_c1 - tsame) + x_c1 * (tsame - t_t1)) / (t_c1 - t_t1)
                        //    - (x_t2 * (t_c2 - tsame) + x_c2 * (tsame - t_t2)) / (t_c2 - t_t2)
                        //   ) - 2 * propradius
                        //
                        // where the vertical throwing velocities are:
                        // v_y1 = 0.5 * g * (t_c1 - t_t1)
                        // v_y2 = 0.5 * g * (t_c2 - t_t2)
                        //
                        // and t_t1, t_c1 are the throw and catch time of arc 1, etc.
                        val tT1 = mpl1Start
                        val tC1 = mpl1End
                        val tT2 = mpl2Start + delay
                        val tC2 = mpl2End + delay

                        val vY1 = 0.5 * g * (tC1 - tT1)
                        val vY2 = 0.5 * g * (tC2 - tT2)
                        var denom = vY1 * (tsame - tT1) + vY2 * (tsame - tT2)

                        if (denom > EPSILON) {
                            denom *= Math.PI / 180 // so margin will be in degrees

                            var coefT1 = (tC1 - tsame) / ((tC1 - tT1) * denom)
                            var coefC1 = (tsame - tT1) / ((tC1 - tT1) * denom)
                            var coefT2 = -(tC2 - tsame) / ((tC2 - tT2) * denom)
                            var coefC2 = -(tsame - tT2) / ((tC2 - tT2) * denom)
                            val coef0 = -2 * propradius / denom

                            val t1Varnum: Int
                            val c1Varnum: Int
                            val t2Varnum: Int
                            val c2Varnum: Int

                            var mplev = mpl1.startEvent
                            if (!mplev.isMaster) {
                                if (mplev.getHand() != mplev.getMaster().getHand()) {
                                    coefT1 = -coefT1
                                }
                                mplev = mplev.getMaster()
                            }
                            t1Varnum = variableEvents.indexOf(mplev)
                            mplev = mpl1.endEvent
                            if (!mplev.isMaster) {
                                if (mplev.getHand() != mplev.getMaster().getHand()) {
                                    coefC1 = -coefC1
                                }
                                mplev = mplev.getMaster()
                            }
                            c1Varnum = variableEvents.indexOf(mplev)
                            mplev = mpl2.startEvent
                            if (!mplev.isMaster) {
                                if (mplev.getHand() != mplev.getMaster().getHand()) {
                                    coefT2 = -coefT2
                                }
                                mplev = mplev.getMaster()
                            }
                            t2Varnum = variableEvents.indexOf(mplev)
                            mplev = mpl2.endEvent
                            if (!mplev.isMaster) {
                                if (mplev.getHand() != mplev.getMaster().getHand()) {
                                    coefC2 = -coefC2
                                }
                                mplev = mplev.getMaster()
                            }
                            c2Varnum = variableEvents.indexOf(mplev)

                            if (t1Varnum < 0 || c1Varnum < 0 || t2Varnum < 0 || c2Varnum < 0) {
                                throw JuggleExceptionInternal("Could not find master event in variableEvents")
                            }

                            if (invertMpl2) {
                                coefT2 = -coefT2
                                coefC2 = -coefC2
                            }

                            coefs[t1Varnum] += coefT1
                            coefs[c1Varnum] += coefC1
                            coefs[t2Varnum] += coefT2
                            coefs[c2Varnum] += coefC2
                            coefs[varsNum] = coef0

                            // define coefficients so distance (ignoring prop dimension) is nonnegative
                            var dist = 0.0
                            for (k in 0..<varsNum) dist += coefs[k] * varsValues[k]
                            if (dist < 0) {
                                for (k in 0..<varsNum) {
                                    if (coefs[k] != 0.0) {
                                        coefs[k] = -coefs[k]
                                    }
                                }
                            }

                            eqns.add(coefs)
                            ++marginsNum

                            if (Constants.DEBUG_OPTIMIZE) {
                                println("   mpl[$i] and mpl[$j] at tsame = $tsame")
                            }
                        }
                    }

                    if (symSwitchdelay) {
                        delay += 0.5 * symDelay
                        invertMpl2 = !invertMpl2
                    } else {
                        delay += symDelay
                    }
                } while (mpl1End > (mpl2Start + delay))
            }
        }

        // Step 7: De-duplicate the list of equations; for various reasons the same equation
        // can appear multiple times.
        if (Constants.DEBUG_OPTIMIZE) {
            println("total margin equations = $marginsNum")
            for (i in 0..<marginsNum) {
                val sb = StringBuilder()
                sb.append("{ ")
                val temp = eqns[i]
                for (j in 0..varsNum) {
                    sb.append(toStringRounded(temp[j], 4))
                    if (j == (varsNum - 1)) {
                        sb.append(" : ")
                    } else if (j != varsNum) {
                        sb.append(", ")
                    }
                }
                var dtemp = temp[varsNum]
                for (j in 0..<varsNum) {
                    dtemp += temp[j] * varsValues[j]
                }
                sb.append(" } --> ").append(toStringRounded(dtemp, 4))

                println("   eq[$i] = $sb")
            }
            println("de-duplicating equations...")
        }

        var origRow = 1

        run {
            var i = 1
            while (i < marginsNum) {
                var dupoverall = false
                val rowi = eqns[i]

                var j = 0
                while (!dupoverall && j < i) {
                    val rowj = eqns[j]
                    var duprow = true
                    for (k in 0..varsNum) {
                        if (rowi[k] < (rowj[k] - EPSILON) || rowi[k] > (rowj[k] + EPSILON)) {
                            duprow = false
                            break
                        }
                    }
                    dupoverall = duprow
                    j++
                }

                if (dupoverall) {
                    if (Constants.DEBUG_OPTIMIZE) {
                        println("   removed duplicate equation $origRow")
                    }
                    eqns.removeAt(i)
                    --i
                    --marginsNum
                }
                if (Constants.DEBUG_OPTIMIZE) {
                    ++origRow
                }
                ++i
            }
        }

        // Step 8: Move the equations into an array, and sort it based on margins at the
        // current values of the variables.
        marginsEqs = ArrayList<LinearEquation>(marginsNum)
        for (i in 0..<marginsNum) {
            val le = LinearEquation(varsNum)
            le.setCoefficients(eqns[i])
            marginsEqs.add(le)
        }

        if (Constants.DEBUG_OPTIMIZE) {
            println("total margin equations = $marginsNum")
            for (i in 0..<marginsNum) {
                val sb = StringBuilder()
                sb.append("{ ")
                for (j in 0..varsNum) {
                    sb.append(toStringRounded(marginsEqs[i].coef(j), 4))
                    if (j == (varsNum - 1)) {
                        sb.append(" : ")
                    } else if (j != varsNum) {
                        sb.append(", ")
                    }
                }
                var dtemp = marginsEqs[i].constant()
                for (j in 0..<varsNum) {
                    dtemp += marginsEqs[i].coef(j) * varsValues[j]
                }
                sb.append(" } --> ").append(toStringRounded(dtemp, 4))

                println("   eq[$i] = $sb")
            }
        }

        sort()

        if (Constants.DEBUG_OPTIMIZE) {
            println("sorted:")
            for (i in 0..<marginsNum) {
                val sb = StringBuilder()
                sb.append("{ ")
                for (j in 0..varsNum) {
                    sb.append(toStringRounded(marginsEqs[i].coef(j), 4))
                    if (j == (varsNum - 1)) {
                        sb.append(" : ")
                    } else if (j != varsNum) {
                        sb.append(", ")
                    }
                }
                var dtemp = marginsEqs[i].constant()
                for (j in 0..<varsNum) {
                    dtemp += marginsEqs[i].coef(j) * varsValues[j]
                }
                sb.append(" } --> ").append(toStringRounded(dtemp, 4))

                println("   eq[$i] = $sb")
            }
        }
    }

    fun sort() {
        val comp =
            object : Comparator<LinearEquation> {
                override fun compare(eq1: LinearEquation, eq2: LinearEquation): Int {
                    if (eq1.done && !eq2.done) {
                        return -1
                    }
                    if (!eq1.done && eq2.done) {
                        return 1
                    }

                    var m1 = eq1.constant()
                    var m2 = eq2.constant()
                    for (i in 0..<varsNum) {
                        m1 += eq1.coef(i) * varsValues[i]
                        m2 += eq2.coef(i) * varsValues[i]
                    }
                    if (m1 < m2) {
                        return -1
                    } else if (m1 > m2) {
                        return 1
                    }
                    return 0
                }

                override fun equals(other: Any?): Boolean {
                    return false
                }
            }

        marginsEqs.sortWith(comp)
    }

    companion object {
        const val EPSILON: Double = 0.000001
    }
}
