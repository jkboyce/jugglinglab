// JLVector.java
//
// Copyright 2011 by Jack Boyce (jboyce@users.sourceforge.net) and others

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


public class JLVector {
	public double x = 0.0, y = 0.0, z = 0.0;

	public JLVector() {
	}

	public JLVector(double xpos, double ypos, double zpos) {
		x = xpos;
		y = ypos;
		z = zpos;
	}

	public double length() {	
		return Math.sqrt(x*x+y*y+z*z);
	}

	public JLVector transform(JLMatrix m) {
		double newx = x*m.m00 + y*m.m01 + z*m.m02 + m.m03;
		double newy = x*m.m10 + y*m.m11 + z*m.m12 + m.m13;
		double newz = x*m.m20 + y*m.m21 + z*m.m22 + m.m23;
		return new JLVector(newx, newy, newz);
	}

	public static JLVector add(JLVector a, JLVector b) {
		return new JLVector(a.x + b.x, a.y + b.y, a.z + b.z);
	}
		
	public static JLVector sub(JLVector a, JLVector b) {
		return new JLVector(a.x - b.x, a.y - b.y, a.z - b.z);
	}
	
	public static JLVector scale(double f, JLVector a) {
		return new JLVector(f * a.x, f * a.y, f * a.z);
	}
}