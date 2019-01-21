// View.java
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

package jugglinglab.view;

import java.awt.Dimension;
import java.io.*;
import java.util.ResourceBundle;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import jugglinglab.core.*;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;


// This class represents the entire displayed contents of a PatternWindow.
// Subclasses of this are used to show different pattern views, which the
// user can select from a menu on the pattern window.

public abstract class View extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected JFrame parent;

    public void setParent(JFrame p) { this.parent = p; }

    // null argument means no update for that item:
    public abstract void restartView(JMLPattern p, AnimationPrefs c) throws
                            JuggleExceptionUser, JuggleExceptionInternal;

    // restart without changing pattern or preferences
    public abstract void restartView() throws JuggleExceptionUser,
                            JuggleExceptionInternal;

    // size of just the juggler animation, not any extra elements
    public abstract Dimension getAnimationPanelSize();

    public abstract void setAnimationPanelPreferredSize(Dimension d);

    public abstract JMLPattern getPattern();

    public abstract AnimationPrefs getAnimationPrefs();

    public abstract boolean getPaused();

    public abstract void setPaused(boolean pause);

    public abstract void disposeView();

    public abstract void writeGIF();


    // Utility class for the various View subclasses to use for writing GIFs.
    // This does the processing in a thread separate from the EDT.
    protected class GIFWriter extends Thread {
        private AnimationPanel ap;
        private Runnable cleanup;

        public GIFWriter(AnimationPanel ap, Runnable cleanup) {
            this.ap = ap;
            this.cleanup = cleanup;
            setPriority(Thread.MIN_PRIORITY);
            start();
        }

        @Override
        public void run() {
            try {
                File file = null;
                try {
                    int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(parent);

                    if (option == JFileChooser.APPROVE_OPTION) {
                        file = PlatformSpecific.getPlatformSpecific().getSelectedFile();
                        if (file != null) {
                            FileOutputStream out = new FileOutputStream(file);

                            ProgressMonitor pm = new ProgressMonitor(parent,
                                    guistrings.getString("Saving_animated_GIF"), "", 0, 1);
                            pm.setMillisToPopup(1000);

                            Animator.WriteGIFMonitor wgm = new Animator.WriteGIFMonitor() {
                                @Override
                                public void update(int step, int steps_total) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            pm.setMaximum(steps_total);
                                            pm.setProgress(step);
                                        }
                                    });
                                }

                                @Override
                                public boolean isCanceled() {
                                    return (pm.isCanceled() || GIFWriter.this.interrupted());
                                }
                            };
                            ap.getAnimator().writeGIF(out, wgm);
                        }
                    }
                } catch (IOException ioe) {
                    throw new JuggleExceptionUser("Problem writing to file: " + file.toString());
                }
            } catch (JuggleExceptionUser jeu) {
                new ErrorDialog(parent, jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleFatalException(jei);
            }

            if (cleanup != null)
                SwingUtilities.invokeLater(cleanup);
        }
    }

}
