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

public class AnimationEditPanel extends AnimationPanel
                                implements MouseListener, MouseMotionListener {
    public static final double event_box_hw_cm = 5.0;
    public static final double position_box_hw_cm = 10.0;
    public static final double position_box_z_cm = 0.0;
    public static final double xy_grid_spacing_cm = 20.0;
    public static final double xyz_grid_snap_cm = 3.0;

    protected LadderDiagram ladder;

    // for when an event is activated/dragged
    protected boolean event_active;
    protected JMLEvent event;
    protected int[][] event_box;

    // for when a position is activated/dragged
    protected boolean position_active;
    protected JMLPosition position;
    protected double[][][] pos_points;
    //protected boolean[][] pos_points_visible;
    protected boolean dragging_xy;
    protected boolean dragging_z;
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
                dragging_z = JLFunc.isNearLine(mx - t, my,
                                    (int)Math.round(pos_points[i][4][0]),
                                    (int)Math.round(pos_points[i][4][1]),
                                    (int)Math.round(pos_points[i][6][0]),
                                    (int)Math.round(pos_points[i][6][1]),
                                    7);

                if (dragging_z) {
                    dragging = true;
                    dragging_left = (i == 0);
                    deltax = deltay = 0;
                    repaint();
                    return;
                }

                dragging_xy = isInsidePolygon(mx - t, my, i, face_xy);

                if (dragging_xy) {
                    dragging = true;
                    dragging_left = (i == 0);
                    deltax = deltay = 0;
                    repaint();
                    return;
                }

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
            if (dragging_angle) {
                double angle = Math.toRadians(position.getAngle());
                double new_angle = Math.toDegrees(angle + deltaangle);
                while (new_angle > 360.0)
                    new_angle -= 360.0;
                while (new_angle < 0.0)
                    new_angle += 360.0;
                position.setAngle(new_angle);
            } else {
                position.setCoordinate(getCurrentPosition());
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
            } else if (dragging_z) {
                deltax = 0;
                deltay = my - starty;
                getCurrentPosition();
            } else {
                deltax = mx - startx;
                deltay = my - starty;
                getCurrentPosition();
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
            int boxhw = (int)Math.round(event_box_hw_cm / dl);  // in pixels

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
        position_active = false;
        dragging_xy = dragging_z = dragging_angle = false;
    }

    // Points in the juggler's coordinate system that are used for drawing the
    // onscreen representation of a selected position.
    protected static final double[][] pos_control_points =
        {
            // corners of square representing xy movement control
            { -position_box_hw_cm, -position_box_hw_cm,  0 },
            { -position_box_hw_cm,  position_box_hw_cm,  0 },
            {  position_box_hw_cm,  position_box_hw_cm,  0 },
            {  position_box_hw_cm, -position_box_hw_cm,  0 },

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
                                    new Coordinate(0, 0, position_box_z_cm));
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
        }
    }

    // While dragging a position, returns the current coordinate based on
    // dragging mode.
    //
    // This also snaps to selected grid lines, and adjusts the returned
    // Coordinate accordingly. When a grid snap occurs, `deltax` and `deltay`
    // are adjusted so that the position displays in its snapped position.
    protected Coordinate getCurrentPosition() {
        if (!position_active || !dragging)
            return null;

        // screen (pixel) offset of a 1cm offset in each of the
        // cardinal directions
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

            double angle = Math.toRadians(position.getAngle());
            c.x += a * Math.cos(angle) - b * Math.sin(angle);
            c.y += a * Math.sin(angle) + b * Math.cos(angle);

            // Snap to selected grid lines

            if (getCameraAngle()[1] > Math.toRadians(70.0))
                return c;  // don't snap if grid isn't showing

            boolean snapped = false;
            double oldcx = c.x;
            double oldcy = c.y;

            double closest_grid = xy_grid_spacing_cm *
                                        Math.round(c.x / xy_grid_spacing_cm);
            if (Math.abs(c.x - closest_grid) < xyz_grid_snap_cm) {
                c.x = closest_grid;
                snapped = true;
            }
            closest_grid = xy_grid_spacing_cm *
                                        Math.round(c.y / xy_grid_spacing_cm);
            if (Math.abs(c.y - closest_grid) < xyz_grid_snap_cm) {
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
            c.z += deltay / dz[1];

            if (Math.abs(c.z - 100) < xyz_grid_snap_cm) {
                deltay += (int)Math.round(dz[1] * (100 - c.z));
                c.z = 100;
            }
        }

        return c;
    }

    protected boolean isInsidePolygon(int x, int y, int index, int[] points) {
        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            int xi = (int)Math.round(pos_points[index][points[i]][0]);
            int yi = (int)Math.round(pos_points[index][points[i]][1]);
            int xj = (int)Math.round(pos_points[index][points[j]][0]);
            int yj = (int)Math.round(pos_points[index][points[j]][1]);

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

            // edges of xy plane control
            drawLine(g2, i, 0, 1);
            drawLine(g2, i, 1, 2);
            drawLine(g2, i, 2, 3);
            drawLine(g2, i, 3, 0);

            if (!dragging || dragging_z) {
                // z-axis control pointing upward
                drawLine(g2, i, 4, 6);
                drawLine(g2, i, 6, 7);
                drawLine(g2, i, 6, 8);
            }

            if (!dragging || dragging_angle) {
                // angle-changing control pointing backward
                drawLine(g2, i, 4, 5);
                g2.fillOval((int)Math.round(pos_points[i][5][0]) - 4 + deltax,
                            (int)Math.round(pos_points[i][5][1]) - 4 + deltay, 10, 10);
            }

            if (dragging_angle) {
                // sighting line during angle rotation
                drawLine(g2, i, 9, 10);
            }

            if (dragging_z || dragging_xy) {
                // line dropping down to projection on ground (z = 0)
                Coordinate c = getCurrentPosition();
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

                    g2.drawString("z = " + JLFunc.toStringRounded(z, 1),
                                xy_projection[0] + 5, message_y);
                }
            }
        }
    }

    protected void drawLine(Graphics g, int index, int p1, int p2) {
        g.drawLine((int)Math.round(pos_points[index][p1][0]) + deltax,
                   (int)Math.round(pos_points[index][p1][1]) + deltay,
                   (int)Math.round(pos_points[index][p2][0]) + deltax,
                   (int)Math.round(pos_points[index][p2][1]) + deltay);
    }

    // In position editing mode, draw an xy grid where the ground is
    protected void drawGrid(Graphics g) {
        if (!(g instanceof Graphics2D))
            return;

        if (!position_active)
            return;

        // only draw grid when looking down from above
        if (getCameraAngle()[1] > Math.toRadians(70.0))
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
                xy_grid_spacing_cm * ((double)(dx[0] - center[0]) / 100.0),
                xy_grid_spacing_cm * ((double)(dx[1] - center[1]) / 100.0)
            };
            double[] yaxis_spacing = {
                xy_grid_spacing_cm * ((double)(dy[0] - center[0]) / 100.0),
                xy_grid_spacing_cm * ((double)(dy[1] - center[1]) / 100.0)
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
