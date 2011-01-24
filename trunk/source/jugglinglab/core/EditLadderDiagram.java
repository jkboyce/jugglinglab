// EditLadderDiagram.java
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
import javax.swing.filechooser.*;
import java.io.File;
import java.net.*;
import java.text.MessageFormat;

import jugglinglab.util.*;
import jugglinglab.jml.*;
import jugglinglab.path.*;
import jugglinglab.prop.*;


public class EditLadderDiagram extends LadderDiagram implements ActionListener {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    static final protected double min_throw_time = 0.05;
    static final protected double min_hold_time = 0.05;

    protected AnimatorEdit animator = null;
	protected JFrame parent = null;
	
    static final private int STATE_INACTIVE = 0;
    static final private int STATE_EVENT_SELECTED = 1;
    static final private int STATE_MOVING_EVENT = 2;
    static final private int STATE_MOVING_TRACKER = 3;
    static final private int STATE_POPUP = 4;

    protected int gui_state;	// one of STATE_x values above
    protected LadderEventItem active_eventitem = null;
    protected int start_y;
    protected int delta_y, delta_y_min, delta_y_max;
    protected LadderItem popupitem = null;
    protected int popup_y;

    protected JPopupMenu popup = null;
    protected JMenuItem[] popupmenuitems = null;
    protected Vector dialog_controls = null;
    protected ParameterDescriptor[] dialog_pd = null;
    
