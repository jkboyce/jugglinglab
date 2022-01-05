// JMLEvent.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import jugglinglab.util.*;

import java.util.*;
import java.io.*;


public class JMLEvent {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected double x, y, z;  // coordinates in local frame
    protected double gx, gy, gz;  // coordinates in global frame
    protected boolean globaldirty;  // global coordinates need to be recalced?
    protected double t;
    protected int juggler;
    protected int hand;
    protected ArrayList<JMLTransition> transitions;
    protected int[][][] eventarray;
    protected int delay;
    protected int delayunits;
    protected Permutation pathpermfrommaster;
    protected JMLEvent master;  // null if this is a master event
    public boolean calcpos;

    protected JMLEvent prev, next;  // for doubly-linked event list


    public JMLEvent() {
        calcpos = false;
        transitions = new ArrayList<JMLTransition>();
        globaldirty = true;
    }

    public Coordinate getLocalCoordinate()  {
        return new Coordinate(x,y,z);
    }

    public void setLocalCoordinate(Coordinate c) {
        x = c.x;
        y = c.y;
        z = c.z;
        globaldirty = true;
    }

    public Coordinate getGlobalCoordinate() {
        return (globaldirty ? null : new Coordinate(gx, gy, gz));
    }

    public void setGlobalCoordinate(Coordinate c) {
        gx = c.x;
        gy = c.y;
        gz = c.z;
        globaldirty = false;
    }

    public double getT() {
        return t;
    }

    public void setT(double t) {
        this.t = t;
    }

    public int getHand() {
        return hand;
    }

