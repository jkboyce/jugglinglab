//
// JMLEvent.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml;

import java.io.*;
import java.util.*;
import jugglinglab.util.*;

public class JMLEvent {
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

  protected double x, y, z;  // coordinates in local frame
  protected double gx, gy, gz;  // coordinates in global frame
  protected boolean globalvalid;  // global coordinates need to be recalced?
  protected double t;
  protected int juggler;
  protected int hand;
  protected ArrayList<JMLTransition> transitions;
  protected int delay;
  protected int delayunits;
  protected Permutation pathpermfrommaster;
  protected JMLEvent master;  // null if this is a master event
  public boolean calcpos;

  protected JMLEvent prev, next;  // for doubly-linked event list

  public JMLEvent() {
    calcpos = false;
    transitions = new ArrayList<>();
  }

  public Coordinate getLocalCoordinate() {
    return new Coordinate(x, y, z);
  }

  public void setLocalCoordinate(Coordinate c) {
    x = c.x;
    y = c.y;
    z = c.z;
    globalvalid = false;
  }

  public Coordinate getGlobalCoordinate() {
    return (globalvalid ? new Coordinate(gx, gy, gz) : null);
  }

  public void setGlobalCoordinate(Coordinate c) {
    gx = c.x;
    gy = c.y;
    gz = c.z;
    globalvalid = true;
  }

  public double getT() {
    return t;
  }

  public void setT(double time) {
    t = time;
  }

  public int getHand() {
    return hand;
  }

  public void setHand(String strhand) throws JuggleExceptionUser {
    int index = strhand.indexOf(":");

    if (index == -1) {
      juggler = 1;
      if (strhand.equalsIgnoreCase("left")) {
        hand = HandLink.LEFT_HAND;
      } else if (strhand.equalsIgnoreCase("right")) {
        hand = HandLink.RIGHT_HAND;
      } else {
        throw new JuggleExceptionUser(
            errorstrings.getString("Error_hand_name") + " '" + strhand + "'");
      }
    } else {
      juggler = Integer.parseInt(strhand.substring(0, index));
      String substr = strhand.substring(index + 1);
      if (substr.equalsIgnoreCase("left")) {
        hand = HandLink.LEFT_HAND;
      } else if (substr.equalsIgnoreCase("right")) {
        hand = HandLink.RIGHT_HAND;
      } else {
        throw new JuggleExceptionUser(
            errorstrings.getString("Error_hand_name") + " '" + strhand + "'");
      }
    }
  }

  public void setHand(int j, int h) {
    juggler = j;
    hand = h;  // HandLink.LEFT_HAND or HandLink.RIGHT_HAND
  }

  public int getJuggler() {
    return juggler;
  }

  public int getNumberOfTransitions() {
    return transitions.size();
  }

  public JMLTransition getTransition(int index) {
    return transitions.get(index);
  }

  public Collection<JMLTransition> transitions() {
    return transitions;
  }

  public void addTransition(JMLTransition trans) {
    transitions.add(trans);
  }

  public void removeTransition(int index) {
    transitions.remove(index);
  }

  public void removeTransition(JMLTransition trans) {
    transitions.remove(trans);
  }

  public boolean isMaster() {
    return (master == null);
  }

  public JMLEvent getMaster() {
    return master;
  }

  public void setMaster(JMLEvent master) {
    this.master = master;
  }

  public JMLEvent getPrevious() {
    return prev;
  }

  public void setPrevious(JMLEvent prev) {
    this.prev = prev;
  }

  public JMLEvent getNext() {
    return next;
  }

  public void setNext(JMLEvent next) {
    this.next = next;
  }

  public JMLEvent getPreviousForHand() {
    JMLEvent ev = getPrevious();

    while (ev != null) {
      if (ev.getJuggler() == getJuggler() && ev.getHand() == getHand()) {
        return ev;
      }
      ev = ev.getPrevious();
    }
    return null;
  }

  public JMLEvent getNextForHand() {
    JMLEvent ev = getNext();

    while (ev != null) {
      if (ev.getJuggler() == getJuggler() && ev.getHand() == getHand()) {
        return ev;
      }
      ev = ev.getNext();
    }
    return null;
  }

