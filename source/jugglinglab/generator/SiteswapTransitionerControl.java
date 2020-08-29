// SiteswapTransitionerControl.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.generator;

import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;
import javax.swing.*;

import jugglinglab.util.JLFunc;


class SiteswapTransitionerControl extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected JTextField tf1, tf2, tf3;
    protected JCheckBox cb1, cb2, cb3;
    protected JLabel lab4;

    protected final static int border = 10;

    public SiteswapTransitionerControl() {
        setOpaque(false);
        GridBagLayout gb = new GridBagLayout();
        setLayout(gb);

        JPanel p1 = new JPanel();  // top part of window
        p1.setLayout(gb);

        JLabel lab1 = new JLabel(guistrings.getString("from_pattern"));
        p1.add(lab1);
        gb.setConstraints(lab1, JLFunc.constraints(GridBagConstraints.LINE_END,0,0,
                                                new Insets(0,0,10,3)));
        tf1 = new JTextField(15);
        p1.add(tf1);
        gb.setConstraints(tf1, JLFunc.constraints(GridBagConstraints.LINE_START,1,0,
                                                new Insets(0,0,10,0)));

        JLabel lab2 = new JLabel(guistrings.getString("to_pattern"));
        p1.add(lab2);
        gb.setConstraints(lab2, JLFunc.constraints(GridBagConstraints.LINE_END,0,1,
                                                new Insets(0,0,10,3)));
        tf2 = new JTextField(15);
        p1.add(tf2);
        gb.setConstraints(tf2, JLFunc.constraints(GridBagConstraints.LINE_START,1,1,
                                                new Insets(0,0,10,0)));

        JButton but1 = new JButton("\u2195");
        but1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                String temp = tf1.getText();
                tf1.setText(tf2.getText());
                tf2.setText(temp);
            }
        });
        p1.add(but1);
        gb.setConstraints(but1, JLFunc.constraints(GridBagConstraints.LINE_START,1,2));

        JPanel p2 = new JPanel();  // multiplexing part
        p2.setLayout(gb);

        JLabel lab3 = new JLabel(guistrings.getString("multiplexing_in_transitions"));
        p2.add(lab3);
        gb.setConstraints(lab3, JLFunc.constraints(GridBagConstraints.LINE_START,0,0,
                                                new Insets(5,0,0,0)));
        cb1 = new JCheckBox(guistrings.getString("enable"), null);
        p2.add(cb1);
        gb.setConstraints(cb1, JLFunc.constraints(GridBagConstraints.LINE_START,0,1,
                                                new Insets(0,10,0,0)));

        JPanel p3 = new JPanel();
        p3.setLayout(gb);
        tf3 = new JTextField(3);
        p3.add(tf3);
        gb.setConstraints(tf3, JLFunc.constraints(GridBagConstraints.LINE_START,1,0));
        lab4 = new JLabel(guistrings.getString("simultaneous_throws"));
        p3.add(lab4);
        gb.setConstraints(lab4, JLFunc.constraints(GridBagConstraints.LINE_END,0,0,
                                                new Insets(0,0,0,3)));

        p2.add(p3);
        gb.setConstraints(p3, JLFunc.constraints(GridBagConstraints.LINE_START,0,2,
                                                new Insets(0,15,0,0)));

        cb2 = new JCheckBox(guistrings.getString("no_simultaneous_catches"), null);
        p2.add(cb2);
        gb.setConstraints(cb2, JLFunc.constraints(GridBagConstraints.LINE_START,0,3,
                                                new Insets(0,10,0,0)));

        cb3 = new JCheckBox(guistrings.getString("no_clustered_throws"), null);
        p2.add(cb3);
        gb.setConstraints(cb3, JLFunc.constraints(GridBagConstraints.LINE_START,0,4,
                                                new Insets(0,10,0,0)));

        JPanel p4 = new JPanel();  // left justify top and multiplexing parts
        p4.setLayout(gb);
        p4.add(p1);
        gb.setConstraints(p1, JLFunc.constraints(GridBagConstraints.LINE_START,0,0,
                                                new Insets(30,border,5,border)));
        p4.add(p2);
        gb.setConstraints(p2, JLFunc.constraints(GridBagConstraints.LINE_START,0,1,
                                                new Insets(20,border,5,border)));

        add(p4);  // the whole panel
        gb.setConstraints(p4, JLFunc.constraints(GridBagConstraints.CENTER,0,0));

        // add action listeners to enable/disable items depending on context

        cb1.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent iv) {
                boolean active = cb1.isSelected();

                cb2.setEnabled(active);
                cb3.setEnabled(active);
                lab4.setEnabled(active);
                tf3.setEnabled(active);
            }
        });

        resetControl();  // apply defaults
    }

    public void resetControl() {
        tf1.setText("");           // from pattern
        tf2.setText("");           // to pattern
        cb1.setSelected(false);    // multiplexing
        tf3.setText("2");          // number multiplexed throws
        cb2.setSelected(true);     // no simultaneous catches
        cb3.setSelected(false);    // allow clustered throws

        cb2.setEnabled(false);     // multiplexing off
        cb3.setEnabled(false);
        lab4.setEnabled(false);
        tf3.setEnabled(false);
    }

    public String getParams() {
        StringBuffer sb = new StringBuffer(256);

        String from_pattern = tf1.getText();
        if (from_pattern.trim().length() == 0)
            from_pattern = "-";

        String to_pattern = tf2.getText();
        if (to_pattern.trim().length() == 0)
            to_pattern = "-";

        sb.append(from_pattern + " " + to_pattern);

        if (cb1.isSelected() && tf3.getText().length() > 0) {
            sb.append(" -m " + tf3.getText());
            if (!cb2.isSelected())
                sb.append(" -mf");
            if (cb3.isSelected())
                sb.append(" -mc");
        }

        return sb.toString();
    }
}
