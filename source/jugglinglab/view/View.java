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

import java.awt.Component;
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


public abstract class View extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected JFrame parent;

    public void setParent(JFrame p) { this.parent = p; }

    public abstract void restartView() throws JuggleExceptionUser, JuggleExceptionInternal;

    // In the following, a null argument means no update for that item
    public abstract void restartView(JMLPattern p, AnimationPrefs c) throws
                            JuggleExceptionUser, JuggleExceptionInternal;

    public abstract void setAnimationPanelPreferredSize(Dimension d);

    public abstract Dimension getAnimationPanelSize();

    public abstract void disposeView();

    public abstract JMLPattern getPattern();

    public abstract AnimationPrefs getAnimationPrefs();

    public abstract boolean getPaused();

    public abstract void setPaused(boolean pause);

    // Called in PatternWindow when the user selects the "Save as Animated GIF..."
    // menu option. This does all of the processing in a thread separate from
    // the main event loop.
    public abstract void writeGIF();


    // Utility class for the various View subclasses to use for writing GIFs
    protected class GIFWriter extends Thread {
        private Component parent;
        private AnimationPanel ap;
        private Runnable cleanup;

        public GIFWriter(Component parent, AnimationPanel ap, Runnable cleanup) {
            this.parent = parent;
            this.ap = ap;
            this.cleanup = cleanup;
            this.setPriority(Thread.MIN_PRIORITY);
            this.start();
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
                                    return pm.isCanceled();
                                }
                            };
                            ap.getAnimator().writeGIF(out, wgm);
                        }
                    }
                } catch (IOException ioe) {
                    throw new JuggleExceptionUser("Problem writing to file: " + file.toString());
                }
            } catch (JuggleExceptionUser jeu) {
                new ErrorDialog(GIFWriter.this.parent, jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleFatalException(jei);
            }

            if (cleanup != null)
                SwingUtilities.invokeLater(cleanup);
        }
    }

}
