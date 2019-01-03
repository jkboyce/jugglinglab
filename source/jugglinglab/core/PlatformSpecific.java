// PlatformSpecific.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

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


// The platform-specific setup we used to do for Mac OS X is no longer
// working as of Java 7+, since Apple's last release of their own JRE was
// Java 6. On Java 8 none of the API hooks are present.
//
// Eventually we can do platform-specific setup for Mac OS X and other platforms
// with the Desktop class in Java 9+. Unfortunately as yet there is no packaging
// tool available for OpenJDK 11 so we need to stick with Java 8.
// See http://openjdk.java.net/jeps/343


public class PlatformSpecific {
    protected static PlatformSpecific ps = null;

    protected ApplicationWindow jlw = null;
    protected JFileChooser jfc = null;

    public static PlatformSpecific getPlatformSpecific() {
        /*
        if (ps == null) {
            if (System.getProperty("jugglinglab.macosxapp") != null) {
                try {
                    Object obj = Class.forName("jugglinglab.MacOS.PlatformSpecificMacOS").newInstance();
                    ps = (PlatformSpecific)obj;
                }
                catch (ClassNotFoundException cnfe) {
                    ps = null;      // revert to 100% pure stub
                }
                catch (IllegalAccessException iae) {
                    ps = null;
                }
                catch (InstantiationException ie) {
                    ps = null;
                }
            }
        }
        */
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
