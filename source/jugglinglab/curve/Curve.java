// Curve.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.curve;

import jugglinglab.util.*;


public abstract class Curve {
    public static final int splineCurve = 1;    // implemented types
    public static final int lineCurve = 2;

    protected int           numpoints;
    protected Coordinate[]  positions = null;
    protected double[]      times = null;
    protected Coordinate    start_velocity = null, end_velocity = null;

    public abstract void initCurve(String st) throws JuggleExceptionUser;

    public void setCurve(Coordinate[] positions, double[] times, Coordinate start_velocity,
                Coordinate end_velocity) throws JuggleExceptionInternal {
        this.positions = positions;
        this.times = times;
        this.start_velocity = start_velocity;
        this.end_velocity = end_velocity;
        this.numpoints = positions.length;
        if (numpoints != times.length)
            throw new JuggleExceptionInternal("Path error 1");
    }

    public void setCurve(Coordinate[] positions, double[] times) throws JuggleExceptionInternal {
        this.setCurve(positions, times, null, null);
    }

    public abstract void calcCurve() throws JuggleExceptionInternal;

    public double getStartTime()    { return times[0]; }
    public double getEndTime()      { return times[numpoints-1]; }
    public double getDuration()     { return (times[numpoints-1]-times[0]); }
    public void translateTime(double deltat) {
        for (int i = 0; i < numpoints; i++)
            times[i] += deltat;
    }

    public abstract void getCoordinate(double time, Coordinate newPosition);

        // for screen layout purposes
    public Coordinate getMax()  { return getMax2(times[0], times[numpoints-1]); }
    public Coordinate getMin()  { return getMin2(times[0], times[numpoints-1]); }
    public Coordinate getMax(double begin, double end) {
        if ((end < getStartTime()) || (begin > getEndTime()))
            return null;
        return getMax2(begin, end);
    }
    public Coordinate getMin(double begin, double end) {
        if ((end < getStartTime()) || (begin > getEndTime()))
            return null;
        return getMin2(begin, end);
    }
    protected abstract Coordinate getMax2(double begin, double end);
    protected abstract Coordinate getMin2(double begin, double end);

        // utility for getMax2/getMin2
    protected Coordinate check(Coordinate result, double t, boolean findmax) {
        Coordinate loc = new Coordinate();
        this.getCoordinate(t, loc);
        if (findmax)
            result = Coordinate.max(result, loc);
        else
            result = Coordinate.min(result, loc);
        return result;
    }
}
