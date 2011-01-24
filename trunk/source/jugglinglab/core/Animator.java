// Animator.java
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

package jugglinglab.core;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.lang.reflect.*;
import javax.swing.*;

import jugglinglab.jml.*;
import jugglinglab.renderer.*;
import jugglinglab.util.*;


public class Animator extends JPanel implements Runnable {
    static ResourceBundle guistrings;
    // static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        // errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected Thread			engine;
    protected boolean			engineStarted = false;;
    protected boolean			enginePaused = false;
    protected boolean			engineRunning = false;
    public boolean				writingGIF = false;
    public JuggleException		exception;
    public String				message;

    protected JMLPattern		pat;
    protected AnimatorPrefs		jc;
    protected jugglinglab.renderer.Renderer	ren1 = null, ren2 = null;
    protected Coordinate		overallmax = null, overallmin = null;

    protected int[]				animpropnum = null, temppropnum = null;
    protected Permutation		invpathperm = null;
    protected int				num_frames;
    protected double			sim_time;
    protected double			sim_interval_secs;
    protected long				real_interval_millis;
    protected static AudioClip	catchclip = null, bounceclip = null;
	
	protected boolean			waspaused;			// for pause on mouse away
	// protected boolean waspaused_valid = false;
	protected boolean			outside;
	protected boolean			outside_valid;
    
    protected boolean			cameradrag;
    protected int				startx, starty, lastx, lasty;
    protected double[]			camangle;
    protected double[]			actualcamangle;
    protected double[]			actualcamangle1;
    protected double[]			actualcamangle2;
    protected static final double snapangle = JLMath.toRad(15.0);

    protected Dimension			prefsize;

    public Animator() {
        cameradrag = false;
        initHandlers();

        camangle = new double[2];
        camangle[0] = JLMath.toRad(0.0);
        camangle[1] = JLMath.toRad(90.0);
        actualcamangle1 = new double[2];
        actualcamangle2 = new double[2];
		jc = new AnimatorPrefs();
		
		outside = true;
		waspaused = outside_valid = false;
		
		this.setOpaque(true);
    }


    public void setJAPreferredSize(Dimension d) {
        prefsize = d;
    }

    // override methods in java.awt.Component
    public Dimension getPreferredSize() {
		if (prefsize != null)
			return new Dimension(prefsize);
		return getMinimumSize();
    }
    public Dimension getMinimumSize() {
        return new Dimension(100,100);
    }

    public static void setAudioClips(AudioClip[] clips) {
        Animator.catchclip = clips[0];
        Animator.bounceclip = clips[1];
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

                Animator.this.startx = me.getX();
                Animator.this.starty = me.getY();
            }

            public void mouseReleased(MouseEvent me) {
				if (jc.mousePause && (lastpress == lastenter))
					return;
                if (exception != null)
                    return;
                Animator.this.cameradrag = false;

                if (!engineStarted && (engine != null) && engine.isAlive()) {
                    setPaused(!enginePaused);
                    return;
                }
                if ((me.getX() == startx) && (me.getY() == starty) &&
								(engine != null) && engine.isAlive()) {
                    setPaused(!enginePaused);
					// Animator.this.getParent().dispatchEvent(me);
				}
                if (Animator.this.getPaused())
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
                    Animator.this.cameradrag = true;
                    Animator.this.lastx = Animator.this.startx;
                    Animator.this.lasty = Animator.this.starty;
                    Animator.this.camangle = Animator.this.ren1.getCameraAngle();
                }

                int xdelta = me.getX() - Animator.this.lastx;
                int ydelta = me.getY() - Animator.this.lasty;
                Animator.this.lastx = me.getX();
                Animator.this.lasty = me.getY();
                double[] camangle = Animator.this.camangle;
                camangle[0] += (double)(xdelta) * 0.02;
                camangle[1] -= (double)(ydelta) * 0.02;
                if (camangle[1] < 0.000001)
                    camangle[1] = 0.000001;
                if (camangle[1] > JLMath.toRad(90.0))
                    camangle[1] = JLMath.toRad(90.0);
                while (camangle[0] < 0.0)
                    camangle[0] += JLMath.toRad(360.0);
                while (camangle[0] >= JLMath.toRad(360.0))
                    camangle[0] -= JLMath.toRad(360.0);

