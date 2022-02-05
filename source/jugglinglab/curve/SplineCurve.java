// SplineCurve.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.curve;

import jugglinglab.core.Constants;
import jugglinglab.util.*;

import org.apache.commons.math3.linear.*;


public class SplineCurve extends Curve {
    protected int n;  // number of spline segments
    protected double[][] a, b, c, d;  // spline coefficients

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
    //
    // For spline curves, if the velocities at the endpoints are defined (non-
    // null), the curve will match those velocities precisely. For velocities
    // in the middle, the curve will match the *directions* of those velocities,
    // but not their magnitudes. Any of the velocities may be null, in which
    // case the spline will choose a velocity.

    @Override
    public void calcCurve() throws JuggleExceptionInternal {
        n = numpoints - 1;
        if (n < 1)
            throw new JuggleExceptionInternal("SplineCurve error 1");

        a = new double[n][3];
        b = new double[n][3];
        c = new double[n][3];
        d = new double[n][3];
        double[] durations = new double[n];
        for (int i = 0; i < n; i++) {
            durations[i] = times[i+1] - times[i];
            if (durations[i] <= 0)
                throw new JuggleExceptionInternal("SplineCurve error 2");
        }

        // copy the velocity array so we can modify it
        Coordinate[] vel = new Coordinate[n+1];
        for (int i = 0; i < n + 1; i++)
            vel[i] = (velocities[i] == null ? null : new Coordinate(velocities[i]));

        if (vel[0] != null && vel[n] != null)
            findvels_edges_known(n, durations, positions, vel);
        else
            findvels_edges_unknown(n, durations, positions, vel);

        // now that we have all velocities, solve for spline coefficients
        for (int i = 0; i < n; i++) {
            double t = durations[i];

            for (int j = 0; j < 3; j++) {
                double xi0 = positions[i].getIndex(j);
                double xi1 = positions[i+1].getIndex(j);
                double vi0 = vel[i].getIndex(j);
                double vi1 = vel[i+1].getIndex(j);

                a[i][j] = xi0;
                b[i][j] = vi0;
                c[i][j] = (3 * (xi1 - xi0) - (vi1 + 2 * vi0) * t) / (t * t);
                d[i][j] = (-2 * (xi1 - xi0) + (vi1 + vi0) * t) / (t * t * t);
                //System.out.println("a="+a[i][j]+", b="+b[i][j]+", c="+c[i][j]+", d="+d[i][j]+"\n");
            }
        }
    }

    // These are the three techniques to assign velocities:
    //    "MINIMIZE_RMSACCEL" minimizes the rms acceleration of the hand
    //    "CONTINUOUS_ACCEL" makes the hand acceleration a continuous function
    //    "MINIMIZE_RMSVEL" minimizes the rms velocity of the hand

    public static final int MINIMIZE_RMSACCEL = 0;
    public static final int CONTINUOUS_ACCEL = 1;
    public static final int MINIMIZE_RMSVEL = 2;

    // This method assigns the unknown velocities at the intermediate times
    // from the known velocities at the endpoints (and positions at all
    // times). n is the number of sections -- (n-1) is the number of
    // unknown velocities. v and x have dimension (n+1), t has dimension
    // n. v[0] and v[n] are assumed to be initialized to known endpoints.
    //
    // For the minimization techniques, the calculus problem reduces to
    // solving a system of linear equations of the form A.v = b, where v
    // is a column vector of the velocities. In this case, the matrix A is
    // in tridiagonal form, which is solved efficiently in O(N) time. A is
    // also a symmetric matrix: the sub- and super-diagonals are equal.

