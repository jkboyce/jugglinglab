//
// PatternView.java
//
// This view provides the ability to edit the text representation of a pattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view;

import java.awt.*;
import java.io.File;
import java.io.StringReader;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.event.*;
import jugglinglab.core.AnimationPanel;
import jugglinglab.core.AnimationPrefs;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;

public class PatternView extends View implements DocumentListener {
  protected AnimationPanel ja;
  protected JSplitPane jsp;
  protected JRadioButton rb_bp;
  protected JLabel bp_edited_icon;
  protected JRadioButton rb_jml;
  protected JTextArea ta;
  protected JButton compile;
  protected JButton revert;
  protected JLabel lab;
  protected boolean text_edited = false;

  public PatternView(Dimension dim) {
    makePanel(dim);
    updateButtons();
  }

  protected void makePanel(Dimension dim) {
    setLayout(new BorderLayout());

    // animator on the left
    ja = new AnimationPanel();
    ja.setPreferredSize(dim);
    ja.setMinimumSize(new Dimension(10, 10));

    // controls panel on the right
    JPanel controls = new JPanel();
    GridBagLayout gb = new GridBagLayout();
    controls.setLayout(gb);

    JLabel lab_view = new JLabel(guistrings.getString("PatternView_heading"));
    gb.setConstraints(
        lab_view,
        JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(15, 4, 10, 0)));
    controls.add(lab_view);

