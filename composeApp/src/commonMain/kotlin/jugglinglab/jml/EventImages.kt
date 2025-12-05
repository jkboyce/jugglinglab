//
// EventImages.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Permutation
import jugglinglab.util.Permutation.Companion.lcm

class EventImages(private var pat: JMLPattern, private var ev: JMLEvent) {
    private var numjugglers: Int = 0
    private var numpaths: Int = 0
    private var looptime: Double = 0.0
    private var loopperm: Permutation? = null

    private var evjuggler: Int = 0
    private var evhand: Int = 0
    private var evtransitions: Int = 0 // hand is by index (0 or 1)
    private var evtime: Double = 0.0

    private lateinit var ea: Array<Array<Array<Permutation?>>>

    private var numentries: Int = 0
    private lateinit var transitiontype: IntArray

    private var currentloop: Int = 0
    private var currentj: Int = 0
    private var currenth: Int = 0
    private var currententry: Int = 0

    init {
        calcarray()
        resetPosition()
        ev.delay = 0 // delay relative to master -> none for ev
        ev.delayunits = numentries
    }

    val next: JMLEvent
        get() {
            // move pointer to next in line
            do {
                if (++currenth == 2) {
                    currenth = 0
                    if (++currentj == this.numjugglers) {
                        currentj = 0
                        if (++currententry == this.numentries) {
                            currententry = 0
                            currentloop++
                        }
                    }
                }
            } while (ea[currentj][currenth][currententry] == null)

            return makeEvent()
        }

    val previous: JMLEvent
        get() {
            // move point to previous in line
            do {
                if (currenth-- == 0) {
                    currenth = 1
                    if (currentj-- == 0) {
                        currentj = numjugglers - 1
                        if (currententry-- == 0) {
                            currententry = numentries - 1
                            --currentloop
                        }
                    }
                }
            } while (ea[currentj][currenth][currententry] == null)

            return makeEvent()
        }

    private fun makeEvent(): JMLEvent {
        val pathPermFromMaster = run {
            var ptemp = ea[currentj][currenth][currententry]
            var lp = loopperm!!
            var pow = currentloop
            if (pow < 0) {
                lp = lp.inverse
                pow = -pow
            }
            while (pow > 0) {
                ptemp = ptemp!!.apply(lp)
                --pow
            }
            ptemp!!
        }

        val newX = if (currenth != evhand) -ev.x else ev.x
        val newT = (evtime
            + currentloop.toDouble() * looptime + currententry.toDouble() * (looptime / numentries.toDouble()))
        val newJuggler = currentj + 1
        val newHand = if (currenth == 0) HandLink.LEFT_HAND else HandLink.RIGHT_HAND

        val newEvent = ev.copy(
            x = newX,
            y = ev.y,
            z = ev.z,
            t = newT,
            juggler = newJuggler,
            hand = newHand,
            transitions = ev.transitions.map { tr ->
                tr.copy(path = pathPermFromMaster.getMapping(tr.path))
            }
        )

        newEvent.copyLayoutDataFrom(ev, currententry + numentries * currentloop, numentries)
        newEvent.pathPermFromMaster = pathPermFromMaster
        return newEvent
    }

    fun resetPosition() {
        currentloop = 0
        currentj = evjuggler
        currenth = evhand
        currententry = 0
    }

    // Determine if this event has any transitions for the specified hand, after
    // symmetries are applied.

    fun hasJMLTransitionForHand(jug: Int, han: Int): Boolean {
        for (i in 0..<numentries) {
            if (ea[jug - 1][HandLink.index(han)][i] != null) {
                return true
            }
        }
        return false
    }

    // Determine if this event has any velocity-defining transitions (e.g., throws)
    // for the specified hand, after symmetries are applied.

    fun hasVDJMLTransitionForHand(jug: Int, han: Int): Boolean {
        var i = 0
        while (i < numentries) {
            if (ea[jug - 1][HandLink.index(han)][i] != null) {
                break
            }
            ++i
        }
        if (i == numentries) return false

        for (j in 0..<evtransitions) {
            if (transitiontype[j] == JMLTransition.TRANS_THROW
                || transitiontype[j] == JMLTransition.TRANS_SOFTCATCH
            ) {
                return true
            }
        }
        return false
    }

