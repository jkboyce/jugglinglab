// AnimationPanel.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import jugglinglab.core.PatternWindow;
import jugglinglab.jml.*;
import jugglinglab.renderer.Renderer2D;
import jugglinglab.util.*;


// This class creates the juggling animation on screen. It spawns a thread
// that loops over time and draws frames. It also interprets some mouse
// interactions such as camera drag and click to pause.

public class AnimationPanel extends JPanel implements Runnable {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
    static final double snapangle = Math.toRadians(8.0);

    protected Animator anim;
    protected AnimationPrefs jc;

    protected Thread engine;
    protected boolean engineRunning;
    protected boolean enginePaused;
    protected boolean engineAnimating;
    protected double sim_time;
    public boolean writingGIF;
    public String message;

    protected Clip catchclip;
    protected Clip bounceclip;

    protected boolean waspaused;  // for pause on mouse away
    protected boolean outside;
    protected boolean outside_valid;

    // for camera dragging
    protected boolean dragging_camera;
    protected int startx, starty;
    protected int lastx, lasty;
    protected double[] dragcamangle;


    public AnimationPanel() {
        anim = new Animator();
        jc = new AnimationPrefs();
        setOpaque(true);
        loadAudioClips();
        initHandlers();
    }

    protected void loadAudioClips() {
        try {
            URL catchurl = AnimationPanel.class.getResource("/catch.au");
            AudioInputStream catchAudioIn = AudioSystem.getAudioInputStream(catchurl);
            DataLine.Info info = new DataLine.Info(Clip.class, catchAudioIn.getFormat());
            catchclip = (Clip)AudioSystem.getLine(info);
            catchclip.open(catchAudioIn);
        } catch (Exception e) {
            // System.out.println("Error loading catch.au: " + e.getMessage());
            catchclip = null;
        }
        try {
            URL bounceurl = AnimationPanel.class.getResource("/bounce.au");
            AudioInputStream bounceAudioIn = AudioSystem.getAudioInputStream(bounceurl);
            DataLine.Info info = new DataLine.Info(Clip.class, bounceAudioIn.getFormat());
            bounceclip = (Clip)AudioSystem.getLine(info);
            bounceclip.open(bounceAudioIn);
        } catch (Exception e) {
            // System.out.println("Error loading bounce.au: " + e.getMessage());
            bounceclip = null;
        }
    }

    protected void initHandlers() {
        addMouseListener(new MouseAdapter() {
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
                if (jc.mousePause && lastpress == lastenter)
                    return;

                if (!engineAnimating)
                    return;
                if (writingGIF)
                    return;

                startx = me.getX();
                starty = me.getY();
            }

            @Override
            public void mouseReleased(MouseEvent me) {
                if (jc.mousePause && lastpress == lastenter)
                    return;
                if (writingGIF)
                    return;
                dragging_camera = false;

                if (!engineAnimating && engine != null && engine.isAlive()) {
                    setPaused(!enginePaused);
                    return;
                }
                if (me.getX() == startx && me.getY() == starty &&
                                engine != null && engine.isAlive()) {
                    setPaused(!enginePaused);
                    getParent().dispatchEvent(me);
                }
                if (isPaused())
                    repaint();
            }

            @Override
            public void mouseEntered(MouseEvent me) {
                lastenter = me.getWhen();
                if (jc.mousePause && !writingGIF)
                    setPaused(waspaused);
                outside = false;
                outside_valid = true;
            }

            @Override
            public void mouseExited(MouseEvent me) {
                if (jc.mousePause && !writingGIF) {
                    waspaused = isPaused();
                    setPaused(true);
                }
                outside = true;
                outside_valid = true;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent me) {
                if (!engineAnimating)
                    return;
                if (writingGIF)
                    return;
                if (!dragging_camera) {
                    dragging_camera = true;
                    lastx = startx;
                    lasty = starty;
                    dragcamangle = getCameraAngle();
                }

                int xdelta = me.getX() - lastx;
                int ydelta = me.getY() - lasty;
                lastx = me.getX();
                lasty = me.getY();
                double[] ca = dragcamangle;
                ca[0] += (double)(xdelta) * 0.02;
                ca[1] -= (double)(ydelta) * 0.02;
                if (ca[1] < Math.toRadians(0.0001))
                    ca[1] = Math.toRadians(0.0001);
                if (ca[1] > Math.toRadians(179.9999))
                    ca[1] = Math.toRadians(179.9999);
                while (ca[0] < 0.0)
                    ca[0] += Math.toRadians(360.0);
                while (ca[0] >= Math.toRadians(360.0))
                    ca[0] -= Math.toRadians(360.0);

                double[] snappedcamangle = snapCamera(ca);
                setCameraAngle(snappedcamangle);

                // send event to the parent so that SelectionView can update
                // camera angles of other animations
                getParent().dispatchEvent(me);

                if (isPaused())
                    repaint();
            }
        });

