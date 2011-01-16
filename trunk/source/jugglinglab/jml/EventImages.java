// EventImages.java
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

package jugglinglab.jml;

import jugglinglab.util.*;


public class EventImages {
    protected JMLPattern pat = null;
    protected int numjugglers, numpaths;
    protected double looptime;
    protected Permutation loopperm = null;

    protected JMLEvent ev = null;
    protected int evjuggler, evhand, evtransitions;	// hand is by index (0 or 1)
    protected double evtime;

    protected Permutation[][][] ea = null;

    protected int numentries;
    protected int[] transitiontype = null;

    protected int currentloop, currentj, currenth, currententry;


    public EventImages(JMLPattern pat, JMLEvent ev) throws JuggleExceptionUser {
        this.pat = pat;
        this.ev = ev;
        calcarray();
        resetPosition();
        ev.delay = 0;					// delay relative to master -> none for ev
        ev.delayunits = numentries;
    }

    public JMLEvent getNext() {
        // move pointer to next in line
        do {
            if (++currenth == 2) {
                currenth = 0;
                if (++currentj == this.numjugglers) {
                    currentj = 0;
                    if (++currententry == this.numentries) {
                        currententry = 0;
                        currentloop++;
                    }
                }
            }
        } while (ea[currentj][currenth][currententry] == null);

        return makeEvent();
    }

    public JMLEvent getPrevious() {
        // move point to previous in line
        do {
            if (currenth-- == 0) {
                currenth = 1;
                if (currentj-- == 0) {
                    currentj = this.numjugglers - 1;
                    if (currententry-- == 0) {
                        currententry = this.numentries - 1;
                        currentloop--;
                    }
                }
            }
        } while (ea[currentj][currenth][currententry] == null);

        return makeEvent();
    }

    protected JMLEvent makeEvent() {
        JMLEvent newevent = ev.duplicate(currententry + numentries * currentloop, numentries);

        newevent.setHand(currentj+1, (currenth==0 ?
                                      HandLink.LEFT_HAND : HandLink.RIGHT_HAND));
        if (currenth != evhand) {
            Coordinate c1 = newevent.getLocalCoordinate();
            c1.x = -c1.x;
            newevent.setLocalCoordinate(c1);
        }
        Permutation p = ea[currentj][currenth][currententry];
        Permutation lp = this.loopperm;
        int pow = currentloop;
        if (pow < 0) {
            lp = lp.getInverse();
            pow = -pow;
        }
        while (pow > 0) {
            p = lp.apply(p);
            pow--;
        }
        for (int i = 0; i < this.evtransitions; i++) {
            JMLTransition tr = newevent.getTransition(i);
            int masterpath = ev.getTransition(i).getPath();
            tr.setPath(p.getMapping(masterpath));
        }
        newevent.setPathPermFromMaster(p);

        double t = this.evtime +
            (double)currentloop * this.looptime +
            (double)currententry * (this.looptime / (double)this.numentries);
        newevent.setT(t);

        return newevent;
    }

    public void resetPosition() {
        this.currentloop = 0;
        this.currentj = evjuggler;
        this.currenth = evhand;
        this.currententry = 0;
    }

    // Does this event have any transitions for the specified hand, after
    // symmetries are applied?
    public boolean hasJMLTransitionForHand(int jug, int han) {
        for (int i = 0; i < numentries; i++) {
            if (ea[jug-1][HandLink.index(han)][i] != null)
                return true;
        }
        return false;
    }

    // Does this event have any velocity-defining transitions (e.g., throws)
    // for the specified hand, after symmetries are applied?
    public boolean hasVDJMLTransitionForHand(int jug, int han) {
        int i;
        for (i = 0; i < numentries; i++)
            if (ea[jug-1][HandLink.index(han)][i] != null)
                break;
        if (i == numentries)
            return false;

        for (int j = 0; j < evtransitions; j++)
            if ((transitiontype[j] == JMLTransition.TRANS_THROW) ||
                (transitiontype[j] == JMLTransition.TRANS_SOFTCATCH))
                return true;
        return false;
    }

