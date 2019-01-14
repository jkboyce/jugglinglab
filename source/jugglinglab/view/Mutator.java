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

/*

small mutations:
- change positions of events (in-plane)
- change times of events
- change overall timing of pattern (uniform speedup/slowdown)

moderate mutations:
- add new (tweaked) event to a given hand
- remove event

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

*/

public class Mutator {
    protected JPanel controls;

    // amount that event positions can move, in centimeters
    protected double mutationScaleCm = 20.0;


    public Mutator() {
        this.controls = makeControlPanel();
    }

    // return a mutated version of the input pattern.
    // Important: This should not change the input pattern in any way
    public JMLPattern mutatePattern(JMLPattern pat) throws JuggleExceptionInternal {
        JMLPattern clone = (JMLPattern)pat.clone();
        JMLPattern mutant = null;
        try {
            mutant = mutateEventPositions(clone);
        } catch (JuggleExceptionUser jeu) {
            throw new JuggleExceptionInternal("Mutator: User error: " + jeu.getMessage());
        }
        return mutant;
    }

    // First type of mutation: Tweak the position of an event
    protected JMLPattern mutateEventPositions(JMLPattern pat) throws JuggleExceptionUser,
                    JuggleExceptionInternal {
        JMLEvent ev = pickEvent(pat);
        Coordinate pos = ev.getLocalCoordinate();

        // leave y component of position unchanged to maintain plane of juggling
        pos.x += 2.0 * mutationScaleCm * (Math.random() - 0.5);
        pos.z += 2.0 * mutationScaleCm * (Math.random() - 0.5);
        ev.setLocalCoordinate(pos);
        pat.setNeedsLayout(true);
        return pat;
    }

    // return a random master event from the pattern
    protected JMLEvent pickEvent(JMLPattern pat) throws JuggleExceptionUser,
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
