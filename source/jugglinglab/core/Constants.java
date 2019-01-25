// Constants.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com) and others

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
    public static final String version = "1.1";
    public static final String year = "2019";

    public static final String site_URL = "http://jugglinglab.org";
    public static final String download_URL = "https://jugglinglab.org/#download";
    public static final String help_URL = "https://jugglinglab.org/#help";

    public static final boolean DEBUG_LAYOUT = false;
    public static final boolean DEBUG_PARSING = false;
	public static final boolean DEBUG_OPTIMIZE = true;

    public static final int ANGLE_LAYOUT_METHOD = jugglinglab.curve.Curve.lineCurve;
    public static final int SPLINE_LAYOUT_METHOD = jugglinglab.curve.splineCurve.rmsaccel;
}
