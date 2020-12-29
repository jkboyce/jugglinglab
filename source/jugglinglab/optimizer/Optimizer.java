// Optimizer.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.optimizer;

import java.util.ResourceBundle;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import jugglinglab.core.Constants;
import jugglinglab.jml.*;
import jugglinglab.util.*;


// Class that optimizes a JMLPattern by maximizing the allowed margin of error
// in throwing angle. (Maximize the minimum margin of error across all throws
// in the pattern.)
//
// It does this by adjusting the throw and catch positions, leaving throw and
// catch times unchanged.

public class Optimizer {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
    static final protected double epsilon = 0.0000001;
    static final protected double infinity = java.lang.Double.POSITIVE_INFINITY;
    static protected boolean optimizer_loaded = false;

    static {
        // Load the Google OR-Tools library, if available
        try {
            System.loadLibrary("jniortools");
            optimizer_loaded = true;
        } catch (java.lang.UnsatisfiedLinkError e) {}
    }

    public static boolean optimizerAvailable() {
        return optimizer_loaded;
    }

    // Optimizes a pattern, if possible.
    //
    // This is the main entry point into the optimizer.
    public static JMLPattern optimize(JMLPattern pat) throws JuggleExceptionInternal, JuggleExceptionUser {
        if (!optimizerAvailable()) {
            if (Constants.DEBUG_OPTIMIZE)
                System.out.println("---- Optimizer not loaded, bailing");
            throw new JuggleExceptionUser(errorstrings.getString("Error_optimizer_unavailable"));
        }

        Optimizer opt = new Optimizer(pat);

        if (opt.me.marginsNum > 0) {
            boolean success = opt.doOptimizationMILP();
            if (success)
                opt.updatePattern();
            else
                throw new JuggleExceptionUser(errorstrings.getString("Error_optimizer_failed"));
        } else if (Constants.DEBUG_OPTIMIZE) {
            // do nothing if no margin equations
            System.out.println("---- No margin equations, bailing");
        }

        return pat;
    }


    // Instance variables and methods

    protected JMLPattern pat;
    protected MarginEquations me;
    protected boolean[] pinned;     // true when variable is done optimizing


    protected Optimizer(JMLPattern p) throws JuggleExceptionInternal, JuggleExceptionUser {
        pat = p;
        me = new MarginEquations(p);

        if (me.marginsNum == 0)
            return;

        pinned = new boolean[me.varsNum];
    }


    // Runs the MILP solver to maximize the minimum throwing margin of error in
    // the pattern.
    //
    // Returns true on success, false on failure.
    protected boolean runMILP() {
        // use COIN-OR CBC solver backend
        MPSolver solver = new MPSolver(
            "JugglingLab",
            MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING
        );

        // Define the problem for the MILP solver.
        // Variables

        MPVariable[] x = new MPVariable[me.varsNum];
        for (int i = 0; i < me.varsNum; ++i) {
            if (!pinned[i])
                x[i] = solver.makeNumVar(me.varsMin[i], me.varsMax[i], "x" + i);
        }

        // boolean variables are used to model disjunctive constraints below
        MPVariable[] z = new MPVariable[me.marginsNum];
        for (int i = 0; i < me.marginsNum; ++i) {
            if (!me.marginsEqs[i].done())
                z[i] = solver.makeBoolVar("z" + i);
        }

        MPVariable err = solver.makeNumVar(-infinity, infinity, "err");

        // Constraints

        MPConstraint[] c = new MPConstraint[me.marginsNum * 2];
        for (int i = 0; i < me.marginsNum; ++i) {
            if (me.marginsEqs[i].done())
                continue;

            // Calculate upper bound on abs(Ax) in order to implement the
            // constraint err < abs(Ax + b), which becomes two disjunctive
            // constraints since the feasible set is concave.
            double maxAx = 0.0;
            double minAx = 0.0;

            for (int j = 0; j < me.varsNum; ++j) {
                double coef = me.marginsEqs[i].coef(j);

                if (coef > 0.0) {
                    maxAx += coef * me.varsMax[j];
                    minAx += coef * me.varsMin[j];
                } else {
                    maxAx += coef * me.varsMin[j];
                    minAx += coef * me.varsMax[j];
                }
            }

            double bound = 2.0 * Math.max(Math.abs(maxAx), Math.abs(minAx)) + 1.0;

            // (-Ax) <= -err + b_i + bound * z_i
            double rhs = me.marginsEqs[i].constant();
            for (int j = 0; j < me.varsNum; ++j) {
                if (pinned[j])
                    rhs += me.marginsEqs[i].coef(j) * me.varsValues[j];
            }
            c[2*i] = solver.makeConstraint(-infinity, rhs, "c" + i + "a");
            c[2*i].setCoefficient(err, 1);
            c[2*i].setCoefficient(z[i], -bound);
            for (int j = 0; j < me.varsNum; ++j) {
                if (!pinned[j]) {
                    double coef = me.marginsEqs[i].coef(j);

                    if (coef != 0.0)
                        c[2*i].setCoefficient(x[j], -coef);
                }
            }

            // Ax <= -err + b_i + bound * (1 - z_i)
            rhs = me.marginsEqs[i].constant() + bound;
            for (int j = 0; j < me.varsNum; ++j) {
                if (pinned[j])
                    rhs -= me.marginsEqs[i].coef(j) * me.varsValues[j];
            }
            c[2*i + 1] = solver.makeConstraint(-infinity, rhs, "c" + i + "b");
            c[2*i + 1].setCoefficient(err, 1);
            c[2*i + 1].setCoefficient(z[i], bound);
            for (int j = 0; j < me.varsNum; ++j) {
                if (!pinned[j]) {
                    double coef = me.marginsEqs[i].coef(j);

                    if (coef != 0.0)
                        c[2*i + 1].setCoefficient(x[j], coef);
                }
            }
        }

        // Objective: Maximize `err`.

        MPObjective objective = solver.objective();
        objective.setCoefficient(err, 1);
        objective.setMaximization();

        if (Constants.DEBUG_OPTIMIZE) {
            System.out.println("MILP number of variables = " + solver.numVariables());
            System.out.println("MILP number of constraints = " + solver.numConstraints());
            System.out.println("starting solve...");
        }

        // Run solver

        final MPSolver.ResultStatus resultStatus = solver.solve();

        if (Constants.DEBUG_OPTIMIZE)
            System.out.println("done");

        if (resultStatus != MPSolver.ResultStatus.OPTIMAL) {
            if (Constants.DEBUG_OPTIMIZE)
                System.out.println("The problem does not have an optimal solution!");
            return false;
        }

        for (int i = 0; i < me.varsNum; ++i) {
            if (!pinned[i])
                me.varsValues[i] = x[i].solutionValue();
        }

        if (Constants.DEBUG_OPTIMIZE) {
            System.out.println("Solution:");
            System.out.println("   Objective value = " + objective.value());
            for (int i = 0; i < me.varsNum; ++i)
                if (!pinned[i])
                    System.out.println("   x[" + i + "] = " + x[i].solutionValue());
            for (int i = 0; i < me.marginsNum; ++i)
                if (!me.marginsEqs[i].done())
                    System.out.println("   z[" + i + "] = " + z[i].solutionValue());

            System.out.println("Problem solved in " + solver.wallTime() + " milliseconds");
            System.out.println("                  " + solver.iterations() + " iterations");
            System.out.println("                  " + solver.nodes() + " branch-and-bound nodes");
        }

        return true;
    }


