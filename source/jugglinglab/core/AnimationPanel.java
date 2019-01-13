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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JPanel;

import jugglinglab.jml.*;
import jugglinglab.renderer.Renderer2D;
import jugglinglab.util.*;


public class AnimationPanel extends JPanel implements Runnable {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected static final double snapangle = JLMath.toRad(15.0);

    protected Animator          anim;
    protected AnimationPrefs    jc;

    protected Thread            engine;
    protected boolean           engineStarted = false;
    protected boolean           enginePaused = false;
    protected boolean           engineRunning = false;
    protected double            sim_time;
    public boolean              writingGIF = false;
    public JuggleException      exception;
    public String               message;

    protected Clip              catchclip;
    protected Clip              bounceclip;

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
        this.loadAudioClips();
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

    protected void loadAudioClips() {
        try {
            URL catchurl = AnimationPanel.class.getResource("/catch.au");
            AudioInputStream catchAudioIn = AudioSystem.getAudioInputStream(catchurl);
            this.catchclip = AudioSystem.getClip();
            this.catchclip.open(catchAudioIn);
        } catch (Exception e) {}
        try {
            URL bounceurl = AnimationPanel.class.getResource("/bounce.au");
            AudioInputStream bounceAudioIn = AudioSystem.getAudioInputStream(bounceurl);
            this.bounceclip = AudioSystem.getClip();
            this.bounceclip.open(bounceAudioIn);
        } catch (Exception e) {}
    }

    protected void initHandlers() {
        this.addMouseListener(new MouseAdapter() {
            long lastpress = 0L;
            long lastenter = 1L;

            @Override
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

            @Override
            public void mouseReleased(MouseEvent me) {
                if (jc.mousePause && lastpress == lastenter)
                    return;
                if (exception != null)
                    return;
                AnimationPanel.this.cameradrag = false;

                if (!engineStarted && engine != null && engine.isAlive()) {
                    setPaused(!enginePaused);
                    return;
                }
                if (me.getX() == startx && me.getY() == starty &&
                                engine != null && engine.isAlive()) {
                    setPaused(!enginePaused);
                    AnimationPanel.this.getParent().dispatchEvent(me);
                }
                if (AnimationPanel.this.getPaused())
                    repaint();
            }

            @Override
            public void mouseEntered(MouseEvent me) {
                lastenter = me.getWhen();
                if (jc.mousePause)
                    setPaused(waspaused);
                outside = false;
                outside_valid = true;
            }

            @Override
            public void mouseExited(MouseEvent me) {
                if (jc.mousePause) {
                    waspaused = getPaused();
                    setPaused(true);
                }
                outside = true;
                outside_valid = true;
            }
        });

        this.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent me) {
                if (exception != null)
                    return;
                if (!engineStarted)
                    return;
                if (!cameradrag) {
                    AnimationPanel.this.cameradrag = true;
                    AnimationPanel.this.lastx = AnimationPanel.this.startx;
                    AnimationPanel.this.lasty = AnimationPanel.this.starty;
                    AnimationPanel.this.dragcamangle = AnimationPanel.this.getCameraAngle();
                }

                int xdelta = me.getX() - AnimationPanel.this.lastx;
                int ydelta = me.getY() - AnimationPanel.this.lasty;
                AnimationPanel.this.lastx = me.getX();
                AnimationPanel.this.lasty = me.getY();
                double[] ca = AnimationPanel.this.dragcamangle;
                ca[0] += (double)(xdelta) * 0.02;
                ca[1] -= (double)(ydelta) * 0.02;
                if (ca[1] < 0.0001)
                    ca[1] = 0.0001;
                if (ca[1] > JLMath.toRad(90.0))
                    ca[1] = JLMath.toRad(90.0);
                while (ca[0] < 0.0)
                    ca[0] += JLMath.toRad(360.0);
                while (ca[0] >= JLMath.toRad(360.0))
                    ca[0] -= JLMath.toRad(360.0);

                double[] snappedcamangle = snapCamera(ca);
                AnimationPanel.this.setCameraAngle(snappedcamangle);

                // send event to the parent so that SelectionView can update
                // camera angles of other animations
                AnimationPanel.this.getParent().dispatchEvent(me);

                if (AnimationPanel.this.getPaused())
                    repaint();
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            @Override
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
        if (result[1] > (JLMath.toRad(90.0) - snapangle))
            result[1] = JLMath.toRad(90.0);

        if (anim.pat.getNumberOfJugglers() == 1) {
            double a = JLMath.toRad(anim.pat.getJugglerAngle(1, getTime()));

            if (anglediff(a - result[0]) < snapangle)
                result[0] = a;
            else if (anglediff(a + 90.0*0.0174532925194 - result[0]) < snapangle)
                result[0] = a + 90.0*0.0174532925194;
            else if (anglediff(a + 180.0*0.0174532925194 - result[0]) < snapangle)
                result[0] = a + 180.0*0.0174532925194;
            else if (anglediff(a + 270.0*0.0174532925194 - result[0]) < snapangle)
                result[0] = a + 270.0*0.0174532925194;
        }
        return result;
    }

