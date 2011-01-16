// HandLink.java
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

package jugglinglab.jml;

import jugglinglab.util.*;
import jugglinglab.curve.*;


public class HandLink {
    protected int		jugglernum;
    protected int		handnum;
    protected JMLEvent		startevent, endevent;
    protected VelocityRef	startvelref, endvelref;
    protected Curve		hp;

    protected boolean		ismaster;
    protected HandLink[]	duplicates;		// if master
    protected HandLink		master;			// if duplicate

    public static final int NO_HAND = 0;
    public static final int LEFT_HAND = 1;
    public static final int RIGHT_HAND = 2;


    public HandLink(int jugglernum, int handnum, JMLEvent startevent, JMLEvent endevent) {
        this.jugglernum = jugglernum;
        this.handnum = handnum;
        this.startevent = startevent;
        this.endevent = endevent;
        this.hp = null;
        this.ismaster = true;
        this.duplicates = null;
        this.master = null;
    }

    public static int index(int handdescription) {
        return (handdescription == LEFT_HAND ? 0 : 1);
    }

    public int getJuggler()				{ return jugglernum; }
    public int getHand()				{ return handnum; }
    public JMLEvent getStartEvent()			{ return startevent; }
    public JMLEvent getEndEvent()			{ return endevent; }
    public VelocityRef getStartVelocityRef()		{ return startvelref; }
    public void setStartVelocityRef(VelocityRef vr)	{ this.startvelref = vr; }
    public VelocityRef getEndVelocityRef()		{ return endvelref; }
    public void setEndVelocityRef(VelocityRef vr)	{ this.endvelref = vr; }
    public void setHandCurve(Curve hp)			{ this.hp = hp; }
    public Curve getHandCurve()				{ return hp; }
    public boolean isMaster()				{ return ismaster; }

    public String toString() {
        String result = null;

        Coordinate start = startevent.getGlobalCoordinate();
        result = "Link from (x="+start.x+",y="+start.y+",z="+start.z+",t="+
            startevent.getT()+") ";
        Coordinate end = endevent.getGlobalCoordinate();
        result += "to (x="+end.x+",y="+end.y+",z="+end.z+",t="+
            endevent.getT()+")";

        VelocityRef vr = getStartVelocityRef();
        if (vr != null) {
            Coordinate vel = vr.getVelocity();
            result += "\n      start velocity (x="+vel.x+",y="+vel.y+",z="+vel.z+")";
        }
        vr = getEndVelocityRef();
        if (vr != null) {
            Coordinate vel = vr.getVelocity();
            result += "\n      end velocity (x="+vel.x+",y="+vel.y+",z="+vel.z+")";
        }
        Curve hp = getHandCurve();
        if (hp != null) {
            Coordinate maxcoord = hp.getMax(startevent.getT(), endevent.getT());
            Coordinate mincoord = hp.getMin(startevent.getT(), endevent.getT());
            result += "\n      minimum (x="+mincoord.x+",y="+mincoord.y+",z="+mincoord.z+")";
            result += "\n      maximum (x="+maxcoord.x+",y="+maxcoord.y+",z="+maxcoord.z+")";
        } else
            result += "\n      no handpath";

        return result;
    }

}
