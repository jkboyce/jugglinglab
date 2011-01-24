// AnimatorPrefs.java
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

package jugglinglab.core;

import java.util.*;
import java.text.MessageFormat;
import jugglinglab.util.*;
import jugglinglab.jml.*;


public class AnimatorPrefs {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }
    
    public static final boolean stereo_def = false;
    public static final boolean startPause_def = false;
    public static final boolean mousePause_def = false;
    public static final boolean catchSound_def = false;
    public static final boolean bounceSound_def = true;
    public static final double 	fps_def = 30.0;
    public static final double 	slowdown_def = 2.0;
    public static final int 	border_def = 0;

    public boolean	stereo = stereo_def;
    public boolean	startPause = startPause_def;
	public boolean  mousePause = mousePause_def;
    public boolean	catchSound = catchSound_def;
    public boolean	bounceSound = bounceSound_def;
    public double	fps = fps_def;
    public double	slowdown = slowdown_def;
    public int		border = border_def;

    public AnimatorPrefs() { super(); }

    public AnimatorPrefs(AnimatorPrefs jc) {
        if (jc.slowdown >= 0.0)		this.slowdown = jc.slowdown;
        if (jc.fps >= 0.0)			this.fps = jc.fps;
        if (jc.border >= 0)			this.border = jc.border;
        this.startPause = jc.startPause;
		this.mousePause = jc.mousePause;
        this.stereo = jc.stereo;
        this.catchSound = jc.catchSound;
        this.bounceSound = jc.bounceSound;
    }

    public void parseInput(String input) throws JuggleExceptionUser {
        int	tempint;
        double 	tempdouble;
        String	value = null;

        ParameterList pl = new ParameterList(input);

        if ((value = pl.getParameter("stereo")) != null)
            this.stereo = Boolean.valueOf(value).booleanValue();
        if ((value = pl.getParameter("startpaused")) != null)
            this.startPause = Boolean.valueOf(value).booleanValue();
        if ((value = pl.getParameter("mousepause")) != null)
            this.mousePause = Boolean.valueOf(value).booleanValue();
        if ((value = pl.getParameter("catchsound")) != null)
            this.catchSound = Boolean.valueOf(value).booleanValue();
        if ((value = pl.getParameter("bouncesound")) != null)
            this.bounceSound = Boolean.valueOf(value).booleanValue();
        if ((value = pl.getParameter("fps")) != null) {
            try {
                tempdouble = Double.valueOf(value).doubleValue();
                this.fps = tempdouble;
            } catch (NumberFormatException e) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "fps" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.getParameter("slowdown")) != null) {
            try {
                tempdouble = Double.valueOf(value).doubleValue();
                this.slowdown = tempdouble;
            } catch (NumberFormatException e) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "slowdown" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
        if ((value = pl.getParameter("border")) != null) {
            try {
                tempint = Integer.parseInt(value);
                this.border = tempint;
            } catch (NumberFormatException e) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "border" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
    }

    public String toString() {
        String result = "";

        if (stereo != stereo_def)
            result += "stereo="+this.stereo+";";
        if (startPause != startPause_def)
            result += "startpaused="+this.startPause+";";
        if (mousePause != mousePause_def)
            result += "mousepause="+this.mousePause+";";
        if (catchSound != catchSound_def)
            result += "catchsound="+this.catchSound+";";
        if (bounceSound != bounceSound_def)
            result += "bouncesound="+this.bounceSound+";";
        if (fps != fps_def)
            result += "fps="+JMLPattern.toStringTruncated(this.fps,2)+";";
        if (slowdown != slowdown_def)
            result += "slowdown="+JMLPattern.toStringTruncated(this.slowdown,2)+";";
        if (border != border_def)
            result += "border="+this.border+";";

        if (result.length() != 0)
            result = result.substring(0, result.length()-1);

        return result;
    }
}