    public boolean hasJMLTransitionForPath(int path) {
        int[] cycle = loopperm.getCycle(path);

        for (int i = 0; i < numjugglers; i++) {
            for (int j = 0; j < numentries; j++) {
                for (int h = 0; h < 2; h++) {
                    for (int k = 0; k < evtransitions; k++) {
                        if (ea[i][h][j] != null) {
                            int newp = ea[i][h][j].getMapping(ev.getTransition(k).getPath());
                            for (int l = 0; l < cycle.length; l++) {
                                if (newp == cycle[l])
                                    return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean hasVDJMLTransitionForPath(int path) {
        int[] cycle = loopperm.getCycle(path);

        for (int k = 0; k < evtransitions; k++) {
            if ((transitiontype[k] == JMLTransition.TRANS_THROW) ||
                (transitiontype[k] == JMLTransition.TRANS_SOFTCATCH)) {
                for (int i = 0; i < numjugglers; i++) {
                    for (int j = 0; j < numentries; j++) {
                        for (int h = 0; h < 2; h++) {
                            if (ea[i][h][j] != null) {
                                int newp = ea[i][h][j].getMapping(ev.getTransition(k).getPath());
                                for (int l = 0; l < cycle.length; l++) {
                                    if (newp == cycle[l])
                                        return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }


    protected void calcarray() throws JuggleExceptionUser {
        this.numjugglers = pat.getNumberOfJugglers();
        this.numpaths = pat.getNumberOfPaths();
        this.looptime = pat.getLoopEndTime() - pat.getLoopStartTime();
        this.loopperm = pat.getPathPermutation();

        this.evjuggler = ev.getJuggler() - 1;
        this.evhand = HandLink.index(ev.getHand());
        this.evtransitions = ev.getNumberOfTransitions();
        this.evtime = ev.getT();

        int numsyms = pat.getNumberOfSymmetries() - 1;
        JMLSymmetry[] sym = new JMLSymmetry[numsyms];
        int[] symperiod = new int[numsyms];
        int[] deltaentries = new int[numsyms];
        Permutation invdelayperm = null;

        this.numentries = 1;
        for (int i = 0, j = 0; i <= numsyms; i++) {
            JMLSymmetry temp = pat.getSymmetry(i);
            switch (temp.getType()) {
                case JMLSymmetry.TYPE_DELAY:
                    invdelayperm = temp.getPathPerm().getInverse();
                    break;
                case JMLSymmetry.TYPE_SWITCH:
                    sym[j] = temp;
                    symperiod[j] = temp.getJugglerPerm().getOrder();
                    deltaentries[j] = 0;
                    j++;
                    break;
                case JMLSymmetry.TYPE_SWITCHDELAY:
                    sym[j] = temp;
                    symperiod[j] = temp.getJugglerPerm().getOrder();
                    this.numentries = Permutation.lcm(this.numentries, symperiod[j]);
                    deltaentries[j] = -1;
                    j++;
                    break;
            }
        }
        for (int i = 0; i < numsyms; i++)	// assume exactly one delay symmetry
            if (deltaentries[i] == -1)	// signals a switchdelay symmetry
                deltaentries[i] = this.numentries / symperiod[i];

        // System.out.println("numentries = "+numentries);

        this.ea = new Permutation[numjugglers][2][numentries];
        this.transitiontype = new int[evtransitions];

        Permutation idperm = new Permutation(numpaths, false);	// identity
        ev.setPathPermFromMaster(idperm);
        ea[evjuggler][evhand][0] = idperm;
        for (int i = 0; i < evtransitions; i++) {
            JMLTransition tr = ev.getTransition(i);
            transitiontype[i] = tr.getType();
        }


        boolean changed;
        do {
            // System.out.println("{"+ea[0][0][0][0]+","+ea[0][0][1][0]+"},{"+ea[0][1][0][0]+","+ea[0][1][1][0]+"}");

            changed = false;

            for (int i = 0; i < numsyms; i++) {
                for (int j = 0; j < numjugglers; j++) {
                    for (int k = 0; k < 2; k++) {
                        for (int l = 0; l < numentries; l++) {
                            // apply symmetry to event
                            int newj = sym[i].getJugglerPerm().getMapping(j+1);
                            if (newj != 0) {
                                int newk = (newj < 0 ? (1-k) : k);
                                if (newj < 0)
                                    newj = -newj;
                                newj--;

                                Permutation p = ea[j][k][l];
                                if (p != null) {
                                    p = sym[i].getPathPerm().apply(p);

                                    int newl = l + deltaentries[i];
                                    // map back into range
                                    if (newl >= numentries) {
                                        p = invdelayperm.apply(p);
                                        newl -= numentries;
                                    }
                                    // System.out.println("newj = "+newj+", newk = "+newk+", newl = "+newl);
                                    // check for consistency
                                    if (ea[newj][newk][newl] != null) {
                                        if (!p.equals(ea[newj][newk][newl]))
                                            throw new JuggleExceptionUser("Symmetries inconsistent");
                                    } else {
                                        ea[newj][newk][newl] = p;
                                        changed = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } while (changed);
        // System.out.println("**** done with event");

        /*		int[][][] ea = eventlist.getEventArray();
        for (int j = 0; j < numjugglers; j++) {
            for (int k = 0; k < 2; k++) {
                for (int l = 0; l < numentries; l++) {
                    System.out.println("ea["+(j+1)+","+k+","+l+"] = "+ea[j][k][l][0]);
                }
            }
        }*/
    }
}