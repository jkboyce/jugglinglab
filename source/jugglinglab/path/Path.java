// Path.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.path;

import java.util.*;
import jugglinglab.util.*;


// This is the base class for all Path types in Juggling Lab. A Path describes
// the movement of an object during the time between throw and catch.

public abstract class Path {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected double        start_time, end_time;
    protected Coordinate    start_coord, end_coord;

    // The built-in path types
    public static final String[] builtinPaths = { "Toss", "Bounce" };

    // Creates a new path of the given type
    public static Path newPath(String type) throws JuggleExceptionUser {
        if (type == null)
            throw new JuggleExceptionUser("Path type not specified");

        if (type.equalsIgnoreCase("toss"))
            return new TossPath();
        else if (type.equalsIgnoreCase("bounce"))
            return new BouncePath();

        throw new JuggleExceptionUser("Path type '"+type+"' not recognized");
    }

    public void setStart(Coordinate position, double time) {
        start_coord = position;
        start_time = time;
    }
    public void setEnd(Coordinate position, double time) {
        end_coord = position;
        end_time = time;
    }
    public double getStartTime()        { return start_time; }
    public double getEndTime()          { return end_time; }
    public double getDuration()         { return (end_time-start_time); }
    public void translateTime(double deltat) {
        start_time += deltat;
        end_time += deltat;
    }

    // for screen layout
    public Coordinate getMax()  { return getMax2(start_time, end_time); }
    public Coordinate getMin()  { return getMin2(start_time, end_time); }
    public Coordinate getMax(double begin, double end) {
        if (end < start_time || begin > end_time)
            return null;
        return getMax2(begin, end);
    }
    public Coordinate getMin(double begin, double end) {
        if (end < start_time || begin > end_time)
            return null;
        return getMin2(begin, end);
    }

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

    // string indicating the type of path
    public abstract String getType();

    // used for defining the path in the UI (EditLadderDiagram)
    public abstract ParameterDescriptor[] getParameterDescriptors();

    // defines the path from a config string
    public abstract void initPath(String st) throws JuggleExceptionUser;

    public abstract void calcPath() throws JuggleExceptionInternal;

    // for hand layout purposes, only valid after calcPath()
    public abstract Coordinate getStartVelocity();
    public abstract Coordinate getEndVelocity();

    // only valid after calcPath()
    public abstract void getCoordinate(double time, Coordinate newPosition);

    // for hand layout, only valid after calcPath()
    protected abstract Coordinate getMax2(double begin, double end);
    protected abstract Coordinate getMin2(double begin, double end);
}
