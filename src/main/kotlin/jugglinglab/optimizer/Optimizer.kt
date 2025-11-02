//
// Optimizer.kt
//
// Class that optimizes a JMLPattern by maximizing the allowed margin of error
// in throwing angle. (Maximize the minimum margin of error across all throws
// in the pattern.)
//
// It does this by adjusting the throw and catch positions, leaving throw and
// catch times unchanged.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.optimizer

import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolver.ResultStatus
import com.google.ortools.linearsolver.MPVariable
import jugglinglab.JugglingLab
import jugglinglab.JugglingLab.errorstrings
import jugglinglab.core.Constants
import jugglinglab.jml.JMLPattern
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class Optimizer private constructor(val pat: JMLPattern) {
    private val me: MarginEquations = MarginEquations(pat)
    private val pinned: BooleanArray =
        if (me.marginsNum > 0) {
            // set to true when variable is done optimizing
            BooleanArray(me.varsNum)
        } else {
            BooleanArray(0)
        }

    // Run the MILP solver to maximize the minimum throwing margin of error in
    // the pattern.
    //
    // Returns true on success, false on failure.

    private fun runMILP(): Boolean {
        // use COIN-OR CBC solver backend
        val solver =
            MPSolver("JugglingLab", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)

        // Define the problem for the MILP solver.
        // variables
        val x = arrayOfNulls<MPVariable>(me.varsNum)
        for (i in 0..<me.varsNum) {
            if (!pinned[i]) {
                x[i] = solver.makeNumVar(me.varsMin[i], me.varsMax[i], "x$i")
            }
        }

        // boolean variables are used to model disjunctive constraints below
        val z = arrayOfNulls<MPVariable>(me.marginsNum)
        for (i in 0..<me.marginsNum) {
            if (!me.marginsEqs[i].done) {
                z[i] = solver.makeBoolVar("z$i")
            }
        }

        val err = solver.makeNumVar(-INFINITY, INFINITY, "err")

        // constraints
        val c = arrayOfNulls<MPConstraint>(me.marginsNum * 2)
        for (i in 0..<me.marginsNum) {
            if (me.marginsEqs[i].done) {
                continue
            }

            // Calculate upper bound on abs(Ax) in order to implement the
            // constraint err < abs(Ax + b), which becomes two disjunctive
            // constraints since the feasible set is concave.
            var maxAx = 0.0
            var minAx = 0.0

            for (j in 0..<me.varsNum) {
                val coef = me.marginsEqs[i].coef(j)

                if (coef > 0) {
                    maxAx += coef * me.varsMax[j]
                    minAx += coef * me.varsMin[j]
                } else {
                    maxAx += coef * me.varsMin[j]
                    minAx += coef * me.varsMax[j]
                }
            }

            val bound = 2 * max(abs(maxAx), abs(minAx)) + 1

            // (-Ax) <= -err + b_i + bound * z_i
            var rhs = me.marginsEqs[i].constant()
            for (j in 0..<me.varsNum) {
                if (pinned[j]) {
                    rhs += me.marginsEqs[i].coef(j) * me.varsValues[j]
                }
            }
            c[2 * i] = solver.makeConstraint(-INFINITY, rhs, "c" + i + "a")
            c[2 * i]!!.setCoefficient(err, 1.0)
            c[2 * i]!!.setCoefficient(z[i], -bound)
            for (j in 0..<me.varsNum) {
                if (!pinned[j]) {
                    val coef = me.marginsEqs[i].coef(j)

                    if (coef != 0.0) {
                        c[2 * i]!!.setCoefficient(x[j], -coef)
                    }
                }
            }

            // Ax <= -err + b_i + bound * (1 - z_i)
            rhs = me.marginsEqs[i].constant() + bound
            for (j in 0..<me.varsNum) {
                if (pinned[j]) {
                    rhs -= me.marginsEqs[i].coef(j) * me.varsValues[j]
                }
            }
            c[2 * i + 1] = solver.makeConstraint(-INFINITY, rhs, "c" + i + "b")
            c[2 * i + 1]!!.setCoefficient(err, 1.0)
            c[2 * i + 1]!!.setCoefficient(z[i], bound)
            for (j in 0..<me.varsNum) {
                if (!pinned[j]) {
                    val coef = me.marginsEqs[i].coef(j)

                    if (coef != 0.0) {
                        c[2 * i + 1]!!.setCoefficient(x[j], coef)
                    }
                }
            }
        }

        // objective: Maximize `err`.
        val objective = solver.objective()
        objective.setCoefficient(err, 1.0)
        objective.setMaximization()

        if (Constants.DEBUG_OPTIMIZE) {
            println("MILP number of variables = " + solver.numVariables())
            println("MILP number of constraints = " + solver.numConstraints())
            println("starting solve...")
        }

        // run solver
        val resultStatus = solver.solve()

        if (Constants.DEBUG_OPTIMIZE) {
            println("done")
        }

        if (resultStatus != ResultStatus.OPTIMAL) {
            if (Constants.DEBUG_OPTIMIZE) {
                println("The problem does not have an optimal solution!")
            }
            return false
        }

        for (i in 0..<me.varsNum) {
            if (!pinned[i]) {
                me.varsValues[i] = x[i]!!.solutionValue()
            }
        }

        if (Constants.DEBUG_OPTIMIZE) {
            println("Solution:")
            println("   Objective value = " + objective.value())
            for (i in 0..<me.varsNum) {
                if (!pinned[i]) {
                    println("   x[" + i + "] = " + x[i]!!.solutionValue())
                }
            }
            for (i in 0..<me.marginsNum) {
                if (!me.marginsEqs[i].done) {
                    println("   z[" + i + "] = " + z[i]!!.solutionValue())
                }
            }

            println("Problem solved in " + solver.wallTime() + " milliseconds")
            println("                  " + solver.iterations() + " iterations")
            println("                  " + solver.nodes() + " branch-and-bound nodes")
        }

        return true
    }

    // Mark variables and equations that have been solved, and should be
    // excluded from further optimization passes.
    //
    // The variables (throw and catch positions) participating in a minimum-
    // margin equation are fixed; remaining variables need to be solved in
    // subsequent runs.

    private fun markFinished() {
        // Mark newly-pinned variables (present in minimum-margin equation(s))
        var minmargin: Double = INFINITY
        for (i in 0..<me.marginsNum) {
            if (!me.marginsEqs[i].done && me.getMargin(i) < minmargin) {
                minmargin = me.getMargin(i)
            }
        }
        if (Constants.DEBUG_OPTIMIZE) {
            println("minimum active margin = $minmargin")
        }

        for (i in 0..<me.marginsNum) {
            if (me.marginsEqs[i].done) {
                continue
            }

            val mar = me.getMargin(i)
            if (Constants.DEBUG_OPTIMIZE) {
                println("   margin[$i] = $mar")
            }

            val diff = mar - minmargin
            if (diff < -EPSILON || diff > EPSILON) {
                continue
            }

            for (j in 0..<me.varsNum) {
                val cj = me.marginsEqs[i].coef(j)

                if (!pinned[j] && (cj > EPSILON || cj < -EPSILON)) {
                    pinned[j] = true

                    if (Constants.DEBUG_OPTIMIZE) {
                        println("pinned variable x$j")
                    }
                }
            }
        }

        // Mark newly-completed equations (all variables pinned)
        for (row in 0..<me.marginsNum) {
            if (me.marginsEqs[row].done) {
                continue
            }

            var eqndone = true
            for (i in 0..<me.varsNum) {
                val ci = me.marginsEqs[row].coef(i)
                if (!pinned[i] && (ci > EPSILON || ci < -EPSILON)) {
                    eqndone = false
                    break
                }
            }
            if (eqndone) {
                me.marginsEqs[row].done = true
                if (Constants.DEBUG_OPTIMIZE) {
                    println("equation $row done")
                }
            }
        }
    }

    // Do the overall pattern optimization.

    private fun doOptimizationMILP(): Boolean {
        if (Constants.DEBUG_OPTIMIZE) {
            println("\noptimizing...")
        }

        var stage = 1

        while (true) {
            if (Constants.DEBUG_OPTIMIZE) {
                println("---- MILP stage $stage:")
            }

            val optimal = runMILP()
            if (!optimal) {
                if (Constants.DEBUG_OPTIMIZE) {
                    println("---- Bailing from optimizer")
                }
                return false
            }

            markFinished()

            var done = true
            for (i in 0..<me.marginsNum) {
                if (!me.marginsEqs[i].done) {
                    done = false
                    break
                }
            }
            if (done) {
                break
            }

            ++stage
        }

        if (Constants.DEBUG_OPTIMIZE) {
            println("---- Optimizer finished, updating pattern")
        }

        return true
    }

    // Update the pattern's JMLEvents with the current variable values, for
    // variables that were solved by optimizer.

    private fun updatePattern() {
        for (i in 0..<me.varsNum) {
            if (pinned[i]) {
                val ev = me.varsEvents[i]
                val newx = (me.varsValues[i] * 100.0).roundToInt().toDouble() / 100.0
                val coord = ev.localCoordinate
                coord.x = newx
                ev.localCoordinate = coord
            }
        }
        pat.setNeedsLayout()
    }

    companion object {
        private const val EPSILON: Double = 0.0000001
        private const val INFINITY: Double = Double.POSITIVE_INFINITY
        private var _optimizerLoaded: Boolean = false
        private var _optimizerAvailable: Boolean = false

        @JvmStatic
        fun optimizerAvailable(): Boolean {
            loadOptimizer()
            return _optimizerAvailable
        }

        @JvmStatic
        private fun loadOptimizer() {
            if (_optimizerLoaded) {
                return
            }
            _optimizerLoaded = true

            // load the Google OR-Tools library, if available
            try {
                System.loadLibrary("jniortools")
                _optimizerAvailable = true
            } catch (e: UnsatisfiedLinkError) {
                // The following is helpful to debug issues loading the OR-Tools
                // libraries on Linux. A common issue is a system version of glibc
                // that is older than the library requires.
                if (JugglingLab.isLinux) {
                    println(e)
                }
            }
        }

        // Optimize a pattern, if possible.
        //
        // This is the main entry point into the optimizer.

        @JvmStatic
        @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
        fun optimize(pat: JMLPattern): JMLPattern {
            if (!optimizerAvailable()) {
                if (Constants.DEBUG_OPTIMIZE) {
                    println("---- Optimizer not loaded, bailing")
                }
                throw JuggleExceptionUser(errorstrings.getString("Error_optimizer_unavailable"))
            }

            val opt = Optimizer(pat)

            if (opt.me.marginsNum > 0) {
                val success = opt.doOptimizationMILP()
                if (success) {
                    opt.updatePattern()
                } else {
                    throw JuggleExceptionUser(errorstrings.getString("Error_optimizer_failed"))
                }
            } else if (Constants.DEBUG_OPTIMIZE) {
                // do nothing if no margin equations
                println("---- No margin equations, bailing")
            }

            return pat
        }
    }
}
