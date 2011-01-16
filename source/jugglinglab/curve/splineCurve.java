// splineCurve.java
//
// Copyright 2003 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.curve;

import jugglinglab.util.*;


public class splineCurve extends Curve {
    protected int			n;				// number of spline segments
    protected double[][]	a, b, c, d;		// spline coefficients
    protected double[]		durations;		// durations of segments

    public void initCurve(String st) {
    }

    // Calculate the coefficients a, b, c, d for each portion of the spline path.
    // To solve for these four unknowns, we need four boundary conditions: the
    // path position at each endpoint (2), and the hand velocity at each endpoint (2).
    // hand the hand is making a throw or softcatch, the velocities are known and
    // given by the velocity of the object being thrown/caught.  The other velocities
    // are not known and must be assigned -- we assign them according to one of three
    // minimization techniques.

    public void	calcCurve() throws JuggleExceptionInternal {
        int i, j;
        boolean edgeVelocitiesKnown = ((start_velocity != null) && (end_velocity != null));

        this.n = numpoints - 1;
        if (n < 1)
            throw new JuggleExceptionInternal("splineCurve error 1");

        this.a = new double[n][3];
        this.b = new double[n][3];
        this.c = new double[n][3];
        this.d = new double[n][3];
        this.durations = new double[n];
        for (i = 0; i < n; i++) {
            durations[i] = times[i+1] - times[i];
            if (durations[i] < 0.0)
                throw new JuggleExceptionInternal("splineCurve error 2");
        }

        double[] x = new double[n+1];
        double[] v = new double[n+1];
        double t;

        for (i = 0; i < 3; i++) {
            for (j = 0; j < (n+1); j++)
                x[j] = positions[j].getIndex(i);

            if (edgeVelocitiesKnown) {
                v[0] = start_velocity.getIndex(i);
                v[n] = end_velocity.getIndex(i);
                // find velocities by minimizing rms acceleration
                findvels_edges_known(v, x, durations, n, jugglinglab.core.Constants.SPLINE_LAYOUT_METHOD);
            } else {
                findvels_edges_unknown(v, x, durations, n, jugglinglab.core.Constants.SPLINE_LAYOUT_METHOD);
            }

            //System.out.println("index = "+i+", v[1] = "+v[1]+"\n");

            // now that we have velocities, solve for spline coefficients
            for (j = 0; j < n; j++) {
                a[j][i] = x[j];
                b[j][i] = v[j];
                t = durations[j];
                c[j][i] = (3.0*(x[j+1]-x[j])-(v[j+1]+2.0*v[j])*t)/(t*t);
                d[j][i] = (-2.0*(x[j+1]-x[j])+(v[j+1]+v[j])*t)/(t*t*t);
                //System.out.println("a="+a[j][i]+", b="+b[j][i]+", c="+c[j][i]+", d="+d[j][i]+"\n");
            }
        }
    }


    // These are the three minimization techniques to assign velocities:
    //    "rmsaccel" minimizes the rms acceleration of the hand
    //    "continaccel" makes the hand acceleration a continuous function
    //    "rmsvel" minimizes the rms velocity of the hand

    public static final int rmsaccel = 0;
    public static final int continaccel = 1;
    public static final int rmsvel = 2;

    // This method assigns the unknown velocities at the intermediate times
    // from the known velocities at the endpoints (and positions at all
    // times).  n is the number of sections -- (n-1) is the number of
    // unknown velocities.  v and x have dimension (n+1), t has dimension
    // n.  v[0] and v[n] are assumed to be initialized to known endpoints.
    //
    // In each minimization technique, the calculus problem reduces to
    // solving a system of linear equations of the form A.v = b, where v
    // is a column vector of the velocities.  In this case, the matrix A is
    // in tridiagonal form, which is solved efficiently in O(N) time.  A is
    // also a symmetric matrix, so the sub- and super-diagonals are equal.

    static protected void findvels_edges_known(double[] v, double[] x, double[] t, int n, int method) throws JuggleExceptionInternal {
        if (n < 2) return;

        double[] Adiag = new double[n-1];
        double[] Aoffd = new double[n-1];	// A is symmetric
        double[] b = new double[n-1];

        for (int i = 0; i < n-1; i++) {
            switch (method) {
                case rmsaccel:
                case continaccel:
                    // cases end up being identical
                    Adiag[i] = 2.0/t[i+1] + 2.0/t[i];
                    Aoffd[i] = 1.0/t[i+1];
                    b[i] = 3.0*(x[i+2]-x[i+1])/(t[i+1]*t[i+1]) +
                        3.0*(x[i+1]-x[i])/(t[i]*t[i]);
                    if (i == 0)
                        b[0] -= v[0]/t[0];
                        if (i == (n-2))
                            b[n-2] -= v[n]/t[n-1];
                            break;
                case rmsvel:
                    Adiag[i] = 4.0*(t[i] + t[i+1]);
                    Aoffd[i] = -t[i+1];
                    b[i] = 3.0*(x[i+2]-x[i]);
                    if (i == 0)
                        b[0] += v[0]*t[0];
                        if (i == (n-2))
                            b[n-2] += v[n]*t[n-1];
                            break;
            }
        }

        double[] vtemp = new double[n-1];				// n-1 unknown velocities
        tridag(Aoffd, Adiag, Aoffd, b, vtemp, n-1);		// solve
        for (int i = 0; i < n-1; i++)
            v[i+1] = vtemp[i];
    }

