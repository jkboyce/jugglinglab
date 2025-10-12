//
// Curve.java
//
// This type describes a path through 3D space, used to model hand movements as
// well as juggler positions/angles.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.curve;

import jugglinglab.util.*;

public abstract class Curve {
  public static final int CURVE_SPLINE = 1; // implemented types
  public static final int CURVE_LINE = 2;

  protected int numpoints;
  protected double[] times;

  // Positions must not be null. The curve is expected to match each position
  // at its corresponding time.
  protected Coordinate[] positions;

  // How the velocities are used depends on the curve type.
  //
  // For line curves, velocities are ignored.
  //
  // For spline curves, if the velocities at the endpoints are defined (non-
  // null), the curve will match those velocities precisely. For velocities
  // in the middle, the curve will match the *directions* of those velocities,
  // but not their magnitudes. Any of the velocities may be null, in which
  // case the spline will choose a velocity.
  protected Coordinate[] velocities;

  //----------------------------------------------------------------------------
  // Abstract methods for subclasses to define
  //----------------------------------------------------------------------------

  // Calculate the curve; this is called after setting curve parameters but
  // before any calls to getCoordinate().
  public abstract void calcCurve() throws JuggleExceptionInternal;

  // Return the coordinate at a specific time.
  public abstract void getCoordinate(double time, Coordinate newPosition);

  // Find the maximum value of each of the 3 coordinates separately, within
  // the given time range.
  protected abstract Coordinate getMax2(double begin, double end);

  // Find the maximum value of each of the 3 coordinates separately, within
  // the given time range.
  protected abstract Coordinate getMin2(double begin, double end);

  //----------------------------------------------------------------------------
  // Helper methods
  //----------------------------------------------------------------------------

  public void setCurve(double[] times, Coordinate[] positions, Coordinate[] velocities)
      throws JuggleExceptionInternal {
    numpoints = times.length;
    this.times = times;
    this.positions = positions;
    this.velocities = velocities;

    if (numpoints != positions.length || numpoints != velocities.length) {
      throw new JuggleExceptionInternal("Curve error 1");
    }
  }

  public double getStartTime() {
    return times[0];
  }

  public double getEndTime() {
    return times[numpoints - 1];
  }

  public double getDuration() {
    return (times[numpoints - 1] - times[0]);
  }

  public void translateTime(double deltat) {
    for (int i = 0; i < numpoints; i++) {
      times[i] += deltat;
    }
  }

  // for screen layout purposes

  public Coordinate getMax() {
    return getMax2(times[0], times[numpoints - 1]);
  }

  public Coordinate getMin() {
    return getMin2(times[0], times[numpoints - 1]);
  }

  public Coordinate getMax(double begin, double end) {
    if (end < getStartTime() || begin > getEndTime()) {
      return null;
    }
    return getMax2(begin, end);
  }

  public Coordinate getMin(double begin, double end) {
    if (end < getStartTime() || begin > getEndTime()) {
      return null;
    }
    return getMin2(begin, end);
  }

  // utility for getMax2/getMin2

  protected Coordinate check(Coordinate result, double t, boolean findmax) {
    Coordinate loc = new Coordinate();
    getCoordinate(t, loc);
    if (findmax) {
      result = Coordinate.max(result, loc);
    } else {
      result = Coordinate.min(result, loc);
    }
    return result;
  }
}
