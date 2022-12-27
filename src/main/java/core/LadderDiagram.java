// LadderDiagram.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.*;
import javax.swing.*;

import jugglinglab.util.*;
import jugglinglab.jml.*;


// This class draws the vertical ladder diagram on the right side of Edit view.
// This version does not include any mouse interaction or editing functions;
// those are added in EditLadderDiagram.

public class LadderDiagram extends JPanel implements
            AnimationPanel.AnimationAttachment, MouseListener, MouseMotionListener {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // overall sizing
    public static final int MAX_JUGGLERS = 8;
    protected static final int LADDER_WIDTH_PER_JUGGLER = 150;  // pixels
    protected static final int LADDER_MIN_WIDTH_PER_JUGGLER = 60;
    protected static final Font MSGFONT = new Font("SansSerif", Font.PLAIN, 12);

    // geometric constants in pixels
    protected static final int BORDER_TOP = 25;
    protected static final int TRANSITION_RADIUS = 5;
    protected static final int PATH_SLOP = 5;
    protected static final int POSITION_RADIUS = 5;

    // geometric constants as fraction of hands separation for each juggler
    protected static final double BORDER_SIDES = 0.15;
    protected static final double JUGGLER_SEPARATION = 0.45;
    protected static final double SELFTHROW_WIDTH = 0.25;

    protected static final Color COLOR_BACKGROUND = Color.white;
    protected static final Color COLOR_HANDS = Color.black;
    protected static final Color COLOR_POSITIONS = Color.black;
    protected static final Color COLOR_SYMMETRIES = Color.lightGray;
    protected static final Color COLOR_TRACKER = Color.red;
    protected static final int IMAGE_DRAW_WAIT = 5;  // frames

    // GUI states
    protected static final int STATE_INACTIVE = 0;
    protected static final int STATE_MOVING_TRACKER = 1;

    protected AnimationPanel ap;
    protected JMLPattern pat;

    protected int width;  // pixel dimensions of entire panel
    protected int height;
    protected int right_x;  // right/left hand pos. for juggler 1 (px)
    protected int left_x;
    protected int juggler_delta_x;  // horizontal offset between jugglers (px)

    protected int gui_state = STATE_INACTIVE;  // one of STATE_x values above
    protected double sim_time;
    protected int tracker_y = BORDER_TOP;
    protected boolean has_switch_symmetry;
    protected boolean has_switchdelay_symmetry;

    protected ArrayList<LadderEventItem> laddereventitems;
    protected ArrayList<LadderPathItem> ladderpathitems;
    protected ArrayList<LadderPositionItem> ladderpositionitems;

    protected BufferedImage im;
    protected boolean image_valid;
    protected int frames_until_image_draw;

    protected boolean anim_paused;


    public LadderDiagram(JMLPattern p) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        setBackground(COLOR_BACKGROUND);
        setOpaque(false);
        pat = p;

        int jugglers = pat.getNumberOfJugglers();
        if (jugglers > MAX_JUGGLERS) {
            // allocate enough space for a "too many jugglers" message; see
            // paintLadder()
            String template = guistrings.getString("Too_many_jugglers");
            Object[] arguments = { Integer.valueOf(MAX_JUGGLERS) };
            String message = MessageFormat.format(template, arguments);
            int mwidth = 20 + getFontMetrics(MSGFONT).stringWidth(message);
            setPreferredSize(new Dimension(mwidth, 1));
            setMinimumSize(new Dimension(mwidth, 1));
            return;
        }

        int pref_width = LADDER_WIDTH_PER_JUGGLER * jugglers;
        int min_width = LADDER_MIN_WIDTH_PER_JUGGLER * jugglers;
        double[] width_mult = new double[] {
            1.0,
            1.0,
            0.85,
            0.72,
            0.65,
            0.55,
        };
        pref_width *= (jugglers >= width_mult.length ? 0.5 : width_mult[jugglers]);
        pref_width = Math.max(pref_width, min_width);
        setPreferredSize(new Dimension(pref_width, 1));
        setMinimumSize(new Dimension(min_width, 1));

        pat.layoutPattern();  // ensures we have event list
        createView();

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    //-------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //-------------------------------------------------------------------------

    @Override
    public void mousePressed(final MouseEvent me) {
        if (ap != null && (ap.writingGIF || !ap.engineAnimating))
            return;

        int my = me.getY();
        my = Math.min(Math.max(my, BORDER_TOP), height - BORDER_TOP);

        gui_state = STATE_MOVING_TRACKER;
        tracker_y = my;
        if (ap != null) {
            double scale = (pat.getLoopEndTime() - pat.getLoopStartTime()) /
                            (double)(height - 2 * BORDER_TOP);
            double newtime = (double)(my - BORDER_TOP) * scale;
            anim_paused = ap.isPaused();
            ap.setPaused(true);
            ap.setTime(newtime);
        }

        repaint();
        if (ap != null)
            ap.repaint();
    }

    @Override
    public void mouseReleased(final MouseEvent me) {
        if (ap != null && (ap.writingGIF || !ap.engineAnimating))
            return;

        gui_state = STATE_INACTIVE;
        if (ap != null)
            ap.setPaused(anim_paused);
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    //-------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //-------------------------------------------------------------------------

    @Override
    public void mouseDragged(MouseEvent me) {
        if (ap != null && (ap.writingGIF || !ap.engineAnimating))
            return;

        int my = me.getY();
        my = Math.min(Math.max(my, BORDER_TOP), height - BORDER_TOP);
        tracker_y = my;
        repaint();

        if (ap != null) {
            double scale = (pat.getLoopEndTime() - pat.getLoopStartTime()) /
                    (double)(height - 2 * BORDER_TOP);
            double newtime = (double)(my - BORDER_TOP) * scale;
            ap.setTime(newtime);
            ap.repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

    //-------------------------------------------------------------------------
    // Methods to interact with ladder items
    //-------------------------------------------------------------------------

    protected LadderEventItem getSelectedLadderEvent(int x, int y) {
        for (LadderEventItem item : laddereventitems) {
            if (x >= item.xlow && x <= item.xhigh &&
                        y >= item.ylow && y <= item.yhigh)
                return item;
        }
        return null;
    }

    protected LadderPositionItem getSelectedLadderPosition(int x, int y) {
        for (LadderPositionItem item : ladderpositionitems) {
            if (x >= item.xlow && x <= item.xhigh &&
                        y >= item.ylow && y <= item.yhigh)
                return item;
        }
        return null;
    }

    protected LadderPathItem getSelectedLadderPath(int x, int y, int slop) {
        LadderPathItem result = null;
        double dmin = 0.0;

        if (y < (BORDER_TOP - slop) || y > (height - BORDER_TOP + slop))
            return null;

        for (LadderPathItem item : ladderpathitems) {
            double d;

            if (item.type == LadderPathItem.TYPE_SELF) {
                if (y < (item.ystart - slop) || y > (item.yend + slop))
                    continue;
                d = (x - item.xcenter)*(x - item.xcenter) +
                    (y - item.ycenter)*(y - item.ycenter);
                d = Math.abs(Math.sqrt(d) - item.radius);
            }
            else {
                int xmin = (item.xstart < item.xend) ? item.xstart : item.xend;
                int xmax = (item.xstart < item.xend) ? item.xend : item.xstart;

                if (x < (xmin - slop) || x > (xmax + slop))
                    continue;
                if (y < (item.ystart - slop) || y > (item.yend + slop))
                    continue;
                d = (item.xend - item.xstart)*(y - item.ystart) -
                    (x - item.xstart)*(item.yend - item.ystart);
                d = Math.abs(d) / Math.sqrt((item.xend - item.xstart)*(item.xend - item.xstart) +
                                            (item.yend - item.ystart)*(item.yend - item.ystart));
            }

            if ((int)d < slop) {
                if (result == null || d < dmin) {
                    result = item;
                    dmin = d;
                }
            }
        }
        return result;
    }

    public void setPathColor(int path, Color color) {
        for (LadderPathItem item : ladderpathitems) {
            if (item.pathnum == path)
                item.color = color;
        }
    }

    protected void updateTrackerPosition() {
        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();
        tracker_y = (int)(0.5 + (double)(height-2*BORDER_TOP) * (sim_time-loop_start) /
                          (loop_end - loop_start)) + BORDER_TOP;
    }

    //-------------------------------------------------------------------------
    // Methods to create and paint the ladder view
    //-------------------------------------------------------------------------

    // Create arrays of all the elements in the ladder diagram
    protected void createView() {
        has_switch_symmetry = has_switchdelay_symmetry = false;

        for (JMLSymmetry sym : pat.symmetries()) {
            switch (sym.getType()) {
                case JMLSymmetry.TYPE_SWITCH:
                    has_switch_symmetry = true;
                    break;
                case JMLSymmetry.TYPE_SWITCHDELAY:
                    has_switchdelay_symmetry = true;
                    break;
            }
        }

        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();

        // first create events (little circles)
        laddereventitems = new ArrayList<LadderEventItem>();
        JMLEvent eventlist = pat.getEventList();
        JMLEvent ev = eventlist;

        while (ev.getT() < loop_start)
            ev = ev.getNext();

        while (ev.getT() < loop_end) {
            LadderEventItem item = new LadderEventItem();
            item.type = LadderEventItem.TYPE_EVENT;
            item.eventitem = item;
            item.event = ev;
            laddereventitems.add(item);

            for (int i = 0; i < ev.getNumberOfTransitions(); i++) {
                LadderEventItem item2 = new LadderEventItem();
                item2.type = LadderEventItem.TYPE_TRANSITION;
                item2.eventitem = item;
                item2.event = ev;
                item2.transnum = i;
                laddereventitems.add(item2);
            }

            ev = ev.getNext();
        }

        // create paths (lines and arcs)
        ladderpathitems = new ArrayList<LadderPathItem>();
        ev = eventlist;
        while (ev.getT() <= loop_end) {
            for (int i = 0; i < ev.getNumberOfTransitions(); i++) {
                JMLTransition tr = ev.getTransition(i);
                PathLink opl = tr.getOutgoingPathLink();

                if (opl != null) {
                    LadderPathItem item = new LadderPathItem();
                    item.transnum_start = i;
                    item.startevent = opl.getStartEvent();
                    item.endevent = opl.getEndEvent();

                    if (opl.isInHand())
                        item.type = LadderPathItem.TYPE_HOLD;
                    else if (item.startevent.getJuggler() != item.endevent.getJuggler())
                        item.type = LadderPathItem.TYPE_PASS;
                    else
                        item.type = (item.startevent.getHand()==item.endevent.getHand()) ?
                            LadderPathItem.TYPE_SELF : LadderPathItem.TYPE_CROSS;

                    item.pathnum = opl.getPathNum();
                    item.color = Color.black;
                    ladderpathitems.add(item);
                }
            }

            ev = ev.getNext();
        }

        // create juggler positions
        ladderpositionitems = new ArrayList<LadderPositionItem>();
        JMLPosition positionlist = pat.getPositionList();
        JMLPosition pos = positionlist;

        while (pos != null && pos.getT() < loop_start)
            pos = pos.getNext();

        while (pos != null && pos.getT() < loop_end) {
            LadderPositionItem item = new LadderPositionItem();
            item.type = LadderPositionItem.TYPE_POSITION;
            item.position = pos;
            ladderpositionitems.add(item);

            pos = pos.getNext();
        }

        updateView();
    }

    // Assign physical locations to all the elements in the ladder diagram
    protected void updateView() {
        Dimension dim = getSize();
        width = dim.width;
        height = dim.height;

        // calculate placements of hands and jugglers
        double scale = (double)width / (BORDER_SIDES * 2 +
                                        JUGGLER_SEPARATION * (pat.getNumberOfJugglers() - 1) +
                                        pat.getNumberOfJugglers());
        left_x = (int)(scale * BORDER_SIDES + 0.5);
        right_x = (int)(scale * (BORDER_SIDES + 1.0) + 0.5);
        juggler_delta_x = (int)(scale * (1.0 + JUGGLER_SEPARATION) + 0.5);

        // invalidate cached image of ladder diagram
        image_valid = false;
        im = null;
        frames_until_image_draw = IMAGE_DRAW_WAIT;

        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();

        // set locations of events and transitions
        for (LadderEventItem item : laddereventitems) {
            JMLEvent ev = item.event;

            int event_x = (ev.getHand() == HandLink.LEFT_HAND ? left_x : right_x) +
                          (ev.getJuggler() - 1) * juggler_delta_x -
                          TRANSITION_RADIUS;
            int event_y = (int)(0.5 + (double)(height-2*BORDER_TOP) * (ev.getT()-loop_start) /
                                (loop_end - loop_start)) + BORDER_TOP - TRANSITION_RADIUS;

            if (item.type == LadderEventItem.TYPE_EVENT) {
                item.xlow = event_x;
                item.xhigh = event_x + 2 * TRANSITION_RADIUS;
                item.ylow = event_y;
                item.yhigh = event_y + 2 * TRANSITION_RADIUS;
            } else {
                if (ev.getHand() == HandLink.LEFT_HAND)
                    event_x += 2 * TRANSITION_RADIUS * (item.transnum+1);
                else
                    event_x -= 2 * TRANSITION_RADIUS * (item.transnum+1);
                item.xlow = event_x;
                item.xhigh = event_x + 2 * TRANSITION_RADIUS;
                item.ylow = event_y;
                item.yhigh = event_y + 2 * TRANSITION_RADIUS;
            }
        }

        // set locations of paths (lines and arcs)
        for (LadderPathItem item : ladderpathitems) {
            item.xstart = (item.startevent.getHand() == HandLink.LEFT_HAND ?
                           (left_x + (item.transnum_start+1)*2*TRANSITION_RADIUS) :
                           (right_x - (item.transnum_start+1)*2*TRANSITION_RADIUS)) +
                           (item.startevent.getJuggler() - 1) * juggler_delta_x;
            item.ystart = (int)(0.5 + (double)(height-2*BORDER_TOP) * (item.startevent.getT()-loop_start) /
                                (loop_end - loop_start)) + BORDER_TOP;
            item.yend = (int)(0.5 + (double)(height-2*BORDER_TOP) * (item.endevent.getT()-loop_start) /
                              (loop_end - loop_start)) + BORDER_TOP;

            int slot = 0;
            for (int j = 0; j < item.endevent.getNumberOfTransitions(); j++) {
                JMLTransition temp = item.endevent.getTransition(j);
                if (temp.getPath() == item.pathnum) {
                    slot = j;
                    break;
                }
            }
            item.xend = (item.endevent.getHand() == HandLink.LEFT_HAND ?
                         (left_x + (slot+1)*2*TRANSITION_RADIUS) :
                         (right_x - (slot+1)*2*TRANSITION_RADIUS)) +
                         (item.endevent.getJuggler() - 1) * juggler_delta_x;

            if (item.type == LadderPathItem.TYPE_SELF) {
                double a = 0.5 * Math.sqrt((double)((item.xstart-item.xend)*(item.xstart-item.xend)) +
                                           (double)((item.ystart-item.yend)*(item.ystart-item.yend)));
                double xt = 0.5 * (double)(item.xstart + item.xend);
                double yt = 0.5 * (double)(item.ystart + item.yend);
                double b = SELFTHROW_WIDTH * ((double)width / pat.getNumberOfJugglers());
                double d = 0.5 * (a*a / b - b);
                if (d < (0.5 * b))
                    d = 0.5 * b;
                double mult = (item.endevent.getHand()==HandLink.LEFT_HAND) ? -1.0 : 1.0;
                double xc = xt + mult * d * (yt - (double)item.ystart) / a;
                double yc = yt + mult * d * ((double)item.xstart - xt) / a;
                double rad = Math.sqrt(((double)item.xstart-xc)*((double)item.xstart-xc) +
                                       ((double)item.ystart-yc)*((double)item.ystart-yc));
                item.xcenter = (int)(0.5 + xc);
                item.ycenter = (int)(0.5 + yc);
                item.radius = (int)(0.5 + rad);
            }
        }

        // set locations of juggler positions
        for (LadderPositionItem item : ladderpositionitems) {
            JMLPosition pos = item.position;

            int position_x = (left_x + right_x) / 2 +
                          (pos.getJuggler() - 1) * juggler_delta_x -
                          POSITION_RADIUS;
            int position_y = (int)(0.5 + (double)(height-2*BORDER_TOP) * (pos.getT()-loop_start) /
                                (loop_end - loop_start)) + BORDER_TOP - POSITION_RADIUS;

            item.xlow = position_x;
            item.xhigh = position_x + 2 * POSITION_RADIUS;
            item.ylow = position_y;
            item.yhigh = position_y + 2 * POSITION_RADIUS;
        }

        // update position of tracker bar
        updateTrackerPosition();
    }

    // Return true if ladder was drawn successfully, false otherwise
    protected boolean paintLadder(Graphics gr) {
        if (pat.getNumberOfJugglers() > MAX_JUGGLERS) {
            Dimension dim = getSize();
            gr.setFont(MSGFONT);
            FontMetrics fm = gr.getFontMetrics();
            String template = guistrings.getString("Too_many_jugglers");
            Object[] arguments = { Integer.valueOf(MAX_JUGGLERS) };
            String message = MessageFormat.format(template, arguments);
            int mwidth = fm.stringWidth(message);
            int x = Math.max((dim.width - mwidth) / 2, 0);
            int y = (dim.height + fm.getHeight()) / 2;
            gr.setColor(COLOR_BACKGROUND);
            gr.fillRect(0, 0, dim.width, dim.height);
            gr.setColor(Color.black);
            gr.drawString(message, x, y);
            return false;
        }

        Graphics g = gr;

        // check if ladder was resized
        Dimension dim = getSize();
        if (dim.width != width || dim.height != height)
            updateView();

        boolean rebuild_ladder_image = (!image_valid &&
                                        --frames_until_image_draw <= 0);

        if (rebuild_ladder_image) {
            im = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleImage(width, height, Transparency.OPAQUE);
            g = im.getGraphics();

            if (g instanceof Graphics2D) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
            }
        }

        if (!image_valid) {
            // first erase the background
            g.setColor(COLOR_BACKGROUND);
            g.fillRect(0, 0, width, height);

            // draw the lines signifying symmetries
            g.setColor(COLOR_SYMMETRIES);
            g.drawLine(0, BORDER_TOP, width, BORDER_TOP);
            g.drawLine(0, height - BORDER_TOP, width, height-BORDER_TOP);
            if (has_switch_symmetry) {
                g.drawLine(left_x, height - BORDER_TOP / 2,
                           width - left_x, height - BORDER_TOP / 2);
                g.drawLine(left_x, height - BORDER_TOP / 2,
                           left_x + left_x, height - BORDER_TOP * 3 / 4);
                g.drawLine(left_x, height - BORDER_TOP / 2,
                           left_x + left_x, height - BORDER_TOP / 4);
                g.drawLine(width - left_x, height - BORDER_TOP / 2,
                           width - 2 * left_x, height - BORDER_TOP * 3 / 4);
                g.drawLine(width - left_x, height - BORDER_TOP / 2,
                           width - 2 * left_x, height - BORDER_TOP / 4);
            }
            if (has_switchdelay_symmetry)
                g.drawLine(0, height / 2, width, height / 2);

            // draw the lines representing the hands
            g.setColor(COLOR_HANDS);
            for (int j = 0; j < pat.getNumberOfJugglers(); j++) {
                for (int i = -1; i < 2; i++) {
                    g.drawLine(left_x + i + j * juggler_delta_x, BORDER_TOP,
                               left_x + i + j * juggler_delta_x, height - BORDER_TOP);
                    g.drawLine(right_x + i + j * juggler_delta_x, BORDER_TOP,
                               right_x + i + j * juggler_delta_x, height - BORDER_TOP);
                }
            }

            // draw paths
            Shape clip = g.getClip();

            for (LadderPathItem item : ladderpathitems) {
                g.setColor(item.color);

                Graphics2D gdash = null;

                if (item.type == LadderPathItem.TYPE_PASS) {
                    gdash = (Graphics2D)g.create();
                    Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                                              1f, new float[] {7f, 3f}, 0);
                    gdash.setStroke(dashed);

                    gdash.clipRect(left_x, BORDER_TOP,
                               width - left_x, height - 2 * BORDER_TOP);
                 } else {
                    g.clipRect(left_x + (item.startevent.getJuggler() - 1) * juggler_delta_x,
                               BORDER_TOP,
                               right_x - left_x + (item.startevent.getJuggler() - 1) * juggler_delta_x,
                               height - 2 * BORDER_TOP);
                }

                if (item.type == LadderPathItem.TYPE_CROSS) {
                    g.drawLine(item.xstart, item.ystart, item.xend, item.yend);
                } else if (item.type == LadderPathItem.TYPE_HOLD) {
                    g.drawLine(item.xstart, item.ystart, item.xend, item.yend);
                } else if (item.type == LadderPathItem.TYPE_PASS) {
                    gdash.drawLine(item.xstart, item.ystart, item.xend, item.yend);
                    gdash.dispose();
                } else if (item.type == LadderPathItem.TYPE_SELF) {
                    if (!(item.yend < BORDER_TOP)) {
                        g.clipRect(left_x + (item.startevent.getJuggler() - 1) * juggler_delta_x,
                                   item.ystart,
                                   right_x - left_x + (item.startevent.getJuggler() - 1) * juggler_delta_x,
                                   item.yend - item.ystart);
                        g.drawOval(item.xcenter - item.radius, item.ycenter - item.radius,
                                   2 * item.radius, 2 * item.radius);
                    }
                }
                g.setClip(clip);
            }
        }

        if (rebuild_ladder_image)
            image_valid = true;

        if (image_valid)
            gr.drawImage(im, 0, 0, this);

        // draw positions
        for (LadderPositionItem item : ladderpositionitems) {
            if (item.ylow >= BORDER_TOP || item.yhigh <= height + BORDER_TOP) {
                gr.setColor(COLOR_BACKGROUND);
                gr.fillRect(item.xlow, item.ylow,
                            item.xhigh - item.xlow, item.yhigh - item.ylow);
                gr.setColor(COLOR_POSITIONS);
                gr.drawRect(item.xlow, item.ylow,
                            item.xhigh - item.xlow, item.yhigh - item.ylow);
            }
        }

        // draw events
        int[] animpropnum = null;
        if (ap != null && ap.getAnimator() != null)
            animpropnum = ap.getAnimator().getAnimPropNum();

        for (LadderEventItem item : laddereventitems) {
            if (item.type == LadderItem.TYPE_EVENT) {
                gr.setColor(COLOR_HANDS);
                gr.fillOval(item.xlow, item.ylow,
                            item.xhigh - item.xlow, item.yhigh - item.ylow);
            } else {
                if (item.ylow >= BORDER_TOP || item.yhigh <= height + BORDER_TOP) {
                    if (animpropnum == null) {
                        gr.setColor(COLOR_BACKGROUND);
                    } else {
                        // color ball representation with the prop's color
                        JMLTransition tr = item.event.getTransition(item.transnum);
                        int propnum = animpropnum[tr.getPath() - 1];
                        gr.setColor(pat.getProp(propnum).getEditorColor());
                    }
                    gr.fillOval(item.xlow, item.ylow,
                                item.xhigh - item.xlow, item.yhigh - item.ylow);

                    gr.setColor(COLOR_HANDS);
                    gr.drawOval(item.xlow, item.ylow,
                                item.xhigh-item.xlow, item.yhigh-item.ylow);
                }
            }
        }
        
        // draw the tracker line showing the time
        gr.setColor(COLOR_TRACKER);
        gr.drawLine(0, tracker_y, width, tracker_y);

        return true;
    }

    //-------------------------------------------------------------------------
    // AnimationPanel.AnimationAttachment methods
    //-------------------------------------------------------------------------

    @Override
    public void setAnimationPanel(AnimationPanel a) {
        ap = a;
    }

    @Override
    public void setTime(double time) {
        if (sim_time == time)
            return;

        sim_time = time;
        updateTrackerPosition();
        repaint();
    }

    //-------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //-------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics gr) {
        if (gr instanceof Graphics2D) {
            Graphics2D gr2 = (Graphics2D)gr;
            gr2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                 RenderingHints.VALUE_ANTIALIAS_ON);
        }

        paintLadder(gr);

        // label the tracker line with the time
        if (gui_state == STATE_MOVING_TRACKER) {
            gr.setColor(COLOR_TRACKER);
            gr.drawString(JLFunc.toStringRounded(sim_time, 2) + " s",
                        width / 2 - 18, tracker_y - 5);
        }
    }
}


class LadderItem {
    static final public int TYPE_EVENT = 1;
    static final public int TYPE_TRANSITION = 2;
    static final public int TYPE_SELF = 3;
    static final public int TYPE_CROSS = 4;
    static final public int TYPE_HOLD = 5;
    static final public int TYPE_PASS = 6;
    static final public int TYPE_POSITION = 7;

    public int type;
}

class LadderEventItem extends LadderItem {
    public int xlow, xhigh, ylow, yhigh;

    // for transitions within an event, the next two point to the containing
    // event:
    public LadderEventItem eventitem;
    public JMLEvent event;

    public int transnum;

    public int getHashCode() {
        return event.getHashCode() * 17 + type * 23 + transnum * 27;
    }
}

class LadderPathItem extends LadderItem {
    public int xstart, ystart, xend, yend;
    public int xcenter, ycenter, radius;  // for type SELF
    public Color color;

    public JMLEvent startevent;
    public JMLEvent endevent;
    public int transnum_start;
    public int pathnum;
}

class LadderPositionItem extends LadderItem {
    public int xlow, xhigh, ylow, yhigh;

    public JMLPosition position;

    public int getHashCode() {
        return position.getHashCode();
    }
}
