// JLMath.java
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

package jugglinglab.util;


// Random math functions

public class JLMath {
	
	public static final double pi = 3.141592653589793238;
	
	public static double toRad(double deg) {
		return (deg * pi / 180.0);
	}
	
	// a choose b
	public static int choose(int a, int b) {
		int result = 1;
		
		for (int i = 0; i < b; i++) {
			result *= (a - i);
			result /= (i + 1);
		}
		
		return result;
	}
}

