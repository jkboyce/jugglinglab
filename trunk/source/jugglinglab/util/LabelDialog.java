// LabelDialog.java
//
// Copyright 2002 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.util;

import javax.swing.*;
import java.awt.*;
// import java.awt.event.*;
                         

public class LabelDialog implements Runnable {
	Component parent = null;
        String title = null;
	String msg = null;
	
	public LabelDialog(Component parent, String title, String msg) {
		this.parent = parent;
                this.title = title;
		this.msg = msg;
		
		SwingUtilities.invokeLater(this);
	}
	
	public void run() {
		JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.INFORMATION_MESSAGE);
	}
}

/*
public class LabelDialog extends JDialog {
	Button okbutton;
	
	protected final static int border = 10;
	
	
	public LabelDialog(Frame parent, String title, String text) {
		super(parent, title, true);
		this.setResizable(false);

		GridBagLayout gb = new GridBagLayout();
		this.setLayout(gb);
		
		int pbtopborder = 0;
		
		if (text != null) {
			Label lab = new Label(text);
			this.add(lab);
			gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_START,0,0,
						new Insets(border,border,10,border)));
		} else
			pbtopborder = border;
			
		okbutton = new Button("OK");
		
		okbutton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				setVisible(false);
				dispose();
			}
		});
		
		this.add(okbutton);
		gb.setConstraints(okbutton, make_constraints(GridBagConstraints.LINE_END,0,2,
					new Insets(0,border,border,border)));
		
		this.pack();
		this.show();
	}
	
	protected GridBagConstraints make_constraints(int location, int gridx, int gridy,
					Insets ins) {
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.anchor = location;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridheight = gbc.gridwidth = 1;
		gbc.gridx = gridx;
		gbc.gridy = gridy;
		gbc.insets = ins;
		gbc.weightx = gbc.weighty = 0.0;
		return gbc;
	}
}
*/