    // Marks variables and equations that have been solved, and should be
    // excluded from further optimization passes.
    //
    // The variables (throw and catch positions) participating in a minimum-
    // margin equation are fixed; remaining variables need to be solved in
    // subsequent runs.
    protected void markFinished() {
        // Mark newly-pinned variables (present in minimum-margin equation(s))
        double minmargin = infinity;
        for (int i = 0; i < me.marginsNum; ++i) {
            if (!me.marginsEqs[i].done() && me.getMargin(i) < minmargin)
                minmargin = me.getMargin(i);
        }
        if (Constants.DEBUG_OPTIMIZE)
            System.out.println("minimum active margin = " + minmargin);

        for (int i = 0; i < me.marginsNum; ++i) {
            if (me.marginsEqs[i].done())
                continue;

            double mar = me.getMargin(i);
            if (Constants.DEBUG_OPTIMIZE)
                System.out.println("   margin[" + i + "] = " + mar);

            double diff = mar - minmargin;
            if (diff < -epsilon || diff > epsilon)
                continue;

            for (int j = 0; j < me.varsNum; ++j) {
                double cj = me.marginsEqs[i].coef(j);

                if (!pinned[j] && (cj > epsilon || cj < -epsilon)) {
                    pinned[j] = true;

                    if (Constants.DEBUG_OPTIMIZE)
                        System.out.println("pinned variable x" + j);
                }
            }
        }

        // Mark newly-completed equations (all variables pinned)
        for (int row = 0; row < me.marginsNum; row++) {
            if (me.marginsEqs[row].done())
                continue;

            boolean eqndone = true;
            for (int i = 0; i < me.varsNum; i++) {
                double ci = me.marginsEqs[row].coef(i);
                if (!pinned[i] && (ci > epsilon || ci < -epsilon)) {
                    eqndone = false;
                    break;
                }
            }
            if (eqndone) {
                me.marginsEqs[row].setDone(true);
                if (Constants.DEBUG_OPTIMIZE)
                    System.out.println("equation " + row + " done");
            }
        }
    }


    // Executes the overall pattern optimization.
    protected boolean doOptimizationMILP() {
        if (Constants.DEBUG_OPTIMIZE)
            System.out.println("\noptimizing...");

        int stage = 1;

        while (true) {
            if (Constants.DEBUG_OPTIMIZE)
                System.out.println("---- MILP stage " + stage + ":");

            boolean optimal = runMILP();
            if (!optimal) {
                if (Constants.DEBUG_OPTIMIZE)
                    System.out.println("---- Bailing from optimizer");
                return false;
            }

            markFinished();

            boolean done = true;
            for (int i = 0; i < me.marginsNum; ++i) {
                if (!me.marginsEqs[i].done()) {
                    done = false;
                    break;
                }
            }
            if (done)
                break;

            ++stage;
        }

        if (Constants.DEBUG_OPTIMIZE)
            System.out.println("---- Optimizer finished, updating pattern");

        return true;
    }


    // Updates the pattern's JMLEvents with the current variable values, for
    // variables that were solved by optimizer.
    protected void updatePattern() {
        for (int i = 0; i < me.varsNum; i++) {
            if (pinned[i]) {
                JMLEvent ev = me.varsEvents[i];
                double newx = (double)Math.round(me.varsValues[i] * 100d) / 100d;

                Coordinate coord = ev.getLocalCoordinate();
                coord.x = newx;
                ev.setLocalCoordinate(coord);
            }
        }
        pat.setNeedsLayout(true);
    }
}
