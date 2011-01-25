// mhnNotationControl.java
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

package jugglinglab.notation;


import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import jugglinglab.util.*;
import jugglinglab.prop.Prop;

public class mhnNotationControl extends NotationControl {
    protected static final String[] builtinHandsNames = {
        "inside",
        "outside",
        "half",
        "Mills"
    };
    protected static final String[] builtinHandsStrings = {
        "(10)(32.5).",
        "(32.5)(10).",
        "(32.5)(10).(10)(32.5).",
        "(-25)(2.5).(25)(-2.5).(-25)(0)."
    };

    protected static final String[] builtinBodyNames = {
        "line",
        "feed",
        "backtoback",
        "sidetoside",
        "circles"
    };
    protected static final String[] builtinBodyStrings = {
        "<(90).|(270,-125).|(90,125).|(270,-250).|(90,250).|(270,-375).>",
        "<(90,75).|(270,-75,50).|(270,-75,-50).|(270,-75,150).|(270,-75,-150).>",
        "<(270,35).|(90,-35).|(0,0,35).|(180,0,-35).>",
		"<(0).|(0,100).|(0,-100).|(0,200).|(0,-200).|(0,300).>",
        "(0,75,0)...(90,0,75)...(180,-75,0)...(270,0,-75)..."
    };

				// text fields in control panel
    protected JTextField tf1, tf2, tf3, tf4, tf5, tf6;
    protected JComboBox cb1, cb2, cb3;
    protected boolean cb1_selected = false, cb2_selected = false;

    protected final static int border = 10;
    protected final static int hspacing = 5;
    protected final static int vspacing = 12;


