// JMLPosition.java
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

import java.util.*;
import java.io.*;


public class JMLPosition {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected double x, y, z, t, angle;
    protected int juggler;
    protected JMLPosition prev, next;	// for doubly-linked event list


    public JMLPosition() {
        this.prev = this.next = null;
    }

    public Coordinate getCoordinate()			{ return new Coordinate(x,y,z); }
    public void setCoordinate(Coordinate c) {
        this.x = c.x;
        this.y = c.y;
        this.z = c.z;
    }

    public double getAngle()					{ return angle; }
    public void setAngle(double angle)			{ this.angle = angle; }
    public double getT()						{ return t; }
    public void setT(double t)					{ this.t = t; }
    public int getJuggler()						{ return juggler; }
    public void setJuggler(String strjuggler) throws JuggleExceptionUser {
        this.juggler = Integer.parseInt(strjuggler);
    }
    public void setJuggler(int j) {
        this.juggler = j;
    }

    public JMLPosition getPrevious()			{ return prev; }
    public void setPrevious(JMLPosition prev)	{ this.prev = prev; }
    public JMLPosition getNext()				{ return next; }
    public void setNext(JMLPosition next)		{ this.next = next; }


    // Methods to handle JML
    public void readJML(JMLNode current, String jmlvers) throws JuggleExceptionUser {
        JMLAttributes at = current.getAttributes();
        double tempx=0.0, tempy=0.0, tempz=0.0, tempt=0.0, tempangle=0.0;
        String jugglerstr = "1";

        try {
            for (int i = 0; i < at.getNumberOfAttributes(); i++) {
                //			System.out.println("att. "+i+" = "+at.getAttributeValue(i));
                if (at.getAttributeName(i).equalsIgnoreCase("x"))
                    tempx = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("y"))
                    tempy = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("z"))
                    tempz = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("t"))
                    tempt = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("angle"))
                    tempangle = Double.valueOf(at.getAttributeValue(i)).doubleValue();
                else if (at.getAttributeName(i).equalsIgnoreCase("juggler"))
                    jugglerstr = at.getAttributeValue(i);
            }
        } catch (NumberFormatException nfe) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_position_coordinate"));
        }

        setCoordinate(new Coordinate(tempx,tempy,tempz));
        setT(tempt);
        setAngle(tempangle);
        if (jugglerstr == null)
            throw new JuggleExceptionUser(errorstrings.getString("Error_position_nojuggler"));
        setJuggler(jugglerstr);

        if (current.getNumberOfChildren() != 0)
            throw new JuggleExceptionUser(errorstrings.getString("Error_position_subtag"));
    }

    public void writeJML(PrintWriter wr) throws IOException {
        Coordinate c = getCoordinate();
        wr.println("<position x=\""+JMLPattern.toStringTruncated(c.x,4)
                   +"\" y=\""+JMLPattern.toStringTruncated(c.y,4)
                   +"\" z=\""+JMLPattern.toStringTruncated(c.z,4)
                   +"\" t=\""+JMLPattern.toStringTruncated(getT(),4)
                   +"\" angle=\""+JMLPattern.toStringTruncated(getAngle(),4)
                   +"\" juggler=\""+Integer.toString(getJuggler())+"\"/>");
    }
}
