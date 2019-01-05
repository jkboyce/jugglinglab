// AnimationPrefsDialog.java
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
import java.text.MessageFormat;
import javax.swing.*;
import java.util.*;

import jugglinglab.util.*;


public class AnimationPrefsDialog extends JDialog {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected JTextField    tf_width, tf_height, tf_fps, tf_slowdown, tf_border;
    protected JCheckBox     cb_paused, cb_mousepause, cb_stereo, cb_catchsounds, cb_bouncesounds;
    protected JButton       but_cancel, but_ok;

    protected AnimationPrefs newjc;
    //  protected boolean finished = false;

    protected final static int border = 10;


    public AnimationPrefsDialog(JFrame parent) {
        // set up dialog
        super(parent, guistrings.getString("Animation_Preferences"), true);

        GridBagLayout gb = new GridBagLayout();

        this.getContentPane().setLayout(gb);

        JPanel p1 = new JPanel();           // to hold text boxes
        p1.setLayout(gb);
        JLabel lab1 = new JLabel(guistrings.getString("Width"));
        p1.add(lab1);
        gb.setConstraints(lab1, make_constraints(GridBagConstraints.LINE_START,1,0,
                                                 new Insets(0,3,0,0)));
        tf_width = new JTextField(4);
        p1.add(tf_width);
        gb.setConstraints(tf_width, make_constraints(GridBagConstraints.LINE_START,0,0,
                                                 new Insets(0,0,0,0)));
        JLabel lab2 = new JLabel(guistrings.getString("Height"));
        p1.add(lab2);
        gb.setConstraints(lab2, make_constraints(GridBagConstraints.LINE_START,1,1,
                                                 new Insets(0,3,0,0)));
        tf_height = new JTextField(4);
        p1.add(tf_height);
        gb.setConstraints(tf_height, make_constraints(GridBagConstraints.LINE_START,0,1,
                                                 new Insets(0,0,0,0)));
        JLabel lab3 = new JLabel(guistrings.getString("Frames_per_second"));
        p1.add(lab3);
        gb.setConstraints(lab3, make_constraints(GridBagConstraints.LINE_START,1,2,
                                                 new Insets(0,3,0,0)));
        tf_fps = new JTextField(4);
        p1.add(tf_fps);
        gb.setConstraints(tf_fps, make_constraints(GridBagConstraints.LINE_START,0,2,
                                                 new Insets(0,0,0,0)));
        JLabel lab4 = new JLabel(guistrings.getString("Slowdown_factor"));
        p1.add(lab4);
        gb.setConstraints(lab4, make_constraints(GridBagConstraints.LINE_START,1,3,
                                                 new Insets(0,3,0,0)));
        tf_slowdown = new JTextField(4);
        p1.add(tf_slowdown);
        gb.setConstraints(tf_slowdown, make_constraints(GridBagConstraints.LINE_START,0,3,
                                                 new Insets(0,0,0,0)));
        JLabel lab5 = new JLabel(guistrings.getString("Border_(pixels)"));
        p1.add(lab5);
        gb.setConstraints(lab5, make_constraints(GridBagConstraints.LINE_START,1,4,
                                                 new Insets(0,3,0,0)));
        tf_border = new JTextField(4);
        p1.add(tf_border);
        gb.setConstraints(tf_border, make_constraints(GridBagConstraints.LINE_START,0,4,
                                                 new Insets(0,0,0,0)));

        cb_paused = new JCheckBox(guistrings.getString("Start_paused"));
        cb_mousepause = new JCheckBox(guistrings.getString("Pause_on_mouse_away"));
        cb_stereo = new JCheckBox(guistrings.getString("Stereo_display"));
        cb_catchsounds = new JCheckBox(guistrings.getString("Catch_sounds"));
        cb_bouncesounds = new JCheckBox(guistrings.getString("Bounce_sounds"));

        JPanel p2 = new JPanel();           // buttons at bottom
        p2.setLayout(gb);
        but_cancel = new JButton(guistrings.getString("Cancel"));

        but_cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        p2.add(but_cancel);
        gb.setConstraints(but_cancel, make_constraints(GridBagConstraints.LINE_END,0,0,
                                                 new Insets(0,0,0,0)));
        but_ok = new JButton(guistrings.getString("OK"));

        but_ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                int tempint;
                double tempdouble;

                setVisible(false);
                // read out prefs
                newjc = new AnimationPrefs(newjc);  // clone old controls

                try {
                    tempint = Integer.parseInt(tf_width.getText());
                    if (tempint >= 0) newjc.width = tempint;
                } catch (NumberFormatException e) {
                    String template = errorstrings.getString("Error_number_format");
                    Object[] arguments = { "width" };
                    new ErrorDialog(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
                }
                try {
                    tempint = Integer.parseInt(tf_height.getText());
                    if (tempint >= 0) newjc.height = tempint;
                } catch (NumberFormatException e) {
                    String template = errorstrings.getString("Error_number_format");
                    Object[] arguments = { "height" };
                    new ErrorDialog(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
                }
                try {
                    tempdouble = Double.valueOf(tf_fps.getText()).doubleValue();
                    if (tempdouble > 0.0) newjc.fps = tempdouble;
                } catch (NumberFormatException e) {
                    String template = errorstrings.getString("Error_number_format");
                    Object[] arguments = { "fps" };
                    new ErrorDialog(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
                }
                try {
                    tempdouble = Double.valueOf(tf_slowdown.getText()).doubleValue();
                    if (tempdouble > 0.0) newjc.slowdown = tempdouble;
                } catch (NumberFormatException e) {
                    String template = errorstrings.getString("Error_number_format");
                    Object[] arguments = { "slowdown" };
                    new ErrorDialog(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
                }
                try {
                    tempint = Integer.parseInt(tf_border.getText());
                    if (tempint >= 0) newjc.border = tempint;
                } catch (NumberFormatException e) {
                    String template = errorstrings.getString("Error_number_format");
                    Object[] arguments = { "border" };
                    new ErrorDialog(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
                }

                newjc.startPause = cb_paused.isSelected();
                newjc.mousePause = cb_mousepause.isSelected();
                newjc.stereo = cb_stereo.isSelected();
                newjc.catchSound = cb_catchsounds.isSelected();
                newjc.bounceSound = cb_bouncesounds.isSelected();
            }
        });

        p2.add(but_ok);
        gb.setConstraints(but_ok, make_constraints(GridBagConstraints.LINE_END,1,0,
                                                 new Insets(0,10,0,0)));

        // now make the whole window
        this.getContentPane().add(p1);
        gb.setConstraints(p1, make_constraints(GridBagConstraints.LINE_START,0,0,
                                               new Insets(3,border,0,border)));

        this.getContentPane().add(cb_paused);
        gb.setConstraints(cb_paused, make_constraints(GridBagConstraints.LINE_START,0,1,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb_mousepause);
        gb.setConstraints(cb_mousepause, make_constraints(GridBagConstraints.LINE_START,0,2,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb_stereo);
        gb.setConstraints(cb_stereo, make_constraints(GridBagConstraints.LINE_START,0,3,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb_catchsounds);
        gb.setConstraints(cb_catchsounds, make_constraints(GridBagConstraints.LINE_START,0,4,
                                                new Insets(0,border,0,border)));
        this.getContentPane().add(cb_bouncesounds);
        gb.setConstraints(cb_bouncesounds, make_constraints(GridBagConstraints.LINE_START,0,5,
                                                new Insets(0,border,3,border)));

        this.getContentPane().add(p2);
        gb.setConstraints(p2, make_constraints(GridBagConstraints.LINE_END,0,6,
                                               new Insets(0,border,border,border)));

        this.getRootPane().setDefaultButton(but_ok);        // OK button is default

        Locale loc = JLLocale.getLocale();
        this.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        this.pack();
        this.setResizable(false);
        this.setLocationRelativeTo(parent);
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

    public AnimationPrefs getPrefs(AnimationPrefs oldjc) {
        newjc = oldjc;

        tf_width.setText(Integer.toString(oldjc.width));
        tf_height.setText(Integer.toString(oldjc.height));
        tf_fps.setText(Double.toString(oldjc.fps));
        tf_slowdown.setText(Double.toString(oldjc.slowdown));
        tf_border.setText(Integer.toString(oldjc.border));
        cb_paused.setSelected(oldjc.startPause);
        cb_mousepause.setSelected(oldjc.mousePause);
        cb_stereo.setSelected(oldjc.stereo);
        cb_catchsounds.setSelected(oldjc.catchSound);
        cb_bouncesounds.setSelected(oldjc.bounceSound);

        this.setVisible(true);
        return newjc;
    }
}
