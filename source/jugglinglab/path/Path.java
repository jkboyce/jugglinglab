// Path.java
//
// Copyright 2002 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

import java.util.*;
import jugglinglab.util.*;


public abstract class Path {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected double		start_time, end_time;
    protected Coordinate	start_coord = null, end_coord = null;

    // The built-in path types
    public static final String[] builtinPaths = { "Toss", "Bounce" };

    // This is a factory to create Paths from names.  Note the
    // naming convention.
    public static Path getPath(String name) throws JuggleExceptionUser {
        if (name == null)
            throw new JuggleExceptionUser("Prop type not specified");

        try {
            Object obj = Class.forName("jugglinglab.path."+name.toLowerCase()+"Path").newInstance();
            //			if (obj == null)
            //				throw new JuggleExceptionUser("Cannot create Path type '"+name+"'");
            if (!(obj instanceof Path))
                throw new JuggleExceptionUser("Path type '"+name+"' doesn't work");
            return (Path)obj;
        }
        catch (ClassNotFoundException cnfe) {
            throw new JuggleExceptionUser("Path type '"+name+"' not found");
        }
        catch (IllegalAccessException iae) {
            throw new JuggleExceptionUser("Cannot access '"+name+"' path file (security)");
        }
        catch (InstantiationException ie) {
            throw new JuggleExceptionUser("Couldn't create '"+name+"' path");
        }
    }

    public abstract String getName();

    public abstract ParameterDescriptor[] getParameterDescriptors();

    public abstract void initPath(String st) throws JuggleExceptionUser;

    public void setStart(Coordinate position, double time) {
        start_coord = position;
        start_time = time;
    }
    public void setEnd(Coordinate position, double time) {
        end_coord = position;
        end_time = time;
    }

    public abstract void calcPath() throws JuggleExceptionInternal;

    public double getStartTime()		{ return start_time; }
    public double getEndTime()			{ return end_time; }
    public double getDuration()			{ return (end_time-start_time); }
    public void	translateTime(double deltat) {
        start_time += deltat;
        end_time += deltat;
    }
    // for hand layout purposes
    public abstract Coordinate getStartVelocity();
    public abstract Coordinate getEndVelocity();

    public abstract void getCoordinate(double time, Coordinate newPosition);

    // for screen layout purposes
    public Coordinate getMax() 	{ return getMax2(start_time, end_time); }
    public Coordinate getMin() 	{ return getMin2(start_time, end_time); }
    public Coordinate getMax(double begin, double end) {
        if ((end < start_time) || (begin > end_time))
            return null;
        return getMax2(begin, end);
    }
    public Coordinate getMin(double begin, double end) {
        if ((end < start_time) || (begin > end_time))
            return null;
        return getMin2(begin, end);
    }
    protected abstract Coordinate getMax2(double begin, double end);
    protected abstract Coordinate getMin2(double begin, double end);

    // utility for getMax/getMin
    protected Coordinate check(Coordinate result, double t, boolean findmax) {
        Coordinate loc = new Coordinate(0.0,0.0,0.0);
        this.getCoordinate(t, loc);
        if (findmax)
            result = Coordinate.max(result, loc);
        else
            result = Coordinate.min(result, loc);
        return result;
    }

}
