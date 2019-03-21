// Optimizer.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.optimizer;

import java.util.*;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.exception.TooManyIterationsException;

/*
import org.ejml.data.DenseMatrix64F;
import org.ejml.alg.dense.decomposition.svd.*;
*/

import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.core.*;


public class Optimizer {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

	protected JMLPattern		pat;
	protected MarginEquations	me;

	protected boolean[]			pinned;		// true when variable is done optimizing
	protected double[]			r;			// direction vector for variables
	protected int				eqn_start;	// index of first equation in active group
	protected int				eqn_end;	// index of last equation in active group
	protected double			dmdl;		// d(margin)/d(lambda) for active group

	static final double epsilon = 0.0000001;


	public Optimizer(JMLPattern p) throws JuggleExceptionInternal, JuggleExceptionUser {
		this.pat = p;
		this.me = new MarginEquations(p);

		if (me.marginsNum == 0)
			return;

		this.pinned = new boolean[me.varsNum];
		this.r = new double[me.varsNum];
		this.eqn_start = this.eqn_end = 0;
	}


	public double getMargin() {
		return me.getMargin();
	}


	// SVD-based optimizer below


	// try to find a contradictory set of vectors within the current group.
	// a set of vectors {x_i} is contradictory iff there is no solution r to the
	// set of constraints dot(r, x_i) > 0 for all i
	//
	// if a contradictory set is found, mark the related variables as pinned, mark
	// the corresponding margin equations as done, and then return true.  if no
	// contradictory set is found, return false

	protected boolean removeContradictory() {
		if (Constants.DEBUG_OPTIMIZE)
			System.out.println("************* FINDING CONTRADICTORY VECTORS *************");

		if (Constants.DEBUG_OPTIMIZE)
			System.out.println("************* DONE WITH CONTRADICTORY VECTORS *************");

		return false;
	}


	// figure out the direction vector r for the variables in the margin
	// equations.  returns true when a direction is found, false when not (i.e.,
	// the optimization is done)

