// Mutator.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

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

package jugglinglab.view;

import javax.swing.*;

import jugglinglab.jml.*;
import jugglinglab.util.*;

/* ----------------------------------------------------------------------------
This class is used by SelectionView to create random variations of a pattern.
It does this by selecting from the following list of operations:

small mutations:
- change position of a randomly-selected event (but keep in-plane)
- change time of a randomly-selected event
- change overall timing of pattern (uniform speedup/slowdown)

moderate mutations:
- add a new event with no transitions to a hand, at a random time and
  changed position
- remove a randomly-selected event with no transitions

large mutations:
- add throw/catch pair
- delete a throw/catch pair (turn into a hold)
- move a catch/throw pair to the opposite hand

not for consideration:
- remove symmetries
- change throw types
- change positions of events out of plane
- change # of objects
- change # of jugglers
- change positions or angles of jugglers
- change props
---------------------------------------------------------------------------- */

public class Mutator {
    protected JPanel controls;

    // amount that event positions can move, in centimeters
    protected double mutationScaleCm = 20.0;
    protected double mutationMinEventDeltaT = 0.03;
    protected double mutationTimingScale = 1.5;
    protected double mutationNewEventTweakCm = 10.0;

    public Mutator() {
        this.controls = makeControlPanel();
    }

    // return a mutated version of the input pattern.
    // Important: This should not change the input pattern in any way
    public JMLPattern mutatePattern(JMLPattern pat) throws JuggleExceptionInternal {
        JMLPattern clone = (JMLPattern)pat.clone();
        JMLPattern mutant = null;
        try {
            do {
                int type = (int)(4.0 * Math.random());

                switch (type) {
                    case 0:
                        mutant = mutateEventPosition(clone);
                        break;
                    case 1:
                        mutant = mutateEventTime(clone);
                        break;
                    case 2:
                        mutant = mutatePatternTiming(clone);
                        break;
                    case 3:
                        mutant = mutateAddEvent(clone);
                        break;
                }
            } while (mutant == null);
        } catch (JuggleExceptionUser jeu) {
            throw new JuggleExceptionInternal("Mutator: User error: " + jeu.getMessage());
        }
        return mutant;
    }

    // ------------------------------------------------------------------------

    // Pick a random event and tweak its position
    protected JMLPattern mutateEventPosition(JMLPattern pat) throws JuggleExceptionUser,
                    JuggleExceptionInternal {
        JMLEvent ev = pickMasterEvent(pat);
        Coordinate pos = ev.getLocalCoordinate();

        // leave y component of position unchanged to maintain plane of juggling
        pos.x += 2.0 * mutationScaleCm * (Math.random() - 0.5);
        pos.z += 2.0 * mutationScaleCm * (Math.random() - 0.5);
        ev.setLocalCoordinate(pos);
        pat.setNeedsLayout(true);
        return pat;
    }

    // Pick a random event and tweak its time
    protected JMLPattern mutateEventTime(JMLPattern pat) throws JuggleExceptionUser,
                    JuggleExceptionInternal {
        JMLEvent ev = pickMasterEvent(pat);

        JMLEvent ev_prev = ev.getPrevious();
        while (ev_prev != null) {
            if (ev_prev.getJuggler() == ev.getJuggler() &&
                        ev_prev.getHand() == ev.getHand())
                break;
            ev_prev = ev_prev.getPrevious();
        }
        double tmin = (ev_prev == null ? pat.getLoopStartTime() :
                       Math.max(pat.getLoopStartTime(), ev_prev.getT()) +
                       mutationMinEventDeltaT);

        JMLEvent ev_next = ev.getNext();
        while (ev_next != null) {
            if (ev_next.getJuggler() == ev.getJuggler() &&
                        ev_next.getHand() == ev.getHand())
                break;
            ev_next = ev_next.getNext();
        }
        double tmax = (ev_next == null ? pat.getLoopEndTime() :
                       Math.min(pat.getLoopEndTime(), ev_next.getT()) -
                       mutationMinEventDeltaT);

        if (tmax <= tmin)
            return null;

        // Sample t from two one-sided triangular distributions: Event time has
        // equal probability of going down or up.
        double r = Math.random();
        double tnow = ev.getT();
        double t = 0.0;
        if (r < 0.5)
            t = tmin + (tnow - tmin) * Math.sqrt(2 * r);
        else
            t = tmax - (tmax - tnow) * Math.sqrt(2 * (1 - r));

        ev.setT(t);
        pat.setNeedsLayout(true);
        return pat;
    }

