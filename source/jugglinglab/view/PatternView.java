// PatternView.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

package jugglinglab.view;

import java.awt.*;
import java.awt.event.*;
import java.io.StringReader;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.event.*;

import jugglinglab.core.*;
import jugglinglab.jml.JMLPattern;
import jugglinglab.notation.Pattern;
import jugglinglab.util.*;


// This view provides the ability to edit the textual representation of a pettern.

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

        ja = new AnimationPanel();
        ja.setAnimationPanelPreferredSize(dim);

        JPanel controls = new JPanel();
        GridBagLayout gb = new GridBagLayout();
        controls.setLayout(gb);

        JLabel lab_view = new JLabel(guistrings.getString("PatternView_heading"));
        gb.setConstraints(lab_view, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0,
                          new Insets(15, 0, 10, 0)));
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
            if (edited_icon != null) {
                ImageIcon edited_icon_scaled = new ImageIcon(edited_icon.getImage().getScaledInstance(22, 22,  java.awt.Image.SCALE_SMOOTH));
                bp_edited_icon = new JLabel(edited_icon_scaled);
                bp_edited_icon.setToolTipText(guistrings.getString("PatternView_alert"));
                bppanel.add(Box.createHorizontalStrut(10));
                bppanel.add(bp_edited_icon);
            }
        }
        controls.add(bppanel);
        gb.setConstraints(bppanel, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1));

        rb_jml = new JRadioButton(guistrings.getString("PatternView_rb2"));
        bg.add(rb_jml);
        controls.add(rb_jml);
        gb.setConstraints(rb_jml, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2));

        ta = new JTextArea();
        JScrollPane jscroll = new JScrollPane(ta);
        jscroll.setPreferredSize(new Dimension(400, 1));
        jscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        controls.add(jscroll);
        GridBagConstraints gbc = JLFunc.constraints(GridBagConstraints.LINE_START, 0, 3,
                                                    new Insets(15, 0, 0, 0));
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = gbc.weighty = 1.0;
        gb.setConstraints(jscroll, gbc);

        jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ja, controls);
        add(jsp, BorderLayout.CENTER);

        JPanel lower = new JPanel();
        lower.setLayout(new FlowLayout(FlowLayout.LEADING));
        compile = new JButton(guistrings.getString("PatternView_compile_button"));
        lower.add(compile);
        revert = new JButton(guistrings.getString("PatternView_revert_button"));
        lower.add(revert);
        lab = new JLabel("");
        lower.add(lab);
        add(lower, BorderLayout.PAGE_END);

        // add actions to the various items

        ta.getDocument().addDocumentListener(this);

        rb_bp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) { reloadTextArea(); }
        });

        rb_jml.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) { reloadTextArea(); }
        });

        compile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) { compilePattern(); }
        });

        revert.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) { revertPattern(); }
        });
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
            if (bp_edited_icon != null)
                bp_edited_icon.setVisible(false);
            rb_jml.setEnabled(false);
        } else if (pat.getBasePatternNotation() == null || pat.getBasePatternConfig() == null) {
            // no base pattern set
            rb_bp.setEnabled(false);
            if (bp_edited_icon != null)
                bp_edited_icon.setVisible(false);
            rb_jml.setEnabled(true);
            rb_jml.setSelected(true);
        } else {
            rb_bp.setEnabled(true);
            if (bp_edited_icon != null)
                bp_edited_icon.setVisible(pat.isBasePatternEdited());
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

    // (Re)load the text in the JTextArea from the pattern, overwriting
    // anything that was there.
    protected void reloadTextArea() {
        if (rb_bp.isSelected())
            ta.setText(getPattern().getBasePatternConfig().replace(";", ";\n"));
        else if (rb_jml.isSelected())
            ta.setText(getPattern().toString());

        ta.setCaretPosition(0);
        lab.setText("");
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
        ApplicationWindow.updateWindowMenus();
    }

    protected void revertPattern() {
        reloadTextArea();
    }

    // View methods

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle(p, c);

        if (p != null) {
            String notation = p.getBasePatternNotation();
            String template = guistrings.getString("PatternView_rb1");
            Object[] arg = { notation == null ? "none set" : notation };
            rb_bp.setText(MessageFormat.format(template, arg));

            if (!(rb_bp.isSelected() || rb_jml.isSelected())) {
                if (notation == null)
                    rb_jml.setSelected(true);
                else
                    rb_bp.setSelected(true);
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
        ja.setAnimationPanelPreferredSize(d);
        jsp.resetToPreferredSizes();
    }

    @Override
    public JMLPattern getPattern() { return ja.getPattern(); }

    @Override
    public AnimationPrefs getAnimationPrefs() { return ja.getAnimationPrefs(); }

    @Override
    public boolean getPaused() { return ja.getPaused(); }

    @Override
    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
    }

    @Override
    public void disposeView() { ja.disposeAnimation(); }

    @Override
    public void writeGIF() {
        ja.writingGIF = true;
        updateButtons();
        boolean origpause = getPaused();
        setPaused(true);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                setPaused(origpause);
                ja.writingGIF = false;
                updateButtons();
            }
        };

        new View.GIFWriter(ja, cleanup);
    }

    // javax.swing.event.DocumentListener methods

    @Override
    public void insertUpdate(DocumentEvent e) { setTextEdited(true); }

    @Override
    public void removeUpdate(DocumentEvent e) { setTextEdited(true); }

    @Override
    public void changedUpdate(DocumentEvent e) { setTextEdited(true); }
}