	protected boolean findDirection() throws JuggleExceptionInternal {
		if (eqn_start >= me.marginsNum)
			return false;

		if (me.marginsEqs[eqn_start].done()) {
			eqn_start++;
			if (eqn_end < eqn_start)
				eqn_end = eqn_start;
			if (Constants.DEBUG_OPTIMIZE)
				System.out.println("equation " + (eqn_start-1) + " done, continuing with group " + eqn_start +
								   " through " + eqn_end);
			return findDirection();
		}

		/*
		// JKB START
		if (true) {
		for (int i = 0; i < me.varsNum; i++) {
			if (pinned[i])
				r[i] = 0.0;
			else
				r[i] = me.marginsEqs[eqn_start].coef(i);
		}
		return true;
		}
		// JKB END
		*/

		for (int i = 0; i < me.varsNum; i++)
			r[i] = 0.0;

		if (eqn_end == eqn_start) {
			// we don't have a group, so we try to find an unpinned variable
			// in our current equation, and move in a direction that increases
			// margin.
			for (int i = 0; i < me.varsNum; i++) {
				double ci = me.marginsEqs[eqn_start].coef(i);
				if (!pinned[i] && (ci > epsilon || ci < -epsilon)) {
					r[i] = (ci > 0.0) ? 1.0 : -1.0;

					if (Constants.DEBUG_OPTIMIZE)
						System.out.println("moving in direction of variable " + i);

					return true;
				}
			}

			throw new JuggleExceptionInternal("couldn't find nonzero unpinned coefficient");
		}

		// We have a group of equations, and need to find a direction r that:
		// (a) advances them all at the same rate, and (b) doesn't move any of
		// the pinned variables.
		boolean[] varincluded = new boolean[me.varsNum];
		int[] varnum = new int[me.varsNum];
		int numincluded = 0;
		for (int i = 0; i < me.varsNum; i++) {
			if (pinned[i])
				varincluded[i] = false;
			else {
				boolean skip = true;
				for (int row = eqn_start; skip && row <= eqn_end; row++) {
					double ci = me.marginsEqs[row].coef(i);
					if (ci > epsilon || ci < -epsilon)
						skip = false;
				}
				varincluded[i] = !skip;
				if (!skip) {
					varnum[numincluded] = i;
					numincluded++;
				}
			}
			System.out.println("vi[" + i + "] = " + varincluded[i]);
		}

		if (Constants.DEBUG_OPTIMIZE) {
			System.out.println("equations = " + (eqn_end - eqn_start + 1) +
							   ", dimensions = " + numincluded);

			for (int row = eqn_start; row <= eqn_end; row++) {
				StringBuffer sb = new StringBuffer();
				sb.append("  equation " + (row - eqn_start) + " = { ");
				for (int j = 0; j < numincluded; j++) {
					sb.append(JMLPattern.toStringTruncated(me.marginsEqs[row].coef(varnum[j]), 4));
					if (j != (numincluded-1))
						sb.append(", ");
				}
				sb.append(" : ");
				sb.append(JMLPattern.toStringTruncated(me.marginsEqs[row].constant(), 4));
				sb.append(" }");

				System.out.println(sb.toString());
			}
		}

		if (numincluded <= (eqn_end - eqn_start)) {
			if (Constants.DEBUG_OPTIMIZE) {
				System.out.println("************* RUNNING LP *************");
			}

			if (removeContradictory()) {
				if (Constants.DEBUG_OPTIMIZE) {
					System.out.println("****** REMOVED CONTRADICTORY EQUATIONS ******");
				}
				return findDirection();
			}

			boolean found = false;
			for (int trialrow = eqn_start; !found && trialrow <= eqn_end; trialrow++) {
				double[] objarray = new double[numincluded];
				for (int i = 0; i < numincluded; i++)
					objarray[i] = 0.0;
				LinearObjectiveFunction f = new LinearObjectiveFunction(objarray, 1.0);

				ArrayList<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
				for (int row = eqn_start; row <= eqn_end; row++) {
					double[] constarray = new double[numincluded];
					for (int i = 0; i < numincluded; i++)
						constarray[i] = me.marginsEqs[row].coef(varnum[i]);

					if (row == trialrow)
						constraints.add(new LinearConstraint(constarray, Relationship.GEQ, 100.0*epsilon));
					else
						constraints.add(new LinearConstraint(constarray, Relationship.GEQ, 100.0*epsilon));
				}
				for (int i = 0; i < numincluded; i++) {
					double[] maxarray = new double[numincluded];
					maxarray[i] = 1.0;
					constraints.add(new LinearConstraint(maxarray, Relationship.LEQ, 1.0));
					double[] minarray = new double[numincluded];
					minarray[i] = -1.0;
					constraints.add(new LinearConstraint(minarray, Relationship.LEQ, 1.0));
				}

				SimplexSolver solver = new SimplexSolver();
				LinearConstraintSet con = new LinearConstraintSet(constraints);
				NonNegativeConstraint nnc = new NonNegativeConstraint(false);

				try {
					PointValuePair solution = solver.optimize(f, con, GoalType.MAXIMIZE, nnc);

					if (Constants.DEBUG_OPTIMIZE) {
						StringBuffer sb = new StringBuffer();
						sb.append("vector = { ");
						for (int i = 0; i < numincluded; i++) {
							sb.append(JMLPattern.toStringTruncated(solution.getPoint()[i], 4));
							if (i != numincluded-1)
								sb.append(", ");
						}
						sb.append(" }");
						System.out.println(sb.toString());
					}

					found = true;
					for (int row = eqn_start; row <= eqn_end; row++) {
						double dot = 0.0;
						for (int i = 0; i < numincluded; i++)
							dot += solution.getPoint()[i] * me.marginsEqs[row].coef(varnum[i]);
						if (dot < -epsilon)
							found = false;
						System.out.println("dot[" + row + "] = " + dot + ", found = " + found);
					}

					if (found) {
						for (int i = 0; i < numincluded; i++)
							r[varnum[i]] = solution.getPoint()[i];
						eqn_end = eqn_start;
						System.out.println("LP successful");
						return true;
					}
				} catch (TooManyIterationsException e) {
					if (Constants.DEBUG_OPTIMIZE)
						System.out.println("optimization exception: " + e.getMessage());
				}
			}

			if (Constants.DEBUG_OPTIMIZE) {
				System.out.println("************** PUNTING **************");
			}
			for (int i = 0; i < numincluded; i++)
				pinned[varnum[i]] = true;
			markDoneEquations();
			me.sort();
			eqn_start = eqn_end + 1;
			eqn_end = eqn_start;
			return findDirection();
			/*
			throw new JuggleExceptionInternal("can't find direction: " + (eqn_end - eqn_start) +
											  " difference vectors and " + numincluded + " dimensions");
			*/
		}

		Array2DRowRealMatrix mat = new Array2DRowRealMatrix(eqn_end - eqn_start, numincluded);
		int col = 0;
		for (int i = 0; i < me.varsNum; i++) {
			if (varincluded[i]) {
				for (int row = eqn_start + 1; row <= eqn_end; row++) {
					mat.setEntry(row - eqn_start - 1, col, me.marginsEqs[row].coef(i) -
						me.marginsEqs[eqn_start].coef(i));
				}
				col++;
			}
		}

		SingularValueDecomposition svd = new SingularValueDecomposition(mat);
		RealMatrix vt = svd.getVT();

		// Now the vectors we want are the last row(s) of vt.  The row numbers
		// are (eqn_end - eqn_start) through (numincluded - 1), inclusive.
		// Choose the row with the largest dot product with our vectors.
		double max_product = 0.0;
		int max_row = -1;
		int max_sign = 0;
		for (int row = eqn_end - eqn_start; row < numincluded; row++) {
			double dot = 0.0;
			for (int i = 0; i < numincluded; i++)
				dot += vt.getEntry(row, i) * me.marginsEqs[eqn_start].coef(varnum[i]);
			double abs_dot = (dot > 0.0) ? dot : -dot;
			if (max_row < 0 || abs_dot > max_product) {
				max_product = abs_dot;
				max_row = row;
				max_sign = (dot > 0.0) ? 1 : -1;
			}
		}

		if (max_product < epsilon) {
			if (removeContradictory())
				return findDirection();
			else
				throw new JuggleExceptionInternal("removeContradictory() unsuccessful after SVD failure");
		}

		for (int i = 0; i < numincluded; i++)
			r[varnum[i]] = vt.getEntry(max_row, i) * max_sign;

		if (Constants.DEBUG_OPTIMIZE) {
			System.out.println("  found r using SVD");
			for (int row = eqn_start; row <= eqn_end; row++) {
				double dot = 0.0;
				for (int i = 0; i < me.varsNum; i++)
					dot += me.marginsEqs[row].coef(i) * r[i];
				System.out.println("  dot product with equation " + row + " = " + dot);
			}
		}
		return true;
	}


