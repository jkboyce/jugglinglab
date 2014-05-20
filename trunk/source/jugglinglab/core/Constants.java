// Constants.java
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

package jugglinglab.core;


public class Constants {
    public static final String version = "0.6.2";
    public static final String year = "2014";
    
    public static final boolean DEBUG_LAYOUT = false;
    public static final boolean DEBUG_PARSING = false;

    public static final boolean INCLUDE_GIF_SAVE = true;

    public static final int ANGLE_LAYOUT_METHOD = jugglinglab.curve.Curve.lineCurve;
    public static final int SPLINE_LAYOUT_METHOD = jugglinglab.curve.splineCurve.rmsaccel;
}