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
import jugglinglab.renderer.Renderer;


// This subclass of AnimationPanel is used by Edit view. It adds functionality
// for interacting with on-screen representations of JML events and positions,
// and for interacting with a ladder diagram.

public class AnimationEditPanel extends AnimationPanel
                                implements MouseListener, MouseMotionListener {
    // constants for rendering events
    protected static final double EVENT_BOX_HW_CM = 5;
    protected static final double YZ_EVENT_SNAP_CM = 3;
    protected static final double XZ_CONTROL_SHOW_DEG = 60;
    protected static final double Y_CONTROL_SHOW_DEG = 30;

    // constants for rendering positions
    protected static final double POSITION_BOX_HW_CM = 10;
    protected static final double POSITION_BOX_Z_OFFSET_CM = 0;
    protected static final double XY_GRID_SPACING_CM = 20;
    protected static final double XYZ_GRID_POSITION_SNAP_CM = 3;
    protected static final double GRID_SHOW_DEG = 70;
    protected static final double ANGLE_CONTROL_SHOW_DEG = 70;
    protected static final double XY_CONTROL_SHOW_DEG = 70;
    protected static final double Z_CONTROL_SHOW_DEG = 30;

    protected LadderDiagram ladder;

    // for when an event is activated/dragged
    protected boolean event_active;
    protected JMLEvent event;
    protected double[][][] event_points;
    protected boolean dragging_xz;
    protected boolean dragging_y;
    protected boolean show_xz_drag_control;
    protected boolean show_y_drag_control;

    // for when a position is activated/dragged
    protected boolean position_active;
    protected JMLPosition position;
    protected double[][][] pos_points;
    protected boolean dragging_xy;
    protected boolean dragging_z;
    protected boolean dragging_angle;
    protected boolean show_xy_drag_control;
    protected boolean show_z_drag_control;
    protected boolean show_angle_drag_control;

    // for when a position angle is being dragged
    protected double deltaangle;
    protected double[] start_dx, start_dy, start_control;

    // for when either an event or position is being dragged
    protected boolean dragging;
    protected boolean dragging_left;  // for stereo mode; may not be necessary?
    protected int deltax, deltay;  // extent of drag action (pixels)


    public AnimationEditPanel() {
        super();
    }

    public void setLadderDiagram(LadderDiagram lad) {
        ladder = lad;
    }

    //-------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //-------------------------------------------------------------------------

    long lastpress = 0L;
    long lastenter = 1L;

    @Override
    public void mousePressed(MouseEvent me) {
        lastpress = me.getWhen();

        // The following (and its equivalent in mouseReleased()) is a hack to
        // swallow a mouseclick when the browser stops reporting enter/exit
        // events because the user has clicked on something else. The system
        // reports simultaneous enter/press events when the user mouses down in
        // the component; we want to not treat this as a click, but just use it
        // to get focus back.
        if (jc.mousePause && lastpress == lastenter)
            return;

        if (!engineAnimating)
            return;
        if (writingGIF)
            return;

        startx = me.getX();
        starty = me.getY();

        if (event_active) {
            int mx = me.getX();
            int my = me.getY();

            for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                int t = i * getSize().width / 2;

                if (show_y_drag_control) {
                    dragging_y = JLFunc.isNearLine(mx - t, my,
                                        (int)Math.round(event_points[i][5][0]),
                                        (int)Math.round(event_points[i][5][1]),
                                        (int)Math.round(event_points[i][6][0]),
                                        (int)Math.round(event_points[i][6][1]),
                                        4);

                    if (dragging_y) {
                        dragging = true;
                        dragging_left = (i == 0);
                        deltax = deltay = 0;
                        repaint();
                        return;
                    }
                }

                if (show_xz_drag_control) {
                    dragging_xz = isInsidePolygon(mx - t, my, event_points, i, face_xz);

                    if (dragging_xz) {
                        dragging = true;
                        dragging_left = (i == 0);
                        deltax = deltay = 0;
                        repaint();
                        return;
                    }
                }
            }
        }

        if (position_active) {
            int mx = me.getX();
            int my = me.getY();

            for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                int t = i * getSize().width / 2;

                if (show_z_drag_control) {
                    dragging_z = JLFunc.isNearLine(mx - t, my,
                                        (int)Math.round(pos_points[i][4][0]),
                                        (int)Math.round(pos_points[i][4][1]),
                                        (int)Math.round(pos_points[i][6][0]),
                                        (int)Math.round(pos_points[i][6][1]),
                                        4);

                    if (dragging_z) {
                        dragging = true;
                        dragging_left = (i == 0);
                        deltax = deltay = 0;
                        repaint();
                        return;
                    }
                }

                if (show_xy_drag_control) {
                    dragging_xy = isInsidePolygon(mx - t, my, pos_points, i, face_xy);

                    if (dragging_xy) {
                        dragging = true;
                        dragging_left = (i == 0);
                        deltax = deltay = 0;
                        repaint();
                        return;
                    }
                }

                if (show_angle_drag_control) {
                    int dmx = mx - t - (int)Math.round(pos_points[i][5][0]);
                    int dmy = my - (int)Math.round(pos_points[i][5][1]);
                    dragging_angle = (dmx * dmx + dmy * dmy < 49.0);

                    if (dragging_angle) {
                        dragging = true;
                        dragging_left = (i == 0);
                        deltax = deltay = 0;

                        // record pixel coordinates of x and y unit vectors
                        // in juggler's frame, at start of angle drag
                        start_dx = new double[] {
                            pos_points[i][11][0] - pos_points[i][4][0],
                            pos_points[i][11][1] - pos_points[i][4][1]
                        };
                        start_dy = new double[] {
                            pos_points[i][12][0] - pos_points[i][4][0],
                            pos_points[i][12][1] - pos_points[i][4][1]
                        };
                        start_control = new double[] {
                            pos_points[i][5][0] - pos_points[i][4][0],
                            pos_points[i][5][1] - pos_points[i][4][1]
                        };

                        repaint();
                        return;
                    }
                }
            }
        }
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

        boolean mouse_moved = (me.getX() != startx) || (me.getY() != starty);

        if (event_active && dragging && mouse_moved) {
            // set the new coordinate in the master event
            JMLEvent master = (event.isMaster() ? event : event.getMaster());
            boolean flipx = (event.getHand() != master.getHand());
            Coordinate deltalc = Coordinate.sub(getCurrentCoordinate(),
                                    event.getLocalCoordinate());
            deltalc = Coordinate.truncate(deltalc, 1e-7);
            if (flipx)
                deltalc.x = -deltalc.x;
            master.setLocalCoordinate(Coordinate.add(master.getLocalCoordinate(), deltalc));

            dragging_xz = dragging_y = false;

            if (ladder instanceof EditLadderDiagram)
                ((EditLadderDiagram)ladder).activeEventChanged();
        }

        if (position_active && dragging && mouse_moved) {
            if (dragging_angle) {
                double angle = Math.toRadians(position.getAngle());
                double new_angle = Math.toDegrees(angle + deltaangle);
                while (new_angle > 360.0)
                    new_angle -= 360.0;
                while (new_angle < 0.0)
                    new_angle += 360.0;
                position.setAngle(new_angle);
            } else {
                position.setCoordinate(getCurrentCoordinate());
            }

            dragging_xy = dragging_z = dragging_angle = false;
            deltaangle = 0.0;

            if (ladder instanceof EditLadderDiagram)
                ((EditLadderDiagram)ladder).activePositionChanged();
        }

        if (!mouse_moved && !dragging && engine != null && engine.isAlive())
            setPaused(!enginePaused);

        dragging_camera = false;
        dragging = false;
        dragging_xz = dragging_y = false;
        dragging_xy = dragging_z = dragging_angle = false;
        deltax = deltay = 0;
        deltaangle = 0.0;
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

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

    //-------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //-------------------------------------------------------------------------

    @Override
    public void mouseDragged(MouseEvent me) {
        if (!engineAnimating)
            return;
        if (writingGIF)
            return;

        if (dragging) {
            int mx = me.getX();
            int my = me.getY();

            if (dragging_angle) {
                // shift pixel coords of control point by mouse drag
                double dcontrol[] = {
                    start_control[0] + mx - startx,
                    start_control[1] + my - starty
                };

                // re-express control point location in coordinate
                // system of juggler:
                //
                // dcontrol_x = A * start_dx_x + B * start_dy_x;
                // dcontrol_y = A * start_dx_y + B * start_dy_y;
                //
                // then (A, B) are coordinates of shifted control
                // point, in juggler space

                double det = start_dx[0] * start_dy[1] - start_dx[1] * start_dy[0];
                double a = ( start_dy[1] * dcontrol[0] - start_dy[0] * dcontrol[1]) / det;
                double b = (-start_dx[1] * dcontrol[0] + start_dx[0] * dcontrol[1]) / det;
                deltaangle = -Math.atan2(-a, -b);

                // snap the angle to the four cardinal directions
                double angle = Math.toRadians(position.getAngle());
                double new_angle = angle + deltaangle;
                if (anglediff(new_angle) < snapangle / 2)
                    deltaangle = -angle;
                else if (anglediff(new_angle + 0.5 * Math.PI) < snapangle / 2)
                    deltaangle = -angle - 0.5 * Math.PI;
                else if (anglediff(new_angle + Math.PI) < snapangle / 2)
                    deltaangle = -angle + Math.PI;
                else if (anglediff(new_angle + 1.5 * Math.PI) < snapangle / 2)
                    deltaangle = -angle + 0.5 * Math.PI;
            } else {
                deltax = mx - startx;
                deltay = my - starty;
                getCurrentCoordinate();  // modifies deltax, deltay based on snapping, etc.
            }
        } else if (!dragging_camera) {
            dragging_camera = true;
            lastx = startx;
            lasty = starty;
            dragcamangle = anim.getCameraAngle();
        }

        if (dragging_camera) {
            int dx = me.getX() - lastx;
            int dy = me.getY() - lasty;
            lastx = me.getX();
            lasty = me.getY();
            double[] ca = dragcamangle;
            ca[0] += (double)dx * 0.02;
            ca[1] -= (double)dy * 0.02;
            if (ca[1] < Math.toRadians(0.0001))
                ca[1] = Math.toRadians(0.0001);
            if (ca[1] > Math.toRadians(179.9999))
                ca[1] = Math.toRadians(179.9999);
            while (ca[0] < 0.0)
                ca[0] += Math.toRadians(360.0);
            while (ca[0] >= Math.toRadians(360.0))
                ca[0] -= Math.toRadians(360.0);

            anim.setCameraAngle(snapCamera(ca));
        }

        if (event_active && dragging_camera)
            createEventView();
        if (position_active && (dragging_camera || dragging_angle))
            createPositionView();
        if (isPaused())
            repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

    //-------------------------------------------------------------------------
    // AnimationPanel methods
    //-------------------------------------------------------------------------

    @Override
    protected void initHandlers() {
        addMouseListener(this);
        addMouseMotionListener(this);

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
                if (position_active)
                    createPositionView();
                if (isPaused())
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

        double a = 0;
        boolean snap_horizontal = true;

        if (event_active) {
            a = -Math.toRadians(anim.pat.getJugglerAngle(event.getJuggler(), event.getT()));
        } else if (position_active) {
            //a = -Math.toRadians(anim.pat.getJugglerAngle(position.getJuggler(), position.getT()));
            a = 0;
        } else if (anim.pat.getNumberOfJugglers() == 1) {
            a = -Math.toRadians(anim.pat.getJugglerAngle(1, getTime()));
        } else
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
        if (position_active)
            createPositionView();
    }

    // set position of tracker bar in ladder diagram as we animate
    @Override
    public void setTime(double time) {
        super.setTime(time);
        if (ladder != null)
            ladder.setTime(time);
    }

    @Override
    public void setZoomLevel(double z) {
        if (!writingGIF) {
            getAnimator().setZoomLevel(z);
            createEventView();
            createPositionView();
            repaint();
        }
    }

    //-------------------------------------------------------------------------
    // Helper functions related to event editing
    //-------------------------------------------------------------------------

    public void activateEvent(JMLEvent ev) {
        deactivatePosition();
        event = ev;
        event_active = true;
        createEventView();
    }

    public void deactivateEvent() {
        event = null;
        event_active = false;
        dragging_xz = dragging_y = false;
    }

    // Points in the juggler's coordinate system that are used for drawing the
    // onscreen representation of a selected event.
    protected static final double[][] event_control_points =
        {
            // corners of square representing xz movement control
            { -EVENT_BOX_HW_CM, 0, -EVENT_BOX_HW_CM },
            { -EVENT_BOX_HW_CM, 0,  EVENT_BOX_HW_CM },
            {  EVENT_BOX_HW_CM, 0,  EVENT_BOX_HW_CM },
            {  EVENT_BOX_HW_CM, 0, -EVENT_BOX_HW_CM },

            { 0,   0,  0 },  // center
            { 0,  10,  0 },  // end 1 of y-axis control
            { 0, -10,  0 },  // end 2 of y-axis control
            { 0,   7,  2 },  // arrow at end 1 of y-axis control
            { 0,   7, -2 },
            { 0,  -7,  2 },  // array at end 2 of y-axis control
            { 0,  -7, -2 },

            // used for moving the event
            { 1, 0, 0 },
            { 0, 1, 0 },
            { 0, 0, 1 },
        };

    // faces in terms of indices in event_control_points[]
    protected static final int[] face_xz = { 0, 1, 2, 3 };

    protected void createEventView() {
        if (!event_active)
            return;

        event_points = new double[2][event_control_points.length][2];

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            // translate by one pixel and see how far it is in juggler space
            Coordinate c = event.getGlobalCoordinate();
            Coordinate c2 = ren.getScreenTranslatedCoordinate(c, 1, 0);
            double dl = 1.0 / Coordinate.distance(c, c2);  // pixels/cm

            double[] ca = ren.getCameraAngle();
            double theta = ca[0] + Math.toRadians(
                    getPattern().getJugglerAngle(event.getJuggler(), event.getT()));
            double phi = ca[1];

            double dlc = dl * Math.cos(phi);
            double dls = dl * Math.sin(phi);
            double dxx = -dl * Math.cos(theta);
            double dxy = dlc * Math.sin(theta);
            double dyx = dl * Math.sin(theta);
            double dyy = dlc * Math.cos(theta);
            double dzx = 0.0;
            double dzy = -dls;

            int[] center = ren.getXY(c);
            for (int j = 0; j < event_control_points.length; j++) {
                event_points[i][j][0] = (double)center[0] +
                                        dxx * event_control_points[j][0] +
                                        dyx * event_control_points[j][1] +
                                        dzx * event_control_points[j][2];
                event_points[i][j][1] = (double)center[1] +
                                        dxy * event_control_points[j][0] +
                                        dyy * event_control_points[j][1] +
                                        dzy * event_control_points[j][2];
            }

            show_y_drag_control = (anglediff(theta) > Math.toRadians(Y_CONTROL_SHOW_DEG) ||
                    anglediff(phi - Math.PI/2) > Math.toRadians(Y_CONTROL_SHOW_DEG));
            show_xz_drag_control = (anglediff(phi - Math.PI/2) < Math.toRadians(XZ_CONTROL_SHOW_DEG) &&
                    anglediff(theta) < Math.toRadians(XZ_CONTROL_SHOW_DEG));
        }
    }

    protected void drawEvent(Graphics g) throws JuggleExceptionInternal {
        if (!event_active)
            return;

        Dimension d = getSize();
        Graphics g2 = g;

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            if (jc.stereo && i == 0)
                g2 = g.create(0, 0, d.width / 2, d.height);
            else if (jc.stereo && i == 1)
                g2 = g.create(d.width / 2, 0, d.width / 2, d.height);

            if (g2 instanceof Graphics2D) {
                ((Graphics2D)g2).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
            }

            g2.setColor(Color.green);

            // dot at center
            g2.fillOval((int)Math.round(event_points[i][4][0]) + deltax - 2,
                        (int)Math.round(event_points[i][4][1]) + deltay - 2, 5, 5);

            if (show_xz_drag_control || dragging) {
                // edges of xz plane control
                drawLine(g2, event_points, i, 0, 1);
                drawLine(g2, event_points, i, 1, 2);
                drawLine(g2, event_points, i, 2, 3);
                drawLine(g2, event_points, i, 3, 0);
            }

            if (show_y_drag_control && (!dragging || dragging_y)) {
                // y-axis control pointing forward/backward
                drawLine(g2, event_points, i, 5, 6);
                drawLine(g2, event_points, i, 5, 7);
                drawLine(g2, event_points, i, 5, 8);
                drawLine(g2, event_points, i, 6, 9);
                drawLine(g2, event_points, i, 6, 10);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Helper functions related to position editing
    //-------------------------------------------------------------------------

    public void activatePosition(JMLPosition pos) {
        deactivateEvent();
        position = pos;
        position_active = true;
        createPositionView();
    }

    public void deactivatePosition() {
        position = null;
        position_active = false;
        dragging_xy = dragging_z = dragging_angle = false;
    }

    // Points in the juggler's coordinate system that are used for drawing the
    // onscreen representation of a selected position.
    protected static final double[][] pos_control_points =
        {
            // corners of square representing xy movement control
            { -POSITION_BOX_HW_CM, -POSITION_BOX_HW_CM,  0 },
            { -POSITION_BOX_HW_CM,  POSITION_BOX_HW_CM,  0 },
            {  POSITION_BOX_HW_CM,  POSITION_BOX_HW_CM,  0 },
            {  POSITION_BOX_HW_CM, -POSITION_BOX_HW_CM,  0 },

            {  0,    0,   0 },  // center
            {  0,  -20,   0 },  // angle control point
            {  0,    0,  20 },  // end of z-vector control
            {  2,    0,  17 },  // arrow at end of z-vector control
            { -2,    0,  17 },
            {  0, -250,   0 },  // direction-sighting line when dragging angle
            {  0,  250,   0 },

            // used for moving the position
            { 1, 0, 0 },
            { 0, 1, 0 },
            { 0, 0, 1 },
        };

    // faces in terms of indices in pos_control_points[]
    protected static final int[] face_xy = { 0, 1, 2, 3 };

    protected void createPositionView() {
        if (!position_active)
            return;

        pos_points = new double[2][pos_control_points.length][2];

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            // translate by one pixel and see how far it is in juggler space
            Coordinate c = Coordinate.add(position.getCoordinate(),
                                    new Coordinate(0, 0, POSITION_BOX_Z_OFFSET_CM));
            Coordinate c2 = ren.getScreenTranslatedCoordinate(c, 1, 0);
            double dl = 1.0 / Coordinate.distance(c, c2);  // pixels/cm

            double[] ca = ren.getCameraAngle();
            double theta = ca[0] + Math.toRadians(position.getAngle()) + deltaangle;
            double phi = ca[1];

            double dlc = dl * Math.cos(phi);
            double dls = dl * Math.sin(phi);
            double dxx = -dl * Math.cos(theta);
            double dxy = dlc * Math.sin(theta);
            double dyx = dl * Math.sin(theta);
            double dyy = dlc * Math.cos(theta);
            double dzx = 0.0;
            double dzy = -dls;

            int[] center = ren.getXY(c);
            for (int j = 0; j < pos_control_points.length; j++) {
                pos_points[i][j][0] = (double)center[0] +
                                        dxx * pos_control_points[j][0] +
                                        dyx * pos_control_points[j][1] +
                                        dzx * pos_control_points[j][2];
                pos_points[i][j][1] = (double)center[1] +
                                        dxy * pos_control_points[j][0] +
                                        dyy * pos_control_points[j][1] +
                                        dzy * pos_control_points[j][2];
            }

            show_angle_drag_control = (anglediff(phi - Math.PI/2) >
                            Math.toRadians(90 - ANGLE_CONTROL_SHOW_DEG));
            show_xy_drag_control = (anglediff(phi - Math.PI/2) >
                            Math.toRadians(90 - XY_CONTROL_SHOW_DEG));
            show_z_drag_control = (anglediff(phi - Math.PI/2) <
                            Math.toRadians(90 - Z_CONTROL_SHOW_DEG));
        }
    }

    protected void drawPosition(Graphics g) throws JuggleExceptionInternal {
        if (!position_active)
            return;

        Dimension d = getSize();
        Graphics g2 = g;

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            if (jc.stereo && i == 0)
                g2 = g.create(0, 0, d.width / 2, d.height);
            else if (jc.stereo && i == 1)
                g2 = g.create(d.width / 2, 0, d.width / 2, d.height);

            if (g2 instanceof Graphics2D) {
                ((Graphics2D)g2).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
            }

            g2.setColor(Color.green);

            // dot at center
            g2.fillOval((int)Math.round(pos_points[i][4][0]) + deltax - 2,
                        (int)Math.round(pos_points[i][4][1]) + deltay - 2, 5, 5);

            if (show_xy_drag_control || dragging) {
                // edges of xy plane control
                drawLine(g2, pos_points, i, 0, 1);
                drawLine(g2, pos_points, i, 1, 2);
                drawLine(g2, pos_points, i, 2, 3);
                drawLine(g2, pos_points, i, 3, 0);
            }

            if (show_z_drag_control && (!dragging || dragging_z)) {
                // z-axis control pointing upward
                drawLine(g2, pos_points, i, 4, 6);
                drawLine(g2, pos_points, i, 6, 7);
                drawLine(g2, pos_points, i, 6, 8);
            }

            if (show_angle_drag_control && (!dragging || dragging_angle)) {
                // angle-changing control pointing backward
                drawLine(g2, pos_points, i, 4, 5);
                g2.fillOval((int)Math.round(pos_points[i][5][0]) - 4 + deltax,
                            (int)Math.round(pos_points[i][5][1]) - 4 + deltay, 10, 10);
            }

            if (dragging_angle) {
                // sighting line during angle rotation
                drawLine(g2, pos_points, i, 9, 10);
            }

            if (!dragging_angle) {
                if (dragging_z || (getCameraAngle()[1] <= Math.toRadians(GRID_SHOW_DEG))) {
                    // line dropping down to projection on ground (z = 0)
                    Coordinate c = getCurrentCoordinate();
                    double z = c.z;
                    c.z = 0;
                    int[] xy_projection = ren.getXY(c);
                    g2.drawLine(xy_projection[0], xy_projection[1],
                                (int)Math.round(pos_points[i][4][0]) + deltax,
                                (int)Math.round(pos_points[i][4][1]) + deltay);
                    g2.fillOval(xy_projection[0] - 2, xy_projection[1] - 2, 5, 5);

                    if (dragging_z) {
                        // z-label on the line
                        double y = Math.max(
                                        Math.max(pos_points[i][0][1], pos_points[i][1][1]),
                                        Math.max(pos_points[i][2][1], pos_points[i][3][1])
                                   );
                        int message_y = (int)Math.round(y) + deltay + 40;

                        g2.setColor(Color.black);
                        g2.drawString("z = " + JLFunc.toStringRounded(z, 1) + " cm",
                                    xy_projection[0] + 5, message_y);
                    }
                }
            }
        }
    }

    // In position editing mode, draw an xy grid at ground level (z = 0)
    protected void drawGrid(Graphics g) {
        if (!(g instanceof Graphics2D))
            return;

        if (!position_active)
            return;

        // only draw grid when looking down from above
        if (getCameraAngle()[1] > Math.toRadians(GRID_SHOW_DEG))
            return;

        Graphics2D g2 = (Graphics2D)g;
        g2.setColor(Color.lightGray);
        Dimension d = getSize();

        int width = (jc.stereo ? d.width / 2 : d.width);

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            if (jc.stereo && i == 0)
                g2 = (Graphics2D)g.create(0, 0, d.width / 2, d.height);
            else if (jc.stereo && i == 1)
                g2 = (Graphics2D)g.create(d.width / 2, 0, d.width / 2, d.height);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);

            // Figure out pixel deltas for 1cm vectors along x and y axes
            int[] center = ren.getXY(new Coordinate(0, 0, 0));

            int[] dx = ren.getXY(new Coordinate(100,   0,   0));
            int[] dy = ren.getXY(new Coordinate(  0, 100,   0));
            double[] xaxis_spacing = {
                XY_GRID_SPACING_CM * ((double)(dx[0] - center[0]) / 100.0),
                XY_GRID_SPACING_CM * ((double)(dx[1] - center[1]) / 100.0)
            };
            double[] yaxis_spacing = {
                XY_GRID_SPACING_CM * ((double)(dy[0] - center[0]) / 100.0),
                XY_GRID_SPACING_CM * ((double)(dy[1] - center[1]) / 100.0)
            };

            double[] axis1 = xaxis_spacing;
            double[] axis2 = yaxis_spacing;

            // Find which grid intersections are visible on screen by solving
            // for the grid coordinates at the four corners.
            double det = axis1[0] * axis2[1] - axis1[1] * axis2[0];
            int mmin = 0;
            int mmax = 0;
            int nmin = 0;
            int nmax = 0;
            for (int j = 0; j < 4; j++) {
                double a = (j % 2 == 0 ? 0 : width) - center[0];
                double b = (j < 2 ? 0 : d.height) - center[1];

                double m = (axis2[1] * a - axis2[0] * b) / det;
                double n = (-axis1[1] * a + axis1[0] * b) / det;
                int mint = (int)Math.floor(m);
                int nint = (int)Math.floor(n);
                mmin = (j == 0 ? mint : Math.min(mmin, mint));
                mmax = (j == 0 ? mint + 1 : Math.max(mmax, mint + 1));
                nmin = (j == 0 ? nint : Math.min(nmin, nint));
                nmax = (j == 0 ? nint + 1 : Math.max(nmax, nint + 1));
            }

            for (int j = mmin; j <= mmax; j++) {
                int x1 = (int)Math.round(center[0] + j * axis1[0] + nmin * axis2[0]);
                int y1 = (int)Math.round(center[1] + j * axis1[1] + nmin * axis2[1]);
                int x2 = (int)Math.round(center[0] + j * axis1[0] + nmax * axis2[0]);
                int y2 = (int)Math.round(center[1] + j * axis1[1] + nmax * axis2[1]);
                if (j == 0)
                    g2.setStroke(new BasicStroke(3));
                g2.drawLine(x1, y1, x2, y2);
                if (j == 0)
                    g2.setStroke(new BasicStroke(1));
            }
            for (int j = nmin; j <= nmax; j++) {
                int x1 = (int)Math.round(center[0] + mmin * axis1[0] + j * axis2[0]);
                int y1 = (int)Math.round(center[1] + mmin * axis1[1] + j * axis2[1]);
                int x2 = (int)Math.round(center[0] + mmax * axis1[0] + j * axis2[0]);
                int y2 = (int)Math.round(center[1] + mmax * axis1[1] + j * axis2[1]);
                if (j == 0)
                    g2.setStroke(new BasicStroke(3));
                g2.drawLine(x1, y1, x2, y2);
                if (j == 0)
                    g2.setStroke(new BasicStroke(1));
            }
        }
    }

    //-------------------------------------------------------------------------
    // Helper functions for both event and position editing
    //-------------------------------------------------------------------------

    // Return the current coordinate for the selected item.
    //
    // For an event the result is in the juggler's local coordinates, and for a
    // position it's in global coordinates.
    //
    // When dragging, this includes any offset from its original coordinate
    // based on mouse deltas and dragging mode.
    //
    // When the user is dragging, this also snaps to selected grid lines, and
    // adjusts the returned Coordinate accordingly. When a grid snap occurs,
    // `deltax` and `deltay` are adjusted so that the item displays in its
    // snapped position.
    protected Coordinate getCurrentCoordinate() {
        if (event_active) {
            Coordinate c = event.getLocalCoordinate();

            if (!dragging)
                return c;

            // screen (pixel) offset of a 1cm offset in each of the cardinal
            // directions in the juggler's coordinate system (i.e., global
            // coordinates rotated by the juggler's angle)
            double dx[] = { 0, 0 };
            double dy[] = { 0, 0 };
            double dz[] = { 0, 0 };
            double f = (jc.stereo ? 0.5 : 1.0);

            for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                dx[0] += f * (event_points[i][11][0] - event_points[i][4][0]);
                dx[1] += f * (event_points[i][11][1] - event_points[i][4][1]);
                dy[0] += f * (event_points[i][12][0] - event_points[i][4][0]);
                dy[1] += f * (event_points[i][12][1] - event_points[i][4][1]);
                dz[0] += f * (event_points[i][13][0] - event_points[i][4][0]);
                dz[1] += f * (event_points[i][13][1] - event_points[i][4][1]);
            }

            if (dragging_xz) {
                // express deltax, deltay in terms of dx, dz above
                //
                // deltax = A * dxx + B * dzx;
                // deltay = A * dxy + B * dzy;
                //
                // then c.x += A
                //      c.z += B
                double det = dx[0] * dz[1] - dx[1] * dz[0];
                double a = ( dz[1] * deltax - dz[0] * deltay) / det;
                double b = (-dx[1] * deltax + dx[0] * deltay) / det;

                c.x += a;
                c.z += b;

                // Snap to z = 0 in local coordinates ("normal" throwing height)
                if (Math.abs(c.z) < YZ_EVENT_SNAP_CM) {
                    deltay += (int)Math.round(dz[1] * (-c.z));
                    c.z = 0;
                }
            }

            if (dragging_y) {
                // express deltax, deltay in terms of dy, dz above
                //
                // deltax = A * dyx + B * dzx;
                // deltay = A * dyy + B * dzy;
                //
                // then c.y += A
                double det = dy[0] * dz[1] - dy[1] * dz[0];
                double a = ( dz[1] * deltax - dz[0] * deltay) / det;

                c.y += a;

                // Snap to y = 0 in local coordinates ("normal" throwing depth)
                if (Math.abs(c.y) < YZ_EVENT_SNAP_CM)
                    c.y = 0;

                // Calculate `deltax`, `deltay` that put the event closest to
                // its final location.
                Coordinate origlc = event.getLocalCoordinate();

                deltax = (int)Math.round((c.y - origlc.y) * dy[0]);
                deltay = (int)Math.round((c.y - origlc.y) * dy[1]);
            }

            return c;
        }

        if (position_active) {
            Coordinate c = position.getCoordinate();

            if (!dragging_xy && !dragging_z)
                return c;

            // screen (pixel) offset of a 1cm offset in each of the cardinal
            // directions in the position's coordinate system (i.e., global
            // coordinates rotated by the position's angle)
            double dx[] = { 0, 0 };
            double dy[] = { 0, 0 };
            double dz[] = { 0, 0 };
            double f = (jc.stereo ? 0.5 : 1.0);

            for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                dx[0] += f * (pos_points[i][11][0] - pos_points[i][4][0]);
                dx[1] += f * (pos_points[i][11][1] - pos_points[i][4][1]);
                dy[0] += f * (pos_points[i][12][0] - pos_points[i][4][0]);
                dy[1] += f * (pos_points[i][12][1] - pos_points[i][4][1]);
                dz[0] += f * (pos_points[i][13][0] - pos_points[i][4][0]);
                dz[1] += f * (pos_points[i][13][1] - pos_points[i][4][1]);
            }

            if (dragging_xy) {
                // express deltax, deltay in terms of dx, dy above
                //
                // deltax = A * dxx + B * dyx;
                // deltay = A * dxy + B * dyy;
                //
                // then position.x += A
                //      position.y += B
                double det = dx[0] * dy[1] - dx[1] * dy[0];
                double a = ( dy[1] * deltax - dy[0] * deltay) / det;
                double b = (-dx[1] * deltax + dx[0] * deltay) / det;

                // transform changes to global coordinates
                double angle = Math.toRadians(position.getAngle());
                c.x += a * Math.cos(angle) - b * Math.sin(angle);
                c.y += a * Math.sin(angle) + b * Math.cos(angle);

                // Snap to selected grid lines

                boolean snapped = false;
                double oldcx = c.x;
                double oldcy = c.y;

                double closest_grid = XY_GRID_SPACING_CM *
                                            Math.round(c.x / XY_GRID_SPACING_CM);
                if (Math.abs(c.x - closest_grid) < XYZ_GRID_POSITION_SNAP_CM) {
                    c.x = closest_grid;
                    snapped = true;
                }
                closest_grid = XY_GRID_SPACING_CM *
                                            Math.round(c.y / XY_GRID_SPACING_CM);
                if (Math.abs(c.y - closest_grid) < XYZ_GRID_POSITION_SNAP_CM) {
                    c.y = closest_grid;
                    snapped = true;
                }

                if (snapped) {
                    // Calculate `deltax` and `deltay` that get us closest to the
                    // snapped position.
                    double deltacx = c.x - oldcx;
                    double deltacy = c.y - oldcy;
                    double deltaa = deltacx * Math.cos(angle) + deltacy * Math.sin(angle);
                    double deltab = -deltacx * Math.sin(angle) + deltacy * Math.cos(angle);
                    double delta_x_px = dx[0] * deltaa + dy[0] * deltab;
                    double delta_y_px = dx[1] * deltaa + dy[1] * deltab;

                    deltax += (int)Math.round(delta_x_px);
                    deltay += (int)Math.round(delta_y_px);
                }
            }

            if (dragging_z) {
                deltax = 0;  // constrain movement to be vertical
                c.z += deltay / dz[1];

                if (Math.abs(c.z - 100) < XYZ_GRID_POSITION_SNAP_CM) {
                    deltay += (int)Math.round(dz[1] * (100 - c.z));
                    c.z = 100;
                }
            }

            return c;
        }

        return null;
    }

    protected void drawLine(Graphics g, double[][][] array, int index, int p1, int p2) {
        g.drawLine((int)Math.round(array[index][p1][0]) + deltax,
                   (int)Math.round(array[index][p1][1]) + deltay,
                   (int)Math.round(array[index][p2][0]) + deltax,
                   (int)Math.round(array[index][p2][1]) + deltay);
    }

    // Test whether a point (x, y) lies inside a polygon.
    protected static boolean isInsidePolygon(
                int x, int y, double[][][] array, int index, int[] points) {
        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            int xi = (int)Math.round(array[index][points[i]][0]);
            int yi = (int)Math.round(array[index][points[i]][1]);
            int xj = (int)Math.round(array[index][points[j]][0]);
            int yj = (int)Math.round(array[index][points[j]][1]);

            // note we only evaluate the second term when yj != yi:
            boolean intersect = ((yi > y) != (yj > y)) &&
                            (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect)
                inside = !inside;
        }

        return inside;
    }

    // javax.swing.JComponent methods

    @Override
    public void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
        }

        if (message != null)
            drawString(message, g);
        else if (engineRunning && !writingGIF) {
            try {
                anim.drawBackground(g);
                drawGrid(g);
                anim.drawFrame(getTime(), g, dragging_camera, false);
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