    public void setHand(String strhand) throws JuggleExceptionUser {
        int index = strhand.indexOf(":");

        if (index == -1) {
            juggler = 1;
            if (strhand.equalsIgnoreCase("left"))
                hand = HandLink.LEFT_HAND;
            else if (strhand.equalsIgnoreCase("right"))
                hand = HandLink.RIGHT_HAND;
            else
                throw new JuggleExceptionUser(errorstrings.getString("Error_hand_name")+" '"+strhand+"'");
        } else {
            juggler = Integer.valueOf(strhand.substring(0,index)).intValue();
            String substr = strhand.substring(index+1);
            if (substr.equalsIgnoreCase("left"))
                hand = HandLink.LEFT_HAND;
            else if (substr.equalsIgnoreCase("right"))
                hand = HandLink.RIGHT_HAND;
            else
                throw new JuggleExceptionUser(errorstrings.getString("Error_hand_name")+" '"+strhand+"'");
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

    public void addTransition(JMLTransition trans) {
        transitions.add(trans);
    }

    public void removeTransition(int index) {
        transitions.remove(index);
    }

    public void removeTransition(JMLTransition trans) {
        for (int i = 0; i < getNumberOfTransitions(); i++) {
            if (getTransition(i) == trans) {
                removeTransition(i);
                return;
            }
        }
    }

    public boolean isMaster() {
        return (master==null);
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

    public Permutation getPathPermFromMaster() {
        return pathpermfrommaster;
    }

    public void setPathPermFromMaster(Permutation p) {
        pathpermfrommaster = p;
    }

    public boolean isDelayOf(JMLEvent ev2) {
        JMLEvent mast1 = (getMaster() == null ? this : getMaster());
        JMLEvent mast2 = (ev2.getMaster() == null ? ev2 : ev2.getMaster());

        if (mast1 != mast2)
            return false;
        if (getJuggler() != ev2.getJuggler() || getHand() != ev2.getHand())
            return false;

        int totaldelay = delay - ev2.delay;
        if (totaldelay < 0)
            totaldelay = -totaldelay;
        if ((totaldelay % delayunits) == 0)
            return true;

        return false;
    }

    public JMLTransition getPathTransition(int path, int transtype) {
        for (JMLTransition tr : transitions) {
            if (tr.getPath() == path) {
                if (transtype == JMLTransition.TRANS_ANY || transtype == tr.getType())
                    return tr;
            }
        }
        return null;
    }

    public JMLEvent duplicate(int delay, int delayunits) {
        JMLEvent dup = new JMLEvent();
        dup.setLocalCoordinate(getLocalCoordinate());
        dup.setT(getT());
        dup.setHand(getJuggler(), getHand());
        dup.delay = delay;
        dup.delayunits = delayunits;
        dup.calcpos = calcpos;

        for (int i = 0; i < getNumberOfTransitions(); i++) {
            JMLTransition trans = getTransition(i).duplicate();
            dup.addTransition(trans);
        }
        dup.setMaster(isMaster() ? this : master);
        return dup;
    }

    //-------------------------------------------------------------------------
    //  Reader/writer methods
    //-------------------------------------------------------------------------

    public void readJML(JMLNode current, String jmlvers, int njugglers, int npaths)
                                            throws JuggleExceptionUser {
        JMLAttributes at = current.getAttributes();
        double tempx = 0.0, tempy = 0.0, tempz = 0.0, tempt = 0.0;
        String handstr = null;

        try {
            for (int i = 0; i < at.getNumberOfAttributes(); i++) {
                // System.out.println("att. "+i+" = "+at.getAttributeValue(i));
                if (at.getAttributeName(i).equalsIgnoreCase("x"))
                    tempx = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("y"))
                    tempy = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("z"))
                    tempz = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("t"))
                    tempt = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("hand"))
                    handstr = at.getAttributeValue(i);
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

        setLocalCoordinate(new Coordinate(tempx,tempy,tempz));
        setT(tempt);
        if (handstr == null)
            throw new JuggleExceptionUser(errorstrings.getString("Error_unspecified_hand"));
        setHand(handstr);
        if (juggler > njugglers || juggler < 1)
            throw new JuggleExceptionUser(errorstrings.getString("Error_juggler_out_of_range"));

        // process current event node children
        for (int i = 0; i < current.getNumberOfChildren(); i++) {
            JMLNode child = current.getChildNode(i);
            String type = child.getNodeType();
            at = child.getAttributes();
            String path = null, throwtype = null, mod = null;

            for (int j = 0; j < at.getNumberOfAttributes(); j++) {
                String value = at.getAttributeValue(j);
                if (at.getAttributeName(j).equalsIgnoreCase("path"))
                    path = value;
                else if (at.getAttributeName(j).equalsIgnoreCase("type"))
                    throwtype = value;
                else if (at.getAttributeName(j).equalsIgnoreCase("mod"))
                    mod = value;
            }

            if (path == null)
                throw new JuggleExceptionUser(errorstrings.getString("Error_no_path"));

            int pnum = Integer.valueOf(path).intValue();
            if ((pnum > npaths) || (pnum < 1))
                throw new JuggleExceptionUser(errorstrings.getString("Error_path_out_of_range"));

            if (type.equalsIgnoreCase("throw"))
                addTransition(new JMLTransition(JMLTransition.TRANS_THROW, pnum, throwtype, mod));
            else if (type.equalsIgnoreCase("catch"))
                addTransition(new JMLTransition(JMLTransition.TRANS_CATCH, pnum, null, null));
            else if (type.equalsIgnoreCase("softcatch"))
                addTransition(new JMLTransition(JMLTransition.TRANS_SOFTCATCH, pnum, null, null));
            else if (type.equalsIgnoreCase("holding"))
                addTransition(new JMLTransition(JMLTransition.TRANS_HOLDING, pnum, null, null));

            if (child.getNumberOfChildren() != 0)
                throw new JuggleExceptionUser(errorstrings.getString("Error_event_subtag"));
        }
    }

    public void writeJML(PrintWriter wr) throws IOException {
        Coordinate c = getLocalCoordinate();
        wr.println("<event x=\"" + JLFunc.toStringRounded(c.x, 4) +
                   "\" y=\"" + JLFunc.toStringRounded(c.y, 4) +
                   "\" z=\"" + JLFunc.toStringRounded(c.z, 4) +
                   "\" t=\"" + JLFunc.toStringRounded(getT(), 4) +
                   "\" hand=\"" + Integer.toString(getJuggler()) + ":" +
                   (getHand() == HandLink.LEFT_HAND ? "left" : "right") +
                   "\">");
        for (int i = 0; i < getNumberOfTransitions(); ++i)
            getTransition(i).writeJML(wr);
        wr.println("</event>");
    }
}
