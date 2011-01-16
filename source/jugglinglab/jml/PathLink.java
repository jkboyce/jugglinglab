// PathLink.java
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

import jugglinglab.path.*;
import jugglinglab.util.*;


public class PathLink {
    protected int		pathnum;
    protected JMLEvent		startevent, endevent;
    protected int		catchtype;
    protected String		throwtype, mod;
    protected Path		proppath;
    protected boolean		inhand;
    protected int		juggler, hand;

    protected boolean		ismaster;
    protected PathLink[]	duplicates;		// if master
    protected PathLink		master;			// if duplicate


    public PathLink(int pathnum, JMLEvent startevent, JMLEvent endevent) {
        this.pathnum = pathnum;
        this.startevent = startevent;
        this.endevent = endevent;
        this.proppath = null;
        this.inhand = false;
    }

    public void setThrow(String type, String mod) throws JuggleExceptionUser, JuggleExceptionInternal {
        proppath = Path.getPath(type);
        proppath.initPath(mod);
        proppath.setStart(startevent.getGlobalCoordinate(), startevent.getT());
        proppath.setEnd(endevent.getGlobalCoordinate(), endevent.getT());
        proppath.calcPath();
        this.throwtype = type;
        this.mod = mod;
        this.inhand = false;
    }

    public void setInHand(int juggler, int hand) {
        this.inhand = true;
        this.juggler = juggler;
        this.hand = hand;
    }

    public Path getPath() 		{ return proppath; }

    public int getCatch()		{ return catchtype; }
    public void setCatch(int catchtype) { this.catchtype = catchtype; }
    public int getPathNum()		{ return pathnum; }
    public JMLEvent getStartEvent()	{ return startevent; }
    public JMLEvent getEndEvent()	{ return endevent; }

    public boolean isInHand()		{ return inhand; }
    public int getHoldingJuggler()	{ return juggler; }
    public int getHoldingHand()		{ return hand; }
    public boolean isMaster()		{ return ismaster; }

    public String toString() {
        String result = null;

        if (inhand) {
            result = "In hand, ";
        } else {
            result = "Not in hand (type=\""+throwtype+"\", mod=\""+
            mod+"\"), ";
        }

        Coordinate start = startevent.getGlobalCoordinate();
        result += "from (x="+start.x+",y="+start.y+",z="+start.z+",t="+
            startevent.getT()+") ";
        Coordinate end = endevent.getGlobalCoordinate();
        result += "to (x="+end.x+",y="+end.y+",z="+end.z+",t="+endevent.getT()+")";
        return result;
    }
}
