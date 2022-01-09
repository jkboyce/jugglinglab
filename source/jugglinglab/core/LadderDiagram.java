// LadderDiagram.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

import jugglinglab.util.*;
import jugglinglab.jml.*;
import jugglinglab.path.*;
import jugglinglab.prop.*;


// This class draws the vertical ladder diagram on the right side of Edit view.
// This version does not include any mouse interaction or editing functions;
// those are added in EditLadderDiagram.

public class LadderDiagram extends JPanel {
    protected static final Color background = Color.white;
    protected static final int image_draw_wait = 5;  // frames

    // geometric constants in pixels
    protected static final int border_top = 25;
    protected static final int transition_radius = 5;
    protected static final int path_slop = 5;

    // geometric constants as fraction of hands separation for each juggler
    protected static final double border_sides = 0.15;
    protected static final double juggler_separation = 0.45;
    protected static final double selfthrow_width = 0.25;

    protected JMLPattern pat;

    protected int width;  // pixel dimensions of entire panel
    protected int height;
    protected int right_x;  // right/left hand pos. for juggler 1 (px)
    protected int left_x;
    protected int juggler_delta_x;  // horizontal offset between jugglers (px)

    protected double sim_time;
    protected int tracker_y = border_top;
    protected boolean has_switch_symmetry;
    protected boolean has_switchdelay_symmetry;

    protected ArrayList<LadderEventItem> laddereventitems;
    protected ArrayList<LadderPathItem> ladderpathitems;

    protected BufferedImage ladderimage;
    protected boolean ladder_image_valid;
    protected int frames_until_ladder_image;

    protected boolean anim_paused;


    public LadderDiagram(JMLPattern p) {
        setBackground(background);
        setOpaque(false);
        pat = p;
        createView();
    }

