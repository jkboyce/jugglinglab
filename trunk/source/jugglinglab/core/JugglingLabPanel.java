// JugglingLabPanel.java
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

import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

import jugglinglab.notation.*;
import jugglinglab.util.*;
import jugglinglab.view.*;


public class JugglingLabPanel extends JPanel {
    /*
	static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }
	*/
	
	protected NotationGUI ng = null;
    protected View view = null;
	

	public JugglingLabPanel(JFrame parent, int entry_type, PatternList pl, int view_type) throws JuggleExceptionUser, JuggleExceptionInternal {
		GridBagLayout gb = new GridBagLayout();
		setLayout(gb);

		if (view_type != View.VIEW_NONE) {
			view = new View(parent, new Dimension(200,300));
			view.setViewMode(view_type);
			add(view);

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.LINE_START;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.gridwidth = gbc.gridheight = 1;
			gbc.gridx = 1;
			gbc.gridy = 0;
			gbc.insets = new Insets(0,0,0,0);
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gb.setConstraints(view, gbc);
		}
		
		if (pl != null)
			pl.setTargetView(view);
			
		if ((entry_type != Notation.NOTATION_NONE) || (pl != null)) {
			ng = new NotationGUI(parent, view, pl, true);
			ng.setNotation(entry_type);		// select the first notation in the menu list
			add(ng);

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.LINE_START;
			gbc.fill = GridBagConstraints.VERTICAL;
			gbc.gridheight = gbc.gridwidth = 1;
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.insets = new Insets(0,0,0,0);
			gbc.weightx = 0.0;
			gbc.weighty = 1.0;
			gb.setConstraints(ng, gbc);
		}
	}
	
	public NotationGUI getNotationGUI() { return ng; }
	public View getView() { return view; }
}
