//
// PathLink.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml;

import jugglinglab.path.*;
import jugglinglab.util.*;

public class PathLink {
  protected int pathnum;
  protected JMLEvent startevent;
  protected JMLEvent endevent;
  protected int catchtype;
  protected String throwtype;
  protected String mod;
  protected Path proppath;
  protected boolean inhand;
  protected int juggler;
  protected int hand;

  protected boolean ismaster;
  protected PathLink master; // if duplicate

  public PathLink(int pathnum, JMLEvent startevent, JMLEvent endevent) {
    this.pathnum = pathnum;
    this.startevent = startevent;
    this.endevent = endevent;
    proppath = null;
    inhand = false;
  }

  public void setThrow(String type, String mod)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    proppath = Path.newPath(type);
    proppath.initPath(mod);
    proppath.setStart(startevent.getGlobalCoordinate(), startevent.getT());
    proppath.setEnd(endevent.getGlobalCoordinate(), endevent.getT());
    proppath.calcPath();
    throwtype = type;
    this.mod = mod;
    inhand = false;
  }

  public void setInHand(int juggler, int hand) {
    inhand = true;
    this.juggler = juggler;
    this.hand = hand;
  }

  public Path getPath() {
    return proppath;
  }

  public int getCatch() {
    return catchtype;
  }

  public void setCatch(int catchtype) {
    this.catchtype = catchtype;
  }

  public int getPathNum() {
    return pathnum;
  }

  public JMLEvent getStartEvent() {
    return startevent;
  }

  public JMLEvent getEndEvent() {
    return endevent;
  }

  public boolean isInHand() {
    return inhand;
  }

  public int getHoldingJuggler() {
    return juggler;
  }

  public int getHoldingHand() {
    return hand;
  }

  public boolean isMaster() {
    return ismaster;
  }

  // java.lang.Object methods

  @Override
  public String toString() {
    String result;

    if (inhand) {
      result = "In hand, ";
    } else {
      result = "Not in hand (type=\"" + throwtype + "\", mod=\"" + mod + "\"), ";
    }

    Coordinate start = startevent.getGlobalCoordinate();
    result +=
        "from (x=" + start.x + ",y=" + start.y + ",z=" + start.z + ",t=" + startevent.getT() + ") ";
    Coordinate end = endevent.getGlobalCoordinate();
    result += "to (x=" + end.x + ",y=" + end.y + ",z=" + end.z + ",t=" + endevent.getT() + ")";
    return result;
  }
}
