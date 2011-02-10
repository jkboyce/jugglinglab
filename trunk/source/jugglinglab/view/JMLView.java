// JMLView.java
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


public class JMLView extends View {
    protected boolean isdirty = false;

    protected Animator ja = null;
    protected JTextArea ta = null;
    protected JButton compile = null;
    protected JButton revert = null;
    //	protected JLabel dirty = null;
    protected JLabel lab = null;

    public JMLView(Dimension dim) {
        this.setLayout(new BorderLayout());
        
        this.ja = new Animator();
        //ja.setPreferredSize(dim);
        ja.setJAPreferredSize(dim);
        //ja.setMinimumSize(dim);

        this.ta = new JTextArea();
        //		ta.setPreferredSize(new Dimension(400,1));
        ChangeListener myListener = new ChangeListener();
        this.ta.getDocument().addDocumentListener(myListener);

        JScrollPane jscroll = new JScrollPane(ta);
        jscroll.setPreferredSize(new Dimension(400,1));
        if (true /*PlatformSpecific.getPlatformSpecific().isMacOS()*/) {
            jscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            jscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        }

        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ja, jscroll);
        this.add(jsp, BorderLayout.CENTER);

        JPanel lower = new JPanel();
        lower.setLayout(new FlowLayout(FlowLayout.LEADING));
        this.compile = new JButton(guistrings.getString("JMLView_compile_button"));
        compile.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                try {
                    JMLView.this.compilePattern();
                } catch (Exception e) {
                    jugglinglab.util.ErrorDialog.handleException(e);
                }
            }
        });
        lower.add(compile);
        this.revert = new JButton(guistrings.getString("JMLView_revert_button"));
        revert.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                try {
                    JMLView.this.revertPattern();
                } catch (Exception e) {
                    jugglinglab.util.ErrorDialog.handleException(e);
                }
            }
        });
        lower.add(revert);

        /*		java.net.URL url = this.getClass().getResource("/images/ball.gif");
        if (url != null) {
            ImageIcon aboutPicture = new ImageIcon(url);
            if (aboutPicture != null)
                this.dirty = new JLabel(aboutPicture);
        }
        lower.add(dirty);*/

        this.lab = new JLabel("");
        lower.add(lab);

        this.add(lower, BorderLayout.PAGE_END);
    }

    public void restartView() {
        try {
            ja.restartJuggle();
        } catch (JuggleException je) {
            lab.setText(je.getMessage());
        }
    }

    public void restartView(JMLPattern p, AnimatorPrefs c) {
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

    public Dimension getAnimatorSize() {
        return ja.getSize(new Dimension());
    }

    public void dispose() {
        ja.dispose();
    }

	public JMLPattern getPattern() { return ja.getPattern(); }
	
    public boolean getPaused() { return ja.getPaused(); }

    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
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
                ErrorDialog.handleException(jei);
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
                ErrorDialog.handleException(ioe);
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
        //		this.dirty.setVisible(dirty);
        this.compile.setEnabled(dirty);
        this.revert.setEnabled(dirty);
    }

    class ChangeListener implements DocumentListener {
        public void insertUpdate(DocumentEvent e) {
            JMLView.this.setDirty(true);
        }
        public void removeUpdate(DocumentEvent e) {
            JMLView.this.setDirty(true);
        }
        public void changedUpdate(DocumentEvent e) {
            JMLView.this.setDirty(true);
        }
    }
}
