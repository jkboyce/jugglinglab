//
// AnimationPrefsDialog.java
//
// This is the dialog box that allows the user to set animation preferences.
// The dialog does not display when the dialog box is constructed, but when
// getPrefs() is called.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.*;
import javax.swing.*;
import jugglinglab.util.*;

public class AnimationPrefsDialog extends JDialog {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
  protected static final int BORDER = 10;

  protected JTextField tf_width;
  protected JTextField tf_height;
  protected JTextField tf_fps;
  protected JTextField tf_slowdown;
  protected JTextField tf_border;
  protected JComboBox<String> combo_showground;
  protected JCheckBox cb_paused;
  protected JCheckBox cb_mousepause;
  protected JCheckBox cb_stereo;
  protected JCheckBox cb_catchsounds;
  protected JCheckBox cb_bouncesounds;
  protected JTextField tf_other;
  protected JButton but_cancel;
  protected JButton but_ok;

  protected boolean ok_selected;

  public AnimationPrefsDialog(JFrame parent) {
    super(parent, guistrings.getString("Animation_Preferences"), true);
    createContents();
    setLocationRelativeTo(parent);

    but_cancel.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            setVisible(false);
            ok_selected = false;
          }
        });

    but_ok.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            setVisible(false);
            ok_selected = true;
          }
        });
  }

  // Show dialog box and return the new preferences.

  public AnimationPrefs getPrefs(AnimationPrefs oldjc) {
    // Fill in UI elements with current prefs
    tf_width.setText(Integer.toString(oldjc.width));
    tf_height.setText(Integer.toString(oldjc.height));
    tf_fps.setText(JLFunc.toStringRounded(oldjc.fps, 2));
    tf_slowdown.setText(JLFunc.toStringRounded(oldjc.slowdown, 2));
    tf_border.setText(Integer.toString(oldjc.border));
    combo_showground.setSelectedIndex(oldjc.showGround);
    cb_paused.setSelected(oldjc.startPause);
    cb_mousepause.setSelected(oldjc.mousePause);
    cb_stereo.setSelected(oldjc.stereo);
    cb_catchsounds.setSelected(oldjc.catchSound);
    cb_bouncesounds.setSelected(oldjc.bounceSound);

    try {
      // filter out all the explicit settings above to populate the
      // manual settings box
      ParameterList pl = new ParameterList(oldjc.toString());
      String[] params_remove = {
        "width",
        "height",
        "fps",
        "slowdown",
        "border",
        "showground",
        "stereo",
        "startpaused",
        "mousepause",
        "catchsound",
        "bouncesound",
      };
      for (String param : params_remove) {
        pl.removeParameter(param);
      }
      tf_other.setText(pl.toString());
      tf_other.setCaretPosition(0);
    } catch (JuggleExceptionUser jeu) {
      // any error here can't be a user error
      ErrorDialog.handleFatalException(
          new JuggleExceptionInternal("Anim Prefs Dialog error: " + jeu.getMessage()));
    }

    ok_selected = false;
    setVisible(true); // Blocks until user clicks OK or Cancel

    if (ok_selected) {
      return readDialogBox(oldjc);
    }

    return oldjc;
  }

  protected void createContents() {
    GridBagLayout gb = new GridBagLayout();
    getContentPane().setLayout(gb);

    // panel of text boxes at the top
    JPanel p1 = new JPanel();
    p1.setLayout(gb);

    JLabel lab1 = new JLabel(guistrings.getString("Width"));
    p1.add(lab1);
    gb.setConstraints(
        lab1, make_constraints(GridBagConstraints.LINE_START, 1, 0, new Insets(0, 3, 0, 0)));
    tf_width = new JTextField(4);
    tf_width.setHorizontalAlignment(JTextField.CENTER);
    p1.add(tf_width);
    gb.setConstraints(
        tf_width, make_constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(0, 0, 0, 0)));

    JLabel lab2 = new JLabel(guistrings.getString("Height"));
    p1.add(lab2);
    gb.setConstraints(
        lab2, make_constraints(GridBagConstraints.LINE_START, 1, 1, new Insets(0, 3, 0, 0)));
    tf_height = new JTextField(4);
    tf_height.setHorizontalAlignment(JTextField.CENTER);
    p1.add(tf_height);
    gb.setConstraints(
        tf_height, make_constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 0, 0, 0)));

    JLabel lab3 = new JLabel(guistrings.getString("Frames_per_second"));
    p1.add(lab3);
    gb.setConstraints(
        lab3, make_constraints(GridBagConstraints.LINE_START, 1, 2, new Insets(0, 3, 0, 0)));
    tf_fps = new JTextField(4);
    tf_fps.setHorizontalAlignment(JTextField.CENTER);
    p1.add(tf_fps);
    gb.setConstraints(
        tf_fps, make_constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(0, 0, 0, 0)));

    JLabel lab4 = new JLabel(guistrings.getString("Slowdown_factor"));
    p1.add(lab4);
    gb.setConstraints(
        lab4, make_constraints(GridBagConstraints.LINE_START, 1, 3, new Insets(0, 3, 0, 0)));
    tf_slowdown = new JTextField(4);
    tf_slowdown.setHorizontalAlignment(JTextField.CENTER);
    p1.add(tf_slowdown);
    gb.setConstraints(
        tf_slowdown, make_constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(0, 0, 0, 0)));

    JLabel lab5 = new JLabel(guistrings.getString("Border_(pixels)"));
    p1.add(lab5);
    gb.setConstraints(
        lab5, make_constraints(GridBagConstraints.LINE_START, 1, 4, new Insets(0, 3, 0, 0)));
    tf_border = new JTextField(4);
    tf_border.setHorizontalAlignment(JTextField.CENTER);
    p1.add(tf_border);
    gb.setConstraints(
        tf_border, make_constraints(GridBagConstraints.LINE_START, 0, 4, new Insets(0, 0, 0, 0)));

    JLabel lab6 = new JLabel(guistrings.getString("Prefs_show_ground"));
    p1.add(lab6);
    gb.setConstraints(
        lab6, make_constraints(GridBagConstraints.LINE_START, 1, 5, new Insets(0, 3, 0, 0)));
    combo_showground = new JComboBox<String>();
    combo_showground.addItem(guistrings.getString("Prefs_show_ground_auto"));
    combo_showground.addItem(guistrings.getString("Prefs_show_ground_yes"));
    combo_showground.addItem(guistrings.getString("Prefs_show_ground_no"));
    p1.add(combo_showground);
    gb.setConstraints(
        combo_showground,
        make_constraints(GridBagConstraints.LINE_START, 0, 5, new Insets(0, 0, 0, 0)));

    // checkboxes farther down
    cb_paused = new JCheckBox(guistrings.getString("Start_paused"));
    cb_mousepause = new JCheckBox(guistrings.getString("Pause_on_mouse_away"));
    cb_stereo = new JCheckBox(guistrings.getString("Stereo_display"));
    cb_catchsounds = new JCheckBox(guistrings.getString("Catch_sounds"));
    cb_bouncesounds = new JCheckBox(guistrings.getString("Bounce_sounds"));

    // manual settings
    JLabel lab_other = new JLabel("Manual settings");
    tf_other = new JTextField(15);

    // buttons at the bottom
    JPanel p2 = new JPanel();
    p2.setLayout(gb);
    but_cancel = new JButton(guistrings.getString("Cancel"));

    p2.add(but_cancel);
    gb.setConstraints(
        but_cancel, make_constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 0)));
    but_ok = new JButton(guistrings.getString("OK"));

    p2.add(but_ok);
    gb.setConstraints(
        but_ok, make_constraints(GridBagConstraints.LINE_END, 1, 0, new Insets(0, 10, 0, 0)));

    // now make the whole window
    getContentPane().add(p1);
    gb.setConstraints(
        p1,
        make_constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(3, BORDER, 0, BORDER)));

    getContentPane().add(cb_paused);
    gb.setConstraints(
        cb_paused,
        make_constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, BORDER, 0, BORDER)));
    getContentPane().add(cb_mousepause);
    gb.setConstraints(
        cb_mousepause,
        make_constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(0, BORDER, 0, BORDER)));
    getContentPane().add(cb_stereo);
    gb.setConstraints(
        cb_stereo,
        make_constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(0, BORDER, 0, BORDER)));
    getContentPane().add(cb_catchsounds);
    gb.setConstraints(
        cb_catchsounds,
        make_constraints(GridBagConstraints.LINE_START, 0, 4, new Insets(0, BORDER, 0, BORDER)));
    getContentPane().add(cb_bouncesounds);
    gb.setConstraints(
        cb_bouncesounds,
        make_constraints(GridBagConstraints.LINE_START, 0, 5, new Insets(0, BORDER, 8, BORDER)));
    getContentPane().add(lab_other);
    gb.setConstraints(
        lab_other,
        make_constraints(GridBagConstraints.LINE_START, 0, 6, new Insets(0, BORDER, 0, BORDER)));
    getContentPane().add(tf_other);
    gb.setConstraints(
        tf_other,
        make_constraints(GridBagConstraints.LINE_START, 0, 7, new Insets(0, BORDER, 3, BORDER)));

    getContentPane().add(p2);
    gb.setConstraints(
        p2,
        make_constraints(GridBagConstraints.LINE_END, 0, 8, new Insets(0, BORDER, BORDER, BORDER)));

    getRootPane().setDefaultButton(but_ok); // OK button is default

    Locale loc = Locale.getDefault();
    applyComponentOrientation(ComponentOrientation.getOrientation(loc));

    pack();
    setResizable(false);
  }

  protected static GridBagConstraints make_constraints(
      int location, int gridx, int gridy, Insets ins) {
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.anchor = location;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridheight = gbc.gridwidth = 1;
    gbc.gridx = gridx;
    gbc.gridy = gridy;
    gbc.insets = ins;
    gbc.weightx = gbc.weighty = 0.0;
    return gbc;
  }

  // Read prefs out of UI elements.

  protected AnimationPrefs readDialogBox(AnimationPrefs oldjc) {
    int tempint;
    double tempdouble;

    // Clone the old preferences so if we get an error we retain as much of
    // it as possible
    AnimationPrefs newjc = new AnimationPrefs(oldjc);

    try {
      tempint = Integer.parseInt(tf_width.getText());
      if (tempint >= 0) {
        newjc.width = tempint;
      }
    } catch (NumberFormatException e) {
      String template = errorstrings.getString("Error_number_format");
      Object[] arguments = {"width"};
      ErrorDialog.handleUserException(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
    }
    try {
      tempint = Integer.parseInt(tf_height.getText());
      if (tempint >= 0) {
        newjc.height = tempint;
      }
    } catch (NumberFormatException e) {
      String template = errorstrings.getString("Error_number_format");
      Object[] arguments = {"height"};
      ErrorDialog.handleUserException(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
    }
    try {
      tempdouble = Double.parseDouble(tf_fps.getText());
      if (tempdouble > 0.0) {
        newjc.fps = tempdouble;
      }
    } catch (NumberFormatException e) {
      String template = errorstrings.getString("Error_number_format");
      Object[] arguments = {"fps"};
      ErrorDialog.handleUserException(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
    }
    try {
      tempdouble = Double.parseDouble(tf_slowdown.getText());
      if (tempdouble > 0.0) {
        newjc.slowdown = tempdouble;
      }
    } catch (NumberFormatException e) {
      String template = errorstrings.getString("Error_number_format");
      Object[] arguments = {"slowdown"};
      ErrorDialog.handleUserException(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
    }
    try {
      tempint = Integer.parseInt(tf_border.getText());
      if (tempint >= 0) {
        newjc.border = tempint;
      }
    } catch (NumberFormatException e) {
      String template = errorstrings.getString("Error_number_format");
      Object[] arguments = {"border"};
      ErrorDialog.handleUserException(AnimationPrefsDialog.this, MessageFormat.format(template, arguments));
    }

    newjc.showGround = combo_showground.getSelectedIndex();
    newjc.startPause = cb_paused.isSelected();
    newjc.mousePause = cb_mousepause.isSelected();
    newjc.stereo = cb_stereo.isSelected();
    newjc.catchSound = cb_catchsounds.isSelected();
    newjc.bounceSound = cb_bouncesounds.isSelected();

    if (tf_other.getText().trim().length() > 0) {
      try {
        newjc = new AnimationPrefs().fromString(newjc.toString() + ";" + tf_other.getText());
      } catch (JuggleExceptionUser jeu) {
        ErrorDialog.handleUserException(AnimationPrefsDialog.this, jeu.getMessage());
      }
    }

    return newjc;
  }
}