        addComponentListener(new ComponentAdapter() {
            boolean hasResized = false;

            @Override
            public void componentResized(ComponentEvent e) {
                if (!engineAnimating)
                    return;
                if (writingGIF)
                    return;
                anim.setDimension(getSize());
                repaint();

                // Don't update the preferred animation size if the enclosing
                // window is maximized
                Component comp = SwingUtilities.getRoot(AnimationPanel.this);
                if (comp instanceof PatternWindow) {
                    if (((PatternWindow)comp).isWindowMaximized())
                        return;
                }

                if (hasResized)
                    jc.setSize(getSize());
                hasResized = true;
            }
        });
    }

    protected double[] snapCamera(double[] ca) {
        double[] result = new double[2];
        result[0] = ca[0];
        result[1] = ca[1];

        if (result[1] < snapangle)
            result[1] = Math.toRadians(0.0001);
        else if (anglediff(Math.toRadians(90.0) - result[1]) < snapangle)
            result[1] = Math.toRadians(90.0);
        else if (result[1] > (Math.toRadians(180.0) - snapangle))
            result[1] = Math.toRadians(179.9999);

        if (anim.pat.getNumberOfJugglers() == 1) {
            double a = -Math.toRadians(anim.pat.getJugglerAngle(1, getTime()));

            while (a < 0)
                a += Math.toRadians(360.0);
            while (a >= Math.toRadians(360.0))
                a -= Math.toRadians(360.0);

            if (anglediff(a - result[0]) < snapangle)
                result[0] = a;
            else if (anglediff(a + 0.5 * Math.PI - result[0]) < snapangle)
                result[0] = a + 0.5 * Math.PI;
            else if (anglediff(a + Math.PI - result[0]) < snapangle)
                result[0] = a + Math.PI;
            else if (anglediff(a + 1.5 * Math.PI - result[0]) < snapangle)
                result[0] = a + 1.5 * Math.PI;
        }
        return result;
    }

    protected static double anglediff(double delta) {
        while (delta > Math.PI)
            delta -= 2.0 * Math.PI;
        while (delta <= -Math.PI)
            delta += 2.0 * Math.PI;
        return Math.abs(delta);
    }

    public void restartJuggle(JMLPattern pat, AnimationPrefs newjc)
                    throws JuggleExceptionUser, JuggleExceptionInternal {
        // Do pattern layout first so if there's an error we don't disrupt the
        // current animation
        if (pat != null)
            pat.layoutPattern();

        // stop the current animation thread, if one is running
        killAnimationThread();

        if (newjc != null)
            jc = newjc;

        anim.setDimension(getSize());
        anim.restartAnimator(pat, newjc);

        setBackground(anim.getBackground());

        engine = new Thread(this);
        engine.start();
    }

    public void restartJuggle() throws JuggleExceptionUser, JuggleExceptionInternal {
        restartJuggle(null, null);
    }

    @Override
    public void run()  {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        engineRunning = true;       // ok to start painting
        engineAnimating = false;

        if (jc.mousePause)
            waspaused = jc.startPause;

        try {
            if (jc.startPause) {
                message = guistrings.getString("Message_click_to_start");
                repaint();
                enginePaused = true;
                while (enginePaused) {
                    synchronized (this) {
                        wait();
                    }
                }
            }

            message = null;

            long real_time_start = System.currentTimeMillis();
            long real_time_wait;
            double oldtime, newtime;

            if (jc.mousePause) {
                if (outside_valid)
                    setPaused(outside);
                else
                    setPaused(true);    // assume mouse is outside animator, if not known
                waspaused = false;
            }

            engineAnimating = true;

            while (true)  {
                setTime(anim.pat.getLoopStartTime());

                for (int i = 0; getTime() < (anim.pat.getLoopEndTime() -
                                    0.5 * anim.sim_interval_secs); i++) {
                    repaint();
                    real_time_wait = anim.real_interval_millis -
                                (System.currentTimeMillis() - real_time_start);

                    if (real_time_wait > 0)
                        Thread.sleep(real_time_wait);
                    else if (engine == null || engine.interrupted())
                        throw new InterruptedException();

                    real_time_start = System.currentTimeMillis();

                    while (enginePaused) {
                        synchronized (this) {
                            wait();
                        }
                    }

                    oldtime = getTime();
                    setTime(getTime() + anim.sim_interval_secs);
                    newtime = getTime();

                    if (jc.catchSound && catchclip != null) {
                        // use synchronized here to prevent editing actions in
                        // EditLadderDiagram from creating data consistency problems
                        synchronized (anim.pat) {
                            for (int path = 1; path <= anim.pat.getNumberOfPaths(); path++) {
                                if (anim.pat.getPathCatchVolume(path, oldtime, newtime) > 0.0) {
                                    // do audio playback on the EDT -- not strictly
                                    // necessary but it seems to work better on Linux
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (catchclip.isActive())
                                                catchclip.stop();
                                            catchclip.setFramePosition(0);
                                            catchclip.start();
                                        }
                                    });
                                }
                            }
                        }
                    }
                    if (jc.bounceSound && bounceclip != null) {
                        synchronized (anim.pat) {
                            for (int path = 1; path <= anim.pat.getNumberOfPaths(); path++) {
                                if (anim.pat.getPathBounceVolume(path, oldtime, newtime) > 0.0) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (bounceclip.isActive())
                                                bounceclip.stop();
                                            bounceclip.setFramePosition(0);
                                            bounceclip.start();
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
                anim.advanceProps();
            }
        } catch (InterruptedException ie) {
            return;
        }
    }

    // stop the current animation thread, if one is running
    protected void killAnimationThread() {
        try {
            if (engine != null && engine.isAlive()) {
                engine.interrupt();
                engine.join();
            }
        } catch (InterruptedException ie) {
            return;
        } finally {
            engine = null;
            engineRunning = false;
            enginePaused = false;
            engineAnimating = false;
            message = null;
        }
    }

    public boolean isPaused() {
        return enginePaused;
    }

    public synchronized void setPaused(boolean wanttopause) {
        if (enginePaused == true && wanttopause == false)
            notify();       // wake up wait() in run() method
        enginePaused = wanttopause;
    }

    public double getTime() {
        return sim_time;
    }

    public void setTime(double time) {
        sim_time = time;
    }

    public double[] getCameraAngle() {
        return anim.getCameraAngle();
    }

    public void setCameraAngle(double[] ca) {
        anim.setCameraAngle(ca);
    }

    protected void drawString(String message, Graphics g) {
        FontMetrics fm = g.getFontMetrics();
        int message_width = fm.stringWidth(message);

        Dimension dim = getSize();
        int x = (dim.width > message_width) ? (dim.width - message_width)/2 : 0;
        int y = (dim.height + fm.getHeight()) / 2;

        g.setColor(Color.white);
        g.fillRect(0, 0, dim.width, dim.height);
        g.setColor(Color.black);
        g.drawString(message, x, y);
    }

    public JMLPattern getPattern() {
        return anim.pat;
    }

    public Animator getAnimator() {
        return anim;
    }

    public AnimationPrefs getAnimationPrefs() {
        return jc;
    }

    public double getZoomLevel() {
        return getAnimator().getZoomLevel();
    }

    public void setZoomLevel(double z) {
        if (!writingGIF) {
            getAnimator().setZoomLevel(z);
            repaint();
        }
    }

    public void disposeAnimation() {
        killAnimationThread();
    }

    // javax.swing.JComponent methods

    @Override
    public void paintComponent(Graphics g) {
        if (message != null)
            drawString(message, g);
        else if (engineRunning && !writingGIF) {
            try {
                anim.drawFrame(getTime(), g, dragging_camera, true);
            } catch (JuggleExceptionInternal jei) {
                killAnimationThread();
                ErrorDialog.handleFatalException(jei);
            }
        }
    }
}