  public Permutation getPathPermFromMaster() {
    return pathpermfrommaster;
  }

  public void setPathPermFromMaster(Permutation p) {
    pathpermfrommaster = p;
  }

  public boolean isDelayOf(JMLEvent ev2) {
    JMLEvent mast1 = (getMaster() == null ? this : getMaster());
    JMLEvent mast2 = (ev2.getMaster() == null ? ev2 : ev2.getMaster());

    if (mast1 != mast2) {
      return false;
    }
    if (getJuggler() != ev2.getJuggler() || getHand() != ev2.getHand()) {
      return false;
    }

    int totaldelay = delay - ev2.delay;
    if (totaldelay < 0) {
      totaldelay = -totaldelay;
    }
    return (totaldelay % delayunits) == 0;
  }

  public boolean hasSameMasterAs(JMLEvent ev2) {
    JMLEvent mast1 = (getMaster() == null ? this : getMaster());
    JMLEvent mast2 = (ev2.getMaster() == null ? ev2 : ev2.getMaster());

    return (mast1 == mast2);
  }

  public JMLTransition getPathTransition(int path, int transtype) {
    for (JMLTransition tr : transitions) {
      if (tr.getPath() == path) {
        if (transtype == JMLTransition.TRANS_ANY || transtype == tr.getType()) {
          return tr;
        }
      }
    }
    return null;
  }

  public boolean hasThrow() {
    for (JMLTransition tr : transitions()) {
      if (tr.getType() == JMLTransition.TRANS_THROW) {
        return true;
      }
    }
    return false;
  }

  public boolean hasThrowOrCatch() {
    for (JMLTransition tr : transitions()) {
      int type = tr.getType();

      if (type == JMLTransition.TRANS_THROW
          || type == JMLTransition.TRANS_CATCH
          || type == JMLTransition.TRANS_SOFTCATCH
          || type == JMLTransition.TRANS_GRABCATCH) {
        return true;
      }
    }
    return false;
  }

  // Return true if the event contains a throw transition to another juggler.
  //
  // Note this will only work after pattern layout.

  public boolean hasPassingThrow() {
    for (JMLTransition tr : transitions()) {
      if (tr.getType() != JMLTransition.TRANS_THROW) {
        continue;
      }

      PathLink pl = tr.getOutgoingPathLink();
      if (pl == null || pl.getEndEvent() == null) {
        continue;
      }
      if (pl.getEndEvent().getJuggler() != getJuggler()) {
        return true;
      }
    }
    return false;
  }

  // Return true if the event contains a catch transition from another juggler.
  //
  // Note this will only work after pattern layout.

  public boolean hasPassingCatch() {
    for (JMLTransition tr : transitions()) {
      if (tr.getType() != JMLTransition.TRANS_CATCH
          && tr.getType() != JMLTransition.TRANS_SOFTCATCH
          && tr.getType() != JMLTransition.TRANS_GRABCATCH) {
        continue;
      }

      PathLink pl = tr.getIncomingPathLink();
      if (pl == null || pl.getStartEvent() == null) {
        continue;
      }
      if (pl.getStartEvent().getJuggler() != getJuggler()) {
        return true;
      }
    }
    return false;
  }

  // Note this will only work after pattern layout.

  public boolean hasPassingTransition() {
    return (hasPassingThrow() || hasPassingCatch());
  }

  public JMLEvent duplicate(int delay, int delayunits) {
    JMLEvent dup = new JMLEvent();
    dup.setLocalCoordinate(getLocalCoordinate());
    dup.setT(getT());
    dup.setHand(getJuggler(), getHand());
    dup.delay = delay;
    dup.delayunits = delayunits;
    dup.calcpos = calcpos;

    for (JMLTransition tr : transitions()) {
      dup.addTransition(tr.duplicate());
    }

    dup.setMaster(isMaster() ? this : master);
    return dup;
  }

  // For locating a particular event in a pattern.

  public int getHashCode() {
    Coordinate c = getLocalCoordinate();
    String s =
        "<event x=\""
            + JLFunc.toStringRounded(c.x, 4)
            + "\" y=\""
            + JLFunc.toStringRounded(c.y, 4)
            + "\" z=\""
            + JLFunc.toStringRounded(c.z, 4)
            + "\" t=\""
            + JLFunc.toStringRounded(getT(), 4)
            + "\" hand=\""
            + getJuggler()
            + ":"
            + (getHand() == HandLink.LEFT_HAND ? "left" : "right")
            + "\">";
    return s.hashCode();
  }