    static protected void findvels_edges_known(int n, double[] t, Coordinate[] x, Coordinate[] v)
                                            throws JuggleExceptionInternal {
        if (n < 2)
            return;

        int numcatches = 0;
        for (int i = 1; i < n; i++) {
            if (v[i] != null)
                numcatches++;
        }
        //System.out.println("# of catches = " + numcatches);

        // In this case we put all three axes into one big matrix, and solve once.
        //
        // Number of variables in linear solve:
        //    3 for each interior velocity v[1]...v[n-1]
        //    2 for each natural catch (Lagrange multipliers for constraints)
        int dim = 3 * (n - 1) + 2 * numcatches;

        double[][] m = new double[dim][dim];
        double[] b = new double[dim];

        for (int axis = 0; axis < 3; axis++) {
            double v0 = v[0].getIndex(axis);
            double vn = v[n].getIndex(axis);

            for (int i = 0; i < n - 1; i++) {
                double xi0 = x[i].getIndex(axis);
                double xi1 = x[i+1].getIndex(axis);
                double xi2 = x[i+2].getIndex(axis);
                int index = i + axis * (n - 1);

                switch (Constants.SPLINE_LAYOUT_METHOD) {
                    case MINIMIZE_RMSACCEL:
                    case CONTINUOUS_ACCEL:
                        // cases end up being identical
                        m[index][index] = 2 / t[i+1] + 2 / t[i];
                        double offdiag1 = (i == n - 2 ? 0 : 1 / t[i+1]);
                        if (index < 3 * (n - 1) - 1) {
                            m[index][index + 1] = offdiag1;
                            m[index + 1][index] = offdiag1;
                        }

                        b[index] = 3 * (xi2 - xi1) / (t[i+1] * t[i+1]) +
                                3 * (xi1 - xi0) / (t[i] * t[i]);
                        if (i == 0)
                            b[index] -= v0 / t[i];
                        if (i == (n - 2))
                            b[index] -= vn / t[n-1];
                        break;
                    case MINIMIZE_RMSVEL:
                        m[index][index] = 4 * (t[i] + t[i+1]);
                        double offdiag2 = (i == n - 2 ? 0 : -t[i+1]);
                        if (index < 3 * (n - 1) - 1) {
                            m[index][index + 1] = offdiag2;
                            m[index + 1][index] = offdiag2;
                        }

                        b[index] = 3 * (xi2 - xi0);
                        if (i == 0)
                            b[index] += v0 * t[0];
                        if (i == (n - 2))
                            b[index] += vn * t[n-1];
                        break;
                }
            }
        }

        // Now we apply the "natural throwing" constraint, that the hand
        // velocity must be parallel to the catch velocity at the time of catch.
        // We implement this constraint by requiring the cross product between
        // vel[] and the catch velocity to be zero. This is three separate
        // constraints (one for each spatial dimension), however they are not
        // linearly independent so we only need to apply two. We select the two
        // to retain based on the components of catch velocity.
        //
        // The constraints are implemented using Lagrange multipliers, two per
        // specified catch velocity.

        for (int i = 0, catchnum = 0; i < n - 1; i++) {
            if (v[i+1] == null)
                continue;

            int index = 3 * (n - 1) + 2 * catchnum;
            double ci0 = v[i+1].getIndex(0);  // components of catch velocity
            double ci1 = v[i+1].getIndex(1);
            double ci2 = v[i+1].getIndex(2);

            //System.out.println("catch velocity (i=" + (i+1) + ") = " + v[i+1]);

            int largeaxis = 0;
            if (Math.abs(ci1) >= Math.max(Math.abs(ci0), Math.abs(ci2)))
                largeaxis = 1;
            else if (Math.abs(ci2) >= Math.max(Math.abs(ci0), Math.abs(ci1)))
                largeaxis = 2;

            switch (largeaxis) {
                case 0:
                    m[i][index] = m[index][i] = ci2;
                    m[i][index+1] = m[index+1][i] = ci1;
                    m[i+(n-1)][index+1] = m[index+1][i+(n-1)] = -ci0;
                    m[i+2*(n-1)][index] = m[index][i+2*(n-1)] = -ci0;
                    break;
                case 1:
                    m[i][index+1] = m[index+1][i] = ci1;
                    m[i+(n-1)][index] = m[index][i+(n-1)] = ci2;
                    m[i+(n-1)][index+1] = m[index+1][i+(n-1)] = -ci0;
                    m[i+2*(n-1)][index] = m[index][i+2*(n-1)] = -ci1;
                    break;
                case 2:
                    m[i][index+1] = m[index+1][i] = ci2;
                    m[i+(n-1)][index] = m[index][i+(n-1)] = ci2;
                    m[i+2*(n-1)][index] = m[index][i+2*(n-1)] = -ci1;
                    m[i+2*(n-1)][index+1] = m[index+1][i+2*(n-1)] = -ci0;
                    break;
            }

            catchnum++;
        }

        try {
            //System.out.println(new Array2DRowRealMatrix(m));
            DecompositionSolver solver = new LUDecomposition(new Array2DRowRealMatrix(m)).getSolver();
            RealVector solution = solver.solve(new ArrayRealVector(b));

            for (int i = 0; i < n - 1; i++) {
                v[i+1] = new Coordinate(solution.getEntry(i),
                                        solution.getEntry(i + (n-1)),
                                        solution.getEntry(i + 2*(n-1)));
            }
        } catch (SingularMatrixException sme) {
            throw new JuggleExceptionInternal("Singular matrix in findvels_edges_known()");
        }
    }