    public EditLadderDiagram(JMLPattern pat, JFrame parent) {
        super(pat);
		this.parent = parent;
		
        active_eventitem = null;
        setupPopup();

        final JMLPattern fpat = pat;
        this.gui_state = STATE_INACTIVE;

        if (pat.getNumberOfJugglers() > 1)
            return;

        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(final MouseEvent me) {
                int my = me.getY();
                if (my < border_top)
                    my = border_top;
                else if (my > (height-border_top))
                    my = height - border_top;

                if (me.isPopupTrigger()) {
                    gui_state = STATE_POPUP;
                    active_eventitem = getSelectedLadderEvent(me.getX(), me.getY());
                    if (active_eventitem != null)
                        popupitem = active_eventitem;
                    else
                        popupitem = getSelectedLadderPath(me.getX(), me.getY(), path_slop);
                    popup_y = me.getY();
                    if (animator != null) {
                        double scale = (fpat.getLoopEndTime() - fpat.getLoopStartTime()) /
                        (double)(height - 2*border_top);
                        double newtime = (double)(my - border_top) * scale;
                        anim_paused = animator.getPaused();
                        animator.setPaused(true);
                        animator.setTime(newtime);
                        animator.deactivateEvent();
                        if (active_eventitem != null)
                            animator.activateEvent(active_eventitem.event);
                        animator.repaint();
                    }
                    adjustPopup(popupitem);
                    popup.show(EditLadderDiagram.this, me.getX(), me.getY());
                }
                else {
                    switch (gui_state) {
                        case STATE_INACTIVE:
                        case STATE_EVENT_SELECTED:
                            active_eventitem = getSelectedLadderEvent(me.getX(), me.getY());

                            if (active_eventitem == null) {
                                gui_state = STATE_MOVING_TRACKER;
                                tracker_y = my;
                                repaint();
                                if (animator != null) {
                                    double scale = (fpat.getLoopEndTime() - fpat.getLoopStartTime()) /
                                    (double)(height - 2*border_top);
                                    double newtime = (double)(my - border_top) * scale;
                                    anim_paused = animator.getPaused();
                                    animator.setPaused(true);
                                    animator.setTime(newtime);
                                    animator.deactivateEvent();
                                    animator.repaint();
                                }
                            } else {
                                gui_state = STATE_MOVING_EVENT;
                                start_y = me.getY();
                                findEventLimits(active_eventitem);
                                repaint();
                                if (animator != null) {
                                    animator.activateEvent(active_eventitem.event);
                                    animator.repaint();
                                }
                            }
                                break;
                        case STATE_MOVING_EVENT:
                            // ErrorDialog.handleException(new JuggleExceptionInternal("mouse pressed in MOVING_EVENT state"));
                            break;
                        case STATE_MOVING_TRACKER:
                            // ErrorDialog.handleException(new JuggleExceptionInternal("mouse pressed in MOVING_TRACKER state"));
                            break;
                        case STATE_POPUP:
                            gui_state = (active_eventitem == null) ? STATE_INACTIVE : STATE_EVENT_SELECTED;
                            if (animator != null)
                                animator.setPaused(anim_paused);
                                break;
                    }
                }
            }

            public void mouseReleased(final MouseEvent me) {
                if (me.isPopupTrigger()) {
                    switch (gui_state) {
                        case STATE_INACTIVE:
                        case STATE_EVENT_SELECTED:
                        case STATE_MOVING_EVENT:
                            // skip this code for MOVING_TRACKER state, since already
                            // executed in mousePressed() above
                            if (animator != null) {
                                int my = me.getY();
                                if (my < border_top)
                                    my = border_top;
                                else if (my > (height-border_top))
                                    my = height - border_top;

                                double scale = (fpat.getLoopEndTime() - fpat.getLoopStartTime()) /
                                    (double)(height - 2*border_top);
                                double newtime = (double)(my - border_top) * scale;
                                anim_paused = animator.getPaused();
                                animator.setPaused(true);
                                animator.setTime(newtime);
                                animator.deactivateEvent();
                                if (active_eventitem != null)
                                    animator.activateEvent(active_eventitem.event);
                                animator.repaint();
                            }
                        case STATE_MOVING_TRACKER:
                            gui_state = STATE_POPUP;

                            if (delta_y != 0) {
                                delta_y = 0;
                                repaint();
                            }
                                popup_y = me.getY();
                            popupitem = active_eventitem;
                            if (popupitem == null) {
                                popupitem = getSelectedLadderEvent(me.getX(), me.getY());
                                if (popupitem == null)
                                    popupitem = getSelectedLadderPath(me.getX(), me.getY(), path_slop);
                            }
                                adjustPopup(popupitem);
                            popup.show(EditLadderDiagram.this, me.getX(), me.getY());
                            break;
                        case STATE_POPUP:
                            ErrorDialog.handleException(new JuggleExceptionInternal("tried to enter POPUP state while already in it"));
                            break;
                    }
                }
                else {
                    switch (gui_state) {
                        case STATE_INACTIVE:
                            // should only get here if user cancelled popup menu or deselected event
                            break;
                        case STATE_EVENT_SELECTED:
                            // should only get here if user cancelled popup menu
                            break;
                        case STATE_MOVING_EVENT:
                            gui_state = STATE_EVENT_SELECTED;
                            if (delta_y != 0) {
                                moveEvent(active_eventitem.eventitem);
                                for (int i = 0; i < laddereventitems.size(); i++) {
                                    LadderEventItem item = (LadderEventItem)laddereventitems.elementAt(i);

                                    if (item.eventitem == active_eventitem.eventitem) {
                                        item.ylow += delta_y;
                                        item.yhigh += delta_y;
                                    }
                                }
                                delta_y = 0;
                                activeEventMoved();
                                /*                               layoutPattern();
                                createView();
                                active_eventitem = null;
                                if (animator != null)
                                    animator.deactivateEvent();
                                */
                                repaint();
                            }
                                break;
                        case STATE_MOVING_TRACKER:
                            gui_state = STATE_INACTIVE;
                            if (animator != null)
                                animator.setPaused(anim_paused);
                                break;
                        case STATE_POPUP:
                            break;
                    }
                }
            }
        });
        
        this.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent me) {
                int my = me.getY();
                if (my < border_top)
                        my = border_top;
                else if (my > (height-border_top))
                        my = height - border_top;
                        
                switch (gui_state) {
                    case STATE_INACTIVE:
                        // This exception was being generated on popup cancelation when it could have been ignored.
                        // See bug report 861856.  
                        //ErrorDialog.handleException(new JuggleExceptionInternal("mouse dragged in INACTIVE state"));
                        break;
                    case STATE_EVENT_SELECTED:
                        ErrorDialog.handleException(new JuggleExceptionInternal("mouse dragged in EVENT_SELECTED state"));
                        break;
                    case STATE_MOVING_EVENT:
                        int old_delta_y = delta_y;
                        delta_y = me.getY() - start_y;
                        if (delta_y < delta_y_min)
                                delta_y = delta_y_min;
                        if (delta_y > delta_y_max)
                                delta_y = delta_y_max;
                        if (delta_y != old_delta_y)
                            EditLadderDiagram.this.repaint();
                            break;
                    case STATE_MOVING_TRACKER:
                        tracker_y = my;
                        EditLadderDiagram.this.repaint();
                        if (animator != null) {
                            double scale = (fpat.getLoopEndTime() - fpat.getLoopStartTime()) /
                            (double)(height - 2*border_top);
                            double newtime = (double)(my - border_top) * scale;
                            animator.setTime(newtime);
                            animator.repaint();
                        }
                            break;
                    case STATE_POPUP:
                        break;
                }
            }
        });
    }


    protected void findEventLimits(LadderEventItem item) {
        double tmin = pat.getLoopStartTime();
        double tmax = pat.getLoopEndTime();
        double scale = (pat.getLoopEndTime() - pat.getLoopStartTime()) /
            (double)(height - 2*border_top);
        for (int j = 0; j < item.event.getNumberOfTransitions(); j++) {
            JMLTransition tr = item.event.getTransition(j);
            switch (tr.getType()) {
                case JMLTransition.TRANS_THROW:
                {
                    // Find out when the ball being thrown was last caught
                    JMLEvent ev = item.event.getPrevious();
                    while (ev != null) {
                        if ((ev.getPathTransition(tr.getPath(), JMLTransition.TRANS_CATCH) != null) ||
                            (ev.getPathTransition(tr.getPath(), JMLTransition.TRANS_SOFTCATCH) != null))
                            break;
                        ev = ev.getPrevious();
                    }
                    if (ev == null)
                        ErrorDialog.handleException(new JuggleExceptionInternal("Null event 1 in mousePressed()"));
                    double tlim = ev.getT() + min_hold_time;
                    if (tlim > tmin)
                        tmin = tlim;
                    // next catch is easy to find
                    ev = tr.getOutgoingPathLink().getEndEvent();
                    if (!sameMaster(ev, item.event)) {
                        tlim = ev.getT() - min_throw_time;
                        if (tlim < tmax)
                            tmax = tlim;
                    }
                }
                    break;
                case JMLTransition.TRANS_CATCH:
                case JMLTransition.TRANS_SOFTCATCH:
                {
                    // previous throw is easy to find
                    JMLEvent ev = tr.getIncomingPathLink().getStartEvent();
                    if (!sameMaster(ev, item.event)) {
                        double tlim = ev.getT() + min_throw_time;
                        if (tlim > tmin)
                            tmin = tlim;
                    }
                    // Find out when the ball being caught is next thrown
                    ev = item.event.getNext();
                    while (ev != null) {
                        if (ev.getPathTransition(tr.getPath(), JMLTransition.TRANS_THROW) != null)
                            break;
                        ev = ev.getNext();
                    }
                    if (ev == null)
                        ErrorDialog.handleException(new JuggleExceptionInternal("Null event 2 in mousePressed()"));
                    double tlim = ev.getT() - min_hold_time;
                    if (tlim < tmax)
                        tmax = tlim;
                }
                    break;

            }
        }
        delta_y_min = (int)((tmin - item.event.getT()) / scale);
        delta_y_max = (int)((tmax - item.event.getT()) / scale);
    }

    private boolean sameMaster(JMLEvent ev1, JMLEvent ev2) {
        JMLEvent ev1m = ev1.isMaster() ? ev1 : ev1.getMaster();
        JMLEvent ev2m = ev2.isMaster() ? ev2 : ev2.getMaster();
        return (ev1m == ev2m);
    }

    protected void moveEvent(LadderEventItem item) {
        JMLEvent ev = item.event;
        double scale = (pat.getLoopEndTime() - pat.getLoopStartTime()) /
            (double)(height - 2*border_top);
        double shift = delta_y * scale;
        double newt = ev.getT() + shift;
        if (newt < pat.getLoopStartTime()) {
            shift = pat.getLoopStartTime() - ev.getT();
            newt = pat.getLoopStartTime();
        } else if (newt >= pat.getLoopEndTime()) {
            shift = pat.getLoopEndTime() - 0.0001 - ev.getT();
            newt = pat.getLoopEndTime() - 0.0001;
       	}

        boolean throwpath[] = new boolean[pat.getNumberOfPaths()];
        boolean catchpath[] = new boolean[pat.getNumberOfPaths()];
        boolean holdpathorig[] = new boolean[pat.getNumberOfPaths()];
        boolean holdpathnew[] = new boolean[pat.getNumberOfPaths()];
        for (int j = 0; j < ev.getNumberOfTransitions(); j++) {
            JMLTransition tr = ev.getTransition(j);
            switch (tr.getType()) {
                case JMLTransition.TRANS_THROW:
                    throwpath[tr.getPath()-1] = true;
                    break;
                case JMLTransition.TRANS_CATCH:
                case JMLTransition.TRANS_SOFTCATCH:
                    catchpath[tr.getPath()-1] = true;
                    break;
                case JMLTransition.TRANS_HOLDING:
                    holdpathnew[tr.getPath()-1] = holdpathorig[tr.getPath()-1] = true;
                    break;
            }
        }

        if (delta_y < 0) {		// moving to earlier time
            ev = ev.getPrevious();
            while ((ev != null) && (ev.getT() > newt)) {
                if (!sameMaster(ev, item.event) && (ev.getJuggler() == item.event.getJuggler()) &&
                                    (ev.getHand() == item.event.getHand())) {
                    for (int j = 0; j < ev.getNumberOfTransitions(); j++) {
                        JMLTransition tr = ev.getTransition(j);
                        switch (tr.getType()) {
                            case JMLTransition.TRANS_THROW:
                                holdpathnew[tr.getPath()-1] = true;
                                break;
                            case JMLTransition.TRANS_CATCH:
                            case JMLTransition.TRANS_SOFTCATCH:
                                holdpathnew[tr.getPath()-1] = false;
                                break;
                            case JMLTransition.TRANS_HOLDING:
                                if (throwpath[tr.getPath()-1]) {
                                    ev.removeTransition(j);
                                    if (!ev.isMaster())
                                        ev.getMaster().removeTransition(j);
                                    j--;	// next trans moved into slot
                                }
                                break;
                        }
                    }

                    for (int j = 0; j < pat.getNumberOfPaths(); j++) {
                        if (catchpath[j]) {
                            JMLTransition tr = new JMLTransition(JMLTransition.TRANS_HOLDING, (j+1), null, null);
                            ev.addTransition(tr);
                            if (!ev.isMaster()) {
                                Permutation pp = ev.getPathPermFromMaster().getInverse();
                                tr = new JMLTransition(JMLTransition.TRANS_HOLDING, pp.getMapping(j+1), null, null);
                                ev.getMaster().addTransition(tr);
                            }
                        }
                    }

                }
                ev = ev.getPrevious();
            }
        } else if (delta_y > 0) {		// moving to later time
            ev = ev.getNext();
            while ((ev != null) && (ev.getT() < newt)) {
                if (!sameMaster(ev, item.event) && (ev.getJuggler() == item.event.getJuggler()) && (ev.getHand() == item.event.getHand())) {
                    for (int j = 0; j < ev.getNumberOfTransitions(); j++) {
                        JMLTransition tr = ev.getTransition(j);
                        switch (tr.getType()) {
                            case JMLTransition.TRANS_THROW:
                                holdpathnew[tr.getPath()-1] = false;
                                break;
                            case JMLTransition.TRANS_CATCH:
                            case JMLTransition.TRANS_SOFTCATCH:
                                holdpathnew[tr.getPath()-1] = true;
                                break;
                            case JMLTransition.TRANS_HOLDING:
                                if (catchpath[tr.getPath()-1]) {
                                    ev.removeTransition(j);
                                    if (!ev.isMaster())
                                        ev.getMaster().removeTransition(j);
                                    j--;
                                }
                                break;
                        }
                    }

                    for (int j = 0; j < pat.getNumberOfPaths(); j++) {
                        if (throwpath[j]) {
                            JMLTransition tr = new JMLTransition(JMLTransition.TRANS_HOLDING, (j+1), null, null);
                            ev.addTransition(tr);
                            if (!ev.isMaster()) {
                                Permutation pp = ev.getPathPermFromMaster().getInverse();
                                tr = new JMLTransition(JMLTransition.TRANS_HOLDING, pp.getMapping(j+1), null, null);
                                ev.getMaster().addTransition(tr);
                            }
                        }
                    }

                }
                ev = ev.getNext();
            }
        }

        ev = item.event;
        Permutation pp = ev.getPathPermFromMaster().getInverse();
        if (!ev.isMaster())
            ev = ev.getMaster();

        for (int j = 0; j < pat.getNumberOfPaths(); j++) {
            if (holdpathnew[j] != holdpathorig[j]) {
                if (holdpathnew[j]) {
                    JMLTransition tr = new JMLTransition(JMLTransition.TRANS_HOLDING,
                                                         pp.getMapping(j+1), null, null);
                    ev.addTransition(tr);
                } else {
                    JMLTransition tr = ev.getPathTransition(pp.getMapping(j+1),
                                                            JMLTransition.TRANS_HOLDING);
                    if (tr == null)
                        ErrorDialog.handleException(new JuggleExceptionInternal("Null transition in removing hold"));
                    ev.removeTransition(tr);
                }
            }
        }

        pat.removeEvent(ev);
        ev.setT(ev.getT() + shift);	// change time of master
        pat.addEvent(ev);	// remove/add cycle keeps events sorted
    }


    private static String popupItems[] = {
        "Change title...", "Change overall timing...", "Add event to L hand",
        "Add event to R hand", null, "Remove event", null,
        "Define prop...", "Make last in event", "Define throw...",
        "Change to catch", "Change to softcatch"
    };

    protected void setupPopup() {
        popup = new JPopupMenu();
        popupmenuitems = new JMenuItem[popupItems.length];
        
        JMenuItem item;
        for (int i = 0; i < popupItems.length; i++) {
            String name = popupItems[i];
            if (name != null) {
                item = new JMenuItem(guistrings.getString(name.replace(' ', '_')));
                item.addActionListener(this);
                popup.add(item);
                popupmenuitems[i] = item;
            } else
                popup.addSeparator();
        }

        popup.setBorder(new BevelBorder(BevelBorder.RAISED));

        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent e) {}
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // System.out.println("popup becoming invisible");
                if (gui_state == STATE_POPUP) {
                    gui_state = (active_eventitem == null) ? STATE_INACTIVE : STATE_EVENT_SELECTED;
                    if (animator != null)
                        animator.setPaused(anim_paused);
                }
            }
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
        });
    }

    protected void adjustPopup(LadderItem item) {
        for (int i = 0; i < popupmenuitems.length; i++) {
            if (popupmenuitems[i] != null)
                popupmenuitems[i].setEnabled(true);
        }

        if (item == null) {
            for (int i = 5; i < popupmenuitems.length; i++)
                if (popupmenuitems[i] != null)
                    popupmenuitems[i].setEnabled(false);
            return;
        }

        switch (item.type) {
            case LadderEventItem.TYPE_EVENT:
            {
                LadderEventItem evitem = (LadderEventItem)item;

                for (int i = 0; i < 4; i++)
                    if (popupmenuitems[i] != null)
                        popupmenuitems[i].setEnabled(false);

                for (int i = 7; i < popupmenuitems.length; i++)
                    if (popupmenuitems[i] != null)
                        popupmenuitems[i].setEnabled(false);

                for (int i = 0; i < evitem.event.getNumberOfTransitions(); i++) {
                    JMLTransition tr = evitem.event.getTransition(i);
                    if (tr.getType() != JMLTransition.TRANS_HOLDING) {
                        popupmenuitems[5].setEnabled(false);
                        break;
                    }
                }

                // check to make sure we're not allowing the user to delete
                // an event if it's the last one in that hand.
                // do this by finding the next event in the same hand; if it
                // has the same master, it's the only one
                if (popupmenuitems[5].isEnabled()) {
                    int hand = evitem.event.getHand();
                    int juggler = evitem.event.getJuggler();
                    JMLEvent evm1 = evitem.event.isMaster() ? evitem.event :
                        evitem.event.getMaster();
                    JMLEvent ev = evitem.event.getNext();
                    while (ev != null) {
                        if ((ev.getHand() == hand) && (ev.getJuggler() == juggler)) {
                            JMLEvent evm2 = ev.isMaster() ? ev : ev.getMaster();
                            if (evm1 == evm2)
                                popupmenuitems[5].setEnabled(false);
                            break;
                        }
                        ev = ev.getNext();
                    }
                }
            }
                break;
            case LadderEventItem.TYPE_TRANSITION:
            {
                LadderEventItem evitem = (LadderEventItem)item;
                JMLTransition tr = evitem.event.getTransition(evitem.transnum);

                for (int i = 0; i < 6; i++)
                    if (popupmenuitems[i] != null)
                        popupmenuitems[i].setEnabled(false);

                if (evitem.transnum == (evitem.event.getNumberOfTransitions()-1))
                    popupmenuitems[8].setEnabled(false);

                if (tr.getType() != JMLTransition.TRANS_THROW)
                    popupmenuitems[9].setEnabled(false);

                if (tr.getType() != JMLTransition.TRANS_SOFTCATCH)
                    popupmenuitems[10].setEnabled(false);

                if (tr.getType() != JMLTransition.TRANS_CATCH)
                    popupmenuitems[11].setEnabled(false);
            }
                break;
            default:	// LadderPathItem
                popupmenuitems[5].setEnabled(false);

                for (int i = 8; i < popupmenuitems.length; i++)
                    if (popupmenuitems[i] != null)
                        popupmenuitems[i].setEnabled(false);
                    break;
        }
    }

    public void actionPerformed(ActionEvent event) {
        String name = event.getActionCommand();
        if (name == null)
            return;
        int itemnum = 0;

        for (int i = 0; i < popupItems.length; i++) {
            if (popupItems[i] != null && name.equals(guistrings.getString(popupItems[i].replace(' ', '_')))) {
                itemnum = i;
                break;
            }
        }

        switch (itemnum) {
            case 0:		// Change title...
                changeTitle();
                break;
            case 1:		// Change overall timing...
                changeTiming();
                break;
            case 2:		// Add event to L hand
            {
                JMLEvent ev = addEventToHand(HandLink.LEFT_HAND);
                active_eventitem = null;
                if (animator != null)
                    animator.deactivateEvent();
                layoutPattern();
                createView();
                repaint();
            }
                break;
            case 3:		// Add event to R hand
            {
                JMLEvent ev = addEventToHand(HandLink.RIGHT_HAND);
                active_eventitem = null;
                if (animator != null)
                    animator.deactivateEvent();
                layoutPattern();
                createView();
                repaint();
            }
                break;
            case 4:
                break;
            case 5:		// Remove event
            {
                // adjustPopup() ensures that the event only has hold transitions
                if (!(popupitem instanceof LadderEventItem)) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("LadderDiagram remove event class format"));
                    return;
                }
                JMLEvent ev = ((LadderEventItem)popupitem).event;
                if (!ev.isMaster())
                    ev = ev.getMaster();
                pat.removeEvent(ev);
                active_eventitem = null;
                if (animator != null)
                    animator.deactivateEvent();
                layoutPattern();
                createView();
                repaint();
            }
                break;
            case 6:
                break;
            case 7:		// Define prop...
                defineProp();
                break;
            case 8:		// Make last in event
            {
                if (popupitem == null) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("No popupitem in case 8"));
                    return;
                }
                if (!(popupitem instanceof LadderEventItem)) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("LadderDiagram make last transition class format"));
                    return;
                }
                JMLEvent ev = ((LadderEventItem)popupitem).event;
                if (!ev.isMaster())
                    ev = ev.getMaster();
                JMLTransition tr = ev.getTransition(((LadderEventItem)popupitem).transnum);
                ev.removeTransition(tr);
                ev.addTransition(tr);	// will add at end
                active_eventitem = null;
                if (animator != null)
                    animator.deactivateEvent();
                layoutPattern();
                createView();
                repaint();
            }
                break;
            case 9:		// Define throw...
                defineThrow();
                break;
            case 10:	// Change to catch
            {
                if (popupitem == null) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("No popupitem in case 10"));
                    return;
                }
                if (!(popupitem instanceof LadderEventItem)) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("LadderDiagram change to catch class format"));
                    return;
                }
                JMLEvent ev = ((LadderEventItem)popupitem).event;
                if (!ev.isMaster())
                    ev = ev.getMaster();
                int transnum = ((LadderEventItem)popupitem).transnum;
                JMLTransition tr = ev.getTransition(((LadderEventItem)popupitem).transnum);
                tr.setType(JMLTransition.TRANS_CATCH);
                active_eventitem = null;
                if (animator != null)
                    animator.deactivateEvent();
                layoutPattern();
                createView();
                repaint();
            }
                break;
            case 11:	// Change to softcatch
            {
                if (popupitem == null) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("No popupitem in case 11"));
                    return;
                }
                if (!(popupitem instanceof LadderEventItem)) {
                    ErrorDialog.handleException(new JuggleExceptionInternal("LadderDiagram change to softcatch class format"));
                    return;
                }
                JMLEvent ev = ((LadderEventItem)popupitem).event;
                if (!ev.isMaster())
                    ev = ev.getMaster();
                int transnum = ((LadderEventItem)popupitem).transnum;
                JMLTransition tr = ev.getTransition(((LadderEventItem)popupitem).transnum);
                tr.setType(JMLTransition.TRANS_SOFTCATCH);
                active_eventitem = null;
                if (animator != null)
                    animator.deactivateEvent();
                layoutPattern();
                createView();
                repaint();
            }
                break;
        }

        popupitem = null;
        // System.out.println("action performed");
        if (gui_state == STATE_POPUP) {
            gui_state = (active_eventitem == null) ? STATE_INACTIVE : STATE_EVENT_SELECTED;
            if (animator != null)
                animator.setPaused(anim_paused);
        }
    }

    protected void changeTitle() {
        final JDialog jd = new JDialog(parent, guistrings.getString("Change_title"), true);
        GridBagLayout gb = new GridBagLayout();
        jd.getContentPane().setLayout(gb);

        final JTextField tf = new JTextField(20);
        tf.setText(pat.getTitle());

        JButton okbutton = new JButton(guistrings.getString("OK"));
        okbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String newtitle = tf.getText();
                pat.setTitle(newtitle);
                jd.dispose();
            }
        });

        jd.getContentPane().add(tf);
        gb.setConstraints(tf, make_constraints(GridBagConstraints.LINE_START,0,0,
                                               new Insets(10,10,0,10)));
        jd.getContentPane().add(okbutton);
        gb.setConstraints(okbutton, make_constraints(GridBagConstraints.LINE_END,0,1,
                                                     new Insets(10,10,10,10)));
        jd.getRootPane().setDefaultButton(okbutton);// OK button is default
		jd.pack();
		jd.setResizable(false);
		jd.setVisible(true);
		parent.setTitle(pat.getTitle());
    }

    protected void changeTiming() {
        final JDialog jd = new JDialog(parent, guistrings.getString("Change_timing"), true);
        GridBagLayout gb = new GridBagLayout();
        jd.getContentPane().setLayout(gb);

        JPanel p1 = new JPanel();
        p1.setLayout(gb);
        JLabel lab = new JLabel(guistrings.getString("Rescale_percentage"));
        p1.add(lab);
        gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_END,0,0,
                                                new Insets(0,0,0,0)));
        final JTextField tf = new JTextField(7);
        p1.add(tf);
        gb.setConstraints(tf, make_constraints(GridBagConstraints.LINE_START,1,0,
                                               new Insets(0,5,0,0)));

        JButton okbutton = new JButton(guistrings.getString("OK"));
        okbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jd.dispose();
            }
        });

        jd.getContentPane().add(p1);
        gb.setConstraints(p1, make_constraints(GridBagConstraints.LINE_START,0,0,
                                               new Insets(10,10,0,10)));
        jd.getContentPane().add(okbutton);
        gb.setConstraints(okbutton, make_constraints(GridBagConstraints.LINE_END,0,1,
                                                     new Insets(10,10,10,10)));
        jd.getRootPane().setDefaultButton(okbutton);// OK button is default
            jd.pack();
            jd.setVisible(true);

            double scale;
            try {
                scale = Double.valueOf(tf.getText()).doubleValue() / 100.0;
            } catch (NumberFormatException e) {
                new ErrorDialog(this, "Number format error in rescale percentage");
                return;
            }
            if (scale > 0.0) {
                JMLEvent ev = pat.getEventList();
                while (ev != null) {
                    if (ev.isMaster())
                        ev.setT(ev.getT() * scale);
                    ev = ev.getNext();
                }
                JMLPosition pos = pat.getPositionList();
                while (pos != null) {
                    pos.setT(pos.getT() * scale);
                    pos = pos.getNext();
                }

                for (int i = 0; i < pat.getNumberOfSymmetries(); i++) {
                    JMLSymmetry sym = pat.getSymmetry(i);
                    double delay = sym.getDelay();
                    if (delay > 0.0) {
                        sym.setDelay(delay * scale);
                        if ((delay * scale) < animator.getTime()) {
                            animator.setTime(0.0);
                        }
                    }
                }
                layoutPattern();
                createView();
            }
    }

    protected JMLEvent addEventToHand(int hand) {
        int juggler = 1;	// assumes single juggler
        double scale = (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double)(height - 2*border_top);
        double evtime = (double)(popup_y - border_top) * scale;
        Coordinate evpos = new Coordinate();
        
        try {
            pat.getHandCoordinate(juggler, hand, evtime, evpos);
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleException(jei);
        }
        
        JMLEvent ev = new JMLEvent();
        ev.setLocalCoordinate(pat.convertGlobalToLocal(evpos, juggler, evtime));
        ev.setT(evtime);
        ev.setHand(juggler, hand);
        pat.addEvent(ev);

        for (int i = 0; i < pat.getNumberOfPaths(); i++) {
            boolean holding = false;

            JMLEvent evt = ev.getPrevious();
            while (evt != null) {
                JMLTransition tr = evt.getPathTransition(i+1, JMLTransition.TRANS_ANY);
                if (tr != null) {
                    if ((evt.getJuggler() != ev.getJuggler()) ||
                        (evt.getHand() != ev.getHand())) {
                        holding = false;
                        break;
                    }
                    if (tr.getType() == JMLTransition.TRANS_THROW) {
                        holding = false;
                        break;
                    }
                    holding = true;
                    break;
                }
                evt = evt.getPrevious();
            }

            if (holding) {
                JMLTransition tr = new JMLTransition(JMLTransition.TRANS_HOLDING, (i+1), null, null);
                ev.addTransition(tr);
            }
        }

        return ev;
    }

    protected void defineProp() {
        if (popupitem == null) {
            ErrorDialog.handleException(new JuggleExceptionInternal("defineProp() null popupitem"));
            return;
        }

        // figure out which path number the user selected
        int pn = 0;
        if (popupitem instanceof LadderEventItem) {
            if (popupitem.type != LadderItem.TYPE_TRANSITION) {
                ErrorDialog.handleException(new JuggleExceptionInternal("defineProp() bad LadderItem type"));
                return;
            }

            JMLEvent ev = ((LadderEventItem)popupitem).event;
            int transnum = ((LadderEventItem)popupitem).transnum;
            JMLTransition tr = ev.getTransition(transnum);
            pn = tr.getPath();
        }
        else {
            pn = ((LadderPathItem)popupitem).pathnum;
        }

        final int pathnum = pn;
        final int[] animpropnum = animator.getAnimPropNum();
        final int propnum = animpropnum[pathnum - 1];
        //final int propnum = pat.getPropAssignment(pathnum);
        //		System.out.println("pathnum = " + pathnum + ", propnum = " + propnum);
        final Prop startprop = pat.getProp(propnum);
        
        final boolean paused = animator.getPaused();
        animator.setPaused(true);

        String[] prtypes = Prop.builtinProps;

        final JDialog jd = new JDialog(parent, guistrings.getString("Define_prop"), true);
        GridBagLayout gb = new GridBagLayout();
        jd.getContentPane().setLayout(gb);

        JPanel p1 = new JPanel();
        p1.setLayout(gb);
        JLabel lab = new JLabel(guistrings.getString("Prop_type"));
        p1.add(lab);
        gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_END,0,0,
                                                new Insets(0,0,0,0)));

        final JPanel p2 = new JPanel();
        p2.setLayout(gb);

        final JComboBox cb1 = new JComboBox(prtypes);
        p1.add(cb1);
        gb.setConstraints(cb1, make_constraints(GridBagConstraints.LINE_START,1,0,
                                                new Insets(0,10,0,0)));
        cb1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                String type = (String)cb1.getItemAt(cb1.getSelectedIndex());
                //				System.out.println("Got an action item: "+type);
                try {
                    Prop pt;
                    if (type.equalsIgnoreCase(startprop.getName()))
                        pt = startprop;
                    else
                        pt = Prop.getProp(type);
                    makeParametersPanel(p2, pt.getParameterDescriptors());
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(jd, jeu.getMessage());
                    return;
                }
                jd.pack();
            }
        });
        String[] bp = Prop.builtinProps;
        for (int i = 0; i < bp.length; i++) {
            if (bp[i].equalsIgnoreCase(startprop.getName())) {
                cb1.setSelectedIndex(i);
                break;
            }
        }

        final JPanel p3 = new JPanel();
        p3.setLayout(gb);
        JButton cancelbutton = new JButton(guistrings.getString("Cancel"));
        p3.add(cancelbutton);
        gb.setConstraints(cancelbutton, make_constraints(GridBagConstraints.LINE_END,0,0,new Insets(0,0,0,0)));
        cancelbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jd.dispose();
                animator.setPaused(paused);
            }
        });
        JButton okbutton = new JButton(guistrings.getString("OK"));
        p3.add(okbutton);
        gb.setConstraints(okbutton, make_constraints(GridBagConstraints.LINE_END,1,0,new Insets(0,10,0,0)));
        okbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = (String)cb1.getItemAt(cb1.getSelectedIndex());

                String mod = null;
                try {
                    mod = getParameterList();
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(parent, jeu.getMessage());
                    return;
                }

                // System.out.println("type = "+type+", mod = "+mod);
                
                // Sync paths with current prop list
                for (int i = 0; i < pat.getNumberOfPaths(); i++) {
                    pat.setPropAssignment(i+1, animpropnum[i]);
                }

                // check to see if any other paths are using this prop definition
                boolean killprop = true;
                for (int i = 0; i < pat.getNumberOfPaths(); i++) {
                    if (i != pathnum - 1) {
                        if (animpropnum[i] == propnum) {
                            killprop = false;
                            break;
                        }
                    }
                }
                
                if (killprop) {
                    pat.removeProp(propnum);
                }
                    
                // check to see if a prop like this one has already been defined
                boolean gotmatch = false;
                int matchingprop = 0;
                for (int i = 1; i <= pat.getNumberOfProps(); i++) {
                    PropDef pdef = pat.getPropDef(i);
                    if (type.equalsIgnoreCase(pdef.getType())) {
                        if (((mod == null) && (pdef.getMod() == null)) ||
                            ((mod != null) && mod.equalsIgnoreCase(pdef.getMod()))) {
                            gotmatch = true;
                            matchingprop = i;
                            break;
                        }
                    }
                }

                if (gotmatch) {
                    // new prop is identical to pre-existing one
                    pat.setPropAssignment(pathnum, matchingprop);
                }
                else {
                    // new prop is different
                    PropDef newprop = new PropDef(type.toLowerCase(), mod);
                    pat.addProp(newprop);
                    pat.setPropAssignment(pathnum, pat.getNumberOfProps());
                }

                layoutPattern();
                jd.dispose();
                animator.setPaused(paused);
            }
        });

        jd.getContentPane().add(p1);
        gb.setConstraints(p1, make_constraints(GridBagConstraints.LINE_START,0,0,new Insets(10,10,0,10)));
        jd.getContentPane().add(p2);
        gb.setConstraints(p2, make_constraints(GridBagConstraints.LINE_START,0,1,new Insets(0,0,0,0)));
        jd.getContentPane().add(p3);
        gb.setConstraints(p3, make_constraints(GridBagConstraints.LINE_END,0,2,new Insets(10,10,10,10)));
        jd.getRootPane().setDefaultButton(okbutton);// OK button is default

		Locale loc = JLLocale.getLocale();
		jd.applyComponentOrientation(ComponentOrientation.getOrientation(loc));
		
        jd.pack();
		jd.setResizable(false);
        jd.setVisible(true);	// blocks until dispose() above
        dialog_controls = null;
    }

    protected void defineThrow() {
        if (!(popupitem instanceof LadderEventItem)) {
            ErrorDialog.handleException(new JuggleExceptionInternal("defineThrow() class format"));
            return;
        }
        JMLEvent ev = ((LadderEventItem)popupitem).event;
        if (!ev.isMaster())
            ev = ev.getMaster();
        final JMLTransition tr = ev.getTransition(((LadderEventItem)popupitem).transnum);

        String[] pptypes = Path.builtinPaths;
        
        final JDialog jd = new JDialog(parent, guistrings.getString("Define_throw"), true);
        GridBagLayout gb = new GridBagLayout();
        jd.getContentPane().setLayout(gb);

        JPanel p1 = new JPanel();
        p1.setLayout(gb);
        JLabel lab = new JLabel(guistrings.getString("Throw_type"));
        p1.add(lab);
        gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_END,0,0,new Insets(0,0,0,0)));

        final JPanel p2 = new JPanel();
        p2.setLayout(gb);

        final JComboBox cb1 = new JComboBox(pptypes);
        p1.add(cb1);
        gb.setConstraints(cb1, make_constraints(GridBagConstraints.LINE_START,1,0,
                                                new Insets(0,10,0,0)));
        cb1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                String type = (String)cb1.getItemAt(cb1.getSelectedIndex());
                // System.out.println("Got an action item: "+type);
                try {
                    Path ppt;
                    if (type.equalsIgnoreCase(tr.getThrowType()))
                        ppt = tr.getOutgoingPathLink().getPath();
                    else
                        ppt = Path.getPath(type);
                    makeParametersPanel(p2, ppt.getParameterDescriptors());
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(jd, jeu.getMessage());
                    return;
                }
                jd.pack();
            }
        });
        String[] bpp = Path.builtinPaths;
        for (int i = 0; i < bpp.length; i++) {
            if (bpp[i].equalsIgnoreCase(tr.getThrowType())) {
                cb1.setSelectedIndex(i);
                break;
            }
        }

        final JPanel p3 = new JPanel();
        p3.setLayout(gb);
        JButton cancelbutton = new JButton(guistrings.getString("Cancel"));
        p3.add(cancelbutton);
        gb.setConstraints(cancelbutton, make_constraints(GridBagConstraints.LINE_END,0,0,new Insets(0,0,0,0)));
        cancelbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jd.dispose();
            }
        });
        JButton okbutton = new JButton(guistrings.getString("OK"));
        p3.add(okbutton);
        gb.setConstraints(okbutton, make_constraints(GridBagConstraints.LINE_END,1,0,
                                                     new Insets(0,10,0,0)));
        okbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = (String)cb1.getItemAt(cb1.getSelectedIndex());
                tr.setThrowType(type.toLowerCase());

                String mod = null;
                try {
                    mod = getParameterList();
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(parent, jeu.getMessage());
                    return;
                }

                tr.setMod(mod);

                layoutPattern();
                jd.dispose();
            }
        });

        jd.getContentPane().add(p1);
        gb.setConstraints(p1, make_constraints(GridBagConstraints.LINE_START,0,0,new Insets(10,10,0,10)));
        jd.getContentPane().add(p2);
        gb.setConstraints(p2, make_constraints(GridBagConstraints.LINE_START,0,1,new Insets(0,0,0,0)));
        jd.getContentPane().add(p3);
        gb.setConstraints(p3, make_constraints(GridBagConstraints.LINE_END,0,2,new Insets(10,10,10,10))); 
        jd.getRootPane().setDefaultButton(okbutton);// OK button is default

        jd.pack();
		jd.setResizable(false);
        jd.setVisible(true);	// blocks until dispose() above
        dialog_controls = null;
    }

    private static final String[] booleanList = { "True", "False" };

    protected void makeParametersPanel(JPanel jp, ParameterDescriptor[] pd) {
        jp.removeAll();
        GridBagLayout gb = new GridBagLayout();
        jp.setLayout(gb);

        dialog_controls = new Vector();
        dialog_pd = pd;

        if (pd.length != 0) {
            /*
             JLabel lab1 = new JLabel("Properties");

             jp.add(lab1);
             gb.setConstraints(lab1, make_constraints(GridBagConstraints.LINE_START,0,0,
                                                      new Insets(10,10,0,10)));
             */
            JPanel pdp = new JPanel();
            pdp.setLayout(gb);

            for (int i = 0; i < pd.length; i++) {
                JLabel lab = new JLabel(pd[i].name);
                pdp.add(lab);
                gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_START,0,i,new Insets(0,0,0,0)));
                if (pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                    //JComboBox jcb = new JComboBox(booleanList);
					JCheckBox jcb = new JCheckBox();
                    pdp.add(jcb);
                    gb.setConstraints(jcb, make_constraints(GridBagConstraints.LINE_START,1,i,new Insets(2,5,2,0)));
                    dialog_controls.add(jcb);
                    boolean def = ((Boolean)(pd[i].value)).booleanValue();
                    //jcb.setSelectedIndex(def ? 0 : 1);
					jcb.setSelected(def);
                }
                else if (pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
                    JTextField tf = new JTextField(7);
                    pdp.add(tf);
                    gb.setConstraints(tf, make_constraints(GridBagConstraints.LINE_START,1,i,new Insets(0,5,0,0)));
                    dialog_controls.add(tf);
                    Double def = (Double)(pd[i].value);
                    tf.setText(def.toString());
                }
                else if (pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
                    String[] choices = new String[pd[i].range.size()];
                    pd[i].range.copyInto(choices);

                    JComboBox jcb = new JComboBox(choices);
                    pdp.add(jcb);
                    gb.setConstraints(jcb, make_constraints(GridBagConstraints.LINE_START,1,i,new Insets(0,5,0,0)));
                    dialog_controls.add(jcb);

                    String val = (String)(pd[i].value);
                    for (int j = 0; j < choices.length; j++) {
                        if (val.equalsIgnoreCase(choices[j])) {
                            jcb.setSelectedIndex(j);
                            break;
                        }
                    }
                }
                else if (pd[i].type == ParameterDescriptor.TYPE_INT) {
                    JTextField tf = new JTextField(4);
                    pdp.add(tf);
                    gb.setConstraints(tf, make_constraints(GridBagConstraints.LINE_START,1,i,new Insets(0,5,0,0)));
                    dialog_controls.add(tf);
                    Integer def = (Integer)(pd[i].value);
                    tf.setText(def.toString());
					
					tf.addCaretListener(new CaretListener() {
						public void caretUpdate(CaretEvent e) {
							//System.out.println("Caret Update");
						}
					});
                }
				else if (pd[i].type == ParameterDescriptor.TYPE_ICON) {
					final ParameterDescriptor fpd = pd[i];
					final ParameterDescriptor[] fpds = pd;
					final JPanel fjp = jp;
					URL filename = (URL)fpd.value;
					
					ImageIcon icon = new ImageIcon(filename, filename.toString());
					// Scale the image down if it's too big
					final float MAX_HEIGHT = 100;
					if (icon.getIconHeight() > MAX_HEIGHT) {
						float scaleFactor = MAX_HEIGHT/icon.getIconHeight();
						int height = (int)(scaleFactor*icon.getIconHeight());
						int width = (int)(scaleFactor*icon.getIconWidth());
						icon.setImage(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
					}
					JLabel label = new JLabel(icon);
					
					// Clicking on the icon launches a file chooser for getting a new image
					label.addMouseListener(new MouseAdapter() {
						public void mouseClicked(MouseEvent e) {
							FileFilter filter = new FileFilter() {
								public boolean accept(File f) {
									StringTokenizer st = new StringTokenizer(f.getName(), ".");
									String ext = "";
									while (st.hasMoreTokens())
										ext = st.nextToken();
									return (ext.equals("jpg") || ext.equals("gif") || 
											ext.equals("png") || f.isDirectory());
								}
								
								public String getDescription() {
									return "Image Files";
								}
							};
							PlatformSpecific ps = PlatformSpecific.getPlatformSpecific();
							int result = ps.showOpenDialog(EditLadderDiagram.this, filter);
							if (result == JFileChooser.APPROVE_OPTION) {
								try {
									URL source = ps.getSelectedFile().toURI().toURL();
									// We have to load the image to get the correct dimensions
									ImageIcon icon = new ImageIcon(source, source.toString());
									// Rebuild the paramter descriptions
									fpds[0].value = source;
									//fpds[1].value = new Integer(icon.getIconWidth());
									//fpds[2].value = new Integer(icon.getIconHeight());
									//fpds[1].default_value = fpds[1].value;
									//fpds[2].default_value = fpds[2].value;
									// Remake the parameter panal with new default values.
									makeParametersPanel(fjp, fpds);
									((JDialog)(fjp.getTopLevelAncestor())).pack();
								} catch (MalformedURLException ex) {
									// This should never happen
									ErrorDialog.handleException(new JuggleExceptionUser(errorstrings.getString("Error_malformed_URL.")));
								}
							}
						}
					});
					// Add the icon to the panel
					pdp.add(label);
					gb.setConstraints(label, make_constraints(GridBagConstraints.LINE_START,1,i,new Insets(0,5,5,0)));
					dialog_controls.add(label);
				}
            }

            jp.add(pdp);
            gb.setConstraints(pdp, make_constraints(GridBagConstraints.LINE_START,0,1,new Insets(10,10,0,10)));
        }
    }

    protected String getParameterList() throws JuggleExceptionUser {
        String result = null;
        for (int i = 0; i < dialog_pd.length; i++) {
            String term = null;
            Object control = dialog_controls.get(i);
            if (dialog_pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                //JComboBox jcb = (JComboBox)control;
                //boolean val = ((jcb.getSelectedIndex() == 0) ? true : false);
				JCheckBox jcb = (JCheckBox)control;
				boolean val = jcb.isSelected();
                boolean def_val = ((Boolean)(dialog_pd[i].default_value)).booleanValue();
                if (val != def_val)
                    term = (new Boolean(val)).toString();
            }
            else if (dialog_pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
                JTextField tf = (JTextField)control;
                try {
                    double val = Double.valueOf(tf.getText()).doubleValue();
                    double def_val = ((Double)(dialog_pd[i].default_value)).doubleValue();
                    if (val != def_val)
                        term = tf.getText().trim();
                } catch (NumberFormatException nfe) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { dialog_pd[i].name };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            }
            else if (dialog_pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
                JComboBox jcb = (JComboBox)control;
                int ind = jcb.getSelectedIndex();
                String val = (String)(dialog_pd[i].range.elementAt(ind));
                String def_val = (String)(dialog_pd[i].default_value);
                if (!val.equalsIgnoreCase(def_val))
                    term = val;
            }
            else if (dialog_pd[i].type == ParameterDescriptor.TYPE_INT) {
                JTextField tf = (JTextField)control;
                try {
                    int val = Integer.valueOf(tf.getText()).intValue();
                    int def_val = ((Integer)(dialog_pd[i].default_value)).intValue();
                    if (val != def_val)
                        term = tf.getText().trim();
                } catch (NumberFormatException nfe) {
					String template = errorstrings.getString("Error_number_format");
					Object[] arguments = { dialog_pd[i].name };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }
            }
			else if (dialog_pd[i].type == ParameterDescriptor.TYPE_ICON) {
				JLabel label = (JLabel)control;
				ImageIcon icon = (ImageIcon)label.getIcon();
				String def = ((URL)(dialog_pd[i].default_value)).toString();
				if (!icon.getDescription().equals(def))
					term = icon.getDescription();  // This contains the URL string
			}

            if (term != null) {
                term = dialog_pd[i].name + "=" + term;

                if (result == null)
                    result = term;
                else
                    result = result + ";" + term;
            }
        }
        return result;
    }

    //	public void popupMenuCanceled(PopupMenuEvent e) { popupitem = null; }
    //	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
    //	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}


    protected static GridBagConstraints make_constraints(int location, int gridx, int gridy, Insets ins) {
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = location;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.insets = ins;
        gbc.weightx = gbc.weighty = 0.0;
        return gbc;
    }

    public void setAnimator(AnimatorEdit anim) {
        this.animator = anim;
    }

    public void setTime(double time) {
        if (gui_state == STATE_MOVING_TRACKER)
            return;
        super.setTime(time);
    }

    public void activeEventMoved() {
        if ((active_eventitem == null) || (animator == null))
            return;
        // find the screen coordinates of the event that moved
        int x = (active_eventitem.xlow + active_eventitem.xhigh) / 2;
        int y = (active_eventitem.ylow + active_eventitem.yhigh) / 2;

        layoutPattern();	// rebuild pattern event list
        createView();		// rebuild ladder diagram

        // reactivate the moved event with AnimatorEdit
        active_eventitem = getSelectedLadderEvent(x, y);
        animator.activateEvent(active_eventitem.event);
    }

    protected void layoutPattern() {
        try {
            pat.layoutPattern();
            if (animator != null) {
                animator.syncToPattern();
                animator.repaint();
            }
        } catch (JuggleExceptionUser jeu) {
            ErrorDialog.handleException(new JuggleExceptionInternal(jeu.getMessage()));
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleException(jei);
        }
    }

    protected void paintComponent(Graphics gr) {
        // try to turn on antialiased rendering
        VersionSpecific.getVersionSpecific().setAntialias(gr);

		if (pat.getNumberOfJugglers() > 1) {
            int x, y, width;
            Dimension cdim = this.getSize();
            int cWidth = cdim.width;
            int cHeight = cdim.height;
            FontMetrics	fm = gr.getFontMetrics();

            width = fm.stringWidth("Not available");
            x = (cWidth > width) ? (cWidth-width)/2 : 0;
            y = (cHeight + fm.getHeight()) / 2;
            gr.setColor(Color.white);
            gr.fillRect(0, 0, cWidth, cHeight);
            gr.setColor(Color.black);
            gr.drawString("Not available", x, y);
            return;
        }
        
        paintBackground(gr);

        // Could probably get this permutation from the pattern instead of the animator.
        int[] animpropnum = animator.getAnimPropNum();
        
        // draw events
        gr.setColor(Color.black);
        for (int i = 0; i < laddereventitems.size(); i++) {
            LadderEventItem item = (LadderEventItem)laddereventitems.elementAt(i);

            int yoffset = ((gui_state == STATE_MOVING_EVENT) &&
                           (active_eventitem.eventitem == item.eventitem)) ? delta_y : 0;
            if (item.type == item.TYPE_EVENT)
                gr.fillOval(item.xlow, item.ylow + yoffset,
                            (item.xhigh-item.xlow), (item.yhigh-item.ylow));
            else {
                // This condition could probably be applied to all event drawing.
                if (item.ylow + yoffset >= border_top || item.yhigh + yoffset <= height + border_top) {
                    // Color ball representation with the prop's color.
                    JMLTransition tr = item.event.getTransition(item.transnum);
                    int pathnum = tr.getPath();                    
                    int propnum = animpropnum[pathnum - 1];
                    
                    gr.setColor(pat.getProp(propnum).getEditorColor());
                    gr.fillOval(item.xlow, item.ylow + yoffset,
                                (item.xhigh-item.xlow), (item.yhigh-item.ylow));
                    
                    gr.setColor(Color.black);
                    gr.drawOval(item.xlow, item.ylow + yoffset,
                                (item.xhigh-item.xlow), (item.yhigh-item.ylow));
                }
            }
        }
        // draw the box around the selected event
        if (active_eventitem != null) {
            int yoffset = (gui_state == STATE_MOVING_EVENT) ? delta_y : 0;
            gr.setColor(Color.green);
            gr.drawLine(active_eventitem.xlow-1, active_eventitem.ylow+yoffset-1, active_eventitem.xhigh+1, active_eventitem.ylow+yoffset-1);
            gr.drawLine(active_eventitem.xhigh+1, active_eventitem.ylow+yoffset-1, active_eventitem.xhigh+1, active_eventitem.yhigh+yoffset+1);
            gr.drawLine(active_eventitem.xhigh+1, active_eventitem.yhigh+yoffset+1, active_eventitem.xlow, active_eventitem.yhigh+yoffset+1);
            gr.drawLine(active_eventitem.xlow-1, active_eventitem.yhigh+yoffset+1, active_eventitem.xlow-1, active_eventitem.ylow+yoffset-1);
        }

        // draw the tracker line showing the time
        gr.setColor(Color.red);
        gr.drawLine(0, tracker_y, width, tracker_y);
    }
	