    protected double anglediff(double delta) {
        while (delta > JLMath.toRad(180.0))
            delta -= JLMath.toRad(360.0);
        while (delta <= JLMath.toRad(-180.0))
            delta += JLMath.toRad(360.0);
        return Math.abs(delta);
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
        long real_time_start, real_time_wait;

        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        engineStarted = false;

        if (jc.mousePause)
            waspaused = jc.startPause;

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

            real_time_start = System.currentTimeMillis();
            double oldtime, newtime;

            if (jc.mousePause) {
                if (outside_valid)
                    setPaused(outside);
                else
                    setPaused(true);    // assume mouse is outside animator, if not known
                waspaused = false;
            }

            while (engineRunning)  {
                setTime(anim.pat.getLoopStartTime());
                engineStarted = true;

                for (int i = 0; engineRunning && getTime() < (anim.pat.getLoopEndTime() -
                                    0.5 * anim.sim_interval_secs); i++) {
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

                    if (jc.catchSound && catchclip != null) {
                        for (int path = 1; path <= anim.pat.getNumberOfPaths(); path++) {
                            if (anim.pat.getPathCatchVolume(path, oldtime, newtime) > 0.0) {
                                if (catchclip.isRunning())
                                    catchclip.stop();
                                catchclip.setFramePosition(0);
                                catchclip.start();
                            }
                        }
                    }
                    if (jc.bounceSound && bounceclip != null) {
                        for (int path = 1; path <= anim.pat.getNumberOfPaths(); path++) {
                            if (anim.pat.getPathBounceVolume(path, oldtime, newtime) > 0.0) {
                                if (bounceclip.isRunning())
                                    bounceclip.stop();
                                bounceclip.setFramePosition(0);
                                bounceclip.start();
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
            // this is critical to signal killAnimationThread() that exit is occurring:
            engine = null;
        }
        synchronized (this) {
            // tell possible thread waiting in killAnimationThread() that animator
            // thread is exiting:
            notify();
        }
    }

    // stop the current animation thread, if one is running
    protected synchronized void killAnimationThread() {
        while (engine != null && engine.isAlive()) {
            setPaused(false);       // get thread out of pause so it can exit
            engineRunning = false;
            try {
                wait();             // wait for notify() from exiting run() method
            } catch (InterruptedException ie) {
            }
        }

        engine = null;
        engineStarted = false;
        enginePaused = false;
        engineRunning = false;
        exception = null;
        message = null;
    }

    public boolean getPaused()              { return enginePaused; }

    public synchronized void setPaused(boolean wanttopause) {
        if (enginePaused == true && wanttopause == false)
            notify();       // wake up wait() in run() method
        enginePaused = wanttopause;
    }

    public double getTime()                 { return sim_time; };
    public void setTime(double time)        { sim_time = time; }

    public double[] getCameraAngle()        { return anim.getCameraAngle(); }
    public void setCameraAngle(double[] ca) { anim.setCameraAngle(ca); }

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
                killAnimationThread();
                System.out.println(jei.getMessage());
                System.exit(0);
                // ErrorDialog.handleFatalException(jei);
            }
        }
    }

    protected void drawString(String message, Graphics g) {
        FontMetrics fm = g.getFontMetrics();
        int message_width = fm.stringWidth(message);

        Dimension dim = this.getSize();
        int x = (dim.width > message_width) ? (dim.width - message_width)/2 : 0;
        int y = (dim.height + fm.getHeight()) / 2;

        g.setColor(Color.white);
        g.fillRect(0, 0, dim.width, dim.height);
        g.setColor(Color.black);
        g.drawString(message, x, y);
    }

    public JMLPattern getPattern()              { return anim.pat; }
    public Animator getAnimator()               { return anim; }
    public AnimationPrefs getAnimationPrefs()   { return jc; }
    public void disposeAnimation()              { killAnimationThread(); }
}
