// PlatformSpecific.java
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

import javax.swing.*;
import java.awt.*;
import java.io.*;


public class PlatformSpecific {
    protected static PlatformSpecific ps = null;
    
    protected ApplicationWindow jlw = null;
    protected JFileChooser jfc = null;


    public static PlatformSpecific getPlatformSpecific() {
        if (ps == null) {
            if (System.getProperty("jugglinglab.macosxapp") != null) {
                try {
                    Object obj = Class.forName("jugglinglab.MacOS.PlatformSpecificMacOS").newInstance();
                    ps = (PlatformSpecific)obj;
                }
                catch (ClassNotFoundException cnfe) {
                    ps = null;		// revert to 100% pure stub
                }
                catch (IllegalAccessException iae) {
                    ps = null;
                }
                catch (InstantiationException ie) {
                    ps = null;
                }
            }
        }
        if (ps == null)
            ps = new PlatformSpecific();

        return ps;
    }
    
    public void registerParent(ApplicationWindow jlw) {
        this.jlw = jlw;
    }

    // Now the platform-specific methods:
    
    public boolean isMacOS() { return false; }
    
    public void setupPlatform() {}

    public int showOpenDialog(Component c) {
        return showOpenDialog(c, null);
    }
	
	public int showOpenDialog(Component c, javax.swing.filechooser.FileFilter ff) {
        if (jfc == null) {
            jfc = new JFileChooser(System.getProperty("user.dir"));
		}
		jfc.setFileFilter(ff);  // ff == null => no filter
        return jfc.showOpenDialog(c);
    }

    public int showSaveDialog(Component c) {
        if (jfc == null)
            jfc = new JFileChooser(System.getProperty("user.dir"));
        return jfc.showSaveDialog(c);
    }

    public File getSelectedFile() {
        if (jfc == null)
            return null;
        return jfc.getSelectedFile();
    }
}