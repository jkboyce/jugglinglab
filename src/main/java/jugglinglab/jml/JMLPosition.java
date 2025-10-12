//
// JMLPosition.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml;

import java.io.*;
import java.util.*;
import jugglinglab.util.*;

public class JMLPosition {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

  protected double x, y, z, t, angle;
  protected int juggler;
  protected JMLPosition prev, next; // for doubly-linked event list

  public JMLPosition() {}

  public Coordinate getCoordinate() {
    return new Coordinate(x, y, z);
  }

  public void setCoordinate(Coordinate c) {
    x = c.x;
    y = c.y;
    z = c.z;
  }

  public double getAngle() {
    return angle;
  }

  public void setAngle(double angle) {
    this.angle = angle;
  }

  public double getT() {
    return t;
  }

  public void setT(double t) {
    this.t = t;
  }

  public int getJuggler() {
    return juggler;
  }

  public void setJuggler(String strjuggler) {
    juggler = Integer.parseInt(strjuggler);
  }

  public void setJuggler(int j) {
    juggler = j;
  }

  public JMLPosition getPrevious() {
    return prev;
  }

  public void setPrevious(JMLPosition prev) {
    this.prev = prev;
  }

  public JMLPosition getNext() {
    return next;
  }

  public void setNext(JMLPosition next) {
    this.next = next;
  }

  public int getHashCode() {
    return toString().hashCode();
  }

  //----------------------------------------------------------------------------
  //  Reader/writer methods
  //----------------------------------------------------------------------------

  public void readJML(JMLNode current, String jmlvers) throws JuggleExceptionUser {
    JMLAttributes at = current.getAttributes();
    double tempx = 0, tempy = 0, tempz = 0, tempt = 0, tempangle = 0;
    String jugglerstr = "1";

    try {
      for (int i = 0; i < at.getNumberOfAttributes(); i++) {
        // System.out.println("att. "+i+" = "+at.getAttributeValue(i));
        if (at.getAttributeName(i).equalsIgnoreCase("x")) {
          tempx = JLFunc.parseDouble(at.getAttributeValue(i));
        } else if (at.getAttributeName(i).equalsIgnoreCase("y")) {
          tempy = JLFunc.parseDouble(at.getAttributeValue(i));
        } else if (at.getAttributeName(i).equalsIgnoreCase("z")) {
          tempz = JLFunc.parseDouble(at.getAttributeValue(i));
        } else if (at.getAttributeName(i).equalsIgnoreCase("t")) {
          tempt = JLFunc.parseDouble(at.getAttributeValue(i));
        } else if (at.getAttributeName(i).equalsIgnoreCase("angle")) {
          tempangle = JLFunc.parseDouble(at.getAttributeValue(i));
        } else if (at.getAttributeName(i).equalsIgnoreCase("juggler")) {
          jugglerstr = at.getAttributeValue(i);
        }
      }
    } catch (NumberFormatException nfe) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_position_coordinate"));
    }

    setCoordinate(new Coordinate(tempx, tempy, tempz));
    setT(tempt);
    setAngle(tempangle);
    if (jugglerstr == null) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_position_nojuggler"));
    }
    setJuggler(jugglerstr);

    if (current.getNumberOfChildren() != 0) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_position_subtag"));
    }
  }

  public void writeJML(PrintWriter wr) throws IOException {
    Coordinate c = getCoordinate();
    wr.println(
        "<position x=\""
            + JLFunc.toStringRounded(c.x, 4)
            + "\" y=\""
            + JLFunc.toStringRounded(c.y, 4)
            + "\" z=\""
            + JLFunc.toStringRounded(c.z, 4)
            + "\" t=\""
            + JLFunc.toStringRounded(getT(), 4)
            + "\" angle=\""
            + JLFunc.toStringRounded(getAngle(), 4)
            + "\" juggler=\""
            + Integer.toString(getJuggler())
            + "\"/>");
  }

  // java.lang.Object methods

  @Override
  public String toString() {
    StringWriter sw = new StringWriter();
    try {
      writeJML(new PrintWriter(sw));
    } catch (IOException ioe) {
    }

    return sw.toString();
  }
}
