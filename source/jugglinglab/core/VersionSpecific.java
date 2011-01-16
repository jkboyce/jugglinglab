// VersionSpecific.java
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

import java.awt.*;
import java.net.*;


public class VersionSpecific {
    protected static VersionSpecific vs = null;
    protected static URL[] images = null;
	
    public static VersionSpecific getVersionSpecific() {
        if (vs == null) {
            if (getJavaVersion() >= 2) {
                // use the reflection API so that validating classloaders don't
                // try to load VersionSpecific2
                try {
                    Object vso = Class.forName("jugglinglab.core.VersionSpecific2").newInstance();
                    vs = (VersionSpecific)vso;
                } catch (ClassNotFoundException e) {
                    vs = null;
                } catch (SecurityException e) {
                    vs = null;
                } catch (IllegalAccessException e) {
                    vs = null;
                } catch (InstantiationException e) {
                    vs = null;
                }
            }
        }
        
        if (vs == null)
            vs = new VersionSpecific();
        
        return vs;
    }
    
    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        char ch = version.charAt(2);
        return Character.digit(ch, 10);
    }
	
	public static void setDefaultPropImages(URL[] is) {
		images = is;
	}
    
    // Now the version-specific methods:
    
    public void setAntialias(Graphics g) {
    }

	public void setColorTransparent(Graphics g) {
		g.setColor(Color.white);
	}
    
    public Image makeImage(Component comp, int width, int height) {
        Image image = comp.createImage(width, height);
        Graphics tempg = image.getGraphics();
        tempg.setColor(comp.getBackground());
        tempg.fillRect(0, 0, width, height);
        return image;
    }
	
	public URL getDefaultPropImage() {
		return images[0];
	}
}