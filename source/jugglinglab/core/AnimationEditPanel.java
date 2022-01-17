// AnimationEditPanel.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import javax.swing.SwingUtilities;

import jugglinglab.util.*;
import jugglinglab.jml.JMLEvent;
import jugglinglab.jml.JMLPattern;
import jugglinglab.jml.JMLPosition;


// This subclass of AnimationPanel is used by Edit view. It adds functionality
// for interacting with on-screen representations of JML events, and
// interacting with a ladder diagram.

public class AnimationEditPanel extends AnimationPanel {
    protected LadderDiagram ladder;

    // for when an event is activated
    protected boolean event_active;
    protected JMLEvent event;
    protected int xlow1, xhigh1, ylow1, yhigh1;
    protected int xlow2, xhigh2, ylow2, yhigh2;  // for stereo view

    // for when a position is activated
    protected boolean position_active;
    protected JMLPosition position;
    protected int[][] pos_points1;
    protected boolean[] pos_points1_visible;
    protected boolean dragging_xy;
    protected boolean dragging_xz;
    protected boolean dragging_yz;
    protected boolean dragging_angle;

    // for when either is activated
    protected boolean dragging;
    protected boolean dragging_left;  // may not be necessary
    protected int xstart, ystart, xdelta, ydelta;


    public AnimationEditPanel() {
        super();
    }

    @Override
    protected void initHandlers() {
        final JMLPattern fpat = anim.pat;

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

                if (event_active) {
                    int mx = me.getX();
                    int my = me.getY();
                    if (mx >= xlow1 && mx <= xhigh1 && my >= ylow1 && my <= yhigh1) {
                        dragging = true;
                        dragging_left = true;
                        xstart = mx;
                        ystart = my;
                        xdelta = ydelta = 0;
                        return;
                    }
                    int t = getSize().width / 2;
                    if (jc.stereo && mx >= (xlow2+t) && mx <= (xhigh2+t) && my >= ylow2 && my <= yhigh2) {
                        dragging = true;
                        dragging_left = false;
                        xstart = mx;
                        ystart = my;
                        xdelta = ydelta = 0;
                        return;
                    }
                }

                if (position_active) {
                    int mx = me.getX();
                    int my = me.getY();
                    dragging_xy = (isInsidePolygon(mx, my, face_xy1) ||
                                   isInsidePolygon(mx, my, face_xy2));
                    dragging_xz = (isInsidePolygon(mx, my, face_xz1) ||
                                   isInsidePolygon(mx, my, face_xz2));
                    dragging_yz = (isInsidePolygon(mx, my, face_yz1) ||
                                   isInsidePolygon(mx, my, face_yz2));

                    dragging = dragging_xy || dragging_xz || dragging_yz;

                    if (dragging) {
                        xstart = mx;
                        ystart = my;
                        xdelta = ydelta = 0;
                        return;
                    }

                    int dmx = mx - pos_points1[9][0];
                    int dmy = my - pos_points1[9][1];
                    dragging_angle = (dmx * dmx + dmy * dmy < 49.0);

                    if (dragging_angle) {
                        dragging = true;
                        xstart = mx;
                        ystart = my;
                        xdelta = ydelta = 0;
                        System.out.println("dragging angle");
                        return;
                    }
                }

                // if we get here, start a reposition of the camera
                startx = me.getX();
                starty = me.getY();
            }

