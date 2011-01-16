// lineCurve.java
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


public class lineCurve extends Curve {
    protected int			n;				// number of line segments
    protected double[][]	a, b;			// line coefficients
    protected double[]		durations;		// durations of segments

    public void initCurve(String st) {
    }

    public void	calcCurve() throws JuggleExceptionInternal {
        this.n = numpoints - 1;
        if (n < 1)
            throw new JuggleExceptionInternal("lineCurve error 1");

        this.a = new double[n][3];
        this.b = new double[n][3];
        this.durations = new double[n];
        for (int i = 0; i < n; i++) {
            durations[i] = times[i+1] - times[i];
            if (durations[i] < 0.0)
                throw new JuggleExceptionInternal("lineCurve error 2");
        }

        double[] x = new double[n+1];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < (n+1); j++)
                x[j] = positions[j].getIndex(i);

            // now solve for line coefficients
            for (int j = 0; j < n; j++) {
                a[j][i] = x[j];
                b[j][i] = (x[j+1] - x[j]) / durations[j];
            }
        }
    }

    public void getCoordinate(double time, Coordinate newPosition) {
        if ((time < times[0]) || (time > times[n]))
            return;

        int i;
        for (i = 0; i < n; i++)
            if (time <= times[i+1])
                break;
        if (i == n)
            i = n - 1;
        
        time -= times[i];
        newPosition.setCoordinate(
                                  a[i][0] + time * b[i][0],
                                  a[i][1] + time * b[i][1],
                                  a[i][2] + time * b[i][2] );
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
                }
            }
        }

        return result;
    }
}