    fun hasJMLTransitionForPath(path: Int): Boolean {
        val cycle = loopperm!!.getCycle(path)

        for (k in 0..<evtransitions) {
            val transPath = ev.transitions[k].path
            for (i in 0..<numjugglers) {
                for (j in 0..<numentries) {
                    for (h in 0..1) {
                        val permtemp = ea[i][h][j]
                        if (permtemp != null) {
                            if (permtemp.getMapping(transPath) in cycle) return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun hasVDJMLTransitionForPath(path: Int): Boolean {
        val cycle = loopperm!!.getCycle(path)

        for (k in 0..<evtransitions) {
            if (transitiontype[k] != JMLTransition.TRANS_THROW
                && transitiontype[k] != JMLTransition.TRANS_SOFTCATCH
            ) {
                continue
            }
            val transPath = ev.transitions[k].path
            for (i in 0..<numjugglers) {
                for (j in 0..<numentries) {
                    for (h in 0..1) {
                        val permtemp = ea[i][h][j]
                        if (permtemp != null) {
                            if (permtemp.getMapping(transPath) in cycle) return true
                        }
                    }
                }
            }
        }
        return false
    }

    @Throws(JuggleExceptionUser::class)
    private fun calcarray() {
        numjugglers = pat.numberOfJugglers
        numpaths = pat.numberOfPaths
        looptime = pat.loopEndTime - pat.loopStartTime
        loopperm = pat.pathPermutation

        evjuggler = ev.juggler - 1
        evhand = HandLink.index(ev.hand)
        evtransitions = ev.transitions.size
        evtime = ev.t

        val numsyms = pat.symmetries.size - 1
        val sym = arrayOfNulls<JMLSymmetry>(numsyms)
        val symperiod = IntArray(numsyms)
        val deltaentries = IntArray(numsyms)
        var invdelayperm: Permutation? = null

        numentries = 1
        var index = 0
        for (temp in pat.symmetries) {
            when (temp.type) {
                JMLSymmetry.TYPE_DELAY -> invdelayperm = temp.pathPerm!!.inverse
                JMLSymmetry.TYPE_SWITCH -> {
                    sym[index] = temp
                    symperiod[index] = temp.jugglerPerm!!.order
                    deltaentries[index] = 0
                    index++
                }

                JMLSymmetry.TYPE_SWITCHDELAY -> {
                    sym[index] = temp
                    symperiod[index] = temp.jugglerPerm!!.order
                    numentries = lcm(numentries, symperiod[index])
                    deltaentries[index] = -1
                    index++
                }
            }
        }
        for (i in 0..<numsyms) { // assume exactly one delay symmetry
            if (deltaentries[i] == -1) {
                // signals a switchdelay symmetry
                deltaentries[i] = numentries / symperiod[i]
            }
        }

        ea = Array(numjugglers) { Array(2) { arrayOfNulls(numentries) } }
        transitiontype = IntArray(evtransitions)

        val idperm = Permutation(numpaths, false) // identity
        ev.pathPermFromMaster = idperm
        ea[evjuggler][evhand][0] = idperm
        for (i in 0..<evtransitions) {
            transitiontype[i] = ev.transitions[i].type
        }

        var changed: Boolean
        do {
            changed = false

            for (i in 0..<numsyms) {
                for (j in 0..<numjugglers) {
                    for (k in 0..1) {
                        for (l in 0..<numentries) {
                            // apply symmetry to event
                            var newj = sym[i]!!.jugglerPerm!!.getMapping(j + 1)
                            if (newj == 0) {
                                continue
                            }

                            val newk = (if (newj < 0) (1 - k) else k)
                            if (newj < 0) {
                                newj = -newj
                            }
                            --newj

                            var p = ea[j][k][l] ?: continue

                            p = p.apply(sym[i]!!.pathPerm)

                            var newl = l + deltaentries[i]
                            // map back into range
                            if (newl >= numentries) {
                                p = p.apply(invdelayperm)
                                newl -= numentries
                            }
                            // System.out.println("newj = "+newj+", newk = "+newk+", newl = "+newl);
                            // check for consistency
                            if (ea[newj][newk][newl] != null) {
                                if (!p.equals(ea[newj][newk][newl])) {
                                    throw JuggleExceptionUser("Symmetries inconsistent")
                                }
                            } else {
                                ea[newj][newk][newl] = p
                                changed = true
                            }
                        }
                    }
                }
            }
        } while (changed)

        // System.out.println("**** done with event");

        /*      int[][][] ea = eventlist.getEventArray();
        for (int j = 0; j < numjugglers; j++) {
            for (int k = 0; k < 2; k++) {
                for (int l = 0; l < numentries; l++) {
                    System.out.println("ea["+(j+1)+","+k+","+l+"] = "+ea[j][k][l][0]);
                }
            }
        }*/
    }
}