            @Override
            public void mouseReleased(MouseEvent me) {
                if (jc.mousePause && lastpress == lastenter)
                    return;
                if (writingGIF)
                    return;
                if (!engineAnimating && engine != null && engine.isAlive()) {
                    setPaused(!enginePaused);
                    return;
                }

                if (event_active && dragging) {
                    JMLEvent master = (event.isMaster() ? event : event.getMaster());
                    boolean flipx = (event.getHand() != master.getHand());

                    Coordinate newgc = anim.ren1.getScreenTranslatedCoordinate(
                            event.getGlobalCoordinate(), xdelta, ydelta);
                    if (jc.stereo) {
                        // average the coordinate shifts from each perspective
                        Coordinate newgc2 = anim.ren2.getScreenTranslatedCoordinate(
                                event.getGlobalCoordinate(), xdelta, ydelta);
                        newgc = Coordinate.add(newgc, newgc2);
                        newgc.setCoordinate(0.5*newgc.x, 0.5*newgc.y, 0.5*newgc.z);
                    }

                    Coordinate newlc = anim.pat.convertGlobalToLocal(newgc,
                                            event.getJuggler(), event.getT());
                    Coordinate deltalc = Coordinate.sub(newlc,
                                            event.getLocalCoordinate());
                    deltalc = Coordinate.truncate(deltalc, 1e-7);

                    if (flipx)
                        deltalc.x = -deltalc.x;

                    Coordinate oldlc = master.getLocalCoordinate();
                    master.setLocalCoordinate(Coordinate.add(oldlc, deltalc));

                    if (ladder instanceof EditLadderDiagram)
                        ((EditLadderDiagram)ladder).activeEventChanged();
                }

                if (position_active && dragging) {
                    System.out.println("move the position here");
                    dragging_xy = false;
                    dragging_xz = false;
                    dragging_yz = false;
                }

                cameradrag = false;
                dragging = false;
                xdelta = ydelta = 0;

                if (me.getX() == startx && me.getY() == starty &&
                                engine != null && engine.isAlive())
                    setPaused(!enginePaused);
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

                if (dragging) {
                    int mx = me.getX();
                    int my = me.getY();
                    xdelta = mx - xstart;
                    ydelta = my - ystart;
                    repaint();
                } else if (!cameradrag) {
                    cameradrag = true;
                    lastx = startx;
                    lasty = starty;
                    dragcamangle = anim.getCameraAngle();
                }

                if (!cameradrag)
                    return;

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

                anim.setCameraAngle(snapCamera(ca));

                if (event_active)
                    createEventView();
                if (position_active)
                    createPositionView();
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
                if (event_active)
                    createEventView();
                repaint();

                // Don't update the preferred animation size if the enclosing
                // window is maximized
                Component comp = SwingUtilities.getRoot(AnimationEditPanel.this);
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

    @Override
    protected double[] snapCamera(double[] ca) {
        double[] result = new double[2];
        result[0] = ca[0];
        result[1] = ca[1];

        // vertical snap to equator and north/south poles
        if (result[1] < snapangle)
            result[1] = Math.toRadians(0.0001);  // avoid gimbal lock
        else if (anglediff(Math.toRadians(90.0) - result[1]) < snapangle)
            result[1] = Math.toRadians(90.0);
        else if (result[1] > (Math.toRadians(180.0) - snapangle))
            result[1] = Math.toRadians(179.9999);

        double a = 0.0;
        boolean snap_horizontal = true;

        if (event_active)
            a = -Math.toRadians(anim.pat.getJugglerAngle(event.getJuggler(), event.getT()));
        else if (position_active)
            a = -Math.toRadians(anim.pat.getJugglerAngle(position.getJuggler(), position.getT()));
        else if (anim.pat.getNumberOfJugglers() == 1)
            a = -Math.toRadians(anim.pat.getJugglerAngle(1, getTime()));
        else
            snap_horizontal = false;

        if (snap_horizontal) {
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

    @Override
    public void restartJuggle(JMLPattern pat, AnimationPrefs newjc)
                    throws JuggleExceptionUser, JuggleExceptionInternal {
        super.restartJuggle(pat, newjc);
        if (event_active)
            createEventView();
    }

    public void setLadderDiagram(LadderDiagram lad) {
        ladder = lad;
    }

    // set position of tracker bar in ladder diagram as we animate
    @Override
    public void setTime(double time) {
        super.setTime(time);
        if (ladder != null)
            ladder.setTime(time);
    }

    public void activateEvent(JMLEvent ev) {
        deactivatePosition();
        event = ev;
        event_active = true;
        createEventView();
    }

    public void deactivateEvent() {
        event_active = false;
    }

    protected void createEventView() {
        if (event_active) {
            {
                // translate by one pixel and see how far it is in juggler space
                Coordinate c = event.getGlobalCoordinate();
                Coordinate c2 = anim.ren1.getScreenTranslatedCoordinate(c, 1, 0);
                double dl = Coordinate.distance(c, c2);
                int boxhw = (int)(0.5 + 5.0 / dl);  // pixels corresponding to 5cm in juggler space

                int[] center = anim.ren1.getXY(c);
                xlow1 = center[0] - boxhw;
                ylow1 = center[1] - boxhw;
                xhigh1 = center[0] + boxhw;
                yhigh1 = center[1] + boxhw;
            }

            if (jc.stereo) {
                Coordinate c = event.getGlobalCoordinate();
                Coordinate c2 = anim.ren2.getScreenTranslatedCoordinate(c, 1, 0);
                double dl = Coordinate.distance(c, c2);
                int boxhw = (int)(0.5 + 5.0 / dl);  // pixels corresponding to 5cm in juggler space

                int[] center = anim.ren2.getXY(c);
                xlow2 = center[0] - boxhw;
                ylow2 = center[1] - boxhw;
                xhigh2 = center[0] + boxhw;
                yhigh2 = center[1] + boxhw;
            }
        }
    }

    protected void drawEvent(Graphics g) throws JuggleExceptionInternal {
        if (!event_active)
            return;

        Dimension d = getSize();
        Graphics g2 = g;
        if (jc.stereo)
            g2 = g.create(0, 0, d.width / 2, d.height);

        if (g2 instanceof Graphics2D) {
            Graphics2D g22 = (Graphics2D)g2;
            g22.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g2.setColor(Color.green);
        g2.drawLine(xlow1+xdelta, ylow1+ydelta, xhigh1+xdelta, ylow1+ydelta);
        g2.drawLine(xhigh1+xdelta, ylow1+ydelta, xhigh1+xdelta, yhigh1+ydelta);
        g2.drawLine(xhigh1+xdelta, yhigh1+ydelta, xlow1+xdelta, yhigh1+ydelta);
        g2.drawLine(xlow1+xdelta, yhigh1+ydelta, xlow1+xdelta, ylow1+ydelta);

        if (jc.stereo) {
            g2 = g.create(d.width / 2, 0, d.width / 2, d.height);
            if (g2 instanceof Graphics2D) {
                Graphics2D g22 = (Graphics2D)g2;
                g22.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g2.setColor(Color.green);
            g2.drawLine(xlow2+xdelta, ylow2+ydelta, xhigh2+xdelta, ylow2+ydelta);
            g2.drawLine(xhigh2+xdelta, ylow2+ydelta, xhigh2+xdelta, yhigh2+ydelta);
            g2.drawLine(xhigh2+xdelta, yhigh2+ydelta, xlow2+xdelta, yhigh2+ydelta);
            g2.drawLine(xlow2+xdelta, yhigh2+ydelta, xlow2+xdelta, ylow2+ydelta);
        }
    }

    public void activatePosition(JMLPosition pos) {
        deactivateEvent();
        position = pos;
        position_active = true;
        createPositionView();
    }

    public void deactivatePosition() {
        position_active = false;
        dragging_xy = false;
        dragging_xz = false;
        dragging_yz = false;
        dragging_angle = false;
    }

    protected static final double[][] cube_points =
        {
            // cube corners
            {-1, -1, -1},
            {-1, -1,  1},
            {-1,  1, -1},
            { 1, -1, -1},
            {-1,  1,  1},
            { 1, -1,  1},
            { 1,  1, -1},
            { 1,  1,  1},

            // other useful points
            { 0,  0,  0},
            { 0,  4,  0},
            {-4,  0,  0},
            { 4,  0,  0},
            { 0, -4,  0},
            { 0,  4,  0},
            { 0,  0, -4},
            { 0,  0,  4},
        };

    protected static final int[] face_xy1 = { 1, 4, 7, 5 };
    protected static final int[] face_xy2 = { 0, 2, 6, 3 };
    protected static final int[] face_xz1 = { 4, 2, 6, 7 };
    protected static final int[] face_xz2 = { 1, 0, 3, 5 };
    protected static final int[] face_yz1 = { 5, 7, 6, 3 };
    protected static final int[] face_yz2 = { 1, 4, 2, 0 };

    protected void createPositionView() {
        if (position_active) {
            {
                // translate by one pixel and see how far it is in juggler space
                Coordinate c = position.getCoordinate();
                Coordinate c2 = anim.ren1.getScreenTranslatedCoordinate(c, 1, 0);
                double dl = Coordinate.distance(c, c2);
                double boxhw = 5.0 / dl;  // pixels corresponding to 10cm in juggler space

                double[] ca = anim.ren1.getCameraAngle();
                double theta = ca[0] + Math.toRadians(position.getAngle());
                double phi = ca[1];

                double xya = boxhw;
                double xyb = xya * Math.cos(phi);
                double zlen = xya * Math.sin(phi);
                double dxx = -xya * Math.cos(theta);
                double dxy = xyb * Math.sin(theta);
                double dyx = xya * Math.sin(theta);
                double dyy = xyb * Math.cos(theta);
                double dzx = 0.0;
                double dzy = -zlen;

                pos_points1 = new int[cube_points.length][2];
                pos_points1_visible = new boolean[cube_points.length];

                int[] center = anim.ren1.getXY(c);
                for (int i = 0; i < cube_points.length; ++i) {
                    pos_points1[i][0] = center[0] + (int)(0.5 +
                                                    dxx * cube_points[i][0] +
                                                    dyx * cube_points[i][1] +
                                                    dzx * cube_points[i][2]);
                    pos_points1[i][1] = center[1] + (int)(0.5 +
                                                    dxy * cube_points[i][0] +
                                                    dyy * cube_points[i][1] +
                                                    dzy * cube_points[i][2]);
                    pos_points1_visible[i] = true;
                }

                // top of cube (z = +1)
                pos_points1_visible[1] = (phi <= Math.PI/2) || (dxy > 0) || (dyy > 0);
                pos_points1_visible[4] = (phi <= Math.PI/2) || (dxx < 0) || (dyx < 0);
                pos_points1_visible[5] = (phi <= Math.PI/2) || (dxx > 0) || (dyx > 0);
                pos_points1_visible[7] = (phi <= Math.PI/2) || (dxy < 0) || (dyy < 0);

                // bottom of cube (z = -1)
                pos_points1_visible[0] = (phi > Math.PI/2) || (dxy < 0) || (dyy < 0);
                pos_points1_visible[2] = (phi > Math.PI/2) || (dxx < 0) || (dyx < 0);
                pos_points1_visible[3] = (phi > Math.PI/2) || (dxx > 0) || (dyx > 0);
                pos_points1_visible[6] = (phi > Math.PI/2) || (dxy > 0) || (dyy > 0);
            }

            /*
            if (jc.stereo) {
                Coordinate c = position.getCoordinate();
                Coordinate c2 = anim.ren2.getScreenTranslatedCoordinate(c, 1, 0);
                Coordinate dc = Coordinate.sub(c, c2);
                double dl = Math.sqrt(dc.x*dc.x + dc.y*dc.y + dc.z*dc.z);
                int boxhw = (int)(0.5 + 5.0 / dl);  // pixels corresponding to 5cm in juggler space

                int[] center = anim.ren2.getXY(c);
                xlow2 = center[0] - boxhw;
                ylow2 = center[1] - boxhw;
                xhigh2 = center[0] + boxhw;
                yhigh2 = center[1] + boxhw;
            }
            */
        }
    }

    protected boolean isInsidePolygon(int x, int y, int[] points) {
        for (int i = 0; i < points.length; i++) {
            if (!pos_points1_visible[points[i]])
                return false;
        }

        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            int xi = pos_points1[points[i]][0];
            int yi = pos_points1[points[i]][1];
            int xj = pos_points1[points[j]][0];
            int yj = pos_points1[points[j]][1];

            // note we only evaluate the second term when yj != yi:
            boolean intersect = ((yi > y) != (yj > y)) &&
                            (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect)
                inside = !inside;
        }

        return inside;
    }

    protected void drawPosition(Graphics g) throws JuggleExceptionInternal {
        if (!position_active)
            return;

        Dimension d = getSize();
        Graphics g2 = g;
        if (jc.stereo)
            g2 = g.create(0, 0, d.width / 2, d.height);

        if (g2 instanceof Graphics2D) {
            Graphics2D g22 = (Graphics2D)g2;
            g22.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g2.setColor(Color.green);

        // dot at center
        g2.fillOval(pos_points1[8][0] - 2 + xdelta, pos_points1[8][1] - 2 + ydelta, 5, 5);

        // edges of cube
        drawLine(g2, 1, 4);
        drawLine(g2, 4, 7);
        drawLine(g2, 7, 5);
        drawLine(g2, 5, 1);
        drawLine(g2, 0, 2);
        drawLine(g2, 2, 6);
        drawLine(g2, 6, 3);
        drawLine(g2, 3, 0);
        drawLine(g2, 0, 1);
        drawLine(g2, 2, 4);
        drawLine(g2, 3, 5);
        drawLine(g2, 6, 7);

        // angle-changing control pointing forward
        if (!dragging || dragging_angle) {
            drawLine(g2, 8, 9);
            g2.fillOval(pos_points1[9][0] - 4 + xdelta, pos_points1[9][1] - 4 + ydelta, 10, 10);
        }
        if (dragging_xy) {
            drawLine(g2, 10, 11);
            drawLine(g2, 12, 13);
        }
        if (dragging_xz) {
            drawLine(g2, 10, 11);
            drawLine(g2, 14, 15);
        }
        if (dragging_yz) {
            drawLine(g2, 12, 13);
            drawLine(g2, 14, 15);
        }
        if (dragging_angle) {
            drawLine(g2, 14, 15);
        }

        /*
        if (jc.stereo) {
            g2 = g.create(d.width/2, 0, d.width / 2, d.height);
            if (g2 instanceof Graphics2D) {
                Graphics2D g22 = (Graphics2D)g2;
                g22.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g2.setColor(Color.green);
            g2.drawLine(xlow2+xdelta, ylow2+ydelta, xhigh2+xdelta, ylow2+ydelta);
            g2.drawLine(xhigh2+xdelta, ylow2+ydelta, xhigh2+xdelta, yhigh2+ydelta);
            g2.drawLine(xhigh2+xdelta, yhigh2+ydelta, xlow2+xdelta, yhigh2+ydelta);
            g2.drawLine(xlow2+xdelta, yhigh2+ydelta, xlow2+xdelta, ylow2+ydelta);
        }
        */
    }

    protected void drawLine(Graphics g, int p1, int p2) {
        if (pos_points1_visible[p1] && pos_points1_visible[p2]) {
            g.drawLine(pos_points1[p1][0] + xdelta, pos_points1[p1][1] + ydelta,
                       pos_points1[p2][0] + xdelta, pos_points1[p2][1] + ydelta);
        }
    }

    // javax.swing.JComponent methods

    @Override
    public void paintComponent(Graphics g) {
        if (message != null)
            drawString(message, g);
        else if (engineRunning && !writingGIF) {
            try {
                anim.drawFrame(getTime(), g, cameradrag);
                drawEvent(g);
                drawPosition(g);
            } catch (JuggleExceptionInternal jei) {
                killAnimationThread();
                System.out.println(jei.getMessage());
                System.exit(0);
            }
        }
    }
}
