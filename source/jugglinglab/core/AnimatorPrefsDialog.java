// AnimatorPrefsDialog.java
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
import java.awt.event.*;
import java.text.MessageFormat;
import javax.swing.*;
import java.util.*;

import jugglinglab.util.*;


public class AnimatorPrefsDialog extends JDialog {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected JTextField 	tf1, tf2, tf3;
    protected JCheckBox 	cb2, cb3, cb4, cb5, cb6;
    protected JButton 		but1, but2;

    protected AnimatorPrefs newjc;
    //	protected boolean finished = false;

    protected final static int border = 10;


    public AnimatorPrefsDialog(JFrame parent) {
        // set up dialog
        super(parent, guistrings.getString("Animation_Preferences"), true);
        
        GridBagLayout gb = new GridBagLayout();

        this.getContentPane().setLayout(gb);

        JPanel p1 = new JPanel();			// to hold text boxes
        p1.setLayout(gb);
        JLabel lab1 = new JLabel(guistrings.getString("Frames_per_second"));
        p1.add(lab1);
        gb.setConstraints(lab1, make_constraints(GridBagConstraints.LINE_START,1,0,
                                                 new Insets(0,3,0,0)));
        tf1 = new JTextField(4);
        p1.add(tf1);
        gb.setConstraints(tf1, make_constraints(GridBagConstraints.LINE_START,0,0,
                                                new Insets(0,0,0,0)));
        JLabel lab2 = new JLabel(guistrings.getString("Slowdown_factor"));
        p1.add(lab2);
        gb.setConstraints(lab2, make_constraints(GridBagConstraints.LINE_START,1,1,
                                                 new Insets(0,3,0,0)));
        tf2 = new JTextField(4);
        p1.add(tf2);
        gb.setConstraints(tf2, make_constraints(GridBagConstraints.LINE_START,0,1,
                                                new Insets(0,0,0,0)));
        JLabel lab3 = new JLabel(guistrings.getString("Border_(pixels)"));
        p1.add(lab3);
        gb.setConstraints(lab3, make_constraints(GridBagConstraints.LINE_START,1,2,
                                                 new Insets(0,3,0,0)));
        tf3 = new JTextField(4);
        p1.add(tf3);
        gb.setConstraints(tf3, make_constraints(GridBagConstraints.LINE_START,0,2,
                                                new Insets(0,0,0,0)));

        cb2 = new JCheckBox(guistrings.getString("Start_paused"));
        cb6 = new JCheckBox(guistrings.getString("Pause_on_mouse_away"));
        cb3 = new JCheckBox(guistrings.getString("Stereo_display"));
        cb4 = new JCheckBox(guistrings.getString("Catch_sounds"));
        cb5 = new JCheckBox(guistrings.getString("Bounce_sounds"));

        JPanel p2 = new JPanel();			// buttons at bottom
        p2.setLayout(gb);
        but1 = new JButton(guistrings.getString("Cancel"));

        but1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        p2.add(but1);
        gb.setConstraints(but1, make_constraints(GridBagConstraints.LINE_END,0,0,
                                                 new Insets(0,0,0,0)));
        but2 = new JButton(guistrings.getString("OK"));

        but2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int tempint;
                double tempdouble;

                setVisible(false);
                // read out prefs
                newjc = new AnimatorPrefs(newjc);	// clone old controls

                try {
                    tempdouble = Double.valueOf(tf1.getText()).doubleValue();
                    if (tempdouble > 0.0) newjc.fps = tempdouble;
                } catch (NumberFormatException e) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "fps" };					
                    new ErrorDialog(AnimatorPrefsDialog.this, MessageFormat.format(template, arguments));
                }
                try {
                    tempdouble = Double.valueOf(tf2.getText()).doubleValue();
                    if (tempdouble > 0.0) newjc.slowdown = tempdouble;
                } catch (NumberFormatException e) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "slowdown" };					
                    new ErrorDialog(AnimatorPrefsDialog.this, MessageFormat.format(template, arguments));
                }
                try {
                    tempint = Integer.parseInt(tf3.getText());
                    if (tempint >= 0) newjc.border = tempint;
                } catch (NumberFormatException e) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { "border" };					
                    new ErrorDialog(AnimatorPrefsDialog.this, MessageFormat.format(template, arguments));
                }

                newjc.startPause = cb2.isSelected();
				newjc.mousePause = cb6.isSelected();
                newjc.stereo = cb3.isSelected();
                newjc.catchSound = cb4.isSelected();
                newjc.bounceSound = cb5.isSelected();
            }
        });

        p2.add(but2);
        gb.setConstraints(but2, make_constraints(GridBagConstraints.LINE_END,1,0,
                                                 new Insets(0,10,0,0)));

        this.getContentPane().add(cb3);
        gb.setConstraints(cb3, make_constraints(GridBagConstraints.LINE_START,0,0,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb2);
        gb.setConstraints(cb2, make_constraints(GridBagConstraints.LINE_START,0,1,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb6);
        gb.setConstraints(cb6, make_constraints(GridBagConstraints.LINE_START,0,2,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb4);
        gb.setConstraints(cb4, make_constraints(GridBagConstraints.LINE_START,0,3,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb5);
        gb.setConstraints(cb5, make_constraints(GridBagConstraints.LINE_START,0,4,
                                                new Insets(0,border,3,border)));
        this.getContentPane().add(p1);		// now make the whole window
        gb.setConstraints(p1, make_constraints(GridBagConstraints.LINE_START,0,5,
                                               new Insets(3,border,border,border)));
        this.getContentPane().add(p2);
        gb.setConstraints(p2, make_constraints(GridBagConstraints.LINE_END,0,6,
                                               new Insets(0,border,border,border)));

        this.getRootPane().setDefaultButton(but2);		// OK button is default
		
		Locale loc = JLLocale.getLocale();
		this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));
				
		this.pack();
		this.setResizable(false);
    }

    protected static GridBagConstraints make_constraints(int location, int gridx, int gridy, Insets ins) {
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

    public AnimatorPrefs getPrefs(AnimatorPrefs oldjc) {
        newjc = oldjc;

        tf1.setText(Double.toString(oldjc.fps));
        tf2.setText(Double.toString(oldjc.slowdown));
        tf3.setText(Integer.toString(oldjc.border));
        cb2.setSelected(oldjc.startPause);
		cb6.setSelected(oldjc.mousePause);
        cb3.setSelected(oldjc.stereo);
        cb4.setSelected(oldjc.catchSound);
        cb5.setSelected(oldjc.bounceSound);

        this.setVisible(true);
        return newjc;
    }
}