/*
	private int orbits[][] = null;
	private Color orbitColor[] = null;

	public void restartView(JMLPattern pat, AnimatorPrefs jc) throws JuggleExceptionUser, JuggleExceptionInternal {
		if (pat != null)
			this.pat = pat;
		if (jc != null)
			this.jc = jc;

		if (pat != null) {
			OrbitLadderDiagram new_ladder = new OrbitLadderDiagram(pat, parent);
			new_ladder.setAnimator(jae);
			jae.setLadderDiagram(new_ladder);
			jae.deactivateEvent();
			new_ladder.setPreferredSize(new Dimension(ladder_width, 1));
			new_ladder.setMinimumSize(new Dimension(ladder_min_width, 1));
			this.ladder.removeAll();
			this.ladder.add(new_ladder, BorderLayout.CENTER);
			this.ladder.validate();

			findOrbits();
			// Color the paths of the ladder diagram
			for (int i = 0; i < orbits.length; i++) {
				for (int j = 0; j < orbits[i].length; j++) {
					new_ladder.setPathColor(orbits[i][j]+1, orbitColor[i]);
				}
			}
		}

		jae.restartJuggle(pat, jc);
	}

	private void findOrbits() {
		if (this.pat == null)
			return;

		int pathorb[] = new int[this.pat.getNumberOfPaths()];
		int orbids[] = new int[this.pat.getNumberOfPaths()];
		int orbitCount = 0;

		for (int i = 0; i < pathorb.length ; i++) {
			pathorb[i] = -1;
		}

		// Go through all the symetries and work out which path belongs
		// to which orbit.
		for (int i = 0; i < this.pat.getNumberOfSymmetries(); i++) {
			Permutation perm = this.pat.getSymmetry(i).getPathPerm();
			for (int j = 0; j < perm.getSize(); j++) {
				int elem = perm.getMapping(j+1)-1; // Not zero based !
				if (pathorb[elem] == -1) {
					if (pathorb[j] == -1) {
						pathorb[j] = orbitCount;
						orbids[orbitCount] = orbitCount;
						orbitCount++;
					}
					pathorb[elem] = pathorb[j];
				} else {
					if (pathorb[j] == -1) {
						pathorb[j] = pathorb[elem];
					} else if (pathorb[j] != pathorb[elem]) {
						// These were separate orbits, now should be merged
						orbids[pathorb[elem]] = orbids[pathorb[j]];
					}
				}
			}
		}

		// Clean up to have a sequential list
		int oc = 0;
		for (int i = 0; i < orbitCount; i++) {
			if (orbids[i]>=oc) {
				int v = orbids[i];
				for (int j = i; j < orbitCount; j++)
					if (orbids[j] == v) orbids[j] = oc;
				oc++;
			}
		}

		// Now store which orbit is made out of which paths
		this.orbits = new int[oc][];
		for (int i = 0; i < oc; i++) {
			int ocp = 0;
			for (int j = 0; j < 2; j++) {
				for (int k = 0; k < pathorb.length; k++) {
					if (orbids[pathorb[k]] == i) {
						if (j == 1) this.orbits[i][ocp] = k;
						ocp++;
					}
				}
				if (j == 0) this.orbits[i] = new int[ocp];
				ocp = 0;
			}
		}

		// Now create the colors. We will never need more colors
		// than orbits. We want to exclude gray colors, as that
		// will be used for 'deselected'.
		this.orbitColor = new Color[oc];
		int cols[] = {255,0,0};
		int phase = 0;
		for (int i = 0; i < oc; i++) {
			this.orbitColor[i] = new Color(cols[0], cols[1], cols[2]);

			if ((phase == 0 && cols[2] < 255) ||
			    (phase == 1 && cols[2] == 255)) {
				int d = cols[0];
				cols[0] = cols[2];
				cols[2] = cols[1];
				cols[1] = d;
			} else if (phase == 0) {
				cols[1] = 255;
				phase++;
			} else if (phase == 1) {
				if (cols[2]>0)
					cols[1] = cols[2]/2;
				else
					cols[1] = cols[1]/2;
				cols[2] = cols[1];
				phase = 0;
			}
		}
*/

		/*
		System.out.println("there are " + oc + " orbits");
		String blabel = "";
		for (int j = 0; j < this.orbits.length; j++) {
		    blabel += "(";
		    for (int k = 0; k < this.orbits[j].length; k++) {
			blabel += (this.orbits[j][k]+1);
			if (k < this.orbits[j].length-1)
			    blabel += ",";
		    }
		    blabel += ")";
		}
		System.out.println("orbit decomposition : " + blabel);
		*/
/*
	}
*/

}
