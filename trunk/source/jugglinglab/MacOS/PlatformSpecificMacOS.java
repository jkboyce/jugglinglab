// PlatformSpecificMacOS.java
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

package jugglinglab.MacOS;

import jugglinglab.core.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;

import com.apple.eawt.*;


public class PlatformSpecificMacOS extends jugglinglab.core.PlatformSpecific {

    public FileDialog fd = null;
	public Application app = null;
	

    public boolean isMacOS() { return true; }
    
    public void setupPlatform() {
		// Apple provides some hooks to make the application look more like
		// a native OS X application
		app = com.apple.eawt.Application.getApplication();
		
		app.addApplicationListener(new ApplicationAdapter() {
			public void handleAbout(ApplicationEvent event) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        jlw.getNotationGUI().doMenuCommand(NotationGUI.HELP_ABOUT);
                    }
                });
				event.setHandled(true);
			}
			
			public void handleQuit(ApplicationEvent event) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        try {
                            jlw.doMenuCommand(ApplicationWindow.FILE_EXIT);
                        } catch (JuggleExceptionInternal jei) {
                            ErrorDialog.handleException(jei);
                        }
                    }
                });
				event.setHandled(true);
			}
			
			public void handleOpenFile(ApplicationEvent event) {
				final File toopen = new File(event.getFilename());
				
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        // System.out.println("trying to open file "+filename);
                        try {
                            jlw.showJMLWindow(toopen);
                        } catch (JuggleExceptionUser je) {
                            new ErrorDialog(jlw, je.getMessage());
                        } catch (JuggleExceptionInternal jei) {
                            ErrorDialog.handleException(jei);
                        }
                    }
                });
				event.setHandled(true);
			}
		});
	}

    public int showOpenDialog(Component c) {
        // return super.showOpenDialog(c);

        return showOpenDialog(c, null);
    }
	
	public int showOpenDialog(Component c, javax.swing.filechooser.FileFilter ff) {
		FilenameFilter filter = null;
		if (ff != null) {
			final javax.swing.filechooser.FileFilter fff = ff;
			filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return fff.accept(new File(dir, name));
				}
			};
		}
		
		if (fd == null) {
            Frame f = (Frame)jlw;
            if (c instanceof Frame)
                f = (Frame)c;
            fd = new FileDialog(f);
            fd.setDirectory(System.getProperty("user.dir"));
        }
		fd.setFilenameFilter(filter);  // filter == null => no filter
        fd.setMode(FileDialog.LOAD);
        fd.setVisible(true);			// fd.show();
        if (fd.getFile() == null)
            return JFileChooser.CANCEL_OPTION;
        return JFileChooser.APPROVE_OPTION;
    }

    public int showSaveDialog(Component c) {
        // return super.showSaveDialog(c);

        if (fd == null) {
            Frame f = (Frame)jlw;
            if (c instanceof Frame)
                f = (Frame)c;
            fd = new FileDialog(f);
            fd.setDirectory(System.getProperty("user.dir"));
        }
        fd.setMode(FileDialog.SAVE);
        fd.setVisible(true);		// fd.show();
        if (fd.getFile() == null)
            return JFileChooser.CANCEL_OPTION;
        return JFileChooser.APPROVE_OPTION;
    }

    public File getSelectedFile() {
        // return super.getSelectedFile();

        if (fd == null)
            return null;
        return new File(fd.getDirectory(), fd.getFile());
    }
}