    // This method assigns the unknown velocities at the intermediate times.
    // Unlike the case above, here none of the velocities are known. They are
    // all assigned via minimization, with the constraint that v[n] = v[0].
    //
    // Also unlike the case above, here the calculus does not reduce to a pure
    // tridiagonal system.  The matrix A is tridiagonal, except for nonzero
    // elements in the upper-right and lower-left corners.  Since A is close
    // to tridiagonal, we can use the Woodbury formula, which allows us to
    // solve a few auxiliary tridiagonal problems and then combine the results
    // to solve the full problem. See pg. 77 from Numerical Recipes in C, first
    // edition.

    static protected void findvels_edges_unknown(int n, double[] t, Coordinate[] x, Coordinate[] v)
                                            throws JuggleExceptionInternal {
        if (n < 1)
            return;

        double[] Adiag = new double[n];  // v[0]...v[n-1]
        double[] Aoffd = new double[n];  // A is symmetric
        double Acorner = 0;  // nonzero element in UR/LL corners of A
        double[] b = new double[n];

        for (int i = 0; i < n; i++)
            v[i] = new Coordinate(0, 0, 0);

        // Here we can solve each axis independently, and combine the results

        for (int axis = 0; axis < 3; axis++) {
            double xn0 = x[n].getIndex(axis);
            double xnm1 = x[n-1].getIndex(axis);

            for (int i = 0; i < n; i++) {
                double xi0 = x[i].getIndex(axis);
                double xi1 = x[i+1].getIndex(axis);
                double xim1 = (i == 0 ? 0 : x[i-1].getIndex(axis));

                switch (Constants.SPLINE_LAYOUT_METHOD) {
                    case MINIMIZE_RMSACCEL:
                    case CONTINUOUS_ACCEL:
                        if (i == 0) {
                            Adiag[i] = 2 / t[n-1] + 2 / t[0];
                            Acorner = 1 / t[n-1];
                            b[i] = 3 * (xi1 - xi0) / (t[0] * t[0]) +
                                    3 * (xn0 - xnm1) / (t[n-1] * t[n-1]);
                        } else {
                            Adiag[i] = 2 / t[i-1] + 2 / t[i];
                            b[i] = 3 * (xi1 - xi0) / (t[i] * t[i]) +
                                    3 * (xi0 - xim1) / (t[i-1] * t[i-1]);
                        }
                        Aoffd[i] = 1 / t[i];  // not used for i = n - 1
                        break;
                    case MINIMIZE_RMSVEL:
                        if (i == 0) {
                            Adiag[i] = 4 * (t[n-1] + t[0]);
                            Acorner = -t[n-1];
                            b[i] = 3 * (xn0 - xnm1 + xi1 - xi0);
                        } else {
                            Adiag[i] = 4 * (t[i-1] + t[i]);
                            b[i] = 3 * (xi1 - xim1);
                        }
                        Aoffd[i] = -t[i];
                        break;
                }
            }

            // System.out.println("\nBeginning solution.  RHS:");
            // for (int i = 0; i < n; i++)
            //     System.out.println("  b["+i+"] = "+b[i]);

            double[] vel = new double[n];
            for (int i = 0; i < n; i++)
                vel[i] = v[i].getIndex(axis);

            // Woodbury's formula: First solve the problem ignoring A's nonzero corners
            tridag(Aoffd, Adiag, Aoffd, b, vel, n);

            if (n > 2) {  // need to deal with nonzero corners?
                // solve a few auxiliary problems
                double[] z1 = new double[n];
                b[0] = Acorner;
                for (int i = 1; i < n; i++)
                    b[i] = 0;
                tridag(Aoffd, Adiag, Aoffd, b, z1, n);

                double[] z2 = new double[n];
                b[n-1] = Acorner;
                for (int i = 0; i < n - 1; i++)
                    b[i] = 0;
                tridag(Aoffd, Adiag, Aoffd, b, z2, n);

                // calculate a 2x2 matrix H
                double H00, H01, H10, H11;
                H00 = 1 + z2[0];
                H01 = -z2[n-1];
                H10 = -z1[0];
                H11 = 1 + z1[n-1];
                double det = H00 * H11 - H01 * H10;
                H00 /= det;
                H01 /= det;
                H10 /= det;
                H11 /= det;

                // use Woodbury's formula to adjust the velocities
                double m0 = H00 * vel[n-1] + H01 * vel[0];
                double m1 = H10 * vel[n-1] + H11 * vel[0];
                for (int i = 0; i < n; i++)
                    vel[i] -= (z1[i] * m0 + z2[i] * m1);
            }

            for (int i = 0; i < n; i++)
                v[i].setIndex(axis, vel[i]);
        }

        v[n] = new Coordinate(v[0]);

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

    // The following method is adapted from Numerical Recipes. It solves
    // the linear system A.u = r where A is tridiagonal. a[] is the
    // subdiagonal, b[] the diagonal, c[] the superdiagonal. a, b, c, r, and
    // u are indexed from 0. Only the array u[] is changed.

    static protected void tridag(double[] a, double[] b, double[] c, double[] r, double[] u, int n)
                        throws JuggleExceptionInternal {
        int j;
        double bet;
        double[] gam = new double[n];

        if (b[0] == 0)
            throw new JuggleExceptionInternal("Error 1 in TRIDAG");
        bet = b[0];
        u[0] = r[0] / bet;
        for (j = 1; j < n; j++) {
            gam[j] = c[j-1] / bet;
            bet = b[j] - a[j-1] * gam[j];
            if (bet == 0)
                throw new JuggleExceptionInternal("Error 2 in TRIDAG");
            u[j] = (r[j] - a[j-1] * u[j-1]) / bet;
        }
        for (j = (n-1); j > 0; j--)
            u[j-1] -= gam[j] * u[j];
    }

    @Override
    public void getCoordinate(double time, Coordinate newPosition) {
        if (time < times[0] || time > times[n])
            return;

        int i;
        for (i = 0; i < n; i++)
            if (time <= times[i+1])
                break;
        if (i == n)
            i = n - 1;

        time -= times[i];
        newPosition.setCoordinate(a[i][0]+time*(b[i][0]+time*(c[i][0]+time*d[i][0])),
                                  a[i][1]+time*(b[i][1]+time*(c[i][1]+time*d[i][1])),
                                  a[i][2]+time*(b[i][2]+time*(c[i][2]+time*d[i][2])) );
    }

    @Override
    protected Coordinate getMax2(double begin, double end) {
        if (end < times[0] || begin > times[n])
            return null;

        Coordinate result = null;
        double tlow = Math.max(times[0], begin);
        double thigh = Math.min(times[n], end);
        result = check(result, tlow, true);
        result = check(result, thigh, true);

        for (int i = 0; i <= n; i++) {
            if (tlow <= times[i] && times[i] <= thigh)
                result = check(result, times[i], true);
            if (i != n) {
                double tlowtemp = Math.max(tlow, times[i]);
                double thightemp = Math.min(thigh, times[i+1]);

                if (tlowtemp < thightemp) {
                    result = check(result, tlowtemp, true);
                    result = check(result, thightemp, true);

                    for (int index = 0; index < 3; index++) {
                        if (Math.abs(d[i][index]) > 1.0e-6) {
                            double k = c[i][index]*c[i][index] - 3*b[i][index]*d[i][index];
                            if (k > 0) {
                                double te = times[i] + (-c[i][index]-Math.sqrt(k))/(3*d[i][index]);
                                if (tlowtemp < te && te < thightemp)
                                    result = check(result, te, true);
                            }
                        } else if (c[i][index] < 0) {
                            double te = -b[i][index]/(2*c[i][index]);
                            te += times[i];
                            if (tlowtemp < te && te < thightemp)
                                result = check(result, te, true);
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    protected Coordinate getMin2(double begin, double end) {
        if (end < times[0] || begin > times[n])
            return null;

        Coordinate result = null;
        double tlow = Math.max(times[0], begin);
        double thigh = Math.min(times[n], end);
        result = check(result, tlow, false);
        result = check(result, thigh, false);

        for (int i = 0; i <= n; i++) {
            if (tlow <= times[i] && times[i] <= thigh)
                result = check(result, times[i], false);
            if (i != n) {
                double tlowtemp = Math.max(tlow, times[i]);
                double thightemp = Math.min(thigh, times[i+1]);

                if (tlowtemp < thightemp) {
                    result = check(result, tlowtemp, false);
                    result = check(result, thightemp, false);

                    for (int index = 0; index < 3; index++) {
                        if (Math.abs(d[i][index]) > 1.0e-6) {
                            double k = c[i][index]*c[i][index] - 3*b[i][index]*d[i][index];
                            if (k > 0) {
                                double te = times[i] + (-c[i][index]+Math.sqrt(k))/(3*d[i][index]);
                                if (tlowtemp < te && te < thightemp)
                                    result = check(result, te, false);
                            }
                        } else if (c[i][index] > 0) {
                            double te = -b[i][index]/(2 * c[i][index]);
                            te += times[i];
                            if (tlowtemp < te && te < thightemp)
                                result = check(result, te, false);
                        }
                    }
                }
            }
        }

        return result;
    }
}
