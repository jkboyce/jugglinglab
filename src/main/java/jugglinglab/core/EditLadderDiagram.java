//
// EditLadderDiagram.java
//
// This class draws the vertical ladder diagram on the right side of Edit view.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core;

import java.awt.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import jugglinglab.jml.*;
import jugglinglab.path.*;
import jugglinglab.prop.*;
import jugglinglab.util.*;
import jugglinglab.view.View;

public class EditLadderDiagram extends LadderDiagram implements ActionListener {
  // minimum time (seconds) between a throw and another event with transitions
  protected static final double MIN_THROW_SEP_TIME = 0.05;

  // minimum time (seconds) between all events for a hand
  protected static final double MIN_EVENT_SEP_TIME = 0.01;

  // minimum time (seconds) between positions for a juggler
  protected static final double MIN_POSITION_SEP_TIME = 0.02;

  protected static final Color COLOR_SELECTION = Color.green;

  // additional GUI states
  protected static final int STATE_MOVING_EVENT = 2;
  protected static final int STATE_MOVING_POSITION = 3;
  protected static final int STATE_POPUP = 4;

  protected JFrame parentframe;
  protected View parentview;
  protected AnimationEditPanel aep;

  protected LadderEventItem active_eventitem;
  protected LadderPositionItem active_positionitem;
  protected boolean item_was_selected;  // for detecting de-selecting clicks
  protected int start_y;
  protected int start_ylow, start_yhigh;  // initial box y-coordinates
  protected double start_t;  // initial time
  protected int delta_y;
  protected int delta_y_min, delta_y_max;  // limits for dragging up/down

  protected LadderItem popupitem;
  protected int popup_x;  // screen coordinates where popup was raised
  protected int popup_y;

  protected ArrayList<JComponent> dialog_controls;
  protected ParameterDescriptor[] dialog_pd;