    public mhnNotationControl() {
		this.setOpaque(false);
        this.setLayout(new BorderLayout());

        JPanel p1 = new JPanel();
        GridBagLayout gb = new GridBagLayout();
        p1.setLayout(gb);

        JLabel lab1 = new JLabel(guistrings.getString("Pattern"));
        p1.add(lab1);
        gb.setConstraints(lab1, make_constraints(GridBagConstraints.LINE_END,0,0,
                                                 new Insets(border,border,0,hspacing)));
        tf1 = new JTextField(15);
        p1.add(tf1);
        gb.setConstraints(tf1, make_constraints(GridBagConstraints.LINE_START,1,0,
                                                new Insets(border,0,0,border)));
        JLabel lab3 = new JLabel(guistrings.getString("Beats_per_second"));
        p1.add(lab3);
        gb.setConstraints(lab3, make_constraints(GridBagConstraints.LINE_END,0,1,
                                                 new Insets(2*vspacing,border,0,hspacing)));
        tf3 = new JTextField(4);
        p1.add(tf3);
        gb.setConstraints(tf3, make_constraints(GridBagConstraints.LINE_START,1,1,
                                                new Insets(2*vspacing,0,0,border)));
        JLabel lab2 = new JLabel(guistrings.getString("Dwell_beats"));
        p1.add(lab2);
        gb.setConstraints(lab2, make_constraints(GridBagConstraints.LINE_END,0,2,
                                                 new Insets(vspacing,border,0,hspacing)));
        tf2 = new JTextField(4);
        p1.add(tf2);
        gb.setConstraints(tf2, make_constraints(GridBagConstraints.LINE_START,1,2,
                                                new Insets(vspacing,0,0,border)));

        JLabel lab4 = new JLabel(guistrings.getString("Hand_movement"));
        p1.add(lab4);
        gb.setConstraints(lab4, make_constraints(GridBagConstraints.LINE_END,0,3,
                                                 new Insets(vspacing,border,0,hspacing)));
        cb1 = new JComboBox();
        cb1.addItem(guistrings.getString("MHNHands_name_default"));
        for (int i = 0; i < builtinHandsNames.length; i++) {
			String item = "MHNHands_name_" + builtinHandsNames[i];
			cb1.addItem(guistrings.getString(item));
		}
        cb1.addItem(guistrings.getString("MHNHands_name_custom"));
        p1.add(cb1);
        gb.setConstraints(cb1, make_constraints(GridBagConstraints.LINE_START,1,3,
                                                new Insets(vspacing,0,0,border)));
        tf4 = new JTextField(15);
        p1.add(tf4);
        gb.setConstraints(tf4, make_constraints(GridBagConstraints.LINE_START,1,4,
                                                new Insets(5,0,0,border)));
        cb1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = cb1.getSelectedIndex();
                cb1_selected = true;

                // System.out.println("Selected item number "+index);
                if (index == 0) {
                    tf4.setText("");
                    tf4.setEnabled(false);
                } else if (index == (builtinHandsNames.length+1)) {
                    tf4.setEnabled(true);
                } else {
                    tf4.setText(builtinHandsStrings[index-1]);
                    tf4.setCaretPosition(0);
                    tf4.setEnabled(true);
                }
            }
        });
        tf4.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent de) {}
            public void insertUpdate(DocumentEvent de) {
                if (!cb1_selected)
                    cb1.setSelectedIndex(builtinHandsNames.length+1);
                cb1_selected = false;
            }
            public void removeUpdate(DocumentEvent de) {
                if (!cb1_selected)
                    cb1.setSelectedIndex(builtinHandsNames.length+1);
            }
        });

        JLabel lab5 = new JLabel(guistrings.getString("Body_movement"));
        p1.add(lab5);
        gb.setConstraints(lab5, make_constraints(GridBagConstraints.LINE_END,0,5,
                                                 new Insets(vspacing,border,0,hspacing)));
        cb2 = new JComboBox();
        cb2.addItem(guistrings.getString("MHNBody_name_default"));
        for (int i = 0; i < builtinBodyNames.length; i++) {
			String item = "MHNBody_name_" + builtinBodyNames[i];
			cb2.addItem(guistrings.getString(item));
		}
        cb2.addItem(guistrings.getString("MHNBody_name_custom"));
        p1.add(cb2);
        gb.setConstraints(cb2, make_constraints(GridBagConstraints.LINE_START,1,5,
                                                new Insets(vspacing,0,0,border)));
        tf5 = new JTextField(15);
        p1.add(tf5);
        gb.setConstraints(tf5, make_constraints(GridBagConstraints.LINE_START,1,6,
                                                new Insets(5,0,0,border)));
        cb2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int index = cb2.getSelectedIndex();
                cb2_selected = true;

                // System.out.println("Selected item number "+index);
                if (index == 0) {
                    tf5.setText("");
                    tf5.setEnabled(false);
                } else if (index == (builtinBodyNames.length+1)) {
                    tf5.setEnabled(true);
                } else {
                    tf5.setText(builtinBodyStrings[index-1]);
                    tf5.setCaretPosition(0);
                    tf5.setEnabled(true);
                }
            }
        });
        tf5.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent de) {}
            public void insertUpdate(DocumentEvent de) {
                if (!cb2_selected)
                    cb2.setSelectedIndex(builtinBodyNames.length+1);
                cb2_selected = false;
            }
            public void removeUpdate(DocumentEvent de) {
                if (!cb2_selected)
                    cb2.setSelectedIndex(builtinBodyNames.length+1);
            }
        });
		
		JLabel prop_label = new JLabel(guistrings.getString("Prop_type"));
		p1.add(prop_label);
		gb.setConstraints(prop_label, make_constraints(GridBagConstraints.LINE_END,0,7,
													   new Insets(vspacing,border,0,hspacing)));
		cb3 = new JComboBox();
		for (int i = 0; i < Prop.builtinProps.length; i++) {
			String item = "Prop_name_" + Prop.builtinProps[i].toLowerCase();
			cb3.addItem(guistrings.getString(item));
		}
		p1.add(cb3);
		gb.setConstraints(cb3, make_constraints(GridBagConstraints.LINE_START,1,7,
                                                new Insets(vspacing,0,0,border)));
		

        JLabel lab6 = new JLabel(guistrings.getString("Manual_settings"));
        p1.add(lab6);
        gb.setConstraints(lab6, make_constraints(GridBagConstraints.LINE_START,0,8,
                                                 new Insets(2*vspacing,border,0,hspacing)));
        tf6 = new JTextField(25);
        p1.add(tf6);
        GridBagConstraints gbc6 = make_constraints(GridBagConstraints.LINE_END,0,9,
                                                   new Insets(5,border+hspacing,0,border));
        gbc6.gridwidth = 2;
        gb.setConstraints(tf6, gbc6);
        
        this.resetNotationControl();
        this.add(p1, BorderLayout.PAGE_START);
    }

    protected static GridBagConstraints make_constraints(int location, int gridx, int gridy) {
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = location;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = gbc.weighty = 0.0;
        return gbc;
    }

    protected static GridBagConstraints make_constraints(int location, int gridx, int gridy,
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

    public String getPattern() {
        StringBuffer sb = new StringBuffer(256);

        sb.append("pattern=");
        sb.append(tf1.getText());
		sb.append(";prop=" + Prop.builtinProps[cb3.getSelectedIndex()].toLowerCase());
        if (tf2.getText().length() > 0) {
            if (!tf2.getText().equals(new Double(mhnPattern.dwell_default).toString())) {
                sb.append(";dwell=");
                sb.append(tf2.getText());
            }
        }
        if (tf3.getText().length() > 0) {
            sb.append(";bps=");
            sb.append(tf3.getText());
        }
        if (tf4.getText().length() > 0) {
            sb.append(";hands=");
            sb.append(tf4.getText());
        }
        if (tf5.getText().length() > 0) {
            sb.append(";body=");
            sb.append(tf5.getText());
        }
        if (tf6.getText().length() > 0) {
            sb.append(";");
            sb.append(tf6.getText());
        }
        return sb.toString();
    }

    public void resetNotationControl() {
        tf1.setText("3");												// pattern
        tf2.setText(new Double(mhnPattern.dwell_default).toString());	// dwell beats
        tf3.setText("");												// beats per second
        tf4.setText("");
        cb1.setSelectedIndex(0);
        tf5.setText("");
        cb2.setSelectedIndex(0);
        tf6.setText("");
		cb3.setSelectedIndex(0);
    }

    public String getHandsName() {
        int index = cb1.getSelectedIndex();
        if ((index == 0) || (index == (builtinHandsNames.length+1)))
            return null;

        return builtinHandsNames[index-1];
    }
}
