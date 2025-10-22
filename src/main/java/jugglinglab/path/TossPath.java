//
// TossPath.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.path;

import java.text.MessageFormat;
import jugglinglab.util.*;

public class TossPath extends Path {
  protected static final double G_DEF = 980;  // using CGS units

  protected double bx, cx;
  protected double by, cy;
  protected double az, bz, cz;

  protected double g = G_DEF;

  @Override
  public String getType() {
    return "Toss";
  }

  @Override
  public ParameterDescriptor[] getParameterDescriptors() {
    ParameterDescriptor[] result = new ParameterDescriptor[1];

    result[0] = new ParameterDescriptor(
        "g", ParameterDescriptor.TYPE_FLOAT, null, G_DEF, g);
    return result;
  }

  @Override
  public void initPath(String st) throws JuggleExceptionUser {
    double g = G_DEF;

    // parse for edits to the above variables
    ParameterList pl = new ParameterList(st);
    for (int i = 0; i < pl.getNumberOfParameters(); i++) {
      String pname = pl.getParameterName(i);
      String pvalue = pl.getParameterValue(i);

      if (pname.equalsIgnoreCase("g")) {
        try {
          g = JLFunc.parseDouble(pvalue);
        } catch (NumberFormatException nfe) {
          String template = errorstrings.getString("Error_number_format");
          Object[] arguments = {"g"};
          throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
      } else {
        throw new JuggleExceptionUser(
            errorstrings.getString("Error_path_badmod") + ": '" + pname + "'");
      }
    }
    this.g = g;
    az = -0.5 * g;
  }

  @Override
  public void calcPath() throws JuggleExceptionInternal {
    if (startCoord == null || endCoord == null) {
      throw new JuggleExceptionInternal("Error in parabolic path: endpoints not set");
    }

    double t = getDuration();
    cx = startCoord.x;
    bx = (endCoord.x - startCoord.x) / t;
    cy = startCoord.y;
    by = (endCoord.y - startCoord.y) / t;
    cz = startCoord.z;
    bz = (endCoord.z - startCoord.z) / t - az * t;
  }

  @Override
  public Coordinate getStartVelocity() {
    return new Coordinate(bx, by, bz);
  }

  @Override
  public Coordinate getEndVelocity() {
    return new Coordinate(bx, by, bz + 2 * az * getDuration());
  }

  @Override
  public void getCoordinate(double time, Coordinate newPosition) {
    if (time < getStartTime() || time > getEndTime()) {
      return;
    }
    time -= getStartTime();
    newPosition.setCoordinate(cx + bx * time, cy + by * time, cz + time * (bz + az * time));
  }

  @Override
  protected Coordinate getMax2(double begin, double end) {
    Coordinate result = null;
    double tlow = Math.max(getStartTime(), begin);
    double thigh = Math.min(getEndTime(), end);

    result = check(result, tlow, true);
    result = check(result, thigh, true);

    if (az < 0) {
      double te = -bz / (2 * az) + getStartTime();
      if (tlow < te && te < thigh) {
        result = check(result, te, true);
      }
    }
    return result;
  }

  @Override
  protected Coordinate getMin2(double begin, double end) {
    Coordinate result = null;
    double tlow = Math.max(getStartTime(), begin);
    double thigh = Math.min(getEndTime(), end);

    result = check(result, tlow, false);
    result = check(result, thigh, false);

    if (az > 0) {
      double te = -by / (2 * az) + getStartTime();
      if (tlow < te && te < thigh) {
        result = check(result, te, false);
      }
    }
    return result;
  }
}
