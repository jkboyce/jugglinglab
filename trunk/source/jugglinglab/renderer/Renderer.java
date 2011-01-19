// Renderer.java
//
// Copyright 2003 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.renderer;

import java.awt.*;
import javax.swing.*;

import jugglinglab.jml.*;
import jugglinglab.util.*;


public abstract class Renderer {
    public abstract void setPattern(JMLPattern pat);
	public abstract void initDisplay(Dimension dim, int border,
				Coordinate overallmax, Coordinate overallmin);

	public abstract void setCameraAngle(double[] angle);
	public abstract double[] getCameraAngle();
	
	// the following return results in local coordinates
	public abstract Coordinate getHandWindowMax();
	public abstract Coordinate getHandWindowMin();

	// the following return results in global coordinates
	public abstract Coordinate getJugglerWindowMax();
	public abstract Coordinate getJugglerWindowMin();
	
	public abstract int[] getXY(Coordinate coord);	// pixel coordinates
	public abstract Coordinate getScreenTranslatedCoordinate(Coordinate coord,
				int dx, int dy);
	
	public abstract void drawFrame(double time, int[] pnum,
				Graphics g, JPanel pan) throws JuggleExceptionInternal;
	
	public abstract Color getBackground();
	
	protected static JLVector toVector(Coordinate c, JLVector result) {
        result.x = c.x;
        result.y = c.z;
        result.z = c.y;
        return result;
	}
}