    // This method assigns the unknown velocities at the intermediate times.
    // Unlike the case above, here none of the velocities are known.  They are
    // all assigned via minimization, with the constraint that v[n]=v[0].
    //
    // Also unlike the case above, here the calculus does not reduce to a pure
    // tridiagonal system.  The matrix A is tridiagonal, except for nonzero
    // elements in the upper-right and lower-left corners.  Since A is close
    // to tridiagonal, we can use the Woodbury formula, which allows us to
    // solve a few auxiliary tridiagonal problems and then combine the results
    // to solve the full problem.  See pg. 77 from Numerical Recipes in C, first
    // edition.

    static protected void findvels_edges_unknown(double[] v, double[] x, double[] t, int n, int method) throws JuggleExceptionInternal {
        if (n < 2) return;

        double[] Adiag = new double[n];
        double[] Aoffd = new double[n];		// A is symmetric
        double Acorner = 0.0;				// nonzero element in UR/LL corners of A
        double[] b = new double[n];

        for (int i = 0; i < n; i++) {
            switch (method) {
                case rmsaccel:
                case continaccel:
                    if (i == 0) {
                        Adiag[0] = 2.0/t[n-1] + 2.0/t[0];
                        Acorner = 1.0/t[n-1];
                        b[0] = 3.0*(x[1]-x[0])/(t[0]*t[0]) +
                            3.0*(x[n]-x[n-1])/(t[n-1]*t[n-1]);
                    } else {
                        Adiag[i] = 2.0/t[i-1] + 2.0/t[i];
                        b[i] = 3.0*(x[i+1]-x[i])/(t[i]*t[i]) +
                            3.0*(x[i]-x[i-1])/(t[i-1]*t[i-1]);
                    }
                    Aoffd[i] = 1.0/t[i];		// not used for i=n-1
                    break;
                case rmsvel:
                    if (i == 0) {
                        Adiag[0] = 4.0*(t[n-1] + t[0]);
                        Acorner = -t[n-1];
                        b[0] = 3.0*(x[n]-x[n-1]+x[1]-x[0]);
                    } else {
                        Adiag[i] = 4.0*(t[i-1] + t[i]);
                        b[i] = 3.0*(x[i+1]-x[i-1]);
                    }
                    Aoffd[i] = -t[i];
                    break;
            }
        }

        /*		System.out.println("\nBeginning solution.  RHS:");
        for (int i = 0; i < n; i++)
            System.out.println("  b["+i+"] = "+b[i]); */

        // Woodbury's formula: solve the problem ignoring A's nonzero corners
        tridag(Aoffd, Adiag, Aoffd, b, v, n);

        if (n > 2) {		// need to deal with nonzero corners?
                    // solve a few auxiliary problems:
            double[] z1 = new double[n];
            b[0] = Acorner;
            for (int i = 1; i < n; i++)
                b[i] = 0.0;
            tridag(Aoffd, Adiag, Aoffd, b, z1, n);
            double[] z2 = new double[n];
            b[n-1] = Acorner;
            for (int i = 0; i < n-1; i++)
                b[i] = 0.0;
            tridag(Aoffd, Adiag, Aoffd, b, z2, n);

            // now we need to calculate a 2x2 matrix H:
            double H00, H01, H10, H11;
            H00 = 1.0 + z2[0];
            H01 = -z2[n-1];
            H10 = -z1[0];
            H11 = 1.0 + z1[n-1];
            double det = H00 * H11 - H01 * H10;
            H00 /= det;
            H01 /= det;
            H10 /= det;
            H11 /= det;

            // finally, use the formula to adjust the velocities:
            double m0 = H00 * v[n-1] + H01 * v[0];
            double m1 = H10 * v[n-1] + H11 * v[0];
            for (int i = 0; i < n; i++)
                v[i] -= (z1[i] * m0 + z2[i] * m1);
        }
        v[n] = v[0];

        /*		// do the matrix multiply to check the answer
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
        }*/
    }