    // rescale overall pattern timing faster or slower
    protected JMLPattern mutatePatternTiming(JMLPattern pat) throws JuggleExceptionUser,
                    JuggleExceptionInternal {
        // sample new scale from two one-sided triangular distributions: Scale has
        // equal probability of going up or down
        double r = Math.random();
        double scalemin = 1.0 / mutationTimingScale;
        double scalemax = mutationTimingScale;
        double scale = 0.0;
        if (r < 0.5)
            scale = scalemin + (1.0 - scalemin) * Math.sqrt(2 * r);
        else
            scale = scalemax - (scalemax - 1.0) * Math.sqrt(2 * (1 - r));

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
            if (delay > 0.0)
                sym.setDelay(delay * scale);
        }

        pat.setNeedsLayout(true);
        return pat;
    }

    // add an event with no transitions to a randomly-selected juggler/hand,
    // with a tweaked position
    protected JMLPattern mutateAddEvent(JMLPattern pat) throws JuggleExceptionUser,
                    JuggleExceptionInternal {
        if (!pat.isLaidout())
            pat.layoutPattern();

        int juggler = 1 + (int)(pat.getNumberOfJugglers() * Math.random());
        int hand = Math.random() < 0.5 ? HandLink.LEFT_HAND : HandLink.RIGHT_HAND;

        // Choose the time at which to add the event. We want to bias the
        // selection so that we tend to pick times not too close to other
        // events for that same juggler/hand. Find the bracketing events and
        // pick from a triangular distribution.
        double tmin = pat.getLoopStartTime();
        double tmax = pat.getLoopEndTime();
        double t = tmin + (tmax - tmin) * Math.random();

        JMLEvent ev = pat.getEventList();
        while (ev != null) {
            if (ev.getJuggler() == juggler && ev.getHand() == hand && ev.getT() >= t)
                break;
            ev = ev.getNext();
        }
        if (ev == null)
            return null;
        tmax = ev.getT();

        while (ev != null) {
            if (ev.getJuggler() == juggler && ev.getHand() == hand && ev.getT() <= t)
                break;
            ev = ev.getPrevious();
        }
        if (ev == null)
            return null;
        tmin = ev.getT();

        double r = Math.random();
        if (r < 0.5)
            t = tmin + (tmax - tmin) * Math.sqrt(0.5 * r);
        else
            t = tmax - (tmax - tmin) * Math.sqrt(0.5 * (1 - r));

        // want its time to be within this range since it's a master event
        while (t < pat.getLoopStartTime())
            t += (pat.getLoopEndTime() - pat.getLoopStartTime());
        while (t > pat.getLoopEndTime())
            t -= (pat.getLoopEndTime() - pat.getLoopStartTime());

        ev = new JMLEvent();
        ev.setHand(juggler, hand);
        ev.setT(t);
        ev.setMaster(null);     // null signifies a master event

        // Now choose a spatial location for the event. Figure out where the
        // hand is currently and adjust it.
        Coordinate pos = new Coordinate();
        pat.getHandCoordinate(juggler, hand, t, pos);
        pos = pat.convertGlobalToLocal(pos, juggler, t);
        // leave y component of position unchanged to maintain plane of juggling
        pos.x += 2.0 * mutationNewEventTweakCm * (Math.random() - 0.5);
        pos.z += 2.0 * mutationNewEventTweakCm * (Math.random() - 0.5);
        ev.setLocalCoordinate(pos);

        // Last step: add a "holding" transition for every path that the hand
        // is holding at the chosen time
        for (int path = 1; path <= pat.getNumberOfPaths(); path++) {
            if (pat.isHandHoldingPath(juggler, hand, t, path)) {
                JMLTransition trans = new JMLTransition(
                            JMLTransition.TRANS_HOLDING, path, null, null);
                ev.addTransition(trans);
            }
        }

        pat.addEvent(ev);
        pat.setNeedsLayout(true);
        return pat;
    }

    // ------------------------------------------------------------------------

    // return a random master event from the pattern
    protected JMLEvent pickMasterEvent(JMLPattern pat) throws JuggleExceptionUser,
                    JuggleExceptionInternal {
        if (!pat.isLaidout())
            pat.layoutPattern();

        JMLEvent eventlist = pat.getEventList();
        int master_count = 0;

        JMLEvent current = eventlist;
        do {
            if (current.isMaster())
                master_count++;
            current = current.getNext();
        } while (current != null);

        // pick a number from 0 to (master_count - 1) inclusive
        int event_num = (int)(Math.random() * master_count);

        current = eventlist;
        do {
            if (current.isMaster()) {
                if (event_num == 0)
                    return current;
                event_num--;
            }
            current = current.getNext();
        } while (current != null);

        throw new JuggleExceptionInternal("Mutator: pickEvent() failed");
    }

    protected JPanel makeControlPanel() {
        JPanel p = new JPanel();

        p.add(new JButton("Hello"));
        return p;
    }

    public JPanel getControlPanel() { return this.controls; }
}
