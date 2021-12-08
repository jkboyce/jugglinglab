// Mutator.java
//
// Copyright 2021 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.view;

import java.awt.Component;
import java.awt.Insets;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Dimension;
import java.util.Hashtable;
import java.util.ResourceBundle;
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
- remove a randomly-selected event with only holding transitions

large mutations (NOT IMPLEMENTED):
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
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // baseline amounts that various mutations can adjust events
    static final double mutationPositionCm = 40.0;
    static final double mutationMinEventDeltaSec = 0.03;
    static final double mutationTimingScale = 0.5;
    static final double mutationNewEventPositionCm = 40.0;

    // baseline relative frequency of each mutation type
    static final double[] mutation_freq = { 0.4, 0.1, 0.1, 0.2, 0.2 };

    // overall scale of adjustment, per mutation
    static final double[] slider_rates = { 0.2, 0.4, 0.7, 1.0, 1.3, 1.6, 2.0 };
    protected double rate;

    protected JPanel controls;
    protected JCheckBox[] cb;
    protected JSlider slider_rate;


    public Mutator() {
        this.controls = makeControlPanel();
    }

    // return a mutated version of the input pattern.
    // Important: This should not change the input pattern in any way
    public JMLPattern mutatePattern(JMLPattern pat) throws JuggleExceptionInternal {
        double[] cdf = new double[5];
        double freq_sum = 0.0;
        for (int i = 0; i < 5; i++) {
            freq_sum += (cb[i].isSelected() ? mutation_freq[i] : 0.0);
            cdf[i] = freq_sum;
        }

        try {
            if (freq_sum == 0.0)
                return new JMLPattern(pat);

            this.rate = (slider_rate == null ? 1.0 : slider_rates[slider_rate.getValue()]);

            JMLPattern mutant = null;
            int tries = 0;

            do {
                JMLPattern clone = new JMLPattern(pat);
                double r = freq_sum * Math.random();
                tries++;

                if (r < cdf[0])
                    mutant = mutateEventPosition(clone);
                else if (r < cdf[1])
                    mutant = mutateEventTime(clone);
                else if (r < cdf[2])
                    mutant = mutatePatternTiming(clone);
                else if (r < cdf[3])
                    mutant = mutateAddEvent(clone);
                else
                    mutant = mutateRemoveEvent(clone);
            } while (mutant == null && tries < 5);

            return (mutant == null) ? new JMLPattern(pat) : mutant;
        } catch (JuggleExceptionUser jeu) {
            // this shouldn't be able to happen, so treat it as an internal error
            throw new JuggleExceptionInternal("Mutator: User error: " + jeu.getMessage());
        }
    }

    public JPanel getControlPanel() { return this.controls; }

    // ------------------------------------------------------------------------

    // Pick a random event and tweak its position
    protected JMLPattern mutateEventPosition(JMLPattern pat) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        JMLEvent ev = pickMasterEvent(pat);
        Coordinate pos = ev.getLocalCoordinate();
        pos = pickNewPosition(ev.getHand(), rate * mutationPositionCm, pos);
        ev.setLocalCoordinate(pos);
        pat.setNeedsLayout(true);
        return pat;
    }

    // Pick a random event and tweak its time
    protected JMLPattern mutateEventTime(JMLPattern pat) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
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
                       mutationMinEventDeltaSec);

        JMLEvent ev_next = ev.getNext();
        while (ev_next != null) {
            if (ev_next.getJuggler() == ev.getJuggler() &&
                        ev_next.getHand() == ev.getHand())
                break;
            ev_next = ev_next.getNext();
        }
        double tmax = (ev_next == null ? pat.getLoopEndTime() :
                       Math.min(pat.getLoopEndTime(), ev_next.getT()) -
                       mutationMinEventDeltaSec);

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
    protected JMLPattern mutatePatternTiming(JMLPattern pat) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        // sample new scale from two one-sided triangular distributions: Scale has
        // equal probability of going up or down
        double r = Math.random();
        double scalemin = 1.0 / (1.0 + rate * mutationTimingScale);
        double scalemax = 1.0 + rate * mutationTimingScale;
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
    protected JMLPattern mutateAddEvent(JMLPattern pat) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        if (!pat.isLaidout())
            pat.layoutPattern();

        JMLEvent ev = null;
        double tmin, tmax, t;
        int juggler, hand;
        int tries = 0;

        do {
            juggler = 1 + (int)(pat.getNumberOfJugglers() * Math.random());
            hand = Math.random() < 0.5 ? HandLink.LEFT_HAND : HandLink.RIGHT_HAND;

            // Choose the time at which to add the event. We want to bias the
            // selection so that we tend to pick times not too close to other
            // events for that same juggler/hand. Find the bracketing events and
            // pick from a triangular distribution.
            tmin = pat.getLoopStartTime();
            tmax = pat.getLoopEndTime();
            t = tmin + (tmax - tmin) * Math.random();

            ev = pat.getEventList();
            while (ev != null) {
                if (ev.getJuggler() == juggler && ev.getHand() == hand && ev.getT() >= t)
                    break;
                ev = ev.getNext();
            }
            if (ev == null)
                return null;
            tmax = ev.getT() - mutationMinEventDeltaSec;

            while (ev != null) {
                if (ev.getJuggler() == juggler && ev.getHand() == hand && ev.getT() <= t)
                    break;
                ev = ev.getPrevious();
            }
            if (ev == null)
                return null;
            tmin = ev.getT() + mutationMinEventDeltaSec;

            tries++;
        } while (tmin > tmax && tries < 5);

        if (tries == 5)
            return null;

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
        pos = pickNewPosition(hand, rate * mutationNewEventPositionCm, pos);
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

    // remove a randomly-selected master event with only holding transitions
    protected JMLPattern mutateRemoveEvent(JMLPattern pat) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        // first count the number of such events
        int count = 0;
        JMLEvent ev = pat.getEventList();

        while (ev != null) {
            if (ev.isMaster()) {
                boolean holding_only = true;
                for (int i = 0; i < ev.getNumberOfTransitions(); i++) {
                    int type = ev.getTransition(i).getType();
                    if (type != JMLTransition.TRANS_NONE &&
                                type != JMLTransition.TRANS_HOLDING) {
                        holding_only = false;
                        break;
                    }
                }
                if (holding_only)
                    count++;
            }
            ev = ev.getNext();
        }

        if (count == 0)
            return null;

        // pick one to remove, then go back through event list and find it
        count = (int)(count * Math.random());

        ev = pat.getEventList();

        while (ev != null) {
            if (ev.isMaster()) {
                boolean holding_only = true;
                for (int i = 0; i < ev.getNumberOfTransitions(); i++) {
                    int type = ev.getTransition(i).getType();
                    if (type != JMLTransition.TRANS_NONE &&
                                type != JMLTransition.TRANS_HOLDING) {
                        holding_only = false;
                        break;
                    }
                }
                if (holding_only) {
                    if (count == 0) {
                        pat.removeEvent(ev);
                        pat.setNeedsLayout(true);
                        return pat;
                    }
                    count--;
                }
            }
            ev = ev.getNext();
        }

        throw new JuggleExceptionInternal("mutateRemoveEvent error");
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

    protected Coordinate pickNewPosition(int hand, double scaleDistance, Coordinate pos) {
        /*
        Define a bounding box for "normal" hand positions:
        (x, z) from (-75,-20) to (+40,+80) for left hand
                    (-40,-20) to (+75,+80) for right hand

        Bias the mutations to mostly stay within this region.

        Strategy:
        1. pick a random delta from the current position
        2. if the new position falls outside the bounding box, with probability
           50% accept it as-is. Otherwise goto 1.
        */
        Coordinate result = null;
        boolean outside_box;

        do {
            result = new Coordinate(pos);
            // leave y component unchanged to maintain plane of juggling
            result.x += 2.0 * scaleDistance * (Math.random() - 0.5);
            result.z += 2.0 * scaleDistance * (Math.random() - 0.5);

            if (hand == HandLink.LEFT_HAND)
                outside_box = (result.x < -75 || result.x > 40 ||
                               result.z < -20 || result.z > 80);
            else
                outside_box = (result.x < -40 || result.x > 75 ||
                               result.z < -20 || result.z > 80);
        } while (outside_box && Math.random() < 0.5);

        return result;
    }

    protected JPanel makeControlPanel() {
        JPanel controls = new JPanel();

        GridBagLayout gb = new GridBagLayout();
        controls.setLayout(gb);
        controls.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel lab = new JLabel(guistrings.getString("Mutator_header1"));
        gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_START,
                        0, 0, new Insets(0, 0, 10, 0)));
        controls.add(lab);

        this.cb = new JCheckBox[5];

        this.cb[0] = new JCheckBox(guistrings.getString("Mutator_type1"), true);
        gb.setConstraints(cb[0], make_constraints(GridBagConstraints.LINE_START,
                        0, 1, null));
        controls.add(cb[0]);

        this.cb[1] = new JCheckBox(guistrings.getString("Mutator_type2"), true);
        gb.setConstraints(cb[1], make_constraints(GridBagConstraints.LINE_START,
                        0, 2, null));
        controls.add(cb[1]);

        this.cb[2] = new JCheckBox(guistrings.getString("Mutator_type3"), true);
        gb.setConstraints(cb[2], make_constraints(GridBagConstraints.LINE_START,
                        0, 3, null));
        controls.add(cb[2]);

        this.cb[3] = new JCheckBox(guistrings.getString("Mutator_type4"), true);
        gb.setConstraints(cb[3], make_constraints(GridBagConstraints.LINE_START,
                        0, 4, null));
        controls.add(cb[3]);

        this.cb[4] = new JCheckBox(guistrings.getString("Mutator_type5"), true);
        gb.setConstraints(cb[4], make_constraints(GridBagConstraints.LINE_START,
                        0, 5, null));
        controls.add(cb[4]);

        lab = new JLabel(guistrings.getString("Mutator_header2"));
        gb.setConstraints(lab, make_constraints(GridBagConstraints.LINE_START,
                        0, 6, new Insets(20, 0, 10, 0)));
        controls.add(lab);

        this.slider_rate = new JSlider(SwingConstants.HORIZONTAL, 0, 6, 3);
        GridBagConstraints gbc = make_constraints(GridBagConstraints.LINE_START,
                        0, 7, null);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gb.setConstraints(slider_rate, gbc);
        slider_rate.setMajorTickSpacing(1);
        slider_rate.setPaintTicks(true);
        slider_rate.setSnapToTicks(true);
        Hashtable<Integer,JComponent> labels = new Hashtable<Integer,JComponent>();
        labels.put(Integer.valueOf(0), new JLabel(guistrings.getString("Mutation_rate_low")));
        labels.put(Integer.valueOf(3), new JLabel(guistrings.getString("Mutation_rate_medium")));
        labels.put(Integer.valueOf(6), new JLabel(guistrings.getString("Mutation_rate_high")));
        slider_rate.setLabelTable(labels);
        slider_rate.setPaintLabels(true);
        controls.add(slider_rate);

        return controls;
    }

    protected static GridBagConstraints make_constraints(int location,
                                    int gridx, int gridy, Insets ins) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = location;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.insets = (ins == null ? new Insets(0, 0, 0, 0) : ins);
        gbc.weightx = gbc.weighty = 0.0;
        return gbc;
    }
}
