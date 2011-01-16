// tossPath.java
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


public class tossPath extends Path {
    protected static final double g_def = 980;	// using CGS units

    protected double	bx, cx;
    protected double	by, cy;
    protected double	az, bz, cz;

    protected double	g = g_def;

    public String getName() { return "Toss"; }

    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[1];

        result[0] = new ParameterDescriptor("g", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(g_def), new Double(g));
        return result;
    }

    public void initPath(String st) throws JuggleExceptionUser {
        double g = g_def;

        // now parse for edits to the above variables
        ParameterList pl = new ParameterList(st);
        for (int i = 0; i < pl.getNumberOfParameters(); i++) {
            String pname = pl.getParameterName(i);
            String pvalue = pl.getParameterValue(i);

            if (pname.equalsIgnoreCase("g")) {
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
        this.g = g;
        az = -0.5 * g;
    }

    public void	calcPath() throws JuggleExceptionInternal {
        if (start_coord == null || end_coord == null)
            throw new JuggleExceptionInternal("Error in parabolic path: endpoints not set");

        double	t = getDuration();
        cx = start_coord.x;
        bx = (end_coord.x - start_coord.x) / t;
        cy = start_coord.y;
        by = (end_coord.y - start_coord.y) / t;
        cz = start_coord.z;
        bz = (end_coord.z - start_coord.z) / t - az * t;
    }

    public Coordinate getStartVelocity() {
        return new Coordinate(bx, by, bz);
    }

    public Coordinate getEndVelocity() {
        return new Coordinate(bx, by, bz+2.0*az*getDuration());
    }

    public void getCoordinate(double time, Coordinate newPosition) {
        if ((time < start_time) || (time > end_time))
            return;
        time -= start_time;
        newPosition.setCoordinate(cx+bx*time, cy+by*time, cz+time*(bz+az*time));
    }

    protected Coordinate getMax2(double begin, double end) {
        Coordinate result = null;
        double tlow = Math.max(start_time, begin);
        double thigh = Math.min(end_time, end);

        result = check(result, tlow, true);
        result = check(result, thigh, true);

        if (az < 0.0) {
            double te = -bz / (2.0*az) + start_time;
            if ((tlow < te) && (te < thigh))
                result = check(result, te, true);
        }
        return result;
    }

    protected Coordinate getMin2(double begin, double end) {
        Coordinate result = null;
        double tlow = Math.max(start_time, begin);
        double thigh = Math.min(end_time, end);

        result = check(result, tlow, false);
        result = check(result, thigh, false);

        if (az > 0.0) {
            double te = -by / (2.0*az) + start_time;
            if ((tlow < te) && (te < thigh))
                result = check(result, te, false);
        }
        return result;
    }
}
