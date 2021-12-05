// PatternView.java
//
// Copyright 2021 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.view;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.event.*;
import org.xml.sax.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class PatternView extends View implements DocumentListener {
    protected AnimationPanel ja;
    protected JSplitPane jsp;
    protected JRadioButton rb_bp;
    protected JLabel bp_edited;
    protected JRadioButton rb_jml;
    protected JTextArea ta;
    protected JButton compile;
    protected JButton revert;
    //  protected JLabel edited;
    protected JLabel lab;
    protected boolean isedited = false;


    public PatternView(Dimension dim) {
        makePanel(dim);
        updatePanel();
    }

    protected void makePanel(Dimension dim) {
        setLayout(new BorderLayout());

        ja = new AnimationPanel();
        ja.setAnimationPanelPreferredSize(dim);

        JPanel controls = new JPanel();
        GridBagLayout gb = new GridBagLayout();
        controls.setLayout(gb);

        JLabel lab_view = new JLabel("Select view:");
        gb.setConstraints(lab_view, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0,
                          new Insets(15, 0, 10, 0)));
        controls.add(lab_view);

        ButtonGroup bg = new ButtonGroup();
        JPanel bppanel = new JPanel();
        bppanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rb_bp = new JRadioButton("Base Pattern");
        bg.add(rb_bp);
        bppanel.add(rb_bp);
        java.net.URL url = PatternView.class.getResource("/alert.png");
        if (url != null) {
            ImageIcon edited_icon = new ImageIcon(url);
            if (edited_icon != null) {
                ImageIcon edited_icon_scaled = new ImageIcon(edited_icon.getImage().getScaledInstance(22, 22,  java.awt.Image.SCALE_SMOOTH));
                bp_edited = new JLabel(edited_icon_scaled);
                bp_edited.setToolTipText("Note: Applied JML edits will be lost if base pattern is changed");
                bppanel.add(Box.createHorizontalStrut(10));
                bppanel.add(bp_edited);
            }
        }
        controls.add(bppanel);
        gb.setConstraints(bppanel, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1));

        rb_jml = new JRadioButton("JML");
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

    // This is called whenever the base pattern or JML pattern changes.
    protected void updatePanel() {
        if (getBasePatternNotation() == null || getBasePatternConfig() == null ||
                getBasePatternNotation().equalsIgnoreCase("JML")) {
            // no non-JML base pattern has been set
            rb_jml.setSelected(true);
            rb_bp.setEnabled(false);
            if (bp_edited != null)
                bp_edited.setVisible(false);
        } else {
            rb_bp.setEnabled(true);
            if (bp_edited != null)
                bp_edited.setVisible(getBasePatternEdited());
        }
    }

    @Override
    public void setBasePattern(String bpn, String bpc) throws JuggleExceptionUser {
        super.setBasePattern(bpn, bpc);
        if (!bpn.equalsIgnoreCase("JML"))
            rb_bp.setText("Base Pattern (" + bpn + ")");
        updatePanel();
    }

    @Override
    public void setBasePatternEdited(boolean bpe) {
        super.setBasePatternEdited(bpe);
        updatePanel();
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
            setEdited(true);
        } catch (IOException ioe) {
            lab.setText(ioe.getMessage());
            setEdited(true);
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
        if (!isedited)
            return;

        try {
            JMLPattern newpat = new JMLPattern(new StringReader(ta.getText()));
            ja.restartJuggle(newpat, null);
            parent.setTitle(newpat.getTitle());
            reloadTextArea();
            setBasePatternEdited(true);
        } catch (JuggleExceptionUser jeu) {
            lab.setText(jeu.getMessage());
            setEdited(true);
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleFatalException(jei);
            setEdited(true);
        } catch (SAXParseException spe) {
            String template = errorstrings.getString("Error_parsing");
            Object[] arguments = { new Integer(spe.getLineNumber()) };
            lab.setText(MessageFormat.format(template, arguments));
            setEdited(true);
        } catch (SAXException se) {
            lab.setText(se.getMessage());
            setEdited(true);
        } catch (IOException ioe) {
            ErrorDialog.handleFatalException(ioe);
            setEdited(true);
        }
    }

    protected void revertPattern() {
        if (isedited) {
            try {
                reloadTextArea();
            } catch (IOException ioe) {
                lab.setText(ioe.getMessage());
            }
        }
    }

    protected void reloadTextArea() throws IOException {
        if (rb_bp.isSelected()) {
            ta.setText("pattern goes here");
            ta.setCaretPosition(0);
            lab.setText("");
            setEdited(false);
        } else if (rb_jml.isSelected()) {
            StringWriter sw = new StringWriter();
            ja.getPattern().writeJML(sw, true);
            sw.close();
            ta.setText(sw.toString());
            ta.setCaretPosition(0);
            lab.setText("");
            setEdited(false);
        }
    }

    protected void setEdited(boolean edited) {
        this.isedited = edited;
        //      this.edited.setVisible(edited);
        compile.setEnabled(edited);
        revert.setEnabled(edited);
    }

    // DocumentListener methods

    @Override
    public void insertUpdate(DocumentEvent e) {
        setEdited(true);
    }
    @Override
    public void removeUpdate(DocumentEvent e) {
        setEdited(true);
    }
    @Override
    public void changedUpdate(DocumentEvent e) {
        setEdited(true);
    }
}