	// advance along direction r, until one of two things happens:  (a) a variable
	// reaches a limit and gets pinned, or (b) the smallest active margin becomes
	// equal to the margin of an equation later in the list

	protected void takeStep() throws JuggleExceptionInternal {

		// find the smallest lambda such that moving lambda*r will run
		// one of the variables into a limit
		double pin_lambda = -1.0;
		int pinned_var = -1;

		for (int i = 0; i < me.varsNum; i++) {
			System.out.println("pinned[" + i + "] = " + pinned[i] + ", r[" + i + "] = " + r[i]);

			if (!pinned[i] && r[i] != 0.0) {
				// JKB START
				/*
				if (me.varsValues[i] > me.varsMax[i]) {
					pinned[i] = true;
					me.varsValues[i] = me.varsMax[i];
					markDoneEquations();
					me.sort();
					return;
				} else if (me.varsValues[i] < me.varsMin[i]) {
					pinned[i] = true;
					me.varsValues[i] = me.varsMax[i];
					markDoneEquations();
					me.sort();
					return;
				} else {
				*/
				double lambda = (r[i] > 0.0) ? (me.varsMax[i] - me.varsValues[i]) / r[i] :
								(me.varsMin[i] - me.varsValues[i]) / r[i];
				// lambda += epsilon;
				if (lambda < 0.0)
					throw new JuggleExceptionInternal("negative lambda in takeStep()");

				if (pin_lambda < 0.0 || pin_lambda > lambda) {
					pin_lambda = lambda;
					pinned_var = i;
				}
			}
		}

		// find the smallest lambda that will make one of the later equations
		// in the list have a margin equal to the current group
		double join_lambda = -1.0;
		int joined_eqn = -1;

		double group_margin = me.getMargin(eqn_start);
		double group_dmdl = 0.0;
		for (int i = 0; i < me.varsNum; i++)
			group_dmdl += me.marginsEqs[eqn_start].coef(i) * r[i];

		for (int row = eqn_end + 1; row < me.marginsNum; row++) {
			double test_margin = me.getMargin(row);
			double test_dmdl = 0.0;
			for (int i = 0; i < me.varsNum; i++)
				test_dmdl += me.marginsEqs[row].coef(i) * r[i];

			// solve:  test_margin + lambda * test_dmdl = group_margin + lambda * group_dmdl
			if (test_dmdl - group_dmdl < 0.0) {
				double lambda = (group_margin - test_margin) / (test_dmdl - group_dmdl);

				if (lambda > 0.0 && (join_lambda < 0.0 || join_lambda > lambda)) {
					join_lambda = lambda;
					joined_eqn = row;
				}
			}
		}
		if (Constants.DEBUG_OPTIMIZE)
			System.out.println("join_lambda = " + join_lambda + ", pin_lambda = " + pin_lambda);

		// and now take our step
		if (join_lambda >= 0.0 && join_lambda < pin_lambda) {

			// move until new equation joins the group
			for (int i = 0; i < me.varsNum; i++) {
				if (!pinned[i])
					me.varsValues[i] += join_lambda * r[i];
			}
			eqn_end++;

			if (Constants.DEBUG_OPTIMIZE)
				System.out.println("equation " + joined_eqn + " joined group");
		} else {
			// move until we pin a variable
			for (int i = 0; i < me.varsNum; i++) {
				if (!pinned[i])
					me.varsValues[i] += pin_lambda * r[i];
			}
			me.varsValues[pinned_var] = (r[pinned_var] > 0.0 ? me.varsMax[pinned_var] : me.varsMin[pinned_var]);
			pinned[pinned_var] = true;

			if (Constants.DEBUG_OPTIMIZE)
				System.out.println("variable " + pinned_var + " got pinned");
			markDoneEquations();
		}
		me.sort();
	}


