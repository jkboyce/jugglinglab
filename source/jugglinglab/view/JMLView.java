// JMLView.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

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


public class JMLView extends View implements DocumentListener {
    protected AnimationPanel ja;
    protected JSplitPane jsp;
    protected JTextArea ta;
    protected JButton compile;
    protected JButton revert;
    //  protected JLabel dirty;
    protected JLabel lab;

    protected boolean isdirty = false;


    public JMLView(Dimension dim) {
        setLayout(new BorderLayout());

        this.ja = new AnimationPanel();
        ja.setAnimationPanelPreferredSize(dim);

        this.ta = new JTextArea();
        ta.getDocument().addDocumentListener(this);

        JScrollPane jscroll = new JScrollPane(ta);
        jscroll.setPreferredSize(new Dimension(400, 1));
        if (true /*PlatformSpecific.getPlatformSpecific().isMacOS()*/) {
            jscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            jscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        }

        jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ja, jscroll);
        this.add(jsp, BorderLayout.CENTER);

        JPanel lower = new JPanel();
        lower.setLayout(new FlowLayout(FlowLayout.LEADING));

        this.compile = new JButton(guistrings.getString("JMLView_compile_button"));
        compile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    JMLView.this.compilePattern();
                } catch (Exception e) {
                    ErrorDialog.handleFatalException(e);
                }
            }
        });
        lower.add(compile);

        this.revert = new JButton(guistrings.getString("JMLView_revert_button"));
        revert.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    JMLView.this.revertPattern();
                } catch (Exception e) {
                    ErrorDialog.handleFatalException(e);
                }
            }
        });
        lower.add(revert);

        /*      java.net.URL url = this.getClass().getResource("/images/ball.gif");
        if (url != null) {
            ImageIcon aboutPicture = new ImageIcon(url);
            if (aboutPicture != null)
                this.dirty = new JLabel(aboutPicture);
        }
        lower.add(dirty);*/

        this.lab = new JLabel("");
        lower.add(lab);

        add(lower, BorderLayout.PAGE_END);
    }

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) {
        try {
            ja.restartJuggle(p, c);
            updateTextArea();
            lab.setText("");
            setDirty(false);
            if (p != null)
                parent.setTitle(p.getTitle());
        } catch (JuggleException je) {
            lab.setText(je.getMessage());
            setDirty(true);
        } catch (IOException ioe) {
            lab.setText(ioe.getMessage());
            setDirty(true);
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
    public JMLPattern getPattern()              { return ja.getPattern(); }

    @Override
    public AnimationPrefs getAnimationPrefs()   { return ja.getAnimationPrefs(); }

    @Override
    public boolean getPaused()                  { return ja.getPaused(); }

    @Override
    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
    }

    @Override
    public void disposeView()                   { ja.disposeAnimation(); }

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
        if (isdirty) {
            try {
                JMLPattern newpat = new JMLPattern(new StringReader(ta.getText()));
                ja.restartJuggle(newpat, null);
                lab.setText("");
                parent.setTitle(newpat.getTitle());
                updateTextArea();
                setDirty(false);
            } catch (JuggleExceptionUser jeu) {
                lab.setText(jeu.getMessage());
                setDirty(true);
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleFatalException(jei);
                setDirty(true);
            } catch (SAXParseException spe) {
                String template = errorstrings.getString("Error_parsing");
                Object[] arguments = { new Integer(spe.getLineNumber()) };
                lab.setText(MessageFormat.format(template, arguments));
                setDirty(true);
            } catch (SAXException se) {
                lab.setText(se.getMessage());
                setDirty(true);
            } catch (IOException ioe) {
                ErrorDialog.handleFatalException(ioe);
                setDirty(true);
            }
        }
    }

    protected void revertPattern() {
        if (isdirty) {
            try {
                updateTextArea();
                lab.setText("");
                setDirty(false);
            } catch (IOException ioe) {
                lab.setText(ioe.getMessage());
            }
        }
    }

    protected void updateTextArea() throws IOException {
        StringWriter sw = new StringWriter();
        ja.getPattern().writeJML(sw, true);
        sw.close();
        ta.setText(sw.toString());
        ta.setCaretPosition(0);
    }

    protected void setDirty(boolean dirty) {
        this.isdirty = dirty;
        //      this.dirty.setVisible(dirty);
        compile.setEnabled(dirty);
        revert.setEnabled(dirty);
    }

    // DocumentListener methods

    @Override
    public void insertUpdate(DocumentEvent e) {
        setDirty(true);
    }
    @Override
    public void removeUpdate(DocumentEvent e) {
        setDirty(true);
    }
    @Override
    public void changedUpdate(DocumentEvent e) {
        setDirty(true);
    }
}