  //----------------------------------------------------------------------------
  // Reader/writer methods
  //----------------------------------------------------------------------------

  public void readJML(JMLNode current, String jmlvers, int njugglers, int npaths)
      throws JuggleExceptionUser {
    JMLAttributes at = current.getAttributes();
    double tempx = 0, tempy = 0, tempz = 0, tempt = 0;
    String handstr = null;

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
        } else if (at.getAttributeName(i).equalsIgnoreCase("hand")) {
          handstr = at.getAttributeValue(i);
        }
      }
    } catch (NumberFormatException nfe) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_event_coordinate"));
    }

    // JML version 1.0 used a different coordinate system -- convert
    if (jmlvers.equals("1.0")) {
      double temp = tempy;
      tempy = tempz;
      tempz = temp;
    }

    setLocalCoordinate(new Coordinate(tempx, tempy, tempz));
    setT(tempt);
    if (handstr == null) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_unspecified_hand"));
    }
    setHand(handstr);
    if (juggler > njugglers || juggler < 1) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_juggler_out_of_range"));
    }

    // process current event node children
    for (int i = 0; i < current.getNumberOfChildren(); i++) {
      JMLNode child = current.getChildNode(i);
      String nodetype = child.nodeType;
      at = child.getAttributes();
      String path = null, transtype = null, mod = null;

      for (int j = 0; j < at.getNumberOfAttributes(); j++) {
        String value = at.getAttributeValue(j);
        if (at.getAttributeName(j).equalsIgnoreCase("path")) {
          path = value;
        } else if (at.getAttributeName(j).equalsIgnoreCase("type")) {
          transtype = value;
        } else if (at.getAttributeName(j).equalsIgnoreCase("mod")) {
          mod = value;
        }
      }

      if (path == null) {
        throw new JuggleExceptionUser(errorstrings.getString("Error_no_path"));
      }

      int pnum = Integer.parseInt(path);
      if (pnum > npaths || pnum < 1) {
        throw new JuggleExceptionUser(errorstrings.getString("Error_path_out_of_range"));
      }

      if (nodetype.equalsIgnoreCase("throw")) {
        addTransition(new JMLTransition(JMLTransition.TRANS_THROW, pnum, transtype, mod));
      } else if (nodetype.equalsIgnoreCase("catch") && transtype.equalsIgnoreCase("soft")) {
        addTransition(new JMLTransition(JMLTransition.TRANS_SOFTCATCH, pnum, null, null));
      } else if (nodetype.equalsIgnoreCase("catch") && transtype.equalsIgnoreCase("grab")) {
        addTransition(new JMLTransition(JMLTransition.TRANS_GRABCATCH, pnum, null, null));
      } else if (nodetype.equalsIgnoreCase("catch")) {
        addTransition(new JMLTransition(JMLTransition.TRANS_CATCH, pnum, null, null));
      } else if (nodetype.equalsIgnoreCase("holding")) {
        addTransition(new JMLTransition(JMLTransition.TRANS_HOLDING, pnum, null, null));
      }

      if (child.getNumberOfChildren() != 0) {
        throw new JuggleExceptionUser(errorstrings.getString("Error_event_subtag"));
      }
    }
  }

  public void writeJML(PrintWriter wr) throws IOException {
    Coordinate c = getLocalCoordinate();
    wr.println(
        "<event x=\""
            + JLFunc.toStringRounded(c.x, 4)
            + "\" y=\""
            + JLFunc.toStringRounded(c.y, 4)
            + "\" z=\""
            + JLFunc.toStringRounded(c.z, 4)
            + "\" t=\""
            + JLFunc.toStringRounded(getT(), 4)
            + "\" hand=\""
            + getJuggler()
            + ":"
            + (getHand() == HandLink.LEFT_HAND ? "left" : "right")
            + "\">");
    for (JMLTransition tr : transitions()) {
      tr.writeJML(wr);
    }
    wr.println("</event>");
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
