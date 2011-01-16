// bouncePath.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.path;

import java.text.MessageFormat;
import jugglinglab.util.*;


public class bouncePath extends Path {
    protected static final int bounces_def = 1;		// number of bounces
    protected static final boolean forced_def = false;
    protected static final boolean hyper_def = false;
    protected static final double bounceplane_def = 0.0;  	// floor level
    protected static final double bouncefrac_def = 0.9;
    protected static final double g_def = 980.0;		// using CGS units

    protected double	bx, cx;
    protected double	by, cy;
    protected double	az[], bz[], cz[];
    protected double	endtime[];
    protected int	bounces = bounces_def;
    protected boolean	forced = forced_def;	// true -> forced throw
    protected boolean	hyper = hyper_def;	// true -> same type of catch (lift/forced) as throw
    protected double	bounceplane = bounceplane_def;
    protected double	bouncefrac = bouncefrac_def;
    protected double	g = g_def;
    protected double	bouncefracsqrt;
    protected double	bouncetime;
    protected int	numbounces;		// actual number of bounces (<= this.bounces)
    
    public String getName() { return "Bounce"; }

    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[6];

        result[0] = new ParameterDescriptor("bounces", ParameterDescriptor.TYPE_INT,
                                            null, new Integer(bounces_def), new Integer(bounces));
        result[1] = new ParameterDescriptor("forced", ParameterDescriptor.TYPE_BOOLEAN,
                                            null, new Boolean(forced_def), new Boolean(forced));
        result[2] = new ParameterDescriptor("hyper", ParameterDescriptor.TYPE_BOOLEAN,
                                            null, new Boolean(hyper_def), new Boolean(hyper));
        result[3] = new ParameterDescriptor("bounceplane", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(bounceplane_def), new Double(bounceplane));
        result[4] = new ParameterDescriptor("bouncefrac", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(bouncefrac_def), new Double(bouncefrac));
        result[5] = new ParameterDescriptor("g", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(g_def), new Double(g));