                Animator.this.setCameraAngle(camangle);

                if (Animator.this.getPaused())
                    repaint();
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (exception != null)
                    return;
                if (!engineStarted)
                    return;
                syncRenderer();
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
        // 	result[1] = JLMath.toRad(179.99999);
        return result;
    }

    protected void setCameraAngle(double[] camangle) {
        actualcamangle = snapCamera(camangle);
        while (actualcamangle[0] < 0.0)
            actualcamangle[0] += JLMath.toRad(360.0);
        while (actualcamangle[0] >= JLMath.toRad(360.0))
            actualcamangle[0] -= JLMath.toRad(360.0);

        if (jc.stereo) {
            actualcamangle1[0] = actualcamangle[0] - 0.05;
            actualcamangle1[1] = actualcamangle[1];
            ren1.setCameraAngle(actualcamangle1);
            actualcamangle2[0] = actualcamangle[0] + 0.05;
            actualcamangle2[1] = actualcamangle[1];
            ren2.setCameraAngle(actualcamangle2);
        } else
            ren1.setCameraAngle(actualcamangle);
    }

    public void restartJuggle(JMLPattern pat, AnimatorPrefs newjc) throws JuggleExceptionUser, JuggleExceptionInternal {
        // try to lay out new pattern first so that if there's an error we won't stop the current animation
        if ((pat != null) && !pat.isLaidout())
            pat.layoutPattern();

        // stop the current animation thread, if one is running
        killAnimationThread();

        if (pat != null)	this.pat = pat;
        if (newjc != null)	this.jc = newjc;

        if (this.pat == null)
            return;

		ren1 = new Renderer2D();
		if (this.jc.stereo)
			ren2 = new Renderer2D();

        ren1.setPattern(this.pat);
        if (this.jc.stereo)
            ren2.setPattern(this.pat);

        if (this.pat.getNumberOfJugglers() == 1) {
            this.camangle[0] = JLMath.toRad(0.0);
            this.camangle[1] = JLMath.toRad(90.0);
        } else {
            this.camangle[0] = JLMath.toRad(340.0);
            this.camangle[1] = JLMath.toRad(70.0);
        }
        setCameraAngle(camangle);

        this.setBackground(ren1.getBackground());
        syncToPattern();

        if (jugglinglab.core.Constants.DEBUG_LAYOUT)
            System.out.println(this.pat);

        engine = new Thread(this);
        engine.start();
    }

    public void restartJuggle() throws JuggleExceptionUser, JuggleExceptionInternal {
        restartJuggle(null, null);
    }


    public void run()  {		// Called when this object becomes a thread
        long	real_time_start, real_time_wait;

        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        engineStarted = false;

		if (jc.mousePause) {
			waspaused = jc.startPause;
			// waspaused_valid = outside_valid;
		}
					
        try {
            engineRunning = true;	// ok to start rendering

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
					setPaused(true);	// assume mouse is outside animator, if not known
				waspaused = false;
				// waspaused_valid = true;
			}
				
            while (engineRunning)  {
                setTime(pat.getLoopStartTime());
                engineStarted = true;

                for (int i = 0; engineRunning &&
								(getTime() < (pat.getLoopEndTime() - 0.5*sim_interval_secs)); i++) {
                    repaint();
                    try {
                        real_time_wait = real_interval_millis -
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
                    setTime(getTime() + sim_interval_secs);
                    newtime = getTime();

                    if (jc.catchSound && (catchclip != null)) {
                        for (int path = 1; path <= pat.getNumberOfPaths(); path++) {
                            if (pat.getPathCatchVolume(path, oldtime, newtime) > 0.0) {
                                // System.out.println("Caught path "+path);
                                catchclip.play();
                            }
                        }
                    }
                    if (jc.bounceSound && (bounceclip != null)) {
                        for (int path = 1; path <= pat.getNumberOfPaths(); path++) {
                            if (pat.getPathBounceVolume(path, oldtime, newtime) > 0.0) {
                                // System.out.println("Caught path "+path);
                                bounceclip.play();
                            }
                        }
                    }
                }
                advanceProps(animpropnum);
            }
        } catch (JuggleException je) {
            exception = je;
            repaint();
        } finally {
            engineStarted = engineRunning = enginePaused = false;
            engine = null;	// this is critical as it signals restartJuggle() that exit is occurring
        }
        synchronized (this) {
            notify();	// tell possible thread wait()ing in restartJuggle() that animator thread is exiting
        }
    }

    // stop the current animation thread, if one is running
    protected synchronized void killAnimationThread() {
        while ((engine != null) && engine.isAlive()) {
            setPaused(false);		// get thread out of pause so it can exit
            engineRunning = false;
            try {
                wait();				// wait for notify() from exiting run() method
            } catch (InterruptedException ie) {
            }
        }

        engine = null;			// just in case animator doesn't initialize these
        engineStarted = false;
        enginePaused = false;
        engineRunning = false;
        exception = null;
        message = null;
    }

    public boolean getPaused() {
        return enginePaused;
    }

    public synchronized void setPaused(boolean wanttopause) {
        if ((enginePaused == true) && (wanttopause == false)) {
            notify();		// wake up wait() in run() method
        }
        enginePaused = wanttopause;
    }

    public double getTime() { return sim_time; };

    public void setTime(double time) {
        /*		while (time < pat.getLoopStartTime())
        time += (pat.getLoopEndTime() - pat.getLoopStartTime());
        while (time > pat.getLoopEndTime())
        time -= (pat.getLoopEndTime() - pat.getLoopStartTime());
        */
        sim_time = time;
    }

    public void paintComponent(Graphics g) {
        if (exception != null)
            drawString(exception.getMessage(), g);
        else if (message != null)
            drawString(message, g);
        else if (engineRunning && !writingGIF) {
            try {
                drawFrame(getTime(), animpropnum, g);
            } catch (JuggleExceptionInternal jei) {
                this.killAnimationThread();
                System.out.println(jei.getMessage());
                System.exit(0);
                // ErrorDialog.handleException(jei);
            }
        }
    }

    protected void drawFrame(double sim_time, int[] pnum, Graphics g) throws JuggleExceptionInternal {
        if (this.jc.stereo) {
            Dimension d = this.getSize();
            this.ren1.drawFrame(sim_time, pnum,
                                g.create(0,0,d.width/2,d.height), Animator.this);
            this.ren2.drawFrame(sim_time, pnum,
                                g.create(d.width/2,0,d.width/2,d.height), Animator.this);
        } else {
            this.ren1.drawFrame(sim_time, pnum, g, Animator.this);
        }

        if (!this.cameradrag)
            return;

        // try to turn on antialiased rendering
        VersionSpecific.getVersionSpecific().setAntialias(g);
        
        {
            double[] ca = ren1.getCameraAngle();
            double theta = ca[0];
            double phi = ca[1];

            double xya = 30.0;
            double xyb = xya * Math.sin(90.0*0.0174532925194 - phi);
            double zlen = xya * Math.cos(90.0*0.0174532925194 - phi);
            int cx = 38;
            int cy = 45;
            int xx = cx + (int)(0.5 - xya * Math.cos(theta));
            int xy = cy + (int)(0.5 + xyb * Math.sin(theta));
            int yx = cx + (int)(0.5 - xya * Math.cos(theta +
                                                    90.0*0.0174532925194));
            int yy = cy + (int)(0.5 + xyb * Math.sin(theta +
                                                    90.0*0.0174532925194));
            int zx = cx;
            int zy = cy - (int)(0.5 + zlen);

            g.setColor(Color.green);
            g.drawLine(cx, cy, xx, xy);
            g.drawLine(cx, cy, yx, yy);
            g.drawLine(cx, cy, zx, zy);
            g.fillOval(xx-2, xy-2, 5, 5);
            g.fillOval(yx-2, yy-2, 5, 5);
            g.fillOval(zx-2, zy-2, 5, 5);
            g.drawString("x", xx-2, xy-4);
            g.drawString("y", yx-2, yy-4);
            g.drawString("z", zx-2, zy-4);
        }

        if (this.jc.stereo) {
            double[] ca = ren2.getCameraAngle();
            double theta = ca[0];
            double phi = ca[1];

            double xya = 30.0;
            double xyb = xya * Math.sin(90.0*0.0174532925194 - phi);
            double zlen = xya * Math.cos(90.0*0.0174532925194 - phi);
            int cx = 38 + this.getSize().width/2;
            int cy = 45;
            int xx = cx + (int)(0.5 - xya * Math.cos(theta));
            int xy = cy + (int)(0.5 + xyb * Math.sin(theta));
            int yx = cx + (int)(0.5 - xya * Math.cos(theta + 90.0*0.0174532925194));
            int yy = cy + (int)(0.5 + xyb * Math.sin(theta + 90.0*0.0174532925194));
            int zx = cx;
            int zy = cy - (int)(0.5 + zlen);

            g.setColor(Color.green);
            g.drawLine(cx, cy, xx, xy);
            g.drawLine(cx, cy, yx, yy);
            g.drawLine(cx, cy, zx, zy);
            g.fillOval(xx-2, xy-2, 5, 5);
            g.fillOval(yx-2, yy-2, 5, 5);
            g.fillOval(zx-2, zy-2, 5, 5);
            g.drawString("x", xx-2, xy-4);
            g.drawString("y", yx-2, yy-4);
            g.drawString("z", zx-2, zy-4);
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

    protected void advanceProps(int[] pnum) {
        for (int i = 0; i < pat.getNumberOfPaths(); i++)
            temppropnum[invpathperm.getMapping(i+1)-1] = pnum[i];
        for (int i = 0; i < pat.getNumberOfPaths(); i++)
            pnum[i] = temppropnum[i];
    }

    public void syncToPattern() {
        findMaxMin();
        syncRenderer();

        // figure out timing constants; adjust fps to get integer number of frames in loop
        num_frames = (int)(0.5 + (pat.getLoopEndTime() - pat.getLoopStartTime()) * jc.slowdown * jc.fps);
        sim_interval_secs = (pat.getLoopEndTime()-pat.getLoopStartTime()) / num_frames;
        real_interval_millis = (long)(1000.0 * sim_interval_secs * jc.slowdown);

        animpropnum = new int[pat.getNumberOfPaths()];
        for (int i = 1; i <= pat.getNumberOfPaths(); i++)
            animpropnum[i-1] = pat.getPropAssignment(i);
        temppropnum = new int[pat.getNumberOfPaths()];
        invpathperm = pat.getPathPermutation().getInverse();
    }

    protected void findMaxMin() {
        // the algorithm here could be improved to take into account which props are
        // on which paths.  We may also want to leave room for the rest of the juggler.
        int i;
        Coordinate patternmax = null, patternmin = null;
        Coordinate handmax = null, handmin = null;
        Coordinate propmax = null, propmin = null;

        for (i = 1; i <= pat.getNumberOfPaths(); i++) {
            patternmax = Coordinate.max(patternmax, pat.getPathMax(i));
            patternmin = Coordinate.min(patternmin, pat.getPathMin(i));

            if (jugglinglab.core.Constants.DEBUG_LAYOUT)
                System.out.println("Pattern max "+i+" = "+patternmax);
        }

        // make sure all hands are visible
        for (i = 1; i <= pat.getNumberOfJugglers(); i++) {
            handmax = Coordinate.max(handmax, pat.getHandMax(i, HandLink.LEFT_HAND));
            handmin = Coordinate.min(handmin, pat.getHandMin(i, HandLink.LEFT_HAND));
            handmax = Coordinate.max(handmax, pat.getHandMax(i, HandLink.RIGHT_HAND));
            handmin = Coordinate.min(handmin, pat.getHandMin(i, HandLink.RIGHT_HAND));
        }

        for (i = 1; i <= pat.getNumberOfProps(); i++) {
            propmax = Coordinate.max(propmax, pat.getProp(i).getMax());
            propmin = Coordinate.min(propmin, pat.getProp(i).getMin());
        }

        // make sure props are entirely visible along all paths
        patternmax = Coordinate.add(patternmax, propmax);
        patternmin = Coordinate.add(patternmin, propmin);

        // make sure hands are entirely visible
        handmax = Coordinate.add(handmax, ren1.getHandWindowMax());
        handmin = Coordinate.add(handmin, ren1.getHandWindowMin());

        // make sure jugglers' bodies are visible
        this.overallmax = Coordinate.max(handmax, ren1.getJugglerWindowMax());
        this.overallmax = Coordinate.max(overallmax, patternmax);

        this.overallmin = Coordinate.min(handmin, ren1.getJugglerWindowMin());
        this.overallmin = Coordinate.min(overallmin, patternmin);

		// we want to ensure everything stays visible as we rotate the camera
		// viewpoint.  the following is simple and seems to work ok.
		if (pat.getNumberOfJugglers() == 1) {
			overallmin.z -= 0.3 * Math.max(Math.abs(overallmin.y), Math.abs(overallmax.y));
			overallmax.z += 5.0;	// keeps objects from rubbing against top of window
		} else {
			double tempx = Math.max(Math.abs(overallmin.x), Math.abs(overallmax.x));
			double tempy = Math.max(Math.abs(overallmin.y), Math.abs(overallmax.y));
			overallmin.z -= 0.4 * Math.max(tempx, tempy);
			overallmax.z += 0.4 * Math.max(tempx, tempy);
		}

		// make the x-coordinate origin at the center of the view
		double maxabsx = Math.max(Math.abs(this.overallmin.x), Math.abs(this.overallmax.x));
		this.overallmin.x = -maxabsx;
		this.overallmax.x = maxabsx;
		
        if (jugglinglab.core.Constants.DEBUG_LAYOUT) {
            System.out.println("Hand max = "+handmax);
            System.out.println("Hand min = "+handmin);
            System.out.println("Prop max = "+propmax);
            System.out.println("Prop min = "+propmin);
            System.out.println("Pattern max = "+patternmax);
            System.out.println("Pattern min = "+patternmin);
            System.out.println("Overall max = "+this.overallmax);
            System.out.println("Overall min = "+this.overallmin);

            this.overallmax = new Coordinate(100.0,0.0,500.0);
            this.overallmin = new Coordinate(-100.0,0.0,-100.0);
        }
    }

    protected void syncRenderer() {
        Dimension d = this.getSize();
        if (this.jc.stereo) {
            d.width /= 2;
            this.ren1.initDisplay(d, jc.border, this.overallmax, this.overallmin);
            this.ren2.initDisplay(d, jc.border, this.overallmax, this.overallmin);
        } else
            this.ren1.initDisplay(d, jc.border, this.overallmax, this.overallmin);
    }

    public boolean isAnimInited() {
        return engineStarted;
    }

    public JMLPattern getPattern() {
        return pat;
    }

    public AnimatorPrefs getAnimatorPrefs() {
        return jc;
    }

    public int[] getAnimPropNum() {
        return animpropnum;
    }
    
    public void dispose() {
        killAnimationThread();
    }

    public void writeGIFAnim() {
        try {
            Class jagw = Class.forName("jugglinglab.core.AnimatorGIFWriter");
            Method setup = jagw.getMethod("setup", new Class[] {Animator.class,
                jugglinglab.renderer.Renderer.class,
                jugglinglab.renderer.Renderer.class,
                Integer.TYPE, Double.TYPE, Long.TYPE});
            Object gw = jagw.newInstance();
            setup.invoke(gw, new Object[] {this, ren1, ren2, new Integer(num_frames),
                new Double(sim_interval_secs), new Long(real_interval_millis)});

            writingGIF = true;
            Thread worker = (Thread)gw;
            worker.start();
        } catch (ClassNotFoundException cnfe) {
            return;
        } catch (NoSuchMethodException nsme) {
            return;
        } catch (SecurityException se) {
            return;
        } catch (IllegalAccessException iae) {
            return;
        } catch (InstantiationException ie) {
            return;
        } catch (InvocationTargetException ite) {
            return;
        }
    }
}
