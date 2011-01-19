// AnimatorEdit.java
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

import java.awt.*;
import java.awt.event.*;

import jugglinglab.util.*;
import jugglinglab.jml.*;


public class AnimatorEdit extends Animator {
    protected LadderDiagram ladder = null;
    protected boolean event_active = false;
    protected JMLEvent event;
    protected int xlow1, xhigh1, ylow1, yhigh1;
    protected int xlow2, xhigh2, ylow2, yhigh2;
    protected boolean dragging = false;
    protected boolean dragging_left = false;
    protected int xstart, ystart, xdelta, ydelta;


    public AnimatorEdit() {
        super();
    }

    protected void initHandlers() {
        final JMLPattern fpat = pat;

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

                if (event_active) {
                    int mx = me.getX();
                    int my = me.getY();
                    if ((mx >= xlow1) && (mx <= xhigh1) && (my >= ylow1) && (my <= yhigh1)) {
                        dragging = true;
                        dragging_left = true;
                        xstart = mx;
                        ystart = my;
                        xdelta = ydelta = 0;
                        return;
                    }
                    int t = AnimatorEdit.this.getSize().width / 2;
                    if ((mx >= (xlow2+t)) && (mx <= (xhigh2+t)) && (my >= ylow2) && (my <= yhigh2)) {
                        dragging = true;
                        dragging_left = false;
                        xstart = mx;
                        ystart = my;
                        xdelta = ydelta = 0;
                        return;
                    }
                }

                // if we get here, start a reposition of the camera
                AnimatorEdit.this.startx = me.getX();
                AnimatorEdit.this.starty = me.getY();
            }