  public EditLadderDiagram(JMLPattern pat, JFrame pframe, View pview)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    super(pat);
    parentframe = pframe;
    parentview = pview;
  }

  //----------------------------------------------------------------------------
  // Methods to respond to changes made in this object's UI
  //----------------------------------------------------------------------------

  // Called whenever the active event in the ladder diagram is changed in
  // some way, within this ladder diagram's UI.

  public void activeEventChanged() {
    if (active_eventitem == null) {
      return;
    }

    int hash = active_eventitem.getHashCode();

    layoutPattern(false);  // rebuild pattern event list
    createView();  // rebuild ladder diagram (LadderItem arrays)

    // locate the event we're editing, in the updated pattern
    active_eventitem = null;
    for (LadderEventItem item : laddereventitems) {
      if (item.getHashCode() == hash) {
        active_eventitem = item;
        break;
      }
    }

    try {
      if (active_eventitem == null) {
        throw new JuggleExceptionInternal("activeEventChanged(): event not found", pat);
      } else if (aep != null) {
        aep.activateEvent(active_eventitem.event);
      }
    } catch (JuggleExceptionInternal jei) {
      ErrorDialog.handleFatalException(jei);
    }
  }

  // Called whenever the active position in the ladder diagram is changed in
  // some way, within this ladder diagram's UI.

  public void activePositionChanged() {
    if (active_positionitem == null) {
      return;
    }

    int hash = active_positionitem.getHashCode();

    layoutPattern(false);
    createView();

    active_positionitem = null;
    for (LadderPositionItem item : ladderpositionitems) {
      // System.out.println(item.event.toString());
      if (item.getHashCode() == hash) {
        active_positionitem = item;
        break;
      }
    }

    if (active_positionitem == null) {
      ErrorDialog.handleFatalException(new JuggleExceptionInternal("ELD: position not found", pat));
    } else if (aep != null) {
      aep.activatePosition(active_positionitem.position);
    }
  }

  protected void layoutPattern(boolean undo) {
    try {
      // use synchronized here to avoid data consistency problems with
      // animation thread in AnimationPanel's run() method
      synchronized (pat) {
        pat.setNeedsLayout();
        pat.layoutPattern();

        if (aep != null) {
          aep.getAnimator().initAnimator();
          aep.repaint();
        }
      }

      if (undo) {
        addToUndoList();
      }
    } catch (JuggleException je) {
      // The various editing functions below (e.g., from the popup menu)
      // should never put the pattern into an invalid state -- it is their
      // responsibility to validate input and handle errors. So we
      // shouldn't ever get here.
      ErrorDialog.handleFatalException(je);
      if (parentframe != null) {
        parentframe.dispose();
        parentframe = null;
      }
    }
  }

  public void addToUndoList() {
    parentview.addToUndoList(pat);
  }

  //----------------------------------------------------------------------------
  // Methods to respond to changes made elsewhere
  //----------------------------------------------------------------------------

  public void activateEvent(JMLEvent ev) throws JuggleExceptionInternal {
    createView();  // rebuild ladder diagram (LadderItem arrays)

    active_eventitem = null;
    JMLEvent ev_inloop = pat.getEventImageInLoop(ev);
    if (ev_inloop == null) {
      throw new JuggleExceptionInternal("activateEvent(): null event", pat);
    }

    for (LadderEventItem item : laddereventitems) {
      if (item.event == ev_inloop) {
        active_eventitem = item;
        break;
      }
    }

    if (active_eventitem == null) {
      throw new JuggleExceptionInternal("activateEvent(): event not found", pat);
    }
  }

  public JMLEvent reactivateEvent() throws JuggleExceptionInternal {
    if (active_eventitem == null) {
      throw new JuggleExceptionInternal("reactivateEvent(): null eventitem", pat);
    }

    int hash = active_eventitem.getHashCode();

    createView();  // rebuild ladder diagram (LadderItem arrays)

    // re-locate the event we're editing in the newly laid out pattern
    active_eventitem = null;
    for (LadderEventItem item : laddereventitems) {
      if (item.getHashCode() == hash) {
        active_eventitem = item;
        break;
      }
    }

    if (active_eventitem == null) {
      throw new JuggleExceptionInternal("reactivateEvent(): event not found", pat);
    }

    return active_eventitem.event;
  }

  /*
  public void activatePosition(JMLPosition pos) throws JuggleExceptionInternal {
    createView();

    active_positionitem = null;
    for (LadderPositionItem item : ladderpositionitems) {
      if (item.position == pos) {
        active_positionitem = item;
        break;
      }
    }

    if (active_positionitem == null) {
      throw new JuggleExceptionInternal("activatePosition(): position not found", pat);
    }
  }
  */

  //----------------------------------------------------------------------------
  // java.awt.event.MouseListener methods
  //----------------------------------------------------------------------------

  @Override
  public void mousePressed(final MouseEvent me) {
    if (aep != null && (aep.writingGIF || !aep.engineAnimating)) {
      return;
    }

    int my = me.getY();
    my = Math.min(Math.max(my, BORDER_TOP), height - BORDER_TOP);

    // on macOS the popup triggers here
    if (me.isPopupTrigger()) {
      gui_state = STATE_POPUP;

      active_eventitem = getSelectedLadderEvent(me.getX(), me.getY());
      active_positionitem = getSelectedLadderPosition(me.getX(), me.getY());
      popupitem = active_eventitem != null ? active_eventitem : active_positionitem;
      if (popupitem == null) {
        popupitem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP);
      }

      popup_x = me.getX();
      popup_y = me.getY();
      if (aep != null) {
        double scale =
            (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
        double newtime = (double) (my - BORDER_TOP) * scale;
        anim_paused = aep.isPaused();
        aep.setPaused(true);
        aep.setTime(newtime);
        aep.deactivateEvent();
        aep.deactivatePosition();
        if (active_eventitem != null) {
          try {
            aep.activateEvent(active_eventitem.event);
          } catch (JuggleExceptionInternal jei) {
            jei.attachPattern(pat);
            ErrorDialog.handleFatalException(jei);
          }
        }
        if (active_positionitem != null) {
          aep.activatePosition(active_positionitem.position);
        }
        aep.repaint();
      }

      makePopupMenu(popupitem).show(EditLadderDiagram.this, me.getX(), me.getY());
    } else {
      switch (gui_state) {
        case STATE_INACTIVE:
          item_was_selected = false;

          LadderEventItem old_eventitem = active_eventitem;
          active_eventitem = getSelectedLadderEvent(me.getX(), me.getY());
          if (old_eventitem != null && old_eventitem == active_eventitem) {
            item_was_selected = true;
          }

          if (active_eventitem != null) {
            if (aep != null) {
              try {
                aep.activateEvent(active_eventitem.event);
              } catch (JuggleExceptionInternal jei) {
                jei.attachPattern(pat);
                ErrorDialog.handleFatalException(jei);
              }
            }
            if (active_eventitem.type == LadderEventItem.TYPE_TRANSITION) {
              // only allow dragging of TYPE_EVENT
              break;
            }

            gui_state = STATE_MOVING_EVENT;
            active_positionitem = null;
            start_y = me.getY();
            start_ylow = active_eventitem.ylow;
            start_yhigh = active_eventitem.yhigh;
            start_t = active_eventitem.event.t;
            findEventLimits(active_eventitem);
            break;
          }

          LadderPositionItem old_positionitem = active_positionitem;
          active_positionitem = getSelectedLadderPosition(me.getX(), me.getY());
          if (old_positionitem != null && old_positionitem == active_positionitem) {
            item_was_selected = true;
          }

          if (active_positionitem != null) {
            gui_state = STATE_MOVING_POSITION;
            active_eventitem = null;
            start_y = me.getY();
            start_ylow = active_positionitem.ylow;
            start_yhigh = active_positionitem.yhigh;
            start_t = active_positionitem.position.t;
            findPositionLimits(active_positionitem);
            if (aep != null) {
              aep.activatePosition(active_positionitem.position);
            }
            break;
          }

          gui_state = STATE_MOVING_TRACKER;
          tracker_y = my;
          if (aep != null) {
            double scale =
                (pat.getLoopEndTime() - pat.getLoopStartTime())
                    / (double) (height - 2 * BORDER_TOP);
            double newtime = (double) (my - BORDER_TOP) * scale;
            anim_paused = aep.isPaused();
            aep.setPaused(true);
            aep.setTime(newtime);
            aep.deactivateEvent();
            aep.deactivatePosition();
          }
          break;
        case STATE_MOVING_EVENT:
          // ErrorDialog.handleFatalException(new JuggleExceptionInternal(
          //          "mouse pressed in MOVING_EVENT state", pat));
          break;
        case STATE_MOVING_POSITION:
          // ErrorDialog.handleFatalException(new JuggleExceptionInternal(
          //          "mouse pressed in MOVING_POSITION state", pat));
          break;
        case STATE_MOVING_TRACKER:
          // ErrorDialog.handleFatalException(new JuggleExceptionInternal(
          //          "mouse pressed in MOVING_TRACKER state", pat));
          break;
        case STATE_POPUP:
          // shouldn't ever get here
          finishPopup();
          break;
      }

      repaint();
      if (aep != null) {
        aep.repaint();
      }
    }
  }

  @Override
  public void mouseReleased(final MouseEvent me) {
    if (aep != null && (aep.writingGIF || !aep.engineAnimating)) {
      return;
    }

    // on Windows the popup triggers here
    if (me.isPopupTrigger()) {
      switch (gui_state) {
        case STATE_INACTIVE:
        case STATE_MOVING_EVENT:
        case STATE_MOVING_POSITION:
        case STATE_MOVING_TRACKER:
          // skip this code for MOVING_TRACKER state, since already executed in
          // mousePressed() above
          if (gui_state != STATE_MOVING_TRACKER && aep != null) {
            int my = me.getY();
            my = Math.min(Math.max(my, BORDER_TOP), height - BORDER_TOP);

            double scale = (pat.getLoopEndTime() - pat.getLoopStartTime())
                    / (double) (height - 2 * BORDER_TOP);
            double newtime = (double) (my - BORDER_TOP) * scale;
            anim_paused = aep.isPaused();
            aep.setPaused(true);
            aep.setTime(newtime);
            aep.deactivateEvent();
            aep.deactivatePosition();
            if (active_eventitem != null) {
              try {
                aep.activateEvent(active_eventitem.event);
              } catch (JuggleExceptionInternal jei) {
                jei.attachPattern(pat);
                ErrorDialog.handleFatalException(jei);
              }
            }
            if (active_positionitem != null) {
              aep.activatePosition(active_positionitem.position);
            }
            aep.repaint();
          }

          gui_state = STATE_POPUP;

          if (delta_y != 0) {
            delta_y = 0;
            repaint();
          }
          popup_x = me.getX();
          popup_y = me.getY();
          popupitem = (active_eventitem != null ? active_eventitem : active_positionitem);
          if (popupitem == null) {
            popupitem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP);
          }

          makePopupMenu(popupitem).show(EditLadderDiagram.this, me.getX(), me.getY());
          break;
        case STATE_POPUP:
          ErrorDialog.handleFatalException(
              new JuggleExceptionInternal("tried to enter POPUP state while already in it", pat));
          break;
      }
    } else {
      switch (gui_state) {
        case STATE_INACTIVE:
          // should only get here if user cancelled popup menu or deselected event
          break;
        case STATE_MOVING_EVENT:
          gui_state = STATE_INACTIVE;
          if (delta_y != 0) {
            delta_y = 0;
            addToUndoList();
          } else if (item_was_selected) {
            // clicked without moving --> deselect
            active_eventitem = null;
            if (aep != null) {
              aep.deactivateEvent();
              aep.repaint();
            }
            repaint();
          }
          break;
        case STATE_MOVING_POSITION:
          gui_state = STATE_INACTIVE;
          if (delta_y != 0) {
            delta_y = 0;
            addToUndoList();
          } else if (item_was_selected) {
            active_positionitem = null;
            if (aep != null) {
              aep.deactivatePosition();
              aep.repaint();
            }
            repaint();
          }
          break;
        case STATE_MOVING_TRACKER:
          gui_state = STATE_INACTIVE;
          if (aep != null) {
            aep.setPaused(anim_paused);
          }
          repaint();
          break;
        case STATE_POPUP:
          break;
      }
    }
  }

  //----------------------------------------------------------------------------
  // java.awt.event.MouseMotionListener methods
  //----------------------------------------------------------------------------

  @Override
  public void mouseDragged(MouseEvent me) {
    if (aep != null && (aep.writingGIF || !aep.engineAnimating)) {
      return;
    }

    int my = me.getY();
    my = Math.min(Math.max(my, BORDER_TOP), height - BORDER_TOP);

    switch (gui_state) {
      case STATE_INACTIVE, STATE_POPUP:
        break;
      case STATE_MOVING_EVENT:
        {
          int old_delta_y = delta_y;
          delta_y = getClippedEventTime(me, active_eventitem.event);

          if (delta_y != old_delta_y) {
            moveEventInPattern(active_eventitem.eventitem);
            activeEventChanged();
            repaint();
          }
          break;
        }
      case STATE_MOVING_POSITION:
        {
          int old_delta_y = delta_y;
          delta_y = getClippedPositionTime(me, active_positionitem.position);

          if (delta_y != old_delta_y) {
            movePositionInPattern(active_positionitem);
            activePositionChanged();
            repaint();
          }
          break;
        }
      case STATE_MOVING_TRACKER:
        tracker_y = my;
        EditLadderDiagram.this.repaint();
        if (aep != null) {
          double scale =
              (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
          double newtime = (double) (my - BORDER_TOP) * scale;
          aep.setTime(newtime);
          aep.repaint();
        }
        break;
    }
  }

  //----------------------------------------------------------------------------
  // Utility methods for mouse interactions
  //----------------------------------------------------------------------------

  protected void findEventLimits(LadderEventItem item) {
    double tmin = pat.getLoopStartTime();
    double tmax = pat.getLoopEndTime();
    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);

    for (JMLTransition tr : item.event.getTransitions()) {
      switch (tr.getType()) {
        case JMLTransition.TRANS_THROW:
          {
            // find out when the ball being thrown was last caught
            JMLEvent ev = item.event.getPrevious();
            while (ev != null) {
              if (ev.getPathTransition(tr.path, JMLTransition.TRANS_CATCH) != null
                  || ev.getPathTransition(tr.path, JMLTransition.TRANS_SOFTCATCH) != null
                  || ev.getPathTransition(tr.path, JMLTransition.TRANS_GRABCATCH) != null) {
                break;
              }
              ev = ev.getPrevious();
            }
            if (ev == null) {
              ErrorDialog.handleFatalException(
                  new JuggleExceptionInternal("Null event 1 in mousePressed()", pat));
              if (parentframe != null) {
                parentframe.dispose();
                parentframe = null;
              }
              return;
            }
            tmin = Math.max(tmin, ev.t + MIN_THROW_SEP_TIME);

            // next catch is easy to find
            ev = tr.getOutgoingPathLink().getEndEvent();
            if (!ev.hasSameMasterAs(item.event)) {
              tmax = Math.min(tmax, ev.t - MIN_THROW_SEP_TIME);
            }
          }
          break;
        case JMLTransition.TRANS_CATCH:
        case JMLTransition.TRANS_SOFTCATCH:
        case JMLTransition.TRANS_GRABCATCH:
          {
            // previous throw is easy to find
            JMLEvent ev = tr.getIncomingPathLink().getStartEvent();
            if (!ev.hasSameMasterAs(item.event)) {
              tmin = Math.max(tmin, ev.t + MIN_THROW_SEP_TIME);
            }

            // find out when the ball being caught is next thrown
            ev = item.event.getNext();
            while (ev != null) {
              if (ev.getPathTransition(tr.path, JMLTransition.TRANS_THROW) != null) {
                break;
              }
              ev = ev.getNext();
            }
            if (ev == null) {
              ErrorDialog.handleFatalException(
                  new JuggleExceptionInternal("Null event 2 in mousePressed()", pat));
              if (parentframe != null) {
                parentframe.dispose();
                parentframe = null;
              }
              return;
            }
            tmax = Math.min(tmax, ev.t - MIN_THROW_SEP_TIME);
          }
          break;
      }
    }
    delta_y_min = (int) ((tmin - item.event.t) / scale);
    delta_y_max = (int) ((tmax - item.event.t) / scale);
  }

  // Return value of `delta_y` during mouse drag of an event, clipping it to
  // enforce proximity limits between various event types, as well as hard
  // limits `delta_y_min` and `delta_y_max`.

  protected int getClippedEventTime(MouseEvent me, JMLEvent event) {
    int dy = me.getY() - start_y;

    dy = Math.min(Math.max(dy, delta_y_min), delta_y_max);

    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
    double shift = dy * scale;
    double newt = start_t + shift; // unclipped new event time

    // Calculate a window (t_excl_min, t_excl_max) of excluded times based on
    // proximity to other events, where `newt` is contained within the window.

    double t_excl_min = newt;
    double t_excl_max = newt;
    boolean changed;

    do {
      changed = false;
      JMLEvent ev = pat.getEventList();
      double sep;

      while (ev != null) {
        if (ev != event && ev.getJuggler() == event.getJuggler()
            && ev.getHand() == event.getHand()) {
          if (ev.hasThrow() && event.hasThrowOrCatch()
              || ev.hasThrowOrCatch() && event.hasThrow()) {
            sep = MIN_THROW_SEP_TIME;
          } else {
            sep = MIN_EVENT_SEP_TIME;
          }

          double ev_excl_min = ev.t - sep;
          double ev_excl_max = ev.t + sep;

          if (ev_excl_max > t_excl_max && ev_excl_min <= t_excl_max) {
            t_excl_max = ev_excl_max;
            changed = true;
          }

          if (ev_excl_min < t_excl_min && ev_excl_max >= t_excl_min) {
            t_excl_min = ev_excl_min;
            changed = true;
          }
        }
        ev = ev.getNext();
      }
    } while (changed);

    // System.out.println("t_excl_min = " + t_excl_min + ", t_excl_max = " + t_excl_max);

    // Clip the event time `newt` to whichever end of the exclusion window
    // is closest. First check if each end is feasible.
    int excl_dy_min = (int) Math.floor((t_excl_min - start_t) / scale);
    int excl_dy_max = (int) Math.ceil((t_excl_max - start_t) / scale);
    boolean feasible_min = (excl_dy_min >= delta_y_min && excl_dy_min <= delta_y_max);
    boolean feasible_max = (excl_dy_max >= delta_y_min && excl_dy_max <= delta_y_max);

    int result_dy = dy;

    if (feasible_min && feasible_max) {
      double t_midpoint = 0.5 * (t_excl_min + t_excl_max);
      result_dy = (newt <= t_midpoint ? excl_dy_min : excl_dy_max);
    } else if (feasible_min && !feasible_max) {
      result_dy = excl_dy_min;
    } else if (!feasible_min && feasible_max) {
      result_dy = excl_dy_max;
    }

    return result_dy;
  }

  protected void moveEventInPattern(LadderEventItem item) {
    JMLEvent ev = item.event;

    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
    double shift = delta_y * scale;
    double newt = start_t + shift;
    if (newt < pat.getLoopStartTime() + scale) {
      // within 1 pixel of top
      shift = pat.getLoopStartTime() - start_t;
      newt = pat.getLoopStartTime();
    } else if (newt >= pat.getLoopEndTime()) {
      shift = pat.getLoopEndTime() - 0.0001 - start_t;
      newt = pat.getLoopEndTime() - 0.0001;
    }

    boolean[] throwpath = new boolean[pat.getNumberOfPaths()];
    boolean[] catchpath = new boolean[pat.getNumberOfPaths()];
    boolean[] holdpathorig = new boolean[pat.getNumberOfPaths()];
    boolean[] holdpathnew = new boolean[pat.getNumberOfPaths()];
    for (JMLTransition tr : ev.getTransitions()) {
      switch (tr.getType()) {
        case JMLTransition.TRANS_THROW:
          throwpath[tr.path - 1] = true;
          break;
        case JMLTransition.TRANS_CATCH:
        case JMLTransition.TRANS_SOFTCATCH:
        case JMLTransition.TRANS_GRABCATCH:
          catchpath[tr.path - 1] = true;
          break;
        case JMLTransition.TRANS_HOLDING:
          holdpathnew[tr.path - 1] = holdpathorig[tr.path - 1] = true;
          break;
      }
    }

    if (newt < ev.t) {  // moving to earlier time
      ev = ev.getPrevious();
      while (ev != null && ev.t > newt) {
        if (!ev.hasSameMasterAs(item.event)
            && ev.getJuggler() == item.event.getJuggler()
            && ev.getHand() == item.event.getHand()) {
          for (int j = 0; j < ev.getNumberOfTransitions(); j++) {
            JMLTransition tr = ev.getTransition(j);
            switch (tr.getType()) {
              case JMLTransition.TRANS_THROW:
                holdpathnew[tr.path - 1] = true;
                break;
              case JMLTransition.TRANS_CATCH:
              case JMLTransition.TRANS_SOFTCATCH:
              case JMLTransition.TRANS_GRABCATCH:
                holdpathnew[tr.path - 1] = false;
                break;
              case JMLTransition.TRANS_HOLDING:
                if (throwpath[tr.path - 1]) {
                  ev.removeTransition(j);
                  if (!ev.isMaster()) {
                    ev.getMaster().removeTransition(j);
                  }
                  j--;  // next trans moved into slot
                }
                break;
            }
          }

          for (int j = 0; j < pat.getNumberOfPaths(); j++) {
            if (catchpath[j]) {
              JMLTransition tr =
                  new JMLTransition(JMLTransition.TRANS_HOLDING, (j + 1), null, null);
              ev.addTransition(tr);
              if (!ev.isMaster()) {
                Permutation pp = ev.getPathPermFromMaster().getInverse();
                tr = new JMLTransition(
                        JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null);
                ev.getMaster().addTransition(tr);
              }
            }
          }
        }
        ev = ev.getPrevious();
      }
    } else if (newt > ev.t) {  // moving to later time
      ev = ev.getNext();
      while (ev != null && ev.t < newt) {
        if (!ev.hasSameMasterAs(item.event)
            && ev.getJuggler() == item.event.getJuggler()
            && ev.getHand() == item.event.getHand()) {
          for (int j = 0; j < ev.getNumberOfTransitions(); j++) {
            JMLTransition tr = ev.getTransition(j);
            switch (tr.getType()) {
              case JMLTransition.TRANS_THROW:
                holdpathnew[tr.path - 1] = false;
                break;
              case JMLTransition.TRANS_CATCH:
              case JMLTransition.TRANS_SOFTCATCH:
              case JMLTransition.TRANS_GRABCATCH:
                holdpathnew[tr.path - 1] = true;
                break;
              case JMLTransition.TRANS_HOLDING:
                if (catchpath[tr.path - 1]) {
                  ev.removeTransition(j);
                  if (!ev.isMaster()) {
                    ev.getMaster().removeTransition(j);
                  }
                  j--;
                }
                break;
            }
          }

          for (int j = 0; j < pat.getNumberOfPaths(); j++) {
            if (throwpath[j]) {
              JMLTransition tr =
                  new JMLTransition(JMLTransition.TRANS_HOLDING, (j + 1), null, null);
              ev.addTransition(tr);
              if (!ev.isMaster()) {
                Permutation pp = ev.getPathPermFromMaster().getInverse();
                tr = new JMLTransition(
                        JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null);
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
    double new_master_t = start_t + shift;

    if (!ev.isMaster()) {
      double new_event_t = new_master_t;
      new_master_t += ev.getMaster().t - ev.t;
      ev.t = new_event_t; // update event time so getHashCode() works
      ev = ev.getMaster();
    }

    for (int j = 0; j < pat.getNumberOfPaths(); j++) {
      if (holdpathnew[j] != holdpathorig[j]) {
        if (holdpathnew[j]) {
          JMLTransition tr =
              new JMLTransition(JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null);
          ev.addTransition(tr);
        } else {
          JMLTransition tr =
              ev.getPathTransition(pp.getMapping(j + 1), JMLTransition.TRANS_HOLDING);
          if (tr == null) {
            ErrorDialog.handleFatalException(
                new JuggleExceptionInternal("Null transition in removing hold", pat));
            if (parentframe != null) {
              parentframe.dispose();
              parentframe = null;
            }
            return;
          }
          ev.removeTransition(tr);
        }
      }
    }

    pat.removeEvent(ev);
    ev.t = new_master_t; // change time of master
    pat.addEvent(ev); // remove/add cycle keeps events sorted
  }

  protected void findPositionLimits(LadderPositionItem item) {
    double tmin = pat.getLoopStartTime();
    double tmax = pat.getLoopEndTime();
    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);

    delta_y_min = (int) ((tmin - item.position.t) / scale);
    delta_y_max = (int) ((tmax - item.position.t) / scale);
  }

  // Return value of `delta_y` during mouse drag of an event, clipping it to
  // enforce proximity limits between various event types, as well as hard
  // limits `delta_y_min` and `delta_y_max`.

  protected int getClippedPositionTime(MouseEvent me, JMLPosition position) {
    int dy = me.getY() - start_y;

    dy = Math.min(Math.max(dy, delta_y_min), delta_y_max);

    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
    double shift = dy * scale;
    double newt = start_t + shift; // unclipped new event time

    // Calculate a window (t_excl_min, t_excl_max) of excluded times based on
    // proximity to other events, where `newt` is contained within the window.

    double t_excl_min = newt;
    double t_excl_max = newt;
    boolean changed;

    do {
      changed = false;
      JMLPosition pos = pat.getPositionList();

      while (pos != null) {
        if (pos != position && pos.getJuggler() == position.getJuggler()) {
          double pos_excl_min = pos.t - MIN_POSITION_SEP_TIME;
          double pos_excl_max = pos.t + MIN_POSITION_SEP_TIME;

          if (pos_excl_max > t_excl_max && pos_excl_min <= t_excl_max) {
            t_excl_max = pos_excl_max;
            changed = true;
          }

          if (pos_excl_min < t_excl_min && pos_excl_max >= t_excl_min) {
            t_excl_min = pos_excl_min;
            changed = true;
          }
        }
        pos = pos.getNext();
      }
    } while (changed);

    // Clip the position time `newt` to whichever end of the exclusion window
    // is closest. First check if each end is feasible.
    int excl_dy_min = (int) Math.floor((t_excl_min - start_t) / scale);
    int excl_dy_max = (int) Math.ceil((t_excl_max - start_t) / scale);
    boolean feasible_min = (excl_dy_min >= delta_y_min && excl_dy_min <= delta_y_max);
    boolean feasible_max = (excl_dy_max >= delta_y_min && excl_dy_max <= delta_y_max);

    int result_dy = dy;

    if (feasible_min && feasible_max) {
      double t_midpoint = 0.5 * (t_excl_min + t_excl_max);
      result_dy = (newt <= t_midpoint ? excl_dy_min : excl_dy_max);
    } else if (feasible_min && !feasible_max) {
      result_dy = excl_dy_min;
    } else if (!feasible_min && feasible_max) {
      result_dy = excl_dy_max;
    }

    return result_dy;
  }

  protected void movePositionInPattern(LadderPositionItem item) {
    JMLPosition pos = item.position;

    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);

    double newt = start_t + delta_y * scale;
    if (newt < pat.getLoopStartTime() + scale) {
      newt = pat.getLoopStartTime();  // within 1 pixel of top
    } else if (newt >= pat.getLoopEndTime()) {
      newt = pat.getLoopEndTime() - 0.0001;
    }

    pat.removePosition(pos);
    pos.t = newt;
    pat.addPosition(pos); // remove/add keeps positions sorted
  }

  //----------------------------------------------------------------------------
  // Popup menu and related handlers
  //----------------------------------------------------------------------------

  protected static final String[] popupItems = {
    "Change title...",
    "Change overall timing...",
    null,
    "Add event to L hand",
    "Add event to R hand",
    "Remove event",
    "Add position to juggler",
    "Remove position",
    null,
    "Define prop...",
    "Define throw...",
    "Change to normal catch",
    "Change to soft catch",
    "Change to grab catch",
    "Make last in event",
  };

  protected static final String[] popupCommands = {
    "changetitle",
    "changetiming",
    null,
    "addeventtoleft",
    "addeventtoright",
    "removeevent",
    "addposition",
    "removeposition",
    null,
    "defineprop",
    "definethrow",
    "changetocatch",
    "changetosoftcatch",
    "changetograbcatch",
    "makelast",
  };

  protected JPopupMenu makePopupMenu(LadderItem laditem) {
    JPopupMenu popup = new JPopupMenu();

    for (int i = 0; i < popupItems.length; i++) {
      String name = popupItems[i];

      if (name == null) {
        popup.addSeparator();
        continue;
      }

      JMenuItem item = new JMenuItem(guistrings.getString(name.replace(' ', '_')));
      String command = popupCommands[i];
      item.setActionCommand(command);
      item.addActionListener(this);
      item.setEnabled(isCommandEnabled(laditem, command));

      popup.add(item);
    }

    popup.setBorder(new BevelBorder(BevelBorder.RAISED));

    popup.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            finishPopup();
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
        });

    return popup;
  }

  // Determine which commands are enabled for a particular LadderItem
  //
  // Returns true for enabled, false for disabled

  protected static boolean isCommandEnabled(LadderItem laditem, String command) {
    if (laditem == null) {
      return !Arrays.asList(
              "removeevent",
              "removeposition",
              "defineprop",
              "definethrow",
              "changetocatch",
              "changetosoftcatch",
              "changetograbcatch",
              "makelast")
          .contains(command);
    } else if (laditem.type == LadderItem.TYPE_EVENT) {
      if (Arrays.asList(
              "changetitle",
              "changetiming",
              "addeventtoleft",
              "addeventtoright",
              "addposition",
              "removeposition",
              "defineprop",
              "definethrow",
              "changetocatch",
              "changetosoftcatch",
              "changetograbcatch",
              "makelast")
          .contains(command)) return false;

      if (command.equals("removeevent")) {
        // can't remove an event with throws or catches
        LadderEventItem evitem = (LadderEventItem) laditem;

        for (JMLTransition tr : evitem.event.getTransitions()) {
          if (tr.getType() != JMLTransition.TRANS_HOLDING) {
            return false;
          }
        }

        // check to make sure we're not allowing the user to delete
        // an event if it's the last one in that hand.
        // do this by finding the next event in the same hand; if it
        // has the same master, it's the only one
        int hand = evitem.event.getHand();
        int juggler = evitem.event.getJuggler();
        JMLEvent evm1 = evitem.event.isMaster() ? evitem.event : evitem.event.getMaster();
        JMLEvent ev = evitem.event.getNext();
        while (ev != null) {
          if ((ev.getHand() == hand) && (ev.getJuggler() == juggler)) {
            JMLEvent evm2 = ev.isMaster() ? ev : ev.getMaster();
            if (evm1 == evm2) {
              return false;
            }
            break;
          }
          ev = ev.getNext();
        }
      }
    } else if (laditem.type == LadderItem.TYPE_TRANSITION) {
      if (Arrays.asList(
              "changetitle",
              "changetiming",
              "addeventtoleft",
              "addeventtoright",
              "addposition",
              "removeposition",
              "removeevent")
          .contains(command)) return false;

      LadderEventItem evitem = (LadderEventItem) laditem;
      JMLTransition tr = evitem.event.getTransition(evitem.transnum);

      switch (command) {
        case "makelast" -> {
          return evitem.transnum != (evitem.event.getNumberOfTransitions() - 1);
        }
        case "definethrow" -> {
          return tr.getType() == JMLTransition.TRANS_THROW;
        }
        case "changetocatch" -> {
          return tr.getType() == JMLTransition.TRANS_SOFTCATCH
              || tr.getType() == JMLTransition.TRANS_GRABCATCH;
        }
        case "changetosoftcatch" -> {
          return tr.getType() == JMLTransition.TRANS_CATCH
              || tr.getType() == JMLTransition.TRANS_GRABCATCH;
        }
        case "changetograbcatch" -> {
          return tr.getType() == JMLTransition.TRANS_CATCH
              || tr.getType() == JMLTransition.TRANS_SOFTCATCH;
        }
      }
    } else if (laditem.type == LadderItem.TYPE_POSITION) {
      return !Arrays.asList(
              "changetitle",
              "changetiming",
              "addeventtoleft",
              "addeventtoright",
              "removeevent",
              "addposition",
              "defineprop",
              "definethrow",
              "changetocatch",
              "changetosoftcatch",
              "changetograbcatch",
              "makelast")
          .contains(command);
    } else {  // LadderPathItem
      return !Arrays.asList(
              "removeevent",
              "removeposition",
              "definethrow",
              "changetocatch",
              "changetosoftcatch",
              "changetograbcatch",
              "makelast")
          .contains(command);
    }
    return true;
  }

  //----------------------------------------------------------------------------
  // java.awt.event.ActionListener methods
  //----------------------------------------------------------------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    String command = event.getActionCommand();
    if (command == null) {
      return;
    }

    switch (command) {
      case "changetitle" -> changeTitle();
      case "changetiming" -> changeTiming();
      case "addeventtoleft" -> addEventToHand(HandLink.LEFT_HAND);
      case "addeventtoright" -> addEventToHand(HandLink.RIGHT_HAND);
      case "removeevent" -> removeEvent();
      case "addposition" -> addPositionToJuggler();
      case "removeposition" -> removePosition();
      case "defineprop" -> defineProp();
      case "definethrow" -> defineThrow();
      case "changetocatch" -> changeCatchStyleTo(JMLTransition.TRANS_CATCH);
      case "changetosoftcatch" -> changeCatchStyleTo(JMLTransition.TRANS_SOFTCATCH);
      case "changetograbcatch" -> changeCatchStyleTo(JMLTransition.TRANS_GRABCATCH);
      case "makelast" -> makeLastInEvent();
      default -> ErrorDialog.handleFatalException(
          new JuggleExceptionInternal("unknown item in ELD popup", pat));
    }

    finishPopup();
  }

  protected void changeTitle() {
    final JDialog jd = new JDialog(parentframe, guistrings.getString("Change_title"), true);
    GridBagLayout gb = new GridBagLayout();
    jd.getContentPane().setLayout(gb);

    final JTextField tf = new JTextField(20);
    tf.setText(pat.getTitle());

    JButton okbutton = new JButton(guistrings.getString("OK"));
    okbutton.addActionListener(
        e -> {
          String newtitle = tf.getText();
          pat.setTitle(newtitle);
          jd.dispose();

          addToUndoList();
        });

    jd.getContentPane().add(tf);
    gb.setConstraints(
        tf, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(10, 10, 0, 10)));
    jd.getContentPane().add(okbutton);
    gb.setConstraints(
        okbutton,
        JLFunc.constraints(GridBagConstraints.LINE_END, 0, 1, new Insets(10, 10, 10, 10)));
    jd.getRootPane().setDefaultButton(okbutton); // OK button is default
    jd.pack();
    jd.setResizable(false);
    jd.setLocationRelativeTo(this);
    jd.setVisible(true);
    parentframe.setTitle(pat.getTitle());
  }

  protected void changeTiming() {
    final JDialog jd = new JDialog(parentframe, guistrings.getString("Change_timing"), true);
    GridBagLayout gb = new GridBagLayout();
    jd.getContentPane().setLayout(gb);

    JPanel p1 = new JPanel();
    p1.setLayout(gb);
    JLabel lab = new JLabel(guistrings.getString("Rescale_percentage"));
    p1.add(lab);
    gb.setConstraints(
        lab, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 0)));
    final JTextField tf = new JTextField(7);
    tf.setText("100");
    p1.add(tf);
    gb.setConstraints(
        tf, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0, new Insets(0, 5, 0, 0)));

    JButton okbutton = new JButton(guistrings.getString("OK"));
    okbutton.addActionListener(
        e -> {
          double scale;
          try {
            scale = JLFunc.parseDouble(tf.getText()) / 100.0;
          } catch (NumberFormatException nfe) {
            ErrorDialog.handleUserException(EditLadderDiagram.this, "Number format error in rescale percentage");
            return;
          }
          if (scale > 0.0) {
            pat.scaleTime(scale);
            aep.setTime(0.0);
            layoutPattern(true);
            createView();
          }
          jd.dispose();
        });

    jd.getContentPane().add(p1);
    gb.setConstraints(
        p1, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(10, 10, 0, 10)));
    jd.getContentPane().add(okbutton);
    gb.setConstraints(
        okbutton,
        JLFunc.constraints(GridBagConstraints.LINE_END, 0, 1, new Insets(10, 10, 10, 10)));
    jd.getRootPane().setDefaultButton(okbutton); // OK button is default
    jd.pack();
    jd.setLocationRelativeTo(this);
    jd.setVisible(true);
  }

  protected JMLEvent addEventToHand(int hand) {
    int juggler = 1;
    if (pat.getNumberOfJugglers() > 1) {
      int mouse_x = popup_x;
      int juggler_right_px = (left_x + right_x + juggler_delta_x) / 2;

      while (juggler <= pat.getNumberOfJugglers()) {
        if (mouse_x < juggler_right_px) {
          break;
        }

        mouse_x -= juggler_delta_x;
        juggler++;
      }
      if (juggler > pat.getNumberOfJugglers()) {
        juggler = pat.getNumberOfJugglers();
      }
    }

    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
    double evtime = (double) (popup_y - BORDER_TOP) * scale;

    Coordinate evpos = new Coordinate();
    try {
      pat.getHandCoordinate(juggler, hand, evtime, evpos);
    } catch (JuggleExceptionInternal jei) {
      ErrorDialog.handleFatalException(jei);
      if (parentframe != null) {
        parentframe.dispose();
        parentframe = null;
      }
      return null;
    }

    JMLEvent ev = new JMLEvent();
    ev.setLocalCoordinate(pat.convertGlobalToLocal(evpos, juggler, evtime));
    ev.t = evtime;
    ev.setHand(juggler, hand);
    pat.addEvent(ev);

    // add holding transitions to the new event, if hand is filled
    for (int i = 0; i < pat.getNumberOfPaths(); i++) {
      boolean holding = false;

      JMLEvent evt = ev.getPrevious();
      while (evt != null) {
        JMLTransition tr = evt.getPathTransition(i + 1, JMLTransition.TRANS_ANY);
        if (tr != null) {
          if (evt.getJuggler() != ev.getJuggler() || evt.getHand() != ev.getHand()) {
            break;
          }
          if (tr.getType() == JMLTransition.TRANS_THROW) {
            break;
          }
          holding = true;
          break;
        }
        evt = evt.getPrevious();
      }

      if (holding) {
        JMLTransition tr = new JMLTransition(JMLTransition.TRANS_HOLDING, (i + 1), null, null);
        ev.addTransition(tr);
      }
    }

    active_eventitem = null;
    if (aep != null) {
      aep.deactivateEvent();
    }
    layoutPattern(true);
    createView();
    repaint();

    return ev;
  }

  protected void removeEvent() {
    // makePopupMenu() ensures that the event only has hold transitions
    if (!(popupitem instanceof LadderEventItem)) {
      ErrorDialog.handleFatalException(
          new JuggleExceptionInternal("LadderDiagram illegal remove event", pat));
      return;
    }
    JMLEvent ev = ((LadderEventItem) popupitem).event;
    if (!ev.isMaster()) {
      ev = ev.getMaster();
    }
    pat.removeEvent(ev);
    active_eventitem = null;
    if (aep != null) {
      aep.deactivateEvent();
    }
    layoutPattern(true);
    createView();
    repaint();
  }

  protected JMLPosition addPositionToJuggler() {
    int juggler = 1;
    if (pat.getNumberOfJugglers() > 1) {
      int mouse_x = popup_x;
      int juggler_right_px = (left_x + right_x + juggler_delta_x) / 2;

      while (juggler <= pat.getNumberOfJugglers()) {
        if (mouse_x < juggler_right_px) {
          break;
        }

        mouse_x -= juggler_delta_x;
        juggler++;
      }
      if (juggler > pat.getNumberOfJugglers()) {
        juggler = pat.getNumberOfJugglers();
      }
    }

    double scale =
        (pat.getLoopEndTime() - pat.getLoopStartTime()) / (double) (height - 2 * BORDER_TOP);
    double postime = (double) (popup_y - BORDER_TOP) * scale;

    JMLPosition pos = new JMLPosition();
    Coordinate loc = new Coordinate();
    pat.getJugglerPosition(juggler, postime, loc);
    pos.setCoordinate(loc);
    pos.angle = pat.getJugglerAngle(juggler, postime);
    pos.t = postime;
    pos.setJuggler(juggler);
    pat.addPosition(pos);

    active_eventitem = null;
    if (aep != null) {
      aep.deactivateEvent();
    }
    layoutPattern(true);
    createView();
    repaint();

    return pos;
  }

  protected void removePosition() {
    if (!(popupitem instanceof LadderPositionItem)) {
      ErrorDialog.handleFatalException(
          new JuggleExceptionInternal("LadderDiagram illegal remove position", pat));
      return;
    }
    JMLPosition pos = ((LadderPositionItem) popupitem).position;
    pat.removePosition(pos);
    active_positionitem = null;
    if (aep != null) {
      aep.deactivatePosition();
    }
    layoutPattern(true);
    createView();
    repaint();
  }

  protected void defineProp() {
    if (popupitem == null) {
      ErrorDialog.handleFatalException(new JuggleExceptionInternal("defineProp() null popupitem", pat));
      return;
    }

    // figure out which path number the user selected
    int pn;
    if (popupitem instanceof LadderEventItem) {
      if (popupitem.type != LadderItem.TYPE_TRANSITION) {
        ErrorDialog.handleFatalException(
            new JuggleExceptionInternal("defineProp() bad LadderItem type", pat));
        return;
      }

      JMLEvent ev = ((LadderEventItem) popupitem).event;
      int transnum = ((LadderEventItem) popupitem).transnum;
      JMLTransition tr = ev.getTransition(transnum);
      pn = tr.path;
    } else {
      pn = ((LadderPathItem) popupitem).pathnum;
    }

    final int pathnum = pn;
    final int[] animpropnum = aep.getAnimator().getAnimPropNum();
    final int propnum = animpropnum[pathnum - 1];
    // final int propnum = pat.getPropAssignment(pathnum);
    // System.out.println("pathnum = " + pathnum + ", propnum = " + propnum);
    final Prop startprop = pat.getProp(propnum);
    String[] prtypes = Prop.builtinProps;

    final JDialog jd = new JDialog(parentframe, guistrings.getString("Define_prop"), true);
    GridBagLayout gb = new GridBagLayout();
    jd.getContentPane().setLayout(gb);

    JPanel p1 = new JPanel();
    p1.setLayout(gb);
    JLabel lab = new JLabel(guistrings.getString("Prop_type"));
    p1.add(lab);
    gb.setConstraints(
        lab, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 0)));

    final JPanel p2 = new JPanel();
    p2.setLayout(gb);

    final JComboBox<String> cb1 = new JComboBox<>(prtypes);
    p1.add(cb1);
    gb.setConstraints(
        cb1, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0, new Insets(0, 10, 0, 0)));
    cb1.addActionListener(
        ae -> {
          String type = cb1.getItemAt(cb1.getSelectedIndex());
          try {
            Prop pt;
            if (type.equalsIgnoreCase(startprop.getType())) {
              pt = startprop;
            } else {
              pt = Prop.newProp(type);
            }
            makeParametersPanel(p2, pt.getParameterDescriptors());
          } catch (JuggleExceptionUser jeu) {
            ErrorDialog.handleUserException(jd, jeu.getMessage());
            return;
          }
          jd.pack();
        });
    String[] bp = Prop.builtinProps;
    for (int i = 0; i < bp.length; i++) {
      if (bp[i].equalsIgnoreCase(startprop.getType())) {
        cb1.setSelectedIndex(i);
        break;
      }
    }

    final JPanel p3 = new JPanel();
    p3.setLayout(gb);
    JButton cancelbutton = new JButton(guistrings.getString("Cancel"));
    p3.add(cancelbutton);
    gb.setConstraints(
        cancelbutton,
        JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 0)));
    cancelbutton.addActionListener(e -> jd.dispose());
    JButton okbutton = new JButton(guistrings.getString("OK"));
    p3.add(okbutton);
    gb.setConstraints(
        okbutton, JLFunc.constraints(GridBagConstraints.LINE_END, 1, 0, new Insets(0, 10, 0, 0)));
    okbutton.addActionListener(
        e -> {
          String type = cb1.getItemAt(cb1.getSelectedIndex());
          String mod;

          try {
            mod = getParameterList();
            // System.out.println("type = " + type + ", mod = " + mod);

            // fail if prop definition is invalid, before we change the pattern
            (new PropDef(type.toLowerCase(), mod)).layoutProp();
          } catch (JuggleExceptionUser jeu) {
            ErrorDialog.handleUserException(parentframe, jeu.getMessage());
            return;
          }

          // sync paths with current prop list
          for (int i = 0; i < pat.getNumberOfPaths(); i++) {
            pat.setPropAssignment(i + 1, animpropnum[i]);
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
              if ((mod == null && pdef.getMod() == null)
                  || (mod != null && mod.equalsIgnoreCase(pdef.getMod()))) {
                gotmatch = true;
                matchingprop = i;
                break;
              }
            }
          }

          if (gotmatch) {
            // new prop is identical to pre-existing one
            pat.setPropAssignment(pathnum, matchingprop);
          } else {
            // new prop is different
            PropDef newprop = new PropDef(type.toLowerCase(), mod);
            pat.addProp(newprop);
            pat.setPropAssignment(pathnum, pat.getNumberOfProps());
          }

          if (active_eventitem != null) {
            activeEventChanged();
          } else {
            layoutPattern(true);
          }
          jd.dispose();
          repaint();
        });

    jd.getContentPane().add(p1);
    gb.setConstraints(
        p1, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(10, 10, 0, 10)));
    jd.getContentPane().add(p2);
    gb.setConstraints(
        p2, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 0, 0, 0)));
    jd.getContentPane().add(p3);
    gb.setConstraints(
        p3, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 2, new Insets(10, 10, 10, 10)));
    jd.getRootPane().setDefaultButton(okbutton); // OK button is default

    Locale loc = Locale.getDefault();
    jd.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

    jd.pack();
    jd.setResizable(false);
    jd.setLocationRelativeTo(this);
    jd.setVisible(true); // blocks until dispose() above
    dialog_controls = null;
  }

  protected void defineThrow() {
    if (!(popupitem instanceof LadderEventItem)) {
      ErrorDialog.handleFatalException(new JuggleExceptionInternal("defineThrow() class format", pat));
      return;
    }
    JMLEvent ev = ((LadderEventItem) popupitem).event;
    if (!ev.isMaster()) {
      ev = ev.getMaster();
    }
    final JMLTransition tr = ev.getTransition(((LadderEventItem) popupitem).transnum);

    String[] pptypes = Path.builtinPaths;

    final JDialog jd = new JDialog(parentframe, guistrings.getString("Define_throw"), true);
    GridBagLayout gb = new GridBagLayout();
    jd.getContentPane().setLayout(gb);

    JPanel p1 = new JPanel();
    p1.setLayout(gb);
    JLabel lab = new JLabel(guistrings.getString("Throw_type"));
    p1.add(lab);
    gb.setConstraints(
        lab, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 0)));

    final JPanel p2 = new JPanel();
    p2.setLayout(gb);

    final JComboBox<String> cb1 = new JComboBox<>(pptypes);
    p1.add(cb1);
    gb.setConstraints(
        cb1, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 0, new Insets(0, 10, 0, 0)));
    cb1.addActionListener(
        ae -> {
          String type = cb1.getItemAt(cb1.getSelectedIndex());
          try {
            Path ppt;
            if (type.equalsIgnoreCase(tr.getThrowType())) {
              ppt = tr.getOutgoingPathLink().getPath();
            } else {
              ppt = Path.newPath(type);
            }
            makeParametersPanel(p2, ppt.getParameterDescriptors());
          } catch (JuggleExceptionUser jeu) {
            ErrorDialog.handleUserException(jd, jeu.getMessage());
            return;
          }
          jd.pack();
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
    gb.setConstraints(
        cancelbutton,
        JLFunc.constraints(GridBagConstraints.LINE_END, 0, 0, new Insets(0, 0, 0, 0)));
    cancelbutton.addActionListener(e -> jd.dispose());
    JButton okbutton = new JButton(guistrings.getString("OK"));
    p3.add(okbutton);
    gb.setConstraints(
        okbutton, JLFunc.constraints(GridBagConstraints.LINE_END, 1, 0, new Insets(0, 10, 0, 0)));
    okbutton.addActionListener(
        e -> {
          String type = cb1.getItemAt(cb1.getSelectedIndex());
          tr.setThrowType(type.toLowerCase());

          String mod;
          try {
            mod = getParameterList();
          } catch (JuggleExceptionUser jeu) {
            ErrorDialog.handleUserException(parentframe, jeu.getMessage());
            return;
          }

          tr.mod = mod;

          activeEventChanged();
          jd.dispose();
        });

    jd.getContentPane().add(p1);
    gb.setConstraints(
        p1, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 0, new Insets(10, 10, 0, 10)));
    jd.getContentPane().add(p2);
    gb.setConstraints(
        p2, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(0, 0, 0, 0)));
    jd.getContentPane().add(p3);
    gb.setConstraints(
        p3, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 2, new Insets(10, 10, 10, 10)));
    jd.getRootPane().setDefaultButton(okbutton); // OK button is default

    jd.pack();
    jd.setResizable(false);
    jd.setLocationRelativeTo(this);
    jd.setVisible(true); // blocks until dispose() above
    dialog_controls = null;
  }

  protected void changeCatchStyleTo(int type) {
    if (popupitem == null) {
      ErrorDialog.handleFatalException(new JuggleExceptionInternal("No popupitem in case 10", pat));
      return;
    }
    if (!(popupitem instanceof LadderEventItem)) {
      ErrorDialog.handleFatalException(
          new JuggleExceptionInternal("LadderDiagram change to catch class format", pat));
      return;
    }
    JMLEvent ev = ((LadderEventItem) popupitem).event;
    if (!ev.isMaster()) {
      ev = ev.getMaster();
    }
    // int transnum = ((LadderEventItem)popupitem).transnum;
    JMLTransition tr = ev.getTransition(((LadderEventItem) popupitem).transnum);
    tr.setType(type);
    activeEventChanged();
    repaint();
  }

  protected void makeLastInEvent() {
    if (popupitem == null) {
      ErrorDialog.handleFatalException(new JuggleExceptionInternal("No popupitem in case 8", pat));
      return;
    }
    if (!(popupitem instanceof LadderEventItem)) {
      ErrorDialog.handleFatalException(
          new JuggleExceptionInternal("LadderDiagram make last transition class format", pat));
      return;
    }
    JMLEvent ev = ((LadderEventItem) popupitem).event;
    if (!ev.isMaster()) {
      ev = ev.getMaster();
    }
    JMLTransition tr = ev.getTransition(((LadderEventItem) popupitem).transnum);
    ev.removeTransition(tr);
    ev.addTransition(tr); // will add at end
    active_eventitem = null; // deselect event since it's moving
    if (aep != null) {
      aep.deactivateEvent();
    }
    layoutPattern(true);
    createView();
    repaint();
  }

  // Helper for defineProp() and defineThrow()

  protected void makeParametersPanel(JPanel jp, ParameterDescriptor[] pd) {
    jp.removeAll();
    GridBagLayout gb = new GridBagLayout();
    jp.setLayout(gb);

    dialog_controls = new ArrayList<>();
    dialog_pd = pd;

    if (pd.length != 0) {
      JPanel pdp = new JPanel();
      pdp.setLayout(gb);

      for (int i = 0; i < pd.length; i++) {
        JLabel lab = new JLabel(pd[i].name);
        pdp.add(lab);
        gb.setConstraints(
            lab, JLFunc.constraints(GridBagConstraints.LINE_START, 0, i, new Insets(0, 0, 0, 0)));
        if (pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
          // JComboBox jcb = new JComboBox(booleanList);
          JCheckBox jcb = new JCheckBox();
          pdp.add(jcb);
          gb.setConstraints(
              jcb, JLFunc.constraints(GridBagConstraints.LINE_START, 1, i, new Insets(2, 5, 2, 0)));
          dialog_controls.add(jcb);
          boolean def = (Boolean) (pd[i].value);
          // jcb.setSelectedIndex(def ? 0 : 1);
          jcb.setSelected(def);
        } else if (pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
          JTextField tf = new JTextField(7);
          pdp.add(tf);
          gb.setConstraints(
              tf, JLFunc.constraints(GridBagConstraints.LINE_START, 1, i, new Insets(0, 5, 0, 0)));
          dialog_controls.add(tf);
          Double def = (Double) (pd[i].value);
          tf.setText(def.toString());
        } else if (pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
          String[] choices = new String[pd[i].range.size()];
          pd[i].range.toArray(choices);

          JComboBox<String> jcb = new JComboBox<>(choices);
          jcb.setMaximumRowCount(15);
          pdp.add(jcb);
          gb.setConstraints(
              jcb, JLFunc.constraints(GridBagConstraints.LINE_START, 1, i, new Insets(0, 5, 0, 0)));
          dialog_controls.add(jcb);

          String val = (String) (pd[i].value);
          for (int j = 0; j < choices.length; j++) {
            if (val.equalsIgnoreCase(choices[j])) {
              jcb.setSelectedIndex(j);
              break;
            }
          }
        } else if (pd[i].type == ParameterDescriptor.TYPE_INT) {
          JTextField tf = new JTextField(4);
          pdp.add(tf);
          gb.setConstraints(
              tf, JLFunc.constraints(GridBagConstraints.LINE_START, 1, i, new Insets(0, 5, 0, 0)));
          dialog_controls.add(tf);
          Integer def = (Integer) (pd[i].value);
          tf.setText(def.toString());

          tf.addCaretListener(e -> {
                // System.out.println("Caret Update");
              });
        } else if (pd[i].type == ParameterDescriptor.TYPE_ICON) {
          final ParameterDescriptor fpd = pd[i];
          final ParameterDescriptor[] fpds = pd;
          final JPanel fjp = jp;
          URL filename = (URL) fpd.value;

          ImageIcon icon = new ImageIcon(filename, filename.toString());
          // Scale the image down if it's too big
          final float MAX_HEIGHT = 100;
          if (icon.getIconHeight() > MAX_HEIGHT) {
            float scaleFactor = MAX_HEIGHT / icon.getIconHeight();
            int height = (int) (scaleFactor * icon.getIconHeight());
            int width = (int) (scaleFactor * icon.getIconWidth());
            icon.setImage(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
          }
          JLabel label = new JLabel(icon);

          // Clicking on the icon launches a file chooser for getting a new image
          label.addMouseListener(
              new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                  JLFunc.getJfc()
                      .setFileFilter(
                          new FileNameExtensionFilter("Image file", "jpg", "jpeg", "gif", "png"));
                  int result = JLFunc.getJfc().showOpenDialog(EditLadderDiagram.this);
                  if (result != JFileChooser.APPROVE_OPTION) {
                    return;
                  }

                  try {
                    // We have to load the image to get the correct dimensions
                    // ImageIcon icon = new ImageIcon(source, source.toString());
                    // Rebuild the paramter descriptions
                    fpds[0].value = JLFunc.getJfc().getSelectedFile().toURI().toURL();
                    // fpds[1].value = new Integer(icon.getIconWidth());
                    // fpds[2].value = new Integer(icon.getIconHeight());
                    // fpds[1].default_value = fpds[1].value;
                    // fpds[2].default_value = fpds[2].value;
                    // Remake the parameter panal with new default values.
                    makeParametersPanel(fjp, fpds);
                    ((JDialog) (fjp.getTopLevelAncestor())).pack();
                  } catch (MalformedURLException ex) {
                    // This should never happen
                    ErrorDialog.handleFatalException(
                        new JuggleExceptionUser(errorstrings.getString("Error_malformed_URL.")));
                  }
                }
              });
          // Add the icon to the panel
          pdp.add(label);
          gb.setConstraints(
              label,
              JLFunc.constraints(GridBagConstraints.LINE_START, 1, i, new Insets(0, 5, 5, 0)));
          dialog_controls.add(label);
        }
      }

      jp.add(pdp);
      gb.setConstraints(
          pdp, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 1, new Insets(10, 10, 0, 10)));
    }
  }

  protected String getParameterList() throws JuggleExceptionUser {
    String result = null;
    for (int i = 0; i < dialog_pd.length; i++) {
      String term = null;
      Object control = dialog_controls.get(i);
      if (dialog_pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
        // JComboBox jcb = (JComboBox)control;
        // boolean val = ((jcb.getSelectedIndex() == 0) ? true : false);
        JCheckBox jcb = (JCheckBox) control;
        boolean val = jcb.isSelected();
        boolean def_val = (Boolean) (dialog_pd[i].defaultValue);
        if (val != def_val) {
          term = (Boolean.valueOf(val)).toString();
        }
      } else if (dialog_pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
        JTextField tf = (JTextField) control;
        try {
          double val = JLFunc.parseDouble(tf.getText());
          double def_val = (Double) (dialog_pd[i].defaultValue);
          if (val != def_val) {
            term = tf.getText().trim();
          }
        } catch (NumberFormatException nfe) {
          String template = errorstrings.getString("Error_number_format");
          Object[] arguments = {dialog_pd[i].name};
          throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
      } else if (dialog_pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
        JComboBox<?> jcb = (JComboBox<?>) control;
        int ind = jcb.getSelectedIndex();
        String val = dialog_pd[i].range.get(ind);
        String def_val = (String) (dialog_pd[i].defaultValue);
        if (!val.equalsIgnoreCase(def_val)) {
          term = val;
        }
      } else if (dialog_pd[i].type == ParameterDescriptor.TYPE_INT) {
        JTextField tf = (JTextField) control;
        try {
          int val = Integer.parseInt(tf.getText());
          int def_val = (Integer) (dialog_pd[i].defaultValue);
          if (val != def_val) {
            term = tf.getText().trim();
          }
        } catch (NumberFormatException nfe) {
          String template = errorstrings.getString("Error_number_format");
          Object[] arguments = {dialog_pd[i].name};
          throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
      } else if (dialog_pd[i].type == ParameterDescriptor.TYPE_ICON) {
        JLabel label = (JLabel) control;
        ImageIcon icon = (ImageIcon) label.getIcon();
        String def = dialog_pd[i].defaultValue.toString();
        if (!icon.getDescription().equals(def)) {
          term = icon.getDescription();  // This contains the URL string
        }
      }

      if (term != null) {
        term = dialog_pd[i].name + "=" + term;

        if (result == null) {
          result = term;
        } else {
          result = result + ";" + term;
        }
      }
    }
    return result;
  }

  // Call this at the very end of every popup interaction.
  protected void finishPopup() {
    popupitem = null;

    if (gui_state == STATE_POPUP) {
      gui_state = STATE_INACTIVE;
      if (aep != null) {
        aep.setPaused(anim_paused);
      }
    }
  }

  //----------------------------------------------------------------------------
  // AnimationPanel.AnimationAttachment methods
  //----------------------------------------------------------------------------

  @Override
  public void setAnimationPanel(AnimationPanel a) {
    super.setAnimationPanel(a);

    if (a instanceof AnimationEditPanel) {
      aep = (AnimationEditPanel) a;
    }
  }

  //----------------------------------------------------------------------------
  // javax.swing.JComponent methods
  //----------------------------------------------------------------------------

  @Override
  protected void paintComponent(Graphics gr) {
    if (gr instanceof Graphics2D gr2) {
      gr2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    if (!paintLadder(gr)) {
      return;
    }

    // draw the box around the selected position
    if (active_positionitem != null) {
      gr.setColor(COLOR_SELECTION);
      gr.drawLine(
          active_positionitem.xlow - 1,
          active_positionitem.ylow - 1,
          active_positionitem.xhigh + 1,
          active_positionitem.ylow - 1);
      gr.drawLine(
          active_positionitem.xhigh + 1,
          active_positionitem.ylow - 1,
          active_positionitem.xhigh + 1,
          active_positionitem.yhigh + 1);
      gr.drawLine(
          active_positionitem.xhigh + 1,
          active_positionitem.yhigh + 1,
          active_positionitem.xlow,
          active_positionitem.yhigh + 1);
      gr.drawLine(
          active_positionitem.xlow - 1,
          active_positionitem.yhigh + 1,
          active_positionitem.xlow - 1,
          active_positionitem.ylow - 1);
    }

    // draw the box around the selected event
    if (active_eventitem != null) {
      gr.setColor(COLOR_SELECTION);
      gr.drawLine(
          active_eventitem.xlow - 1,
          active_eventitem.ylow - 1,
          active_eventitem.xhigh + 1,
          active_eventitem.ylow - 1);
      gr.drawLine(
          active_eventitem.xhigh + 1,
          active_eventitem.ylow - 1,
          active_eventitem.xhigh + 1,
          active_eventitem.yhigh + 1);
      gr.drawLine(
          active_eventitem.xhigh + 1,
          active_eventitem.yhigh + 1,
          active_eventitem.xlow,
          active_eventitem.yhigh + 1);
      gr.drawLine(
          active_eventitem.xlow - 1,
          active_eventitem.yhigh + 1,
          active_eventitem.xlow - 1,
          active_eventitem.ylow - 1);
    }

    // label the tracker line with the time
    if (gui_state == STATE_MOVING_TRACKER) {
      gr.setColor(COLOR_TRACKER);
      gr.drawString(JLFunc.toStringRounded(sim_time, 2) + " s", width / 2 - 18, tracker_y - 5);
    }
  }
}