    ButtonGroup bg = new ButtonGroup();
    JPanel bppanel = new JPanel();
    bppanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    rb_bp = new JRadioButton(guistrings.getString("PatternView_rb1_default"));
    bg.add(rb_bp);
    bppanel.add(rb_bp);
    java.net.URL url = PatternView.class.getResource("/alert.png");
    if (url != null) {
      ImageIcon edited_icon = new ImageIcon(url);
      ImageIcon edited_icon_scaled =
          new ImageIcon(
              edited_icon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH));
      bp_edited_icon = new JLabel(edited_icon_scaled);
      bp_edited_icon.setToolTipText(guistrings.getString("PatternView_alert"));
      bppanel.add(Box.createHorizontalStrut(10));
      bppanel.add(bp_edited_icon);
    }
    controls.add(bppanel);
    gb.setConstraints(
        bppanel, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 4, 0, 0)));

    rb_jml = new JRadioButton(guistrings.getString("PatternView_rb2"));
    bg.add(rb_jml);
    controls.add(rb_jml);
    gb.setConstraints(
        rb_jml, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2, new Insets(0, 4, 0, 0)));

    ta = new JTextArea();
    JScrollPane jscroll = new JScrollPane(ta);
    jscroll.setPreferredSize(new Dimension(400, 1));
    jscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    jscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    controls.add(jscroll);
    GridBagConstraints gbc =
        JLFunc.constraints(GridBagConstraints.LINE_START, 0, 3, new Insets(15, 0, 0, 0));
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = gbc.weighty = 1.0;
    gb.setConstraints(jscroll, gbc);

    // split pane dividing the two
    jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ja, controls);
    jsp.setResizeWeight(0.75); // % extra space allocated to left (animation) side

    add(jsp, BorderLayout.CENTER);

    // button + error message label across the bottom
    JPanel lower = new JPanel();
    GridBagLayout gb2 = new GridBagLayout();
    lower.setLayout(gb2);
    compile = new JButton(guistrings.getString("PatternView_compile_button"));
    gb2.setConstraints(
        compile, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(8, 8, 8, 0)));
    lower.add(compile);
    revert = new JButton(guistrings.getString("PatternView_revert_button"));
    gb2.setConstraints(
        revert, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0, new Insets(8, 5, 8, 12)));
    lower.add(revert);
    lab = new JLabel(" ");
    GridBagConstraints gbc2 = JLFunc.constraints(GridBagConstraints.LINE_START, 2, 0);
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    gbc2.weightx = 1.0;
    gb2.setConstraints(lab, gbc2);
    lower.add(lab);

    add(lower, BorderLayout.PAGE_END);

    // add actions to the various items
    ta.getDocument().addDocumentListener(this);
    rb_bp.addActionListener(ae -> reloadTextArea());
    rb_jml.addActionListener(ae -> reloadTextArea());
    compile.addActionListener(ae -> compilePattern());
    revert.addActionListener(ae -> revertPattern());
  }

  // Update the button configs when a radio button is pressed, the base
  // pattern or JML pattern changes, or we start/stop writing an animated GIF.

  protected void updateButtons() {
    if (ja != null && ja.writingGIF) {
      // writing a GIF
      rb_bp.setEnabled(false);
      rb_jml.setEnabled(false);
      compile.setEnabled(false);
      revert.setEnabled(false);
      return;
    }

    JMLPattern pat = getPattern();

    if (pat == null) {
      rb_bp.setEnabled(false);
      if (bp_edited_icon != null) {
        bp_edited_icon.setVisible(false);
      }
      rb_jml.setEnabled(false);
    } else if (pat.getBasePatternNotation() == null || pat.getBasePatternConfig() == null) {
      // no base pattern set
      rb_bp.setEnabled(false);
      if (bp_edited_icon != null) {
        bp_edited_icon.setVisible(false);
      }
      rb_jml.setEnabled(true);
      rb_jml.setSelected(true);
    } else {
      rb_bp.setEnabled(true);
      if (bp_edited_icon != null) {
        bp_edited_icon.setVisible(pat.isBasePatternEdited());
      }
      rb_jml.setEnabled(true);
    }

    if (rb_bp.isSelected()) {
      compile.setEnabled(pat != null && (pat.isBasePatternEdited() || text_edited));
      revert.setEnabled(text_edited);
    } else if (rb_jml.isSelected()) {
      compile.setEnabled(text_edited);
      revert.setEnabled(text_edited);
    }
  }

  // (Re)load the text in the JTextArea from the pattern, overwriting anything
  // that was there.

  protected void reloadTextArea() {
    if (rb_bp.isSelected()) {
      ta.setText(getPattern().getBasePatternConfig().replace(";", ";\n"));
    } else if (rb_jml.isSelected()) {
      ta.setText(getPattern().toString());
    }

    ta.setCaretPosition(0);
    lab.setText(" ");
    setTextEdited(false);

    // Note the above always triggers an updateButtons() call, since
    // text_edited is cycled from true (from the setText() calls) to false.
  }

  protected void setTextEdited(boolean edited) {
    if (text_edited != edited) {
      text_edited = edited;
      updateButtons();
    }
  }

  protected void compilePattern() {
    try {
      if (rb_bp.isSelected()) {
        String notation = getPattern().getBasePatternNotation();
        String config = ta.getText().replace("\n", "").trim();
        JMLPattern newpat = JMLPattern.fromBasePattern(notation, config);
        restartView(newpat, null);
        addToUndoList(newpat);
      } else if (rb_jml.isSelected()) {
        JMLPattern newpat = new JMLPattern(new StringReader(ta.getText()));
        restartView(newpat, null);
        addToUndoList(newpat);
      }
    } catch (JuggleExceptionUser jeu) {
      lab.setText(jeu.getMessage());
      setTextEdited(true);
    } catch (JuggleExceptionInternal jei) {
      ErrorDialog.handleFatalException(jei);
      setTextEdited(true);
    }
  }

  protected void revertPattern() {
    reloadTextArea();
  }

  //----------------------------------------------------------------------------
  // View methods
  //----------------------------------------------------------------------------

  @Override
  public void restartView(JMLPattern p, AnimationPrefs c)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    ja.restartJuggle(p, c);
    setAnimationPanelPreferredSize(getAnimationPrefs().getSize());

    if (p != null) {
      String notation = p.getBasePatternNotation();
      String template = guistrings.getString("PatternView_rb1");
      Object[] arg = {notation == null ? "none set" : notation};
      rb_bp.setText(MessageFormat.format(template, arg));

      if (!(rb_bp.isSelected() || rb_jml.isSelected())) {
        if (notation == null) {
          rb_jml.setSelected(true);
        } else {
          rb_bp.setSelected(true);
        }
      }

      updateButtons();
      reloadTextArea();
      parent.setTitle(p.getTitle());
    }
  }

  @Override
  public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
    ja.restartJuggle();
  }

  @Override
  public Dimension getAnimationPanelSize() {
    return ja.getSize(new Dimension());
  }

  @Override
  public void setAnimationPanelPreferredSize(Dimension d) {
    ja.setPreferredSize(d);
    jsp.resetToPreferredSizes();
  }

  @Override
  public JMLPattern getPattern() {
    return ja.getPattern();
  }

  @Override
  public AnimationPrefs getAnimationPrefs() {
    return ja.getAnimationPrefs();
  }

  @Override
  public double getZoomLevel() {
    return ja.getZoomLevel();
  }

  @Override
  public void setZoomLevel(double z) {
    ja.setZoomLevel(z);
  }

  @Override
  public boolean isPaused() {
    return ja.isPaused();
  }

  @Override
  public void setPaused(boolean pause) {
    if (ja.message == null) {
      ja.setPaused(pause);
    }
  }

  @Override
  public void disposeView() {
    ja.disposeAnimation();
  }

  @Override
  public void writeGIF(File f) {
    ja.writingGIF = true;
    updateButtons();
    boolean origpause = isPaused();
    setPaused(true);
    jsp.setEnabled(false);
    if (parent != null) {
      parent.setResizable(false);
    }

    Runnable cleanup =
        () -> {
          ja.writingGIF = false;
          setPaused(origpause);
          updateButtons();
          jsp.setEnabled(true);
          if (parent != null) {
            parent.setResizable(true);
          }
        };

    new View.GIFWriter(ja, f, cleanup);
  }

  //----------------------------------------------------------------------------
  // javax.swing.event.DocumentListener methods
  //----------------------------------------------------------------------------

  @Override
  public void insertUpdate(DocumentEvent e) {
    setTextEdited(true);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    setTextEdited(true);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    setTextEdited(true);
  }
}