    protected LadderEventItem getSelectedLadderEvent(int x, int y) {
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = laddereventitems.get(i);
            if (x >= item.xlow && x <= item.xhigh &&
                        y >= item.ylow && y <= item.yhigh)
                return item;
        }
        return null;
    }

    protected LadderPathItem getSelectedLadderPath(int x, int y, int slop) {
        LadderPathItem result = null;
        double dmin = 0.0;

        if (y < (border_top - slop) || y > (height - border_top + slop))
            return null;

        for (int i = 0; i < ladderpathitems.size(); i++) {
            LadderPathItem item = ladderpathitems.get(i);
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
        for (int i = 0; i < ladderpathitems.size(); i++) {
            LadderPathItem item = ladderpathitems.get(i);
            if (item.pathnum == path)
                item.color = color;
        }
    }

    public void setTime(double time) {
        if (sim_time == time)
            return;

        sim_time = time;
        setTrackerPosition();
        repaint();
    }

    protected void setTrackerPosition() {
        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();
        tracker_y = (int)(0.5 + (double)(height-2*border_top) * (sim_time-loop_start) /
                          (loop_end - loop_start)) + border_top;
    }

    // Create arrays of all the elements in the ladder diagram
    protected void createView() {
        has_switch_symmetry = has_switchdelay_symmetry = false;
        for (int i = 0; i < pat.getNumberOfSymmetries(); i++) {
            JMLSymmetry sym = pat.getSymmetry(i);
            switch (sym.getType()) {
                case JMLSymmetry.TYPE_SWITCH:
                    has_switch_symmetry = true;
                    break;
                case JMLSymmetry.TYPE_SWITCHDELAY:
                    has_switchdelay_symmetry = true;
                    break;
            }
        }

        // first create events (little circles)
        laddereventitems = new ArrayList<LadderEventItem>();
        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();

        JMLEvent eventlist = pat.getEventList();
        JMLEvent current = eventlist;

        while (current.getT() < loop_start)
            current = current.getNext();

        while (current.getT() < loop_end) {
            LadderEventItem item = new LadderEventItem();
            item.type = LadderEventItem.TYPE_EVENT;
            item.eventitem = item;
            item.event = current;
            laddereventitems.add(item);

            for (int i = 0; i < current.getNumberOfTransitions(); i++) {
                LadderEventItem item2 = new LadderEventItem();
                item2.type = LadderEventItem.TYPE_TRANSITION;
                item2.eventitem = item;
                item2.event = current;
                item2.transnum = i;
                laddereventitems.add(item2);
            }

            current = current.getNext();
        }

        // create paths (lines and arcs)
        ladderpathitems = new ArrayList<LadderPathItem>();

        current = eventlist;

        while (current.getT() <= loop_end) {
            for (int i = 0; i < current.getNumberOfTransitions(); i++) {
                JMLTransition tr = current.getTransition(i);
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

            current = current.getNext();
        }

        updateView();
    }

    // Assign physical locations to all the elements in the ladder diagram
    protected void updateView() {
        Dimension dim = getSize();
        width = dim.width;
        height = dim.height;

        // calculate placements of hands and jugglers
        double scale = (double)width / (border_sides * 2 +
                                        juggler_separation * (pat.getNumberOfJugglers() - 1) +
                                        pat.getNumberOfJugglers());
        left_x = (int)(scale * border_sides + 0.5);
        right_x = (int)(scale * (border_sides + 1.0) + 0.5);
        juggler_delta_x = (int)(scale * (1.0 + juggler_separation) + 0.5);

        // invalidate cached image of ladder diagram
        ladder_image_valid = false;
        ladderimage = null;
        frames_until_ladder_image = image_draw_wait;

        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();

        // set locations of events and transitions
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = laddereventitems.get(i);
            JMLEvent current = item.event;

            int event_x = (current.getHand() == HandLink.LEFT_HAND ? left_x : right_x) +
                          (current.getJuggler() - 1) * juggler_delta_x -
                          transition_radius;
            int event_y = (int)(0.5 + (double)(height-2*border_top) * (current.getT()-loop_start) /
                                (loop_end - loop_start)) + border_top - transition_radius;

            if (item.type == LadderEventItem.TYPE_EVENT) {
                item.xlow = event_x;
                item.xhigh = event_x + 2 * transition_radius;
                item.ylow = event_y;
                item.yhigh = event_y + 2 * transition_radius;
            } else {
                if (current.getHand() == HandLink.LEFT_HAND)
                    event_x += 2 * transition_radius * (item.transnum+1);
                else
                    event_x -= 2 * transition_radius * (item.transnum+1);
                item.xlow = event_x;
                item.xhigh = event_x + 2 * transition_radius;
                item.ylow = event_y;
                item.yhigh = event_y + 2 * transition_radius;
            }
        }

        // set locations of paths (lines and arcs)
        for (int i = 0; i < ladderpathitems.size(); i++) {
            LadderPathItem item = ladderpathitems.get(i);

            item.xstart = (item.startevent.getHand() == HandLink.LEFT_HAND ?
                           (left_x + (item.transnum_start+1)*2*transition_radius) :
                           (right_x - (item.transnum_start+1)*2*transition_radius)) +
                           (item.startevent.getJuggler() - 1) * juggler_delta_x;
            item.ystart = (int)(0.5 + (double)(height-2*border_top) * (item.startevent.getT()-loop_start) /
                                (loop_end - loop_start)) + border_top;
            item.yend = (int)(0.5 + (double)(height-2*border_top) * (item.endevent.getT()-loop_start) /
                              (loop_end - loop_start)) + border_top;

            int slot = 0;
            for (int j = 0; j < item.endevent.getNumberOfTransitions(); j++) {
                JMLTransition temp = item.endevent.getTransition(j);
                if (temp.getPath() == item.pathnum) {
                    slot = j;
                    break;
                }
            }
            item.xend = (item.endevent.getHand() == HandLink.LEFT_HAND ?
                         (left_x + (slot+1)*2*transition_radius) :
                         (right_x - (slot+1)*2*transition_radius)) +
                         (item.endevent.getJuggler() - 1) * juggler_delta_x;

            if (item.type == LadderPathItem.TYPE_SELF) {
                double a = 0.5 * Math.sqrt((double)((item.xstart-item.xend)*(item.xstart-item.xend)) +
                                           (double)((item.ystart-item.yend)*(item.ystart-item.yend)));
                double xt = 0.5 * (double)(item.xstart + item.xend);
                double yt = 0.5 * (double)(item.ystart + item.yend);
                double b = selfthrow_width * ((double)width / pat.getNumberOfJugglers());
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

        // update position of tracker bar
        setTrackerPosition();
    }

    protected void paintBackground(Graphics gr) {
        Graphics g = gr;

        // check if ladder was resized
        Dimension dim = getSize();
        if (dim.width != width || dim.height != height)
            updateView();

        boolean rebuild_ladder_image = (!ladder_image_valid &&
                                        --frames_until_ladder_image <= 0);

        if (rebuild_ladder_image) {
            ladderimage = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleImage(width, height, Transparency.OPAQUE);
            g = ladderimage.getGraphics();

            if (g instanceof Graphics2D) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
            }
        }

        if (!ladder_image_valid) {
            // first erase the background
            g.setColor(getBackground());
            g.fillRect(0, 0, width, height);

            // draw the lines signifying symmetries
            g.setColor(Color.lightGray);
            g.drawLine(0, border_top, width, border_top);
            g.drawLine(0, height - border_top, width, height-border_top);
            if (has_switch_symmetry) {
                g.drawLine(left_x, height - border_top / 2,
                           width - left_x, height - border_top / 2);
                g.drawLine(left_x, height - border_top / 2,
                           left_x + left_x, height - border_top * 3 / 4);
                g.drawLine(left_x, height - border_top / 2,
                           left_x + left_x, height - border_top / 4);
                g.drawLine(width - left_x, height - border_top / 2,
                           width - 2 * left_x, height - border_top * 3 / 4);
                g.drawLine(width - left_x, height - border_top / 2,
                           width - 2 * left_x, height - border_top / 4);
            }
            if (has_switchdelay_symmetry)
                g.drawLine(0, height / 2, width, height / 2);

            // draw the lines representing the hands
            g.setColor(Color.black);
            for (int j = 0; j < pat.getNumberOfJugglers(); j++) {
                for (int i = -1; i < 2; i++) {
                    g.drawLine(left_x + i + j * juggler_delta_x, border_top,
                               left_x + i + j * juggler_delta_x, height - border_top);
                    g.drawLine(right_x + i + j * juggler_delta_x, border_top,
                               right_x + i + j * juggler_delta_x, height - border_top);
                }
            }

            // draw paths
            Shape clip = g.getClip();

            for (int i = 0; i < ladderpathitems.size(); i++) {
                LadderPathItem item = ladderpathitems.get(i);
                g.setColor(item.color);

                Graphics2D gdash = null;

                if (item.type == LadderPathItem.TYPE_PASS) {
                    gdash = (Graphics2D)g.create();
                    Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                                              1f, new float[] {7f, 3f}, 0);
                    gdash.setStroke(dashed);

                    gdash.clipRect(left_x, border_top,
                               width - left_x, height - 2 * border_top);
                 } else {
                    g.clipRect(left_x + (item.startevent.getJuggler() - 1) * juggler_delta_x,
                               border_top,
                               right_x - left_x + (item.startevent.getJuggler() - 1) * juggler_delta_x,
                               height - 2 * border_top);
                }

                if (item.type == LadderPathItem.TYPE_CROSS) {
                    g.drawLine(item.xstart, item.ystart, item.xend, item.yend);
                } else if (item.type == LadderPathItem.TYPE_HOLD) {
                    g.drawLine(item.xstart, item.ystart, item.xend, item.yend);
                } else if (item.type == LadderPathItem.TYPE_PASS) {
                    gdash.drawLine(item.xstart, item.ystart, item.xend, item.yend);
                    gdash.dispose();
                } else if (item.type == LadderPathItem.TYPE_SELF) {
                    if (!(item.yend < border_top)) {
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
            ladder_image_valid = true;

        if (ladder_image_valid)
            gr.drawImage(ladderimage, 0, 0, this);
    }

    /*
    protected void createEventImages() {
        int circle_diam = 0;
        int dot_diam = 0;

        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = laddereventitems.get(i);

            if (item.type == LadderItem.TYPE_EVENT)
                circle_diam = item.xhigh - item.xlow;
            else
                dot_diam = item.xhigh - item.xlow;
        }

        GraphicsConfiguration gc = GraphicsEnvironment
                                    .getLocalGraphicsEnvironment()
                                    .getDefaultScreenDevice()
                                    .getDefaultConfiguration();

        circleimage = gc.createCompatibleImage(circle_diam + 2, circle_diam + 2, Transparency.BITMASK);
        Graphics g = circleimage.getGraphics();

        if (g instanceof Graphics2D) {
            Graphics2D g2 = (Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
        }

        g.setColor(getBackground());
        g.fillOval(0, 0, circle_diam, circle_diam);
        g.setColor(Color.black);
        g.drawOval(0, 0, circle_diam, circle_diam);

        dotimage = gc.createCompatibleImage(dot_diam + 2, dot_diam + 2, Transparency.BITMASK);
        g = dotimage.getGraphics();

        if (g instanceof Graphics2D) {
            Graphics2D g2 = (Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
        }

        g.setColor(Color.black);
        g.fillOval(0, 0, dot_diam, dot_diam);
    }
    */

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

        paintBackground(gr);

        // draw events
        gr.setColor(Color.black);
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = laddereventitems.get(i);

            if (item.type == LadderItem.TYPE_EVENT)
                gr.fillOval(item.xlow, item.ylow,
                            (item.xhigh-item.xlow), (item.yhigh-item.ylow));
            else {
                gr.setColor(this.getBackground());
                gr.fillOval(item.xlow, item.ylow,
                            (item.xhigh-item.xlow), (item.yhigh-item.ylow));
                gr.setColor(Color.black);
                gr.drawOval(item.xlow, item.ylow,
                            (item.xhigh-item.xlow), (item.yhigh-item.ylow));
            }
        }

        // draw the tracker line showing the time
        gr.setColor(Color.red);
        gr.drawLine(0, tracker_y, width, tracker_y);
    }
}


class LadderItem {
    static final public int TYPE_EVENT = 1;
    static final public int TYPE_TRANSITION = 2;
    static final public int TYPE_SELF = 3;
    static final public int TYPE_CROSS = 4;
    static final public int TYPE_HOLD = 5;
    static final public int TYPE_PASS = 6;

    public int type;
}

class LadderEventItem extends LadderItem {
    public int xlow, xhigh, ylow, yhigh;

    public LadderEventItem eventitem;

    public JMLEvent event;
    public int transnum;
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