	protected void markDoneEquations() {
		// check for equations that have all variables pinned, in which case they're done
		for (int row = 0; row < me.marginsNum; row++) {
			if (!me.marginsEqs[row].done()) {
				boolean eqndone = true;
				for (int i = 0; eqndone && i < me.varsNum; i++) {
					double ci = me.marginsEqs[row].coef(i);
					if (!pinned[i] && (ci > epsilon || ci < -epsilon))
						eqndone = false;
				}
				if (eqndone) {
					me.marginsEqs[row].setDone(true);
					eqn_start++;
					if (row >= eqn_end || eqn_end < eqn_start)
						eqn_end++;
					if (Constants.DEBUG_OPTIMIZE)
						System.out.println("equation " + row + " done, new group is " + eqn_start +
										   " through " + eqn_end);
				}
			}
		}
		me.sort();
	}


	public void doOptimizationSVD() throws JuggleExceptionInternal {
		if (Constants.DEBUG_OPTIMIZE)
			System.out.println("\noptimizing...");

		int stage = 1;
		if (Constants.DEBUG_OPTIMIZE)
			System.out.println("---- stage 1");

		while (findDirection()) {
			takeStep();

			if (Constants.DEBUG_OPTIMIZE) {
				StringBuffer sb = new StringBuffer();
				sb.append("margins = { ");
				for (int i = 0; i < me.marginsNum; i++) {
					sb.append(JMLPattern.toStringTruncated(me.getMargin(i), 4));
					if (i != me.marginsNum-1)
						sb.append(", ");
				}
				sb.append(" }");
				System.out.println(sb.toString());
				stage++;
				System.out.println("---- stage " + stage);
			}
		}

		if (Constants.DEBUG_OPTIMIZE)
			System.out.println("optimization done");
	}


	// LP-based optimizer below

