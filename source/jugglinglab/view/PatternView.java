// PatternView.java
//
// Copyright 2021 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.view;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.event.*;
import org.xml.sax.*;

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
        updateRadioButtons();
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
        ta.getDocument().addDocumentListener(this);

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

        // add actions to the various buttons

        rb_bp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    reloadTextArea();
                } catch (IOException ioe) {
                    lab.setText(ioe.getMessage());
                }
            }
        });

        rb_jml.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    reloadTextArea();
                } catch (IOException ioe) {
                    lab.setText(ioe.getMessage());
                }
            }
        });

        compile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    compilePattern();
                } catch (Exception e) {
                    ErrorDialog.handleFatalException(e);
                }
            }
        });

        revert.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    revertPattern();
                } catch (Exception e) {
                    ErrorDialog.handleFatalException(e);
                }
            }
        });
    }

    // Update the radio button status when the base pattern or JML pattern changes.
    protected void updateRadioButtons() {
        if (getBasePatternNotation() == null || getBasePatternConfig() == null) {
            // no base pattern has been set
            rb_jml.setSelected(true);
            rb_bp.setEnabled(false);
            if (bp_edited_icon != null)
                bp_edited_icon.setVisible(false);
        } else {
            rb_bp.setEnabled(true);
            if (bp_edited_icon != null)
                bp_edited_icon.setVisible(getBasePatternEdited());
        }
    }

    @Override
    public void setBasePattern(String bpn, String bpc) throws JuggleExceptionUser {
        try {
            super.setBasePattern(bpn, bpc);
            String template = guistrings.getString("PatternView_rb1");
            Object[] arg = { getBasePatternNotation() };
            rb_bp.setText(MessageFormat.format(template, arg));
            rb_bp.setSelected(true);
            updateRadioButtons();
            reloadTextArea();
        } catch (IOException ioe) {
            throw new JuggleExceptionUser(ioe.getMessage());
        }
    }

    @Override
    public void setBasePatternEdited(boolean bpe) {
        super.setBasePatternEdited(bpe);
        updateRadioButtons();
    }

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) {
        try {
            ja.restartJuggle(p, c);
            if (p != null) {
                reloadTextArea();
                parent.setTitle(p.getTitle());
            }
        } catch (JuggleException je) {
            lab.setText(je.getMessage());
            setTextEdited(true);
        } catch (IOException ioe) {
            lab.setText(ioe.getMessage());
            setTextEdited(true);
        }
    }

    @Override
    public void restartView() {
        try {
            ja.restartJuggle();
        } catch (JuggleException je) {
            lab.setText(je.getMessage());
        }
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
        boolean origpause = getPaused();
        setPaused(true);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                setPaused(origpause);
                ja.writingGIF = false;
            }
        };

        new View.GIFWriter(ja, cleanup);
    }

    protected void compilePattern() {
        if (!text_edited)
            return;

        try {
            JMLPattern newpat = null;

            if (rb_bp.isSelected()) {
                String config = ta.getText().replace('\n', ';');
                Pattern p = Pattern.newPattern(getBasePatternNotation())
                                   .fromString(config);
                newpat = p.asJMLPattern();
                setBasePattern(getBasePatternNotation(), config);
                setBasePatternEdited(false);
            } else if (rb_jml.isSelected()) {
                newpat = new JMLPattern(new StringReader(ta.getText()));
                setBasePatternEdited(true);
            }

            if (newpat != null) {
                ja.restartJuggle(newpat, null);
                parent.setTitle(newpat.getTitle());
                reloadTextArea();
            }
        } catch (JuggleExceptionUser jeu) {
            lab.setText(jeu.getMessage());
            setTextEdited(true);
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleFatalException(jei);
            setTextEdited(true);
        } catch (IOException ioe) {
            ErrorDialog.handleFatalException(ioe);
            setTextEdited(true);
        }
    }

    protected void revertPattern() {
        if (text_edited) {
            try {
                reloadTextArea();
            } catch (IOException ioe) {
                lab.setText(ioe.getMessage());
            }
        }
    }

    protected void reloadTextArea() throws IOException {
        if (rb_bp.isSelected()) {
            ta.setText(getBasePatternConfig().replace(';', '\n'));
            setTextEdited(getBasePatternEdited());
        } else if (rb_jml.isSelected()) {
            StringWriter sw = new StringWriter();
            ja.getPattern().writeJML(sw, true);
            sw.close();
            ta.setText(sw.toString());
            setTextEdited(false);
        }
        ta.setCaretPosition(0);
        lab.setText("");
    }

    protected void setTextEdited(boolean edited) {
        text_edited = edited;
        compile.setEnabled(text_edited);
        revert.setEnabled(text_edited);
    }

    // javax.swing.event.DocumentListener method overrides

    @Override
    public void insertUpdate(DocumentEvent e) { setTextEdited(true); }

    @Override
    public void removeUpdate(DocumentEvent e) { setTextEdited(true); }

    @Override
    public void changedUpdate(DocumentEvent e) { setTextEdited(true); }
}