        return result;
    }

    public void initPath(String st) throws JuggleExceptionUser {
        // default bounce characteristics
        int bounces = bounces_def;
        boolean forced = forced_def;
        boolean hyper = hyper_def;
        double bounceplane = bounceplane_def;
        double bouncefrac = bouncefrac_def;
        double g = g_def;

        // now parse for edits to the above variables
        ParameterList pl = new ParameterList(st);
        for (int i = 0; i < pl.getNumberOfParameters(); i++) {
            String pname = pl.getParameterName(i);
            String pvalue = pl.getParameterValue(i);

            if (pname.equalsIgnoreCase("bounces")) {
                try {
                    bounces = Integer.valueOf(pvalue).intValue();
                } catch (NumberFormatException nfe) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "bounces" };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } else if (pname.equalsIgnoreCase("forced")) {
                forced = Boolean.valueOf(pvalue).booleanValue();
            } else if (pname.equalsIgnoreCase("hyper")) {
                hyper = Boolean.valueOf(pvalue).booleanValue();
            } else if (pname.equalsIgnoreCase("bounceplane")) {
                try {
                    bounceplane = Double.valueOf(pvalue).doubleValue();
                } catch (NumberFormatException nfe) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "bounceplane" };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } else if (pname.equalsIgnoreCase("bouncefrac")) {
                try {
                    bouncefrac = Double.valueOf(pvalue).doubleValue();
                } catch (NumberFormatException nfe) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "bouncefrac" };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } else if (pname.equalsIgnoreCase("g")) {
                try {
                    g = Double.valueOf(pvalue).doubleValue();
                } catch (NumberFormatException nfe) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "g" };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            } else
                throw new JuggleExceptionUser(errorstrings.getString("Error_path_badmod")+": '"+pname+"'");
        }

        this.bounces = bounces;
        this.forced = forced;
        this.hyper = hyper;
        this.bounceplane = bounceplane;
        this.bouncefrac = bouncefrac;
        try {
            this.bouncefracsqrt = Math.sqrt(bouncefrac);
        } catch (ArithmeticException e) {
            this.bouncefracsqrt = 1.0;
        }
        this.g = g;
        
        this.az = new double[bounces + 1];
        this.bz = new double[bounces + 1];
        this.cz = new double[bounces + 1];
        this.endtime = new double[bounces + 1];
        for (int i = 0; i <= bounces; i++)
            az[i] = -0.5 * g;
    }

    // The next function does all the real work of figuring out the ball's
    // path.  It solves a cubic equation to find the time when the ball hits
    // the ground
    public void calcPath() throws JuggleExceptionInternal {
        if ((start_coord == null) || (end_coord == null))
            return;

        double	t2 = getDuration();
        // First do the x and y coordinates -- these are simple.
        cx = start_coord.x;
        bx = (end_coord.x - start_coord.x) / t2;
        cy = start_coord.y;
        by = (end_coord.y - start_coord.y) / t2;

        cz[0] = start_coord.z;

        double[] root = new double[4];
        boolean[] liftcatch = new boolean[4];
        int numroots;

        for (this.numbounces = bounces; numbounces > 0; numbounces--) {
            numroots = 0;
            double f1 = bouncefracsqrt;
            for (int i = 1; i < numbounces; i++)
                f1 *= bouncefracsqrt;
            double k = ((bouncefracsqrt == 1.0) ? 2.0*(double)numbounces :
                        1.0 + f1 + 2.0*bouncefracsqrt*(1.0-f1/bouncefracsqrt)/(1.0-bouncefracsqrt));
            double u = 2.0 * this.g * (start_coord.z - bounceplane);
            double l = 2.0 * this.g * (end_coord.z - bounceplane);
            double f2 = f1 * f1;
            double c = u - l / f2;
            double kk = k * k;
            double gt = this.g * t2;
            double v0;
            
            // We are solving the following equation for v0 (the throw velocity), where
            // the constants are as defined above:
            //
            // gt = v0 + k*sqrt(v0^2+u) +- f1*sqrt(v0^2+c)
            //
            // The plus sign on the last term corresponds to a lift catch, and v0 > 0
            // corresponds to a lift (upward) throw.  When this equation is converted to a
            // polynomial in the usual way, the result is quartic:
            //
            // c4*v0^4 + c3*v0^3 + c2*v0^2 + c1*v0 + c0 = 0
            //
            // When there is only one bounce, c4=0 always and we reduce to a cubic.

            double[] coef = new double[5];
            coef[4] = 1.0 + kk*kk + f2*f2 - 2.0*kk - 2.0*f2 - 2.0*kk*f2;
            coef[3] = -4.0*gt + 4.0*f2*gt + 4.0*kk*gt;
            coef[2] = 6.0*gt*gt + 2.0*kk*kk*u + 2.0*f2*f2*c - 2.0*f2*c - 2.0*f2*gt*gt -
                2.0*kk*gt*gt - 2.0*kk*u - 2.0*kk*f2*c - 2.0*kk*f2*u;
            coef[1] = -4.0*gt*gt*gt + 4.0*f2*gt*c + 4.0*kk*gt*u;
            coef[0] = gt*gt*gt*gt + kk*kk*u*u + f2*f2*c*c - 2.0*gt*gt*f2*c -
                2.0*kk*gt*gt*u - 2.0*kk*f2*u*c;

            double[] realroot = new double[4];
            int numrealroots = 0;
            
            if (numbounces > 1) {
                // More than one bounce, need to solve the quartic case
                for (int i = 0; i < 4; i++)
                    coef[i] /= coef[4];
                numrealroots = findRealRootsPolynomial(coef, 4, realroot);
                // numrealroots = findRealRootsQuartic(coef[0], coef[1], coef[2], coef[3], realroot);
            } else {
                // A single bounce, which reduces to a cubic polynomial (coef[4]=0)
                for (int i = 0; i < 3; i++)
                    coef[i] /= coef[3];
                numrealroots = findRealRootsPolynomial(coef, 3, realroot);
                // numrealroots = findRealRootsCubic(coef[0], coef[1], coef[2], realroot);
            }

            // Check whether the roots found are physical; due to the way the
            // equation was converted into a polynomial, nonphysical extra solutions
            // with (v0^2+c)<0 are generated.  Check for these.
            for (int i = 0; i < numrealroots; i++) {
                v0 = realroot[i];
                if ((v0*v0 + c) >= 0.0) {
                    root[numroots] = v0;
                    liftcatch[numroots] = ((gt - v0 - k*Math.sqrt(v0*v0+u)) > 0.0);
                    numroots++;
                }
            }

            /*
            System.out.println(numroots + " roots found with "+numbounces+" bounces");
            for (int i = 0; i < numroots; i++)
                System.out.println("   v0["+i+"] = "+root[i]+" -- "+(liftcatch[i]?"lift catch":"forced catch"));
            */
            
            if (numroots == 0)
                continue;	// no solution -> go to the next fewer number of bounces
            
            // Select which root to use.  First try to get the forced and hyper values as
            // desired.  If no solution, try to get forced, then try to get hyper as desired.

            boolean choseroot = false;
            v0 = root[0];	// default
            for (int i = 0; i < numroots; i++) {
                if ((forced && (root[i]>0.0)) || (!forced && (root[i]<0.0)))
                    continue;
                if ((hyper && !(liftcatch[i]^forced)) || (!hyper && (liftcatch[i]^forced)))
                    continue;
                v0 = root[i];
                choseroot = true;
                break;
            }
            if (!choseroot) {
                for (int i = 0; i < numroots; i++) {
                    if ((forced && (root[i]>0.0)) || (!forced && (root[i]<0.0)))
                        continue;
                    v0 = root[i];
                    choseroot = true;
                    break;
                }
            }
            if (!choseroot) {
                for (int i = 0; i < numroots; i++) {
                    if ((hyper && !(liftcatch[i]^(root[i]<0.0))) || (!hyper && (liftcatch[i]^(root[i]<0.0))))
                        continue;
                    v0 = root[i];
                    choseroot = true;
                    break;
                }
            }

            /*
            double lhs = gt - v0 - k*Math.sqrt(v0*v0+u);
            double rhs = f1 * Math.sqrt(v0*v0+c);
            System.out.println("Using root v0 = "+v0+" -- lhs = "+lhs+", rhs = "+rhs);
            */
            
            // finally, set the remaining path variables based on our solution of v0
            bz[0] = v0;
            if (az[0] < 0.0)
                endtime[0] = (-v0 - Math.sqrt(v0*v0 - 4.0*az[0]*(cz[0]-bounceplane))) / (2.0*az[0]);
            else
                endtime[0] = (-v0 + Math.sqrt(v0*v0 - 4.0*az[0]*(cz[0]-bounceplane))) / (2.0*az[0]);
            double vrebound = (-v0 - 2.0*az[0]*endtime[0])*bouncefracsqrt;

            for (int i = 1; i <= numbounces; i++) {
                bz[i] = vrebound - 2.0*az[i]*endtime[i-1];
                cz[i] = bounceplane - az[i]*endtime[i-1]*endtime[i-1] - bz[i]*endtime[i-1];
                endtime[i] = endtime[i-1] - vrebound / az[i];
                vrebound = bouncefracsqrt * vrebound;
            }
            endtime[numbounces] = getDuration();	// fix this assignment from the above loop
            
            return;
        }

        throw new JuggleExceptionInternal("No root found in bouncePath");
    }

    public Coordinate getStartVelocity() {
        return new Coordinate(bx, by, bz[0]);
    }

    public Coordinate getEndVelocity() {
        return new Coordinate(bx, by, bz[numbounces] +
                              2.0*az[numbounces]*(end_time-start_time));
    }

    public void getCoordinate(double time, Coordinate newPosition) {
        if ((time < start_time) || (time > end_time))
            return;
        time -= start_time;

        double zpos = 0.0;
        for (int i = 0; i <= numbounces; i++) {
            if ((time < endtime[i]) || (i == numbounces)) {
                zpos = cz[i] + time*(bz[i] + az[i]*time);
                break;
            }
        }
        newPosition.setCoordinate(cx+bx*time, cy+by*time, zpos);
    }

    protected Coordinate getMax2(double start, double end) {
        Coordinate result = null;
        double tlow = Math.max(start_time, start);
        double thigh = Math.min(end_time, end);

        result = check(result, tlow, true);
        result = check(result, thigh, true);
        if (az[0] < 0.0) {
            double te = -bz[0] / (2.0*az[0]) + start_time;
            if ((tlow < te) && (te < Math.min(thigh, start_time+endtime[0])))
                result = check(result, te, true);
        }
        if (az[numbounces] < 0.0) {
            double te = -bz[numbounces] / (2.0*az[numbounces]) + start_time;
            if ((Math.max(tlow,start_time+endtime[numbounces-1]) < te) && (te < thigh))
                result = check(result, te, true);
        }
        if ((tlow < (start_time+endtime[0])) && ((start_time+endtime[0]) < thigh))
            result = check(result, start_time+endtime[0], true);
        for (int i = 1; i < numbounces; i++) {
            if (az[i] < 0.0) {
                double te = -bz[i] / (2.0*az[i]) + start_time;
                if ((Math.max(tlow,start_time+endtime[i-1]) < te) &&
                                    (te < Math.min(thigh, start_time+endtime[i])))
                    result = check(result, te, true);
            }
            if ((tlow < (start_time+endtime[i])) && ((start_time+endtime[i]) < thigh))
                result = check(result, start_time+endtime[i], true);
        }
        return result;
    }

    protected Coordinate getMin2(double start, double end) {
        Coordinate result = null;
        double tlow = Math.max(start_time, start);
        double thigh = Math.min(end_time, end);

        result = check(result, tlow, false);
        result = check(result, thigh, false);
        if (az[0] > 0.0) {
            double te = -bz[0] / (2.0*az[0]) + start_time;
            if ((tlow < te) && (te < Math.min(thigh, start_time+endtime[0])))
                result = check(result, te, false);
        }
        if (az[numbounces] > 0.0) {
            double te = -bz[numbounces] / (2.0*az[numbounces]) + start_time;
            if ((Math.max(tlow,start_time+endtime[numbounces-1]) < te) && (te < thigh))
                result = check(result, te, false);
        }
        if ((tlow < (start_time+endtime[0])) && ((start_time+endtime[0]) < thigh))
            result = check(result, start_time+endtime[0], false);
        for (int i = 1; i < numbounces; i++) {
            if (az[i] > 0.0) {
                double te = -bz[i] / (2.0*az[i]) + start_time;
                if ((Math.max(tlow,start_time+endtime[i-1]) < te) &&
                    (te < Math.min(thigh, start_time+endtime[i])))
                    result = check(result, te, false);
            }
            if ((tlow < (start_time+endtime[i])) && ((start_time+endtime[i]) < thigh))
                result = check(result, start_time+endtime[i], false);
        }
        return result;
    }

    
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
            return 0;		// no real roots

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
    
    
    static protected double evalPolynomial(double[] coef, int degree, double x) {
        double result = coef[0];
        double term = x;

        for (int i = 1; i < degree; i++) {
            result += coef[i] * term;
            term *= x;
        }

        return (result + term);		// add on x^n term
    }

    // returns other endpoint of interval
    static protected double bracketOpenInterval(double[] coef, int degree, double endpoint, boolean pinf) {
        boolean endpointpositive = (evalPolynomial(coef, degree, endpoint) > 0.0);
        double result = endpoint;
        double adder = (pinf ? 1.0 : -1.0);

        do {
            result += adder;
            adder *= 2.0;
        } while ((evalPolynomial(coef, degree, result) > 0.0) == endpointpositive);

        return result;
    }

    // Find roots of polynomial by successive bisection
    static protected double findRoot(double[] coef, int degree, double xlow, double xhigh) {
        double	val1, val2, valtemp, t;

        val1 = evalPolynomial(coef, degree, xlow);
        val2 = evalPolynomial(coef, degree, xhigh);
    
        if (val1*val2 > 0.0)
            return 0.5*(xlow+xhigh);			// should never happen!
    
        while (Math.abs(xlow-xhigh) > 1e-6) {
            t = 0.5*(xlow+xhigh);
            valtemp = evalPolynomial(coef, degree, t);
            if (valtemp*val1 > 0.0) {
                xlow = t;
                val1 = valtemp;
            } else {
                xhigh = t;
                val2 = valtemp;
            }
        }
        return xlow;
    }
     
    // Find real roots of the polynomial expression:
    //    c0 + c1*x + c2*x^2 + ... + c(n-1)*x^(n-1) + x^n = 0
    //
    // where 'n' is the degree of the polynomial, and the x^n coefficient is always 1.0.
    // The c's are passed in as the 'coef' array
    static protected int findRealRootsPolynomial(double[] coef, int degree, double[] result) {
        // First a few special cases:
        if (degree == 0) {
            return 0;
        } else if (degree == 1) {
            result[0] = -coef[0];
            return 1;
        } else if (degree == 2) {
            // Quadratic formula with a=1.0
            double D = coef[1]*coef[1] - 4.0*coef[0];
            if (D < 0.0) {
                return 0;
            } else if (D == 0.0) {
                result[0] = -0.5*coef[1];
                return 1;
            } else {
                double t = Math.sqrt(D);
                result[0] = -0.5*(coef[1] + t);
                result[1] = -0.5*(coef[1] - t);
                return 2;
            }
        } else if (degree == 3) {
            // Algorithm adapted from Numerical Recipes in C (1st edition), page 157
            double q = coef[2]*coef[2]/9.0 - coef[1]/3.0;
            double r = coef[2]*coef[2]*coef[2]/27.0 - coef[1]*coef[2]/6.0 + coef[0]/2.0;
            double D = r*r - q*q*q;

            if (D > 0.0) {
                // one real root
                double k = Math.pow(Math.sqrt(D) + Math.abs(r), 1.0/3.0);
                result[0] = ((r>0.0) ? -(k+q/k) : (k+q/k)) - coef[2]/3.0;
                return 1;
            } else {
                // three real roots
                double theta = Math.acos(r / Math.sqrt(q*q*q)) / 3.0;
                double k = -2.0 * Math.sqrt(q);
                double p = 2.0 * Math.PI / 3.0;

                result[0] = k * Math.cos(theta) - coef[2]/3.0;
                result[1] = k * Math.cos(theta + p) - coef[2]/3.0;
                result[2] = k * Math.cos(theta + 2.0*p) - coef[2]/3.0;
                return 3;
            }
        }

        // We have degree>=4, so the special cases don't apply.  We proceed by finding
        // the extrema of our polynomial, and using these to bracket each zero.
        double[] dcoef = new double[degree-1];
        double[] extremum = new double[degree-1];
        for (int i = 0; i < (degree-1); i++)
            dcoef[i] = (i+1)*coef[i+1] / (double)degree;
        int numextrema = findRealRootsPolynomial(dcoef, degree-1, extremum);

        boolean pinfpositive = true;
        boolean minfpositive = ((degree % 2) == 0);

        int numroots = 0;

        if (numextrema == 0) {
            boolean zeropositive = (coef[0] > 0.0);

            if (zeropositive != pinfpositive) {
                double endpoint2 = bracketOpenInterval(coef, degree, 0.0, true);
                result[numroots++] = findRoot(coef, degree, 0.0, endpoint2);
            }
            if (zeropositive != minfpositive) {
                double endpoint2 = bracketOpenInterval(coef, degree, 0.0, false);
                result[numroots++] = findRoot(coef, degree, endpoint2, 0.0);
            }
            return numroots;
        }
        
        // Sort the extrema using a bubble sort
        for (int i = 0; i < numextrema; i++) {
            for (int j = i; j < numextrema; j++) {
                if (extremum[i] > extremum[j]) {
                    double temp = extremum[i];
                    extremum[i] = extremum[j];
                    extremum[j] = temp;
                }
            }
        }

        boolean[] extremumpositive = new boolean[numextrema];
        for (int i = 0; i < numextrema; i++)
            extremumpositive[i] = (evalPolynomial(coef, degree, extremum[i]) > 0.0);

        if (minfpositive != extremumpositive[0]) {
            // there is a zero left of the first extremum; bracket it and find it
            double endpoint2 = bracketOpenInterval(coef, degree, extremum[0], false);
            result[numroots++] = findRoot(coef, degree, endpoint2, extremum[0]);
        }

        for (int i = 0; i < (numextrema-1); i++) {
            if (extremumpositive[i] != extremumpositive[i+1]) {
                result[numroots++] = findRoot(coef, degree, extremum[i], extremum[i+1]);
            }
        }

        if (pinfpositive != extremumpositive[numextrema-1]) {
            // there is a zero right of the last extremum; bracket it and find it
            double endpoint2 = bracketOpenInterval(coef, degree, extremum[numextrema-1], true);
            result[numroots++] = findRoot(coef, degree, extremum[numextrema-1], endpoint2);
        }

        return numroots;
    }

    // The returned quantity isn't actually used for volume, so just treat it as yes/no
    public double getBounceVolume(double time1, double time2) {
        if ((time2 < start_time) || (time1 > end_time))
            return 0.0;
        time1 -= start_time;
        time2 -= start_time;

        for (int i = 0; i < numbounces; i++) {
            if (time1 < endtime[i]) {
                if (time2 > endtime[i])
                    return 1.0;
                return 0.0;
            }
        }
        return 0.0;
    }
}
