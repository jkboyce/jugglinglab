//
// SiteswapGeneratorControl.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator;

import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;
import javax.swing.*;
import jugglinglab.util.JLFunc;

class SiteswapGeneratorControl extends JPanel {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;

  protected JTextField tf1, tf2, tf3, tf4, tf5, /*tf6,*/ tf7, /*tf8,*/ tf9;
  protected JRadioButton cb1, cb2, /*cb3,*/ cb4, cb5, cb6;
  protected JCheckBox cb7, cb8, cb9, cb10, cb12, cb13, cb14, cb15, cb16, cb17, cb18;
  protected JLabel lab1, lab2, /*lab3,*/ lab4, /*lab5,*/ lab13;
  protected JComboBox<String> c1;

  protected static final int BORDER = 10;

  public SiteswapGeneratorControl() {
    setOpaque(false);
    GridBagLayout gb = new GridBagLayout();
    setLayout(gb);

    JPanel p2 = new JPanel(); // top section
    p2.setLayout(gb);
    JLabel lab6 = new JLabel(guistrings.getString("balls"));
    p2.add(lab6);
    gb.setConstraints(
        lab6, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 3)));
    tf1 = new JTextField(3);
    p2.add(tf1);
    gb.setConstraints(tf1, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0));
    JLabel lab7 = new JLabel(guistrings.getString("max._throw"));
    p2.add(lab7);
    gb.setConstraints(
        lab7, JLFunc.constraints(GridBagConstraints.LINE_END, 2, 0, new Insets(0, 15, 0, 3)));
    tf2 = new JTextField(3);
    p2.add(tf2);
    gb.setConstraints(tf2, JLFunc.constraints(GridBagConstraints.LINE_START, 3, 0));
    JLabel lab8 = new JLabel(guistrings.getString("period"));
    p2.add(lab8);
    gb.setConstraints(
        lab8, JLFunc.constraints(GridBagConstraints.LINE_END, 4, 0, new Insets(0, 15, 0, 3)));
    tf3 = new JTextField(3);
    p2.add(tf3);
    gb.setConstraints(tf3, JLFunc.constraints(GridBagConstraints.LINE_START, 5, 0));

    JPanel p6 = new JPanel(); // Jugglers/Rhythm section
    p6.setLayout(gb);
    JLabel lab14 = new JLabel(guistrings.getString("Jugglers"));
    p6.add(lab14);
    gb.setConstraints(lab14, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0));
    c1 = new JComboBox<>();
    for (int i = 1; i <= 6; i++) c1.addItem(i + "   ");
    p6.add(c1);
    gb.setConstraints(
        c1, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 10, 0, 0)));
    JLabel lab9 = new JLabel(guistrings.getString("Rhythm"));
    p6.add(lab9);
    gb.setConstraints(
        lab9, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(8, 0, 0, 0)));
    ButtonGroup bg1 = new ButtonGroup();
    cb1 = new JRadioButton(guistrings.getString("asynch"));
    bg1.add(cb1);
    p6.add(cb1);
    gb.setConstraints(
        cb1, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(0, 10, 0, 0)));
    cb2 = new JRadioButton(guistrings.getString("synch"));
    bg1.add(cb2);
    p6.add(cb2);
    gb.setConstraints(
        cb2, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 4, new Insets(0, 10, 0, 0)));
    /*
    cb3 = new JRadioButton("passing");
    bg1.add(cb3);
    p6.add(cb3);
    gb.setConstraints(cb3, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 5,
                                            new Insets(0, 10, 0, 0)));
    */

    JPanel p7 = new JPanel(); // Compositions section
    p7.setLayout(gb);
    JLabel lab10 = new JLabel(guistrings.getString("Compositions"));
    p7.add(lab10);
    gb.setConstraints(
        lab10, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(5, 0, 0, 0)));
    ButtonGroup bg2 = new ButtonGroup();
    cb5 = new JRadioButton(guistrings.getString("all"));
    bg2.add(cb5);
    p7.add(cb5);
    gb.setConstraints(
        cb5, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 10, 0, 0)));
    cb4 = new JRadioButton(guistrings.getString("non-obvious"));
    bg2.add(cb4);
    p7.add(cb4);
    gb.setConstraints(
        cb4, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(0, 10, 0, 0)));
    cb6 = new JRadioButton(guistrings.getString("none_(prime_only)"));
    bg2.add(cb6);
    p7.add(cb6);
    gb.setConstraints(
        cb6, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(0, 10, 0, 0)));

    JPanel p8 = new JPanel(); // Find section
    p8.setLayout(gb);
    JLabel lab11 = new JLabel(guistrings.getString("Find"));
    p8.add(lab11);
    gb.setConstraints(lab11, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0));
    cb7 = new JCheckBox(guistrings.getString("ground_state_patterns"), null);
    p8.add(cb7);
    gb.setConstraints(
        cb7, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 10, 0, 0)));
    cb8 = new JCheckBox(guistrings.getString("excited_state_patterns"), null);
    p8.add(cb8);
    gb.setConstraints(
        cb8, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(0, 10, 0, 0)));
    cb9 = new JCheckBox(guistrings.getString("transition_throws"), null);
    p8.add(cb9);
    gb.setConstraints(
        cb9, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(0, 10, 0, 0)));
    cb10 = new JCheckBox(guistrings.getString("pattern_rotations"), null);
    p8.add(cb10);
    gb.setConstraints(
        cb10, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 4, new Insets(0, 10, 0, 0)));
    cb17 = new JCheckBox(guistrings.getString("juggler_permutations"), null);
    p8.add(cb17);
    gb.setConstraints(
        cb17, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 5, new Insets(0, 10, 0, 0)));
    cb15 = new JCheckBox(guistrings.getString("connected_patterns"), null);
    p8.add(cb15);
    gb.setConstraints(
        cb15, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 6, new Insets(0, 10, 0, 0)));
    cb18 = new JCheckBox(guistrings.getString("symmetric_patterns"), null);
    p8.add(cb18);
    gb.setConstraints(
        cb18, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 7, new Insets(0, 10, 0, 0)));

    JPanel p9 = new JPanel(); // Multiplexing section
    p9.setLayout(gb);
    cb12 = new JCheckBox(guistrings.getString("Multiplexing"), null);
    cb12.setHorizontalTextPosition(SwingConstants.LEFT);
    p9.add(cb12);
    gb.setConstraints(
        cb12, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(1, 0, 0, 0)));

    JPanel p3 = new JPanel();
    p3.setLayout(gb);
    lab13 = new JLabel(guistrings.getString("simultaneous_throws"));
    p3.add(lab13);
    gb.setConstraints(
        lab13, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 3)));
    tf9 = new JTextField(3);
    p3.add(tf9);
    gb.setConstraints(tf9, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0));

    p9.add(p3);
    gb.setConstraints(
        p3, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 15, 0, 0)));

    cb13 = new JCheckBox(guistrings.getString("no_simultaneous_catches"), null);
    p9.add(cb13);
    gb.setConstraints(
        cb13, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(0, 10, 0, 0)));

    cb14 = new JCheckBox(guistrings.getString("no_clustered_throws"), null);
    p9.add(cb14);
    gb.setConstraints(
        cb14, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(0, 10, 0, 0)));

    cb16 = new JCheckBox(guistrings.getString("true_multiplexing"), null);
    p9.add(cb16);
    gb.setConstraints(
        cb16, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 4, new Insets(0, 10, 0, 0)));

    JPanel p4 = new JPanel(); // entire middle section
    p4.setLayout(gb);
    p4.add(p6);
    gb.setConstraints(p6, JLFunc.constraints(GridBagConstraints.FIRST_LINE_START, 0, 0));
    p4.add(p7);
    gb.setConstraints(p7, JLFunc.constraints(GridBagConstraints.FIRST_LINE_START, 0, 1));
    p4.add(p8);
    gb.setConstraints(p8, JLFunc.constraints(GridBagConstraints.FIRST_LINE_START, 1, 0));
    p4.add(p9);
    gb.setConstraints(p9, JLFunc.constraints(GridBagConstraints.FIRST_LINE_START, 1, 1));

    JPanel p1 = new JPanel(); // bottom section
    p1.setLayout(gb);
    lab1 = new JLabel(guistrings.getString("Exclude_these_throws"));
    p1.add(lab1);
    gb.setConstraints(
        lab1, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 3)));
    tf4 = new JTextField(10);
    p1.add(tf4);
    gb.setConstraints(tf4, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0));
    lab2 = new JLabel(guistrings.getString("Include_these_throws"));
    p1.add(lab2);
    gb.setConstraints(
        lab2, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 1, new Insets(0, 0, 0, 3)));
    tf5 = new JTextField(10);
    p1.add(tf5);
    gb.setConstraints(tf5, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 1));
    /*
    tf6 = new JTextField(10);
    p1.add(tf6);
    gb.setConstraints(tf6, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2));
    */
    lab4 = new JLabel(guistrings.getString("Passing_communication_delay"));
    p1.add(lab4);
    gb.setConstraints(
        lab4, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 2, new Insets(3, 0, 0, 3)));
    tf7 = new JTextField(3);
    p1.add(tf7);
    gb.setConstraints(
        tf7, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 2, new Insets(3, 0, 0, 0)));
    /*
    tf8 = new JTextField(3);
    p1.add(tf8);
    gb.setConstraints(tf8, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 4));
    lab3 = new JLabel(guistrings.getString("Exclude_these_passes"));
    p1.add(lab3);
    gb.setConstraints(lab3, JLFunc.constraints(GridBagConstraints.LINE_END, 1, 2,
                                            new Insets(0, 0, 0, 3)));
    lab5 = new JLabel("Passing leader slot number");
    p1.add(lab5);
    gb.setConstraints(lab5, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 4,
                                            new Insets(0, 0, 0, 3)));
    */

    add(p2);
    gb.setConstraints(
        p2,
        JLFunc.constraints(GridBagConstraints.CENTER, 0, 0, new Insets(BORDER, BORDER, 5, BORDER)));
    add(p4);
    gb.setConstraints(
        p4, JLFunc.constraints(GridBagConstraints.CENTER, 0, 1, new Insets(5, BORDER, 5, BORDER)));
    add(p1);
    gb.setConstraints(
        p1, JLFunc.constraints(GridBagConstraints.CENTER, 0, 2, new Insets(5, BORDER, 5, BORDER)));

    // add action listeners to enable/disable items depending on context
    c1.addItemListener(
        ex -> {
          if (c1.getSelectedIndex() > 0) {
            // lab3.setEnabled(true);
            // lab5.setEnabled(true);
            // tf6.setEnabled(true);
            cb15.setEnabled(true);
            cb17.setEnabled(cb7.isSelected() && cb8.isSelected());
            cb18.setEnabled(true);
            if (cb7.isSelected() && !cb8.isSelected()) {
              lab4.setEnabled(true);
              tf7.setEnabled(true);
            } else {
              lab4.setEnabled(false);
              tf7.setEnabled(false);
            }
            // tf8.setEnabled(true);
            // lab1.setText(guistrings.getString("Exclude_these_self_throws"));
            // lab2.setText(guistrings.getString("Include_these_self_throws"));
          } else {
            // lab3.setEnabled(false);
            // lab5.setEnabled(false);
            // tf6.setEnabled(false);
            cb15.setEnabled(false);
            cb17.setEnabled(false);
            cb18.setEnabled(false);
            lab4.setEnabled(false);
            tf7.setEnabled(false);
            // tf8.setEnabled(false);
            // lab1.setText(guistrings.getString("Exclude_these_throws"));
            // lab2.setText(guistrings.getString("Include_these_throws"));
          }

          // Transfer focus back up so that the run button works
          c1.transferFocus();
        });

    cb12.addItemListener(
        iv -> {
          boolean active = cb12.isSelected();

          cb13.setEnabled(active);
          cb14.setEnabled(active);
          lab13.setEnabled(active);
          tf9.setEnabled(active);
          cb16.setEnabled(active);
        });

    ActionListener temp =
        ev -> {
          if (!cb7.isSelected() || cb8.isSelected()) {
            lab4.setEnabled(false);
            tf7.setEnabled(false);
          } else {
            if (c1.getSelectedIndex() > 0) {
              lab4.setEnabled(true);
              tf7.setEnabled(true);
            }
          }

          cb17.setEnabled(cb7.isSelected() && cb8.isSelected() && (c1.getSelectedIndex() > 0));
          cb9.setEnabled(cb8.isSelected());
        };
    cb7.addActionListener(temp);
    cb8.addActionListener(temp);

    resetControl();
  }

  public void resetControl() {
    tf1.setText("5"); // balls
    tf2.setText("7"); // max throw
    tf3.setText("5"); // period
    cb1.setSelected(true); // asynch mode
    cb5.setSelected(true); // show all compositions
    cb7.setSelected(true); // ground state patterns
    cb8.setSelected(true); // excited state patterns
    cb9.setSelected(false); // starting/ending sequences
    cb10.setSelected(false); // pattern rotations
    cb17.setSelected(false); // juggler permutations
    cb15.setSelected(true); // connected patterns
    cb18.setSelected(false); // symmetric patterns
    cb12.setSelected(false); // multiplexing
    tf9.setText("2"); // number of multiplexed throws
    cb13.setSelected(true); // no simultaneous catches
    cb14.setSelected(false); // allow clustered throws
    cb16.setSelected(false); // true multiplexing
    tf4.setText(""); // excluded throws
    tf5.setText(""); // included throws
    // tf6.setText("");         // excluded passes
    tf7.setText("0"); // passing communication delay
    // tf8.setText("1");        // passing leader slot number
    c1.setSelectedIndex(0); // one juggler

    // lab3.setEnabled(false);
    cb9.setEnabled(true);
    cb17.setEnabled(false);
    cb15.setEnabled(false);
    cb18.setEnabled(false);
    lab4.setEnabled(false); // passing communication delay
    // lab5.setEnabled(false);
    // tf6.setEnabled(false);
    tf7.setEnabled(false);
    // tf8.setEnabled(false);

    lab13.setEnabled(false); // number of multiplexed throws label
    tf9.setEnabled(false);
    cb13.setEnabled(false);
    cb14.setEnabled(false);
    cb16.setEnabled(false);
  }

  public String getParams() {
    StringBuilder sb = new StringBuilder(256);

    String maxthrow = tf2.getText();
    if (maxthrow.trim().isEmpty()) {
      maxthrow = "-";
    }

    String period = tf3.getText();
    if (period.trim().isEmpty()) {
      period = "-";
    }

    sb.append(tf1.getText()).append(" ").append(maxthrow).append(" ").append(period);

    if (cb2.isSelected()) {
      sb.append(" -s");
    }

    int jugglers = c1.getSelectedIndex() + 1;
    if (jugglers > 1) {
      sb.append(" -j ").append(jugglers);
      if (tf7.isEnabled() && !tf7.getText().isEmpty()) {
        sb.append(" -d ").append(tf7.getText()).append(" -l 1");
      }

      if (cb17.isEnabled()) {
        if (cb17.isSelected()) {
          sb.append(" -jp");
        }
      } else sb.append(" -jp");

      if (cb15.isSelected()) {
        sb.append(" -cp");
      }
      if (cb18.isSelected()) {
        sb.append(" -sym");
      }
    }

    if (cb5.isSelected()) {
      sb.append(" -f");
    } else if (cb6.isSelected()) {
      sb.append(" -prime");
    }

    if (cb7.isSelected() && !cb8.isSelected()) {
      sb.append(" -g");
    } else if (!cb7.isSelected() && cb8.isSelected()) {
      sb.append(" -ng");
    }

    if (!cb9.isEnabled() || !cb9.isSelected()) {
      sb.append(" -se");
    }

    if (cb10.isSelected()) {
      sb.append(" -rot");
    }

    if (cb12.isSelected() && !tf9.getText().isEmpty()) {
      sb.append(" -m ").append(tf9.getText());
      if (!cb13.isSelected()) {
        sb.append(" -mf");
      }
      if (cb14.isSelected()) {
        sb.append(" -mc");
      }
      if (cb16.isSelected()) {
        sb.append(" -mt");
      }
    }

    if (!tf4.getText().isEmpty()) {
      sb.append(" -x ").append(tf4.getText());
    }
    if (!tf5.getText().isEmpty()) {
      sb.append(" -i ").append(tf5.getText());
    }

    sb.append(" -n");

    return sb.toString();
  }
}