    // The following method is adapted from Numerical Recipes.  It solves
    // the linear system A.u = r where A is tridiagonal.  a[] is the
    // subdiagonal, b[] the diagonal, c[] the superdiagonal.  a, b, c, r, and
    // u are indexed from 0.  Only the array u[] is changed.

    static protected void tridag(double[] a, double[] b, double[] c, double[] r, double[] u, int n) throws JuggleExceptionInternal {
        int j;
        double bet;
        double[] gam = new double[n];

        if (b[0] == 0.0)
            throw new JuggleExceptionInternal("Error 1 in TRIDAG");
        bet = b[0];
        u[0] = r[0] / bet;
        for (j = 1; j < n; j++) {
            gam[j] = c[j-1] / bet;
            bet = b[j] - a[j-1]*gam[j];
            if (bet == 0.0)
                throw new JuggleExceptionInternal("Error 2 in TRIDAG");
            u[j] = (r[j] - a[j-1]*u[j-1]) / bet;
        }
        for (j = (n-1); j > 0; j--)
            u[j-1] -= gam[j]*u[j];
    }


    public void getCoordinate(double time, Coordinate newPosition) {
        if ((time < times[0]) || (time > times[n]))
            return;

        int i;
        for (i = 0; i < n; i++)
            if (time <= times[i+1])
                break;
        if (i == n)
            i = n-1;

        time -= times[i];
        newPosition.setCoordinate(
                                  a[i][0]+time*(b[i][0]+time*(c[i][0]+time*d[i][0])),
                                  a[i][1]+time*(b[i][1]+time*(c[i][1]+time*d[i][1])),
                                  a[i][2]+time*(b[i][2]+time*(c[i][2]+time*d[i][2])) );
    }

    protected Coordinate getMax2(double begin, double end) {
        if ((end < times[0]) || (begin > times[n]))
            return null;

        Coordinate result = null;
        double tlow = Math.max(times[0], begin);
        double thigh = Math.min(times[n], end);
        result = check(result, tlow, true);
        result = check(result, thigh, true);

        for (int i = 0; i <= n; i++) {
            if ((tlow <= times[i]) && (times[i] <= thigh))
                result = check(result, times[i], true);
            if (i != n) {
                double tlowtemp = Math.max(tlow, times[i]);
                double thightemp = Math.min(thigh, times[i+1]);

                if (tlowtemp < thightemp) {
                    result = check(result, tlowtemp, true);
                    result = check(result, thightemp, true);

                    for (int index = 0; index < 3; index++) {
                        if (Math.abs(d[i][index]) > 1.0e-6) {
                            double k = c[i][index]*c[i][index] - 3.0*b[i][index]*d[i][index];
                            if (k > 0.0) {
                                double te = times[i] + (-c[i][index]-Math.sqrt(k))/(3*d[i][index]);
                                if ((tlowtemp < te) && (te < thightemp))
                                    result = check(result, te, true);
                            }
                        } else if (c[i][index] < 0.0) {
                            double te = -b[i][index]/(2.0*c[i][index]);
                            te += times[i];
                            if ((tlowtemp < te) && (te < thightemp))
                                result = check(result, te, true);
                        }
                    }
                }
            }
        }

        return result;
    }

    protected Coordinate getMin2(double begin, double end) {
        if ((end < times[0]) || (begin > times[n]))
            return null;

        Coordinate result = null;
        double tlow = Math.max(times[0], begin);
        double thigh = Math.min(times[n], end);
        result = check(result, tlow, false);
        result = check(result, thigh, false);

        for (int i = 0; i <= n; i++) {
            if ((tlow <= times[i]) && (times[i] <= thigh))
                result = check(result, times[i], false);
            if (i != n) {
                double tlowtemp = Math.max(tlow, times[i]);
                double thightemp = Math.min(thigh, times[i+1]);

                if (tlowtemp < thightemp) {
                    result = check(result, tlowtemp, false);
                    result = check(result, thightemp, false);

                    for (int index = 0; index < 3; index++) {
                        if (Math.abs(d[i][index]) > 1.0e-6) {
                            double k = c[i][index]*c[i][index] - 3.0*b[i][index]*d[i][index];
                            if (k > 0.0) {
                                double te = times[i] + (-c[i][index]+Math.sqrt(k))/(3*d[i][index]);
                                if ((tlowtemp < te) && (te < thightemp))
                                    result = check(result, te, false);
                            }
                        } else if (c[i][index] > 0.0) {
                            double te = -b[i][index]/(2.0*c[i][index]);
                            te += times[i];
                            if ((tlowtemp < te) && (te < thightemp))
                                result = check(result, te, false);
                        }
                    }
                }
            }
        }

        return result;
    }
}
