// LadderDiagram.java
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

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

import jugglinglab.util.*;
import jugglinglab.jml.*;
import jugglinglab.path.*;
import jugglinglab.prop.*;


public class LadderDiagram extends JPanel {
    static final protected Color background = Color.white;
    static final protected int border_top = 25;
    static final protected double border_sides = 0.15;
    static final protected int transition_radius = 5;
    static final protected double selfthrow_width = 0.25;
    static final protected int path_slop = 5;
    static final protected int cacheframes = 5;

	static final protected double passing_border_sides = 0.2;
	
    protected JMLPattern pat = null;

    protected double sim_time = 0.0;
    protected int width, height;
    protected int right_x, left_x;
	protected int passing_first_x, passing_offset_x;
	
    protected int tracker_y = border_top;
    private boolean has_switch_symmetry;
    private boolean has_switchdelay_symmetry;

    protected Vector laddereventitems = null;
    protected Vector ladderpathitems = null;
    protected Image laddercache = null;
    protected boolean laddercachedirty;
    protected int cacheframesleft;

    protected boolean anim_paused;


    public LadderDiagram(JMLPattern pat) {
        this.setBackground(background);
		this.setOpaque(false);
        this.pat = pat;
        createView();
    }

    protected LadderEventItem getSelectedLadderEvent(int x, int y) {
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = (LadderEventItem)laddereventitems.elementAt(i);
            if ((x >= item.xlow) && (x <= item.xhigh) &&
                (y >= item.ylow) && (y <= item.yhigh))
                return item;
        }
        return null;
    }

    protected LadderPathItem getSelectedLadderPath(int x, int y, int slop) {
        LadderPathItem result = null;
        double dmin = 0.0;

        if ((y < (border_top - slop)) || (y > (height - border_top + slop)))
            return null;

        for (int i = 0; i < ladderpathitems.size(); i++) {
            LadderPathItem item = (LadderPathItem)ladderpathitems.elementAt(i);
            double d;

            if (item.type == LadderPathItem.TYPE_SELF) {
                if ((y < (item.ystart - slop)) || (y > (item.yend + slop)))
                    continue;
                d = (x - item.xcenter)*(x - item.xcenter) +
                    (y - item.ycenter)*(y - item.ycenter);
                d = Math.abs(Math.sqrt(d) - item.radius);
            }
            else {
                int xmin = ((item.xstart < item.xend) ? item.xstart : item.xend);
                int xmax = ((item.xstart < item.xend) ? item.xend : item.xstart);

                if ((x < (xmin - slop)) || (x > (xmax + slop)))
                    continue;
                if ((y < (item.ystart - slop)) || (y > (item.yend + slop)))
                    continue;
                d = (item.xend - item.xstart)*(y - item.ystart) -
                    (x - item.xstart)*(item.yend - item.ystart);
                d = Math.abs(d) / Math.sqrt((item.xend - item.xstart)*(item.xend - item.xstart) +
                                            (item.yend - item.ystart)*(item.yend - item.ystart));
            }

            if ((int)d < slop) {
                if ((result == null) || (d < dmin)) {
                    result = item;
                    dmin = d;
                }
            }
        }
        return result;
    }

    public void setPathColor(int path, Color color) {
        for (int i = 0; i < ladderpathitems.size(); i++) {
            LadderPathItem item = (LadderPathItem)ladderpathitems.elementAt(i);
            if (item.pathnum == path)
                item.color = color;
        }
    }

    public void setTime(double time) {
        if (this.sim_time == time)
            return;

        this.sim_time = time;

        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();
        tracker_y = (int)(0.5 + (double)(height-2*border_top) * (sim_time-loop_start) /
                          (loop_end - loop_start)) + border_top;
        repaint();
    }

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
        this.laddereventitems = new Vector();
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
        this.ladderpathitems = new Vector();
		
		if (pat.getNumberOfJugglers() == 1) {
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
		}

        updateView();
    }

    protected void updateView() {
        Dimension dim = this.getSize();
        this.width = dim.width;
        this.height = dim.height;
        this.left_x = (int)((double)width/2.0 * border_sides);
        this.right_x = width - left_x;
		if (pat.getNumberOfJugglers() > 1) {
			this.passing_first_x = (int)((double)width/2.0 * passing_border_sides);
			this.passing_offset_x = (int)((double)(width - 2 * passing_first_x) / (double)(pat.getNumberOfJugglers() - 1));
		}

        this.laddercachedirty = true;
        this.laddercache = null;
        this.cacheframesleft = cacheframes;

        double loop_start = pat.getLoopStartTime();
        double loop_end = pat.getLoopEndTime();

        // set locations of events and transitions
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = (LadderEventItem)laddereventitems.elementAt(i);
            JMLEvent current = item.event;

            int event_x = (current.getHand() == HandLink.LEFT_HAND ?
                           left_x : right_x) - transition_radius;
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
            LadderPathItem item = (LadderPathItem)ladderpathitems.elementAt(i);

            item.xstart = (item.startevent.getHand() == HandLink.LEFT_HAND ?
                           (left_x + (item.transnum_start+1)*2*transition_radius) :
                           (right_x - (item.transnum_start+1)*2*transition_radius));
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
                         (right_x - (slot+1)*2*transition_radius));
            if (item.type == LadderPathItem.TYPE_SELF) {
                double a = 0.5 * Math.sqrt((double)((item.xstart-item.xend)*(item.xstart-item.xend)) +
                                           (double)((item.ystart-item.yend)*(item.ystart-item.yend)));
                double xt = 0.5 * (double)(item.xstart + item.xend);
                double yt = 0.5 * (double)(item.ystart + item.yend);
                double b = selfthrow_width * (double)width;
                double d = 0.5 * (a*a / b - b);
                if (d < (0.5*b))
                    d = 0.5 * b;
                double mult = (item.endevent.getHand()==HandLink.LEFT_HAND) ?
                    -1.0 : 1.0;
                double xc = xt + mult * d * (yt - (double)item.ystart) / a;
                double yc = yt + mult * d * ((double)item.xstart - xt) / a;
                double rad = Math.sqrt(((double)item.xstart-xc)*((double)item.xstart-xc) +
                                       ((double)item.ystart-yc)*((double)item.ystart-yc));
                item.xcenter = (int)(0.5 + xc);
                item.ycenter = (int)(0.5 + yc);
                item.radius = (int)(0.5 + rad);
            }
        }

    }

    protected void paintBackground(Graphics gr) {
        // check if ladder was resized
        Dimension dim = this.getSize();
        if ((dim.width != width) || (dim.height != height)) {
            updateView();
            //active_eventitem = null;
            //if (animator != null)
            //	animator.deactivateEvent();
        }

        if (laddercachedirty) {
            Graphics g = gr;

            if (cacheframesleft == 0) {
                laddercache = this.createImage(width, height);
                g = laddercache.getGraphics();
                // try to turn on antialiased rendering
                VersionSpecific.getVersionSpecific().setAntialias(g);

                laddercachedirty = false;
            }
            else {
                cacheframesleft--;
            }

            // first erase the background
            g.setColor(this.getBackground());
            g.fillRect(0, 0, width, height);

			if (pat.getNumberOfJugglers() == 1) {
				// draw the lines signifying symmetries
				g.setColor(Color.lightGray);
				g.drawLine(0, border_top, width, border_top);
				g.drawLine(0, height-border_top, width, height-border_top);
				if (has_switch_symmetry) {
					g.drawLine(left_x, height-border_top/2, right_x, height-border_top/2);
					g.drawLine(left_x, height-border_top/2, left_x+left_x, height-border_top*3/4);
					g.drawLine(left_x, height-border_top/2, left_x+left_x, height-border_top/4);
					g.drawLine(right_x, height-border_top/2, right_x-left_x, height-border_top*3/4);
					g.drawLine(right_x, height-border_top/2, right_x-left_x, height-border_top/4);
				}
				if (has_switchdelay_symmetry)
					g.drawLine(0, height/2, width, height/2);

				// draw the lines representing the hands
				g.setColor(Color.black);
				for (int i = -1; i < 2; i++) {
					g.drawLine(left_x+i, border_top, left_x+i, height-border_top);
					g.drawLine(right_x+i, border_top, right_x+i, height-border_top);
				}

				// draw paths
				Shape clip = g.getClip();
				for (int i = 0; i < ladderpathitems.size(); i++) {
					LadderPathItem item = (LadderPathItem)ladderpathitems.elementAt(i);

					g.setColor(item.color);
					g.clipRect(left_x, border_top, (right_x-left_x), height-2*border_top);
					if (item.type == LadderPathItem.TYPE_CROSS)
						g.drawLine(item.xstart, item.ystart, item.xend, item.yend);
					else if (item.type == LadderPathItem.TYPE_HOLD)
						g.drawLine(item.xstart, item.ystart, item.xend, item.yend);
					else {
						if (!(item.yend < border_top)) {
							g.clipRect(left_x, item.ystart, (right_x-left_x), (item.yend-item.ystart));
							g.drawOval(item.xcenter-item.radius, item.ycenter-item.radius,
									   2*item.radius, 2*item.radius);
						}
					}
					g.setClip(clip);
				}
			} else {
				// draw the lines representing the jugglers
				g.setColor(Color.black);
				for (int j = 0; j < pat.getNumberOfJugglers(); j++) {
					for (int i = -1; i < 2; i++) {
						int px = passing_first_x + j * passing_offset_x;
						g.drawLine(px+i, border_top, px+i, height-border_top);
					}
				}
			}
        }

        if (!laddercachedirty)
            gr.drawImage(laddercache, 0, 0, this);
    }

    protected void paintComponent(Graphics gr) {
        // try to turn on antialiased rendering
        VersionSpecific.getVersionSpecific().setAntialias(gr);

        paintBackground(gr);

        // draw events
        gr.setColor(Color.black);
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = (LadderEventItem)laddereventitems.elementAt(i);

            if (item.type == item.TYPE_EVENT)
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

    public int type;
}

class LadderEventItem extends LadderItem {
    public int xlow, xhigh, ylow, yhigh;

    public LadderEventItem eventitem = null;

    public JMLEvent event = null;
    public int transnum = 0;
}


class LadderPathItem extends LadderItem {
    public int xstart, ystart, xend, yend;
    public int xcenter, ycenter, radius;	// for type SELF
    public Color color;

    public JMLEvent startevent = null;
    public JMLEvent endevent = null;
    public int transnum_start = 0;
    public int pathnum;
}
