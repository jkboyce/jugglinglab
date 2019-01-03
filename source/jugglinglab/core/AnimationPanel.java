// AnimationPanel.java
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

package jugglinglab.core;

import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import jugglinglab.jml.*;
import jugglinglab.renderer.Renderer2D;
import jugglinglab.util.*;


public class AnimationPanel extends JPanel implements Runnable {
    static ResourceBundle guistrings;
    // static ResourceBundle errorstrings;
    static AudioClip catchclip;
    static AudioClip bounceclip;
    protected static final double snapangle = JLMath.toRad(15.0);

    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        // errorstrings = JLLocale.getBundle("ErrorStrings");

        // load audio resources
        URL catchurl = AnimationPanel.class.getResource("/catch.au");
        if (catchurl != null)
            AnimationPanel.catchclip = Applet.newAudioClip(catchurl);
        URL bounceurl = AnimationPanel.class.getResource("/bounce.au");
        if (bounceurl != null)
            AnimationPanel.bounceclip = Applet.newAudioClip(bounceurl);
    }

    protected Animator          anim;
    protected AnimationPrefs    jc;

    protected Thread            engine;
    protected boolean           engineStarted = false;;
    protected boolean           enginePaused = false;
    protected boolean           engineRunning = false;
    protected double            sim_time;
    public boolean              writingGIF = false;
    public JuggleException      exception;
    public String               message;

    protected boolean           waspaused = false;      // for pause on mouse away
    protected boolean           outside = true;
    protected boolean           outside_valid = false;

    protected boolean           cameradrag = false;
    protected int               startx, starty, lastx, lasty;
    protected double[]          dragcamangle;

    protected Dimension         prefsize;


    public AnimationPanel() {
        this.anim = new Animator();
        this.jc = new AnimationPrefs();
        this.setOpaque(true);
        this.initHandlers();
    }

    public void setAnimationPanelPreferredSize(Dimension d) {
        prefsize = d;
    }

    // override methods in java.awt.Component
    @Override
    public Dimension getPreferredSize() {
        if (prefsize != null)
            return new Dimension(prefsize);
        return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(10, 10);
    }

    protected void initHandlers() {
        this.addMouseListener(new MouseAdapter() {
            long lastpress = 0L;
            long lastenter = 1L;

            public void mousePressed(MouseEvent me) {
                lastpress = me.getWhen();

                // The following (and the equivalent in mouseReleased()) is a hack to swallow
                // a mouseclick when the browser stops reporting enter/exit events because the
                // user has clicked on something else.  The system reports simultaneous enter/press
                // events when the user mouses down in the component; we want to swallow this as a
                // click, and just use it to get focus back.
                if (jc.mousePause && (lastpress == lastenter))
                    return;

                if (exception != null)
                    return;
                if (!engineStarted)
                    return;

                AnimationPanel.this.startx = me.getX();
                AnimationPanel.this.starty = me.getY();
            }

            public void mouseReleased(MouseEvent me) {
                if (jc.mousePause && (lastpress == lastenter))
                    return;
                if (exception != null)
                    return;
                AnimationPanel.this.cameradrag = false;

                if (!engineStarted && (engine != null) && engine.isAlive()) {
                    setPaused(!enginePaused);
                    return;
                }
                if ((me.getX() == startx) && (me.getY() == starty) &&
                                (engine != null) && engine.isAlive()) {
                    setPaused(!enginePaused);
                    // AnimationPanel.this.getParent().dispatchEvent(me);
                }
                if (AnimationPanel.this.getPaused())
                    repaint();
            }

            public void mouseEntered(MouseEvent me) {
                lastenter = me.getWhen();
                if (jc.mousePause /*&& waspaused_valid*/)
                    setPaused(waspaused);
                outside = false;
                outside_valid = true;
            }

            public void mouseExited(MouseEvent me) {
                if (jc.mousePause) {
                    waspaused = getPaused();
                    // waspaused_valid = true;
                    setPaused(true);
                }
                outside = true;
                outside_valid = true;
            }
        });

        this.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent me) {
                if (exception != null)
                    return;
                if (!engineStarted)
                    return;
                if (!cameradrag) {
                    // return;
                    AnimationPanel.this.cameradrag = true;
                    AnimationPanel.this.lastx = AnimationPanel.this.startx;
                    AnimationPanel.this.lasty = AnimationPanel.this.starty;
                    AnimationPanel.this.dragcamangle = AnimationPanel.this.anim.getCameraAngle();
                }

                int xdelta = me.getX() - AnimationPanel.this.lastx;
                int ydelta = me.getY() - AnimationPanel.this.lasty;
                AnimationPanel.this.lastx = me.getX();
                AnimationPanel.this.lasty = me.getY();
                double[] ca = AnimationPanel.this.dragcamangle;
                ca[0] += (double)(xdelta) * 0.02;
                ca[1] -= (double)(ydelta) * 0.02;
                if (ca[1] < 0.000001)
                    ca[1] = 0.000001;
                if (ca[1] > JLMath.toRad(90.0))
                    ca[1] = JLMath.toRad(90.0);
                while (ca[0] < 0.0)
                    ca[0] += JLMath.toRad(360.0);
                while (ca[0] >= JLMath.toRad(360.0))
                    ca[0] -= JLMath.toRad(360.0);

                double[] snappedcamangle = snapCamera(ca);
                anim.setCameraAngle(snappedcamangle);

                if (AnimationPanel.this.getPaused())
                    repaint();
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (exception != null)
                    return;
                if (!engineStarted)
                    return;
                anim.setDimension(AnimationPanel.this.getSize());
                repaint();
            }
        });
    }

    protected double[] snapCamera(double[] ca) {
        double[] result = new double[2];

        result[0] = ca[0];
        result[1] = ca[1];

        if (result[1] < snapangle)
            result[1] = 0.000001;
        if ((result[1] > (JLMath.toRad(90.0) - snapangle)) /*&& (result[1] < (JLMath.toRad(90.0) + snapangle)) */)
            result[1] = JLMath.toRad(90.0);
        // if (result[1] > (JLMath.toRad(180.0) - snapangle))
        //  result[1] = JLMath.toRad(179.99999);
        return result;
    }

    public void restartJuggle(JMLPattern pat, AnimationPrefs newjc)
                    throws JuggleExceptionUser, JuggleExceptionInternal {

        // stop the current animation thread, if one is running
        killAnimationThread();

        if (newjc != null)
            this.jc = newjc;

        anim.setDimension(this.getSize());
        anim.restartAnimator(pat, newjc);

        this.setBackground(anim.getBackground());

        engine = new Thread(this);
        engine.start();
    }

    public void restartJuggle() throws JuggleExceptionUser, JuggleExceptionInternal {
        restartJuggle(null, null);
    }


    @Override
    public void run()  {        // Called when this object becomes a thread
        long    real_time_start, real_time_wait;

        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        engineStarted = false;

        if (jc.mousePause) {
            waspaused = jc.startPause;
            // waspaused_valid = outside_valid;
        }

        try {
            engineRunning = true;   // ok to start rendering

            if (jc.startPause) {
                message = guistrings.getString("Message_click_to_start");
                repaint();
                enginePaused = true;
                while (enginePaused && engineRunning) {
                    synchronized (this) {
                        try {
                            wait();
                        } catch (InterruptedException ie) {
                        }
                    }
                }
            }

            message = null;
            // setCameraAngle(camangle);

            real_time_start = System.currentTimeMillis();
            double oldtime, newtime;

            if (jc.mousePause) {
                if (outside_valid)
                    setPaused(outside);
                else
                    setPaused(true);    // assume mouse is outside animator, if not known
                waspaused = false;
                // waspaused_valid = true;
            }

            while (engineRunning)  {
                setTime(anim.pat.getLoopStartTime());
                engineStarted = true;

                for (int i = 0; engineRunning &&
                                (getTime() < (anim.pat.getLoopEndTime() - 0.5*anim.sim_interval_secs)); i++) {
                    repaint();
                    try {
                        real_time_wait = anim.real_interval_millis -
                                    (System.currentTimeMillis() - real_time_start);
                        if (real_time_wait > 0)
                            Thread.sleep(real_time_wait);
                        real_time_start = System.currentTimeMillis();
                    } catch (InterruptedException ie)  {
                        // What should we do here?
                        throw new JuggleExceptionInternal("Animator was interrupted");
                    }

                    while (enginePaused && engineRunning) {
                        synchronized (this) {
                            try {
                                wait();
                            } catch (InterruptedException ie) {
                            }
                        }
                    }

                    oldtime = getTime();
                    setTime(getTime() + anim.sim_interval_secs);
                    newtime = getTime();

                    if (jc.catchSound && (catchclip != null)) {
                        for (int path = 1; path <= anim.pat.getNumberOfPaths(); path++) {
                            if (anim.pat.getPathCatchVolume(path, oldtime, newtime) > 0.0) {
                                // System.out.println("Caught path "+path);
                                catchclip.play();
                            }
                        }
                    }
                    if (jc.bounceSound && (bounceclip != null)) {
                        for (int path = 1; path <= anim.pat.getNumberOfPaths(); path++) {
                            if (anim.pat.getPathBounceVolume(path, oldtime, newtime) > 0.0) {
                                // System.out.println("Caught path "+path);
                                bounceclip.play();
                            }
                        }
                    }
                }
                anim.advanceProps();
            }
        } catch (JuggleException je) {
            exception = je;
            repaint();
        } finally {
            engineStarted = engineRunning = enginePaused = false;
            engine = null;  // this is critical as it signals restartJuggle() that exit is occurring
        }
        synchronized (this) {
            notify();   // tell possible thread wait()ing in restartJuggle() that animator thread is exiting
        }
    }

    // stop the current animation thread, if one is running
    protected synchronized void killAnimationThread() {
        while ((engine != null) && engine.isAlive()) {
            setPaused(false);       // get thread out of pause so it can exit
            engineRunning = false;
            try {
                wait();             // wait for notify() from exiting run() method
            } catch (InterruptedException ie) {
            }
        }

        engine = null;          // just in case animator doesn't initialize these
        engineStarted = false;
        enginePaused = false;
        engineRunning = false;
        exception = null;
        message = null;
    }

    public boolean getPaused() { return enginePaused; }

    public synchronized void setPaused(boolean wanttopause) {
        if ((enginePaused == true) && (wanttopause == false)) {
            notify();       // wake up wait() in run() method
        }
        enginePaused = wanttopause;
    }

    public double getTime() { return sim_time; };

    public void setTime(double time) {
        /*      while (time < pat.getLoopStartTime())
        time += (pat.getLoopEndTime() - pat.getLoopStartTime());
        while (time > pat.getLoopEndTime())
        time -= (pat.getLoopEndTime() - pat.getLoopStartTime());
        */
        sim_time = time;
    }

    @Override
    public void paintComponent(Graphics g) {
        if (exception != null)
            drawString(exception.getMessage(), g);
        else if (message != null)
            drawString(message, g);
        else if (engineRunning && !writingGIF) {
            try {
                anim.drawFrame(getTime(), g, this.cameradrag);
            } catch (JuggleExceptionInternal jei) {
                this.killAnimationThread();
                System.out.println(jei.getMessage());
                System.exit(0);
                // ErrorDialog.handleFatalException(jei);
            }
        }
    }

    protected void drawString(String message, Graphics g) {
        int x, y, width;
        Dimension appdim = this.getSize();
        int appWidth = appdim.width;
        int appHeight = appdim.height;
        FontMetrics fm = g.getFontMetrics();

        width = fm.stringWidth(message);
        x = (appWidth > width) ? (appWidth-width)/2 : 0;
        y = (appHeight + fm.getHeight()) / 2;
        g.setColor(Color.white);
        g.fillRect(0, 0, appWidth, appHeight);
        g.setColor(Color.black);
        g.drawString(message, x, y);
    }

    public JMLPattern getPattern() { return anim.pat; }

    public AnimationPrefs getAnimationPrefs() { return jc; }

    public void dispose() { killAnimationThread(); }


    // Called in View.java when the user selects the "Save as Animated GIF..."
    // menu option.
    //
    // This does all of the processing in a thread separate from the main event
    // loop. This may be overkill since the processing is usually pretty quick.
    public void writeGIF() {
        this.writingGIF = true;
        boolean origpause = this.getPaused();
        this.setPaused(true);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                AnimationPanel.this.setPaused(origpause);
                AnimationPanel.this.writingGIF = false;
            }
        };

        new GIFWriter(this, cleanup);
    }

    private class GIFWriter extends Thread {
        private Component parent = null;
        private Runnable cleanup = null;

        public GIFWriter(Component parent, Runnable cleanup) {
            this.parent = parent;
            this.cleanup = cleanup;
            this.setPriority(Thread.MIN_PRIORITY);
            this.start();
        }

        @Override
        public void run() {
            try {
                try {
                    int option = PlatformSpecific.getPlatformSpecific().showSaveDialog(null);

                    if (option == JFileChooser.APPROVE_OPTION) {
                        if (PlatformSpecific.getPlatformSpecific().getSelectedFile() != null) {
                            File file = PlatformSpecific.getPlatformSpecific().getSelectedFile();
                            FileOutputStream out = new FileOutputStream(file);

                            ProgressMonitor pm = new ProgressMonitor(parent,
                                    AnimationPanel.guistrings.getString("Saving_animated_GIF"), "", 0, 1);
                            pm.setMillisToPopup(1000);

                            Animator.WriteGIFMonitor wgm = new Animator.WriteGIFMonitor() {
                                @Override
                                public void update(int step, int steps_total) {
                                    pm.setMaximum(steps_total);
                                    pm.setProgress(step);
                                }

                                @Override
                                public boolean isCanceled() {
                                    return pm.isCanceled();
                                }
                            };
                            anim.writeGIF(out, wgm);
                        }
                    }
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionInternal("AnimGIFSave file not found: "+fnfe.getMessage());
                } catch (IOException ioe) {
                    throw new JuggleExceptionInternal("AnimGIFSave IOException: "+ioe.getMessage());
                } catch (IllegalArgumentException iae) {
                    throw new JuggleExceptionInternal("AnimGIFSave IllegalArgumentException: "+iae.getMessage());
                }
            } catch (Exception e) {
                ErrorDialog.handleFatalException(e);
            }

            if (cleanup != null)
                SwingUtilities.invokeLater(cleanup);
        }
    }


}
