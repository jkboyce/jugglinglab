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
// for interacting with on-screen representations of JML events, and for
// interacting with a ladder diagram.

public class AnimationEditPanel extends AnimationPanel {
    protected LadderDiagram ladder;

    public static final double event_box_hw_cm = 5.0;
    public static final double position_box_hw_cm = 5.0;

    // for when an event is activated/dragged
    protected boolean event_active;
    protected JMLEvent event;
    protected int[][] event_box;

    // for when a position is activated/dragged
    protected boolean position_active;
    protected JMLPosition position;
    protected double[][][] pos_points;
    protected boolean[][] pos_points_visible;
    protected boolean dragging_xy;
    protected boolean dragging_xz;
    protected boolean dragging_yz;
    protected boolean dragging_angle;

    // for when a position is dragged in angle
    protected double deltaangle;
    protected double[] start_dx, start_dy, start_control;

    // for when either an event or position is dragged
    protected boolean dragging;
    protected boolean dragging_left;  // may not be necessary
    protected int deltax, deltay;  // extent of drag action (pixels)


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

                startx = me.getX();
                starty = me.getY();

                if (event_active) {
                    int mx = me.getX();
                    int my = me.getY();

                    for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                        int t = i * getSize().width / 2;
                        if (mx >= (event_box[i][0] + t) && mx <= (event_box[i][2] + t) &&
                                    my >= event_box[i][1] && my <= event_box[i][3]) {
                            dragging = true;
                            dragging_left = (i == 0);
                            deltax = deltay = 0;
                            repaint();
                            return;
                        }
                    }
                }

                if (position_active) {
                    int mx = me.getX();
                    int my = me.getY();

                    for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                        int t = i * getSize().width / 2;
                        dragging_xy = (isInsidePolygon(mx - t, my, i, face_xy1) ||
                                       isInsidePolygon(mx - t, my, i, face_xy2));
                        dragging_xz = (isInsidePolygon(mx - t, my, i, face_xz1) ||
                                       isInsidePolygon(mx - t, my, i, face_xz2));
                        dragging_yz = (isInsidePolygon(mx - t, my, i, face_yz1) ||
                                       isInsidePolygon(mx - t, my, i, face_yz2));

                        dragging = dragging_xy || dragging_xz || dragging_yz;

                        if (dragging) {
                            dragging_left = (i == 0);
                            deltax = deltay = 0;
                            repaint();
                            return;
                        }

                        int dmx = mx - t - (int)(0.5 + pos_points[i][9][0]);
                        int dmy = my - (int)(0.5 + pos_points[i][9][1]);
                        dragging_angle = (dmx * dmx + dmy * dmy < 49.0);

                        if (dragging_angle) {
                            dragging = true;
                            dragging_left = (i == 0);
                            deltax = deltay = 0;

                            // record pixel coordinates of x and y unit vectors
                            // in juggler's frame, at start of angle drag
                            start_dx = new double[] {
                                pos_points[i][16][0] - pos_points[i][8][0],
                                pos_points[i][16][1] - pos_points[i][8][1]
                            };
                            start_dy = new double[] {
                                pos_points[i][17][0] - pos_points[i][8][0],
                                pos_points[i][17][1] - pos_points[i][8][1]
                            };
                            start_control = new double[] {
                                pos_points[i][9][0] - pos_points[i][8][0],
                                pos_points[i][9][1] - pos_points[i][8][1]
                            };

                            repaint();
                            return;
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
                    JMLEvent master = (event.isMaster() ? event : event.getMaster());
                    boolean flipx = (event.getHand() != master.getHand());

                    Coordinate newgc = anim.ren1.getScreenTranslatedCoordinate(
                            event.getGlobalCoordinate(), deltax, deltay);
                    if (jc.stereo) {
                        // average the coordinate shifts from each perspective
                        Coordinate newgc2 = anim.ren2.getScreenTranslatedCoordinate(
                                event.getGlobalCoordinate(), deltax, deltay);
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

                if (position_active && dragging && mouse_moved) {
                    double angle = Math.toRadians(position.getAngle());

                    if (dragging_angle) {
                        double new_angle = Math.toDegrees(angle + deltaangle);
                        while (new_angle > 360.0)
                            new_angle -= 360.0;
                        while (new_angle < 0.0)
                            new_angle += 360.0;
                        position.setAngle(new_angle);
                    } else {
                        // screen (pixel) offset of a 1cm offset in each of the
                        // cardinal directions
                        double dx[] = { 0, 0 };
                        double dy[] = { 0, 0 };
                        double dz[] = { 0, 0 };
                        double f = (jc.stereo ? 0.5 : 1.0);

                        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                            dx[0] += f * (pos_points[i][16][0] - pos_points[i][8][0]);
                            dx[1] += f * (pos_points[i][16][1] - pos_points[i][8][1]);
                            dy[0] += f * (pos_points[i][17][0] - pos_points[i][8][0]);
                            dy[1] += f * (pos_points[i][17][1] - pos_points[i][8][1]);
                            dz[0] += f * (pos_points[i][18][0] - pos_points[i][8][0]);
                            dz[1] += f * (pos_points[i][18][1] - pos_points[i][8][1]);
                        }

                        Coordinate c = position.getCoordinate();

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

                            c.x += a * Math.cos(angle) - b * Math.sin(angle);
                            c.y += a * Math.sin(angle) + b * Math.cos(angle);
                        }

                        if (dragging_xz) {
                            double det = dx[0] * dz[1] - dx[1] * dz[0];
                            double a = ( dz[1] * deltax - dz[0] * deltay) / det;
                            double b = (-dx[1] * deltax + dx[0] * deltay) / det;

                            c.x += a * Math.cos(angle);
                            c.y += a * Math.sin(angle);
                            c.z += b;
                        }

                        if (dragging_yz) {
                            double det = dy[0] * dz[1] - dy[1] * dz[0];
                            double a = ( dz[1] * deltax - dz[0] * deltay) / det;
                            double b = (-dy[1] * deltax + dy[0] * deltay) / det;

                            c.x += -a * Math.sin(angle);
                            c.y += a * Math.cos(angle);
                            c.z += b;
                        }

                        position.setCoordinate(c);
                    }

                    dragging_xy = false;
                    dragging_xz = false;
                    dragging_yz = false;
                    dragging_angle = false;

                    if (ladder instanceof EditLadderDiagram)
                        ((EditLadderDiagram)ladder).activePositionChanged();
                }

                if (!mouse_moved && !dragging && engine != null && engine.isAlive())
                    setPaused(!enginePaused);

                dragging_camera = false;
                dragging = dragging_xy = dragging_xz = dragging_yz = dragging_angle = false;
                deltax = deltay = 0;
                deltaangle = 0.0;
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
                        deltaangle = -Math.atan2(a, b);
                    } else {
                        deltax = mx - startx;
                        deltay = my - starty;
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
        if (position_active)
            createPositionView();
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
        if (!event_active)
            return;

        event_box = new int[2][4];

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            // translate by one pixel and see how far it is in juggler space
            Coordinate c = event.getGlobalCoordinate();
            Coordinate c2 = ren.getScreenTranslatedCoordinate(c, 1, 0);
            double dl = Coordinate.distance(c, c2);
            int boxhw = (int)(0.5 + event_box_hw_cm / dl);  // in pixels

            int[] center = ren.getXY(c);
            event_box[i][0] = center[0] - boxhw;
            event_box[i][1] = center[1] - boxhw;
            event_box[i][2] = center[0] + boxhw;
            event_box[i][3] = center[1] + boxhw;
        }
    }

    protected void drawEvent(Graphics g) throws JuggleExceptionInternal {
        if (!event_active)
            return;

        Dimension d = getSize();
        Graphics g2 = g;

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            if (jc.stereo && i == 0)
                g2 = g.create(0, 0, d.width / 2, d.height);
            else if (jc.stereo && i == 1)
                g2 = g.create(d.width / 2, 0, d.width / 2, d.height);

            if (g2 instanceof Graphics2D) {
                ((Graphics2D)g2).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
            }

            g2.setColor(Color.green);
            g2.drawLine(event_box[i][0] + deltax, event_box[i][1] + deltay,
                        event_box[i][2] + deltax, event_box[i][1] + deltay);
            g2.drawLine(event_box[i][2] + deltax, event_box[i][1] + deltay,
                        event_box[i][2] + deltax, event_box[i][3] + deltay);
            g2.drawLine(event_box[i][2] + deltax, event_box[i][3] + deltay,
                        event_box[i][0] + deltax, event_box[i][3] + deltay);
            g2.drawLine(event_box[i][0] + deltax, event_box[i][3] + deltay,
                        event_box[i][0] + deltax, event_box[i][1] + deltay);

            if (dragging) {
                // dot at center
                int center_x = (event_box[i][0] + event_box[i][2]) / 2;
                int center_y = (event_box[i][1] + event_box[i][3]) / 2;

                g2.fillOval(center_x - 2 + deltax, center_y - 2 + deltay, 5, 5);
            }
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

    // Constants for calculating screen coordinates of selected points in
    // the juggler's coordinate system. These are used for drawing the onscreen
    // representation of a selected position.

    protected static final double[][] cube_points =
        {
            // cube corners
            { -1, -1, -1 },
            { -1, -1,  1 },
            { -1,  1, -1 },
            {  1, -1, -1 },
            { -1,  1,  1 },
            {  1, -1,  1 },
            {  1,  1, -1 },
            {  1,  1,  1 },

            // other useful points for drawing
            {  0,  0,  0 },
            {  0,  4,  0 },
            { -4,  0,  0 },
            {  4,  0,  0 },
            {  0, -4,  0 },
            {  0,  4,  0 },
            {  0,  0, -4 },
            {  0,  0,  4 },

            // used for moving the position
            { 1.0 / position_box_hw_cm, 0, 0 },
            { 0, 1.0 / position_box_hw_cm, 0 },
            { 0, 0, 1.0 / position_box_hw_cm },
        };

    // cube faces in terms of indices into cube_points[]
    protected static final int[] face_xy1 = { 1, 4, 7, 5 };
    protected static final int[] face_xy2 = { 0, 2, 6, 3 };
    protected static final int[] face_xz1 = { 4, 2, 6, 7 };
    protected static final int[] face_xz2 = { 1, 0, 3, 5 };
    protected static final int[] face_yz1 = { 5, 7, 6, 3 };
    protected static final int[] face_yz2 = { 1, 4, 2, 0 };

    protected void createPositionView() {
        if (!position_active)
            return;

        pos_points = new double[2][cube_points.length][2];
        pos_points_visible = new boolean[2][cube_points.length];

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
            Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

            // translate by one pixel and see how far it is in juggler space
            Coordinate c = position.getCoordinate();
            Coordinate c2 = ren.getScreenTranslatedCoordinate(c, 1, 0);
            double dl = Coordinate.distance(c, c2);
            double boxhw = position_box_hw_cm / dl;  // pixel half-width of box

            double[] ca = ren.getCameraAngle();
            double theta = ca[0] + Math.toRadians(position.getAngle()) + deltaangle;
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

            int[] center = ren.getXY(c);
            for (int j = 0; j < cube_points.length; j++) {
                pos_points[i][j][0] = (double)center[0] +
                                        dxx * cube_points[j][0] +
                                        dyx * cube_points[j][1] +
                                        dzx * cube_points[j][2];
                pos_points[i][j][1] = (double)center[1] +
                                        dxy * cube_points[j][0] +
                                        dyy * cube_points[j][1] +
                                        dzy * cube_points[j][2];
                pos_points_visible[i][j] = true;
            }

            // top of cube (z = +1)
            pos_points_visible[i][1] = (phi <= Math.PI/2) || (dxy > 0) || (dyy > 0);
            pos_points_visible[i][4] = (phi <= Math.PI/2) || (dxx < 0) || (dyx < 0);
            pos_points_visible[i][5] = (phi <= Math.PI/2) || (dxx > 0) || (dyx > 0);
            pos_points_visible[i][7] = (phi <= Math.PI/2) || (dxy < 0) || (dyy < 0);

            // bottom of cube (z = -1)
            pos_points_visible[i][0] = (phi > Math.PI/2) || (dxy < 0) || (dyy < 0);
            pos_points_visible[i][2] = (phi > Math.PI/2) || (dxx < 0) || (dyx < 0);
            pos_points_visible[i][3] = (phi > Math.PI/2) || (dxx > 0) || (dyx > 0);
            pos_points_visible[i][6] = (phi > Math.PI/2) || (dxy > 0) || (dyy > 0);
        }
    }

    protected boolean isInsidePolygon(int x, int y, int index, int[] points) {
        for (int i = 0; i < points.length; i++) {
            if (!pos_points_visible[index][points[i]])
                return false;
        }

        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            int xi = (int)(0.5 + pos_points[index][points[i]][0]);
            int yi = (int)(0.5 + pos_points[index][points[i]][1]);
            int xj = (int)(0.5 + pos_points[index][points[j]][0]);
            int yj = (int)(0.5 + pos_points[index][points[j]][1]);

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

        for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
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
            g2.fillOval((int)(0.5 + pos_points[i][8][0]) - 2 + deltax,
                        (int)(0.5 + pos_points[i][8][1]) - 2 + deltay, 5, 5);

            // edges of cube
            drawLine(g2, i, 1, 4);
            drawLine(g2, i, 4, 7);
            drawLine(g2, i, 7, 5);
            drawLine(g2, i, 5, 1);
            drawLine(g2, i, 0, 2);
            drawLine(g2, i, 2, 6);
            drawLine(g2, i, 6, 3);
            drawLine(g2, i, 3, 0);
            drawLine(g2, i, 0, 1);
            drawLine(g2, i, 2, 4);
            drawLine(g2, i, 3, 5);
            drawLine(g2, i, 6, 7);

            if (!dragging || dragging_angle) {
                // angle-changing control pointing forward
                drawLine(g2, i, 8, 9);
                g2.fillOval((int)(0.5 + pos_points[i][9][0]) - 4 + deltax,
                            (int)(0.5 + pos_points[i][9][1]) - 4 + deltay, 10, 10);
            }
            if (dragging_xy) {
                drawLine(g2, i, 10, 11);
                drawLine(g2, i, 12, 13);
            }
            if (dragging_xz) {
                drawLine(g2, i, 10, 11);
                drawLine(g2, i, 14, 15);
            }
            if (dragging_yz) {
                drawLine(g2, i, 12, 13);
                drawLine(g2, i, 14, 15);
            }
            if (dragging_angle) {
                drawLine(g2, i, 14, 15);
            }
        }
    }

    protected void drawLine(Graphics g, int index, int p1, int p2) {
        if (pos_points_visible[index][p1] && pos_points_visible[index][p2]) {
            g.drawLine((int)(0.5 + pos_points[index][p1][0]) + deltax,
                       (int)(0.5 + pos_points[index][p1][1]) + deltay,
                       (int)(0.5 + pos_points[index][p2][0]) + deltax,
                       (int)(0.5 + pos_points[index][p2][1]) + deltay);
        }
    }

    protected void drawGrid(Graphics g) {
        if (!(g instanceof Graphics2D))
            return;
        Graphics2D g2 = (Graphics2D)g;

        g2.setColor(Color.lightGray);
        Dimension d = getSize();

        if (event_active) {
            final double grid_spacing_cm = 10.0;

            // draw a rectangular grid with 10cm spacing, centered on the
            // juggler's origin in local coordinates.
            JMLPattern pat = getPattern();
            int juggler = event.getJuggler();
            Coordinate lc_origin = new Coordinate();
            Coordinate gc_origin = pat.convertLocalToGlobal(lc_origin, juggler, getTime());

            for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

                if (jc.stereo && i == 0)
                    g2 = (Graphics2D)g.create(0, 0, d.width / 2, d.height);
                else if (jc.stereo && i == 1)
                    g2 = (Graphics2D)g.create(d.width / 2, 0, d.width / 2, d.height);

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                            RenderingHints.VALUE_ANTIALIAS_ON);

                Coordinate c2 = ren.getScreenTranslatedCoordinate(gc_origin, 1, 0);
                double px_spacing = grid_spacing_cm /
                                Coordinate.distance(gc_origin, c2);
                int[] center = ren.getXY(gc_origin);

                for (int j = (int)(-center[0] / px_spacing - 1);
                                j <= (d.width - center[0]) / px_spacing; j++) {
                    int x = (int)(0.5 + center[0] + j * px_spacing);
                    if (x >= 0 && x < d.width) {
                        if (j == 0)
                            g2.setStroke(new BasicStroke(3));
                        g2.drawLine(x, 0, x, d.height);
                        if (j == 0)
                            g2.setStroke(new BasicStroke(1));
                    }
                }
                for (int j = (int)(-center[1] / px_spacing - 1);
                                j <= (d.height - center[1]) / px_spacing; j++) {
                    int y = (int)(0.5 + center[1] + j * px_spacing);
                    if (y >= 0 && y < d.height) {
                        if (j == 0)
                            g2.setStroke(new BasicStroke(3));
                        g2.drawLine(0, y, d.width, y);
                        if (j == 0)
                            g2.setStroke(new BasicStroke(1));
                    }
                }
            }
        }

        if (position_active) {
            final double grid_spacing_cm = 20.0;
            int width = (jc.stereo ? d.width / 2 : d.width);

            for (int i = 0; i < (jc.stereo ? 2 : 1); i++) {
                Renderer ren = (i == 0 ? anim.ren1 : anim.ren2);

                if (jc.stereo && i == 0)
                    g2 = (Graphics2D)g.create(0, 0, d.width / 2, d.height);
                else if (jc.stereo && i == 1)
                    g2 = (Graphics2D)g.create(d.width / 2, 0, d.width / 2, d.height);

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                            RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw xy, xz, or yz global coordinate plane, depending on the
                // dragging mode. Center on (0, 0, 100) in global coordinates.
                int[] center = ren.getXY(new Coordinate(0, 0, 100));

                int[] dx = ren.getXY(new Coordinate(100,   0, 100));
                int[] dy = ren.getXY(new Coordinate(  0, 100, 100));
                int[] dz = ren.getXY(new Coordinate(  0,   0, 200));
                double[] xaxis_spacing = {
                    grid_spacing_cm * ((double)(dx[0] - center[0]) / 100.0),
                    grid_spacing_cm * ((double)(dx[1] - center[1]) / 100.0)
                };
                double[] yaxis_spacing = {
                    grid_spacing_cm * ((double)(dy[0] - center[0]) / 100.0),
                    grid_spacing_cm * ((double)(dy[1] - center[1]) / 100.0)
                };
                double[] zaxis_spacing = {
                    grid_spacing_cm * ((double)(dz[0] - center[0]) / 100.0),
                    grid_spacing_cm * ((double)(dz[1] - center[1]) / 100.0)
                };

                double[] axis1 = xaxis_spacing;
                double[] axis2 = yaxis_spacing;

                if (dragging_xz) {
                    axis1 = xaxis_spacing;
                    axis2 = zaxis_spacing;
                } else if (dragging_yz) {
                    axis1 = yaxis_spacing;
                    axis2 = zaxis_spacing;
                }

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
                    int x1 = (int)(0.5 + center[0] + j * axis1[0] + nmin * axis2[0]);
                    int y1 = (int)(0.5 + center[1] + j * axis1[1] + nmin * axis2[1]);
                    int x2 = (int)(0.5 + center[0] + j * axis1[0] + nmax * axis2[0]);
                    int y2 = (int)(0.5 + center[1] + j * axis1[1] + nmax * axis2[1]);
                    if (j == 0)
                        g2.setStroke(new BasicStroke(3));
                    g2.drawLine(x1, y1, x2, y2);
                    if (j == 0)
                        g2.setStroke(new BasicStroke(1));
                }
                for (int j = nmin; j <= nmax; j++) {
                    int x1 = (int)(0.5 + center[0] + mmin * axis1[0] + j * axis2[0]);
                    int y1 = (int)(0.5 + center[1] + mmin * axis1[1] + j * axis2[1]);
                    int x2 = (int)(0.5 + center[0] + mmax * axis1[0] + j * axis2[0]);
                    int y2 = (int)(0.5 + center[1] + mmax * axis1[1] + j * axis2[1]);
                    if (j == 0)
                        g2.setStroke(new BasicStroke(3));
                    g2.drawLine(x1, y1, x2, y2);
                    if (j == 0)
                        g2.setStroke(new BasicStroke(1));
                }
            }
        }
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
                if (dragging) {
                    anim.drawBackground(g);
                    anim.drawFrame(getTime(), g, dragging_camera, false);
                    drawGrid(g);
                } else {
                    anim.drawFrame(getTime(), g, dragging_camera, true);
                }
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
