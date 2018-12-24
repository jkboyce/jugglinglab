// PatternWindow.java
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

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.view.*;


public class PatternWindow extends JFrame implements ActionListener, WindowListener {
    /*
	static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }
	*/

    protected View view = null;
	protected JMenu filemenu = null;
	protected JMenu viewmenu = null;
    protected boolean exit_on_close = false;


    public PatternWindow(String name, JMLPattern pat, AnimatorPrefs jc) throws JuggleExceptionUser, JuggleExceptionInternal {
        super(name);
		view = new View(this, jc);

		JMenuBar mb = new JMenuBar();
		filemenu = view.createFileMenu();
		mb.add(filemenu);
		viewmenu = view.createViewMenu();
		for (int i = 0; i < viewmenu.getItemCount(); i++) {
			JMenuItem jmi = viewmenu.getItem(i);
			if (jmi == null)
				break;		// hit the first separator, end of the list of views
			jmi.addActionListener(this);   // so we can enable/disable GIFsave depending on view mode
		}
        mb.add(viewmenu);
        setJMenuBar(mb);

		if (pat.getNumberOfJugglers() > 1) {
			view.setViewMode(View.VIEW_SIMPLE);
			viewmenu.getItem(0).setSelected(true);
		} else {
			view.setViewMode(View.VIEW_EDIT);
			viewmenu.getItem(1).setSelected(true);
		}

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		view.setDoubleBuffered(true);
		this.setBackground(Color.white);
		setContentPane(view);

		Locale loc = JLLocale.getLocale();
		this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        pack();
		view.restartView(pat, jc);
        setVisible(true);
        addWindowListener(this);
    }

    public void setExitOnClose(boolean value) {
        this.exit_on_close = value;
    }

    // Implements ActionListener to enable/disable GIFsave as view mode changes
    public void actionPerformed(ActionEvent ae) {
        boolean gifenabled = jugglinglab.core.Constants.INCLUDE_GIF_SAVE;

		for (int i = 0; i < filemenu.getItemCount(); i++) {
			JMenuItem jmi = filemenu.getItem(i);
			if ((jmi != null) && jmi.getActionCommand().equals("savegifanim")) {
				jmi.setEnabled(gifenabled);
				return;
			}
		}
    }


    public synchronized void dispose() {
        super.dispose();
        if (view != null) {
            view.dispose();
            view = null;
        }
    }

    // WindowListener interface methods
    public void windowOpened(WindowEvent e) { }
    public void windowClosing(WindowEvent e) {
        if (this.exit_on_close)
            System.exit(0);
    }
    public void windowClosed(WindowEvent e) { }
    public void windowIconified(WindowEvent e) { }
    public void windowDeiconified(WindowEvent e) { }
    public void windowActivated(WindowEvent e) { }
    public void windowDeactivated(WindowEvent e) { }

}

