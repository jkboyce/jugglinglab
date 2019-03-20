// PlatformSpecific.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

import java.awt.*;
import java.io.*;
import javax.swing.*;

import jugglinglab.JugglingLab;


// The platform-specific setup we used to do for Mac OS X is no longer
// working as of Java 7+, since Apple's last release of their own JRE was
// Java 6. On Java 8 none of Apple's API hooks are present.
//
// Eventually we can do platform-specific setup for Mac OS X and other platforms
// with the Desktop class in Java 9+. Unfortunately as yet there is no packaging
// tool available for OpenJDK 11 so we need to stick with Java 8 if we want
// bundled applications.
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
            if (JugglingLab.base_dir != null)
                jfc = new JFileChooser(JugglingLab.base_dir.toFile());
            else
                jfc = new JFileChooser();
        }
        jfc.setFileFilter(ff);  // ff == null => no filter
        return jfc.showOpenDialog(c);
    }

    public int showSaveDialog(Component c) {
        if (jfc == null) {
            if (JugglingLab.base_dir != null)
                jfc = new JFileChooser(JugglingLab.base_dir.toFile());
            else
                jfc = new JFileChooser();
        }
        return jfc.showSaveDialog(c);
    }

    public File getSelectedFile() {
        if (jfc == null)
            return null;
        return jfc.getSelectedFile();
    }
}