	public double optimizeOnce() {

		if (Constants.DEBUG_OPTIMIZE) {
			StringBuffer sb = new StringBuffer();
			sb.append("margins = { ");
			for (int i = 0; i < me.marginsNum; i++) {
				sb.append(JMLPattern.toStringTruncated(me.getMargin(i), 4));
				if (i != me.marginsNum-1)
					sb.append(", ");
			}
			sb.append(" }");
			System.out.println(sb.toString());
		}

		double bestmargin = me.getMargin();

		for (int row = 0; row < me.marginsNum; row++) {
			if (Constants.DEBUG_OPTIMIZE)
				System.out.println("optimizing row " + row + "...");

			double[] objarray = new double[me.varsNum];
			double tiny = 0.00001;
			for (int i = 0; i < me.varsNum; i++) {
				objarray[i] = me.marginsEqs[row].coef(i);
				/*
				for (int j = 0; j < me.marginsNum; j++) {
					if (j != row)
						objarray[i] += tiny * (me.marginsEqs[j][i] - me.marginsEqs[row][i]);
				}
				*/
			}
			LinearObjectiveFunction f = new LinearObjectiveFunction(objarray, me.marginsEqs[row].constant());

			ArrayList<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
			for (int i = 0; i < me.marginsNum; i++) {
				if (i != row) {
					double[] constarray = new double[me.varsNum];
					for (int j = 0; j < me.varsNum; j++)
						constarray[j] = me.marginsEqs[row].coef(j) - me.marginsEqs[i].coef(j);

					constraints.add(new LinearConstraint(constarray, Relationship.LEQ,
														 me.marginsEqs[i].constant() - me.marginsEqs[row].constant()));
				}
			}
			for (int i = 0; i < me.varsNum; i++) {
				double[] maxarray = new double[me.varsNum];
				maxarray[i] = 1.0;
				constraints.add(new LinearConstraint(maxarray, Relationship.LEQ, me.varsMax[i]));
				double[] minarray = new double[me.varsNum];
				minarray[i] = -1.0;
				constraints.add(new LinearConstraint(minarray, Relationship.LEQ, -me.varsMin[i]));
			}

			SimplexSolver solver = new SimplexSolver();
			LinearConstraintSet con = new LinearConstraintSet(constraints);
			NonNegativeConstraint nnc = new NonNegativeConstraint(false);

			try {
				PointValuePair solution = solver.optimize(f, con, GoalType.MAXIMIZE, nnc);

				if (solution.getValue() > bestmargin) {
					bestmargin = solution.getValue();
					for (int i = 0; i < me.varsNum; i++)
						me.varsValues[i] = solution.getPoint()[i];
				}
				if (Constants.DEBUG_OPTIMIZE) {
					for (int i = 0; i < me.varsNum; i++)
						System.out.println("point " + i + " = " + solution.getPoint()[i]);
					System.out.println("Solution value = " + solution.getValue());
					StringBuffer sb = new StringBuffer();
					sb.append("margins = { ");
					for (int i = 0; i < me.marginsNum; i++) {
						double temp = me.marginsEqs[i].constant();
						for (int j = 0; j < me.varsNum; j++)
							temp += me.marginsEqs[i].coef(j) * solution.getPoint()[j];
						sb.append(JMLPattern.toStringTruncated(temp, 4));
						if (i != me.marginsNum-1)
							sb.append(", ");
					}
					sb.append(" }");
					System.out.println(sb.toString());
				}
			} catch (TooManyIterationsException e) {
				if (Constants.DEBUG_OPTIMIZE)
					System.out.println("optimization exception: " + e.getMessage());
			}

			if (Constants.DEBUG_OPTIMIZE) {
				StringBuffer sb = new StringBuffer();
				sb.append("bestmargins = { ");
				for (int i = 0; i < me.marginsNum; i++) {
					sb.append(JMLPattern.toStringTruncated(me.getMargin(i), 4));
					if (i != me.marginsNum-1)
						sb.append(", ");
				}
				sb.append(" }");
				System.out.println(sb.toString());
			}
		}

		return bestmargin;
	}


	public void doOptimizationLP() {

		if (Constants.DEBUG_OPTIMIZE) {
			System.out.println("doing optimization...");
		}

		double bestmargin = optimizeOnce();
	}



	public void doOptimizationGradient() {
		int eqn = 0;
		boolean go = true;
		double step = 0.01;

		do {
			double rsq = 0.0;
			for (int i = 0; i < me.varsNum; i++) {
				r[i] = me.marginsEqs[eqn].coef(i);
				rsq += r[i] * r[i];
			}
			rsq = Math.sqrt(rsq);
			for (int i = 0; i < me.varsNum; i++)
				r[i] /= rsq;


		} while (go);
	}


	public void updatePattern() {
		// update the pattern's JMLEvents with the current variable values
		for (int i = 0; i < me.varsNum; i++) {
			JMLEvent ev = me.varsEvents[i];
			double newx = me.varsValues[i];

			Coordinate coord = ev.getLocalCoordinate();
			coord.x = newx;
			ev.setLocalCoordinate(coord);
		}
		pat.setNeedsLayout(true);
	}


	public static JMLPattern optimize(JMLPattern pat) throws JuggleExceptionInternal, JuggleExceptionUser {
		Optimizer opt = new Optimizer(pat);

		//opt.doOptimizationGradient();
		opt.doOptimizationSVD();
		//opt.doOptimizationLP();
		opt.updatePattern();

		return pat;
	}
}