            public void mouseReleased(MouseEvent me) {
				if (jc.mousePause && (lastpress == lastenter))
					return;
                if (exception != null)
                    return;
                if (!engineStarted && (engine != null) && engine.isAlive()) {
                    setPaused(!enginePaused);
                    return;
                }

                if (event_active && dragging) {
                    JMLEvent master = (event.isMaster() ? event : event.getMaster());
                    boolean flipx = (event.getHand() != master.getHand());

                    Coordinate newgc = ren1.getScreenTranslatedCoordinate(
                                                                          event.getGlobalCoordinate(), xdelta, ydelta
                                                                          );
                    if (AnimatorEdit.this.jc.stereo) {
                        Coordinate newgc2 = ren2.getScreenTranslatedCoordinate(
                                                                               event.getGlobalCoordinate(), xdelta, ydelta
                                                                               );
                        newgc = Coordinate.add(newgc, newgc2);
                        newgc.setCoordinate(0.5*newgc.x, 0.5*newgc.y, 0.5*newgc.z);
                    }

                    Coordinate newlc = pat.convertGlobalToLocal(newgc,
                                                              event.getJuggler(), event.getT());
                    Coordinate deltalc = Coordinate.sub(newlc,
                                                        event.getLocalCoordinate());

                    if (flipx)
                        deltalc.x = -deltalc.x;
                    Coordinate orig = master.getLocalCoordinate();
                    master.setLocalCoordinate(Coordinate.add(orig, deltalc));
                    xdelta = ydelta = 0;

                    EditLadderDiagram eld = (EditLadderDiagram)ladder;
                    eld.activeEventMoved();
                }
                AnimatorEdit.this.cameradrag = false;
                dragging = false;
                if ((me.getX() == startx) && (me.getY() == starty) &&
                    (engine != null) && engine.isAlive())
                    setPaused(!enginePaused);
                if (AnimatorEdit.this.getPaused())
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
                if (dragging) {
                    int mx = me.getX();
                    int my = me.getY();
                    xdelta = mx - xstart;
                    ydelta = my - ystart;
                    repaint();
                } else if (!cameradrag) {
                    AnimatorEdit.this.cameradrag = true;
                    AnimatorEdit.this.lastx = AnimatorEdit.this.startx;
                    AnimatorEdit.this.lasty = AnimatorEdit.this.starty;
                    AnimatorEdit.this.camangle = AnimatorEdit.this.ren1.getCameraAngle();
                }

                if (!cameradrag)
                    return;

                int xdelta = me.getX() - AnimatorEdit.this.lastx;
                int ydelta = me.getY() - AnimatorEdit.this.lasty;
                AnimatorEdit.this.lastx = me.getX();
                AnimatorEdit.this.lasty = me.getY();
                double[] camangle = AnimatorEdit.this.camangle;
                camangle[0] += (double)(xdelta) * 0.02;
                camangle[1] -= (double)(ydelta) * 0.02;
                if (camangle[1] < 0.0001)
                    camangle[1] = 0.0001;
                if (camangle[1] > JLMath.toRad(90.0))
                    camangle[1] = JLMath.toRad(90.0);
                while (camangle[0] < 0.0)
                    camangle[0] += JLMath.toRad(360.0);
                while (camangle[0] >= JLMath.toRad(360.0))
                    camangle[0] -= JLMath.toRad(360.0);

                AnimatorEdit.this.setCameraAngle(camangle);

                if (event_active)
                    createEventView();
                if (AnimatorEdit.this.getPaused())
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
        double[] result = super.snapCamera(ca);

        if (event_active) {
            double a = JLMath.toRad(pat.getJugglerAngle(event.getJuggler(),
                                                        event.getT()));

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

    public void setLadderDiagram(LadderDiagram ladder) {
        this.ladder = ladder;
    }

    // set position of tracker bar in ladder diagram as we animate
    public void setTime(double time) {
        super.setTime(time);
        if (ladder != null)
            ladder.setTime(time);
    }

    public void activateEvent(JMLEvent ev) {
        if ((ladder != null) && !(ladder instanceof EditLadderDiagram))
            return;
        event = ev;
        event_active = true;
        createEventView();
    }

    public void deactivateEvent() {
        event_active = false;
    }

    protected void createEventView() {
        if (event_active) {
            // translate by one pixel and see how far it was in juggler space
        {
            Coordinate c = event.getGlobalCoordinate();
            Coordinate c2 = ren1.getScreenTranslatedCoordinate(c, 1, 0);
            Coordinate dc = Coordinate.sub(c, c2);
            double dl = Math.sqrt(dc.x*dc.x + dc.y*dc.y + dc.z*dc.z);
            int boxhw = (int)(0.5 + 5.0 / dl);	// pixels corresponding to 5cm in juggler space

            int[] center = ren1.getXY(c);
            xlow1 = center[0] - boxhw;
            ylow1 = center[1] - boxhw;
            xhigh1 = center[0] + boxhw;
            yhigh1 = center[1] + boxhw;
        }

            if (this.jc.stereo) {
                Coordinate c = event.getGlobalCoordinate();
                Coordinate c2 = ren2.getScreenTranslatedCoordinate(c, 1, 0);
                Coordinate dc = Coordinate.sub(c, c2);
                double dl = Math.sqrt(dc.x*dc.x + dc.y*dc.y + dc.z*dc.z);
                int boxhw = (int)(0.5 + 5.0 / dl);	// pixels corresponding to 5cm in juggler space

                int[] center = ren2.getXY(c);
                xlow2 = center[0] - boxhw;
                ylow2 = center[1] - boxhw;
                xhigh2 = center[0] + boxhw;
                yhigh2 = center[1] + boxhw;
            }
        }
    }

    protected void syncRenderer() {
        super.syncRenderer();
        if (event_active)
            createEventView();
    }

    protected void drawFrame(double sim_time, int[] pnum, Graphics g) throws JuggleExceptionInternal {
        super.drawFrame(sim_time, pnum, g);

        if (!event_active)
            return;

        Dimension d = this.getSize();
        Graphics g2 = g;
        if (this.jc.stereo)
            g2 = g.create(0,0,d.width/2,d.height);

        if (g2 instanceof Graphics2D) {
            Graphics2D g22 = (Graphics2D)g2;
            g22.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g2.setColor(Color.green);
        g2.drawLine(xlow1+xdelta, ylow1+ydelta, xhigh1+xdelta, ylow1+ydelta);
        g2.drawLine(xhigh1+xdelta, ylow1+ydelta, xhigh1+xdelta, yhigh1+ydelta);
        g2.drawLine(xhigh1+xdelta, yhigh1+ydelta, xlow1+xdelta, yhigh1+ydelta);
        g2.drawLine(xlow1+xdelta, yhigh1+ydelta, xlow1+xdelta, ylow1+ydelta);

        if (this.jc.stereo) {
            g2 = g.create(d.width/2,0,d.width/2,d.height);
            if (g2 instanceof Graphics2D) {
                Graphics2D g22 = (Graphics2D)g2;
                g22.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g2.setColor(Color.green);
            g2.drawLine(xlow2+xdelta, ylow2+ydelta, xhigh2+xdelta, ylow2+ydelta);
            g2.drawLine(xhigh2+xdelta, ylow2+ydelta, xhigh2+xdelta, yhigh2+ydelta);
            g2.drawLine(xhigh2+xdelta, yhigh2+ydelta, xlow2+xdelta, yhigh2+ydelta);
            g2.drawLine(xlow2+xdelta, yhigh2+ydelta, xlow2+xdelta, ylow2+ydelta);
        }
    }

}
