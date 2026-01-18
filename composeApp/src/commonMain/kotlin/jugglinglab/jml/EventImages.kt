//
// EventImages.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Permutation
import jugglinglab.util.Permutation.Companion.lcm

@Suppress("EmptyRange")
class EventImages(
    val pattern: JmlPattern,
    val primaryEvent: JmlEvent
) {
    private var numJugglers: Int = 0
    private var numPaths: Int = 0
    private var loopTime: Double = 0.0
    private var loopPerm: Permutation? = null

    private var evJuggler: Int = 0
    private var evHand: Int = 0 // hand is by index (0 or 1)
    private var evTransitionCount: Int = 0
    private var evTime: Double = 0.0

    private lateinit var ea: Array<Array<Array<Permutation?>>>

    private var numEntries: Int = 0
    private lateinit var transitionType: IntArray

    private var currentLoop: Int = 0
    private var currentJuggler: Int = 0
    private var currentHand: Int = 0
    private var currentEntry: Int = 0

    init {
        calcArray()
        resetPosition()
    }

    val next: EventImage
        get() {
            // move pointer to next in line
            do {
                if (++currentHand == 2) {
                    currentHand = 0
                    if (++currentJuggler == numJugglers) {
                        currentJuggler = 0
                        if (++currentEntry == numEntries) {
                            currentEntry = 0
                            currentLoop++
                        }
                    }
                }
            } while (ea[currentJuggler][currentHand][currentEntry] == null)

            return makeEventImage()
        }

    val previous: EventImage
        get() {
            // move point to previous in line
            do {
                if (currentHand-- == 0) {
                    currentHand = 1
                    if (currentJuggler-- == 0) {
                        currentJuggler = numJugglers - 1
                        if (currentEntry-- == 0) {
                            currentEntry = numEntries - 1
                            --currentLoop
                        }
                    }
                }
            } while (ea[currentJuggler][currentHand][currentEntry] == null)

            return makeEventImage()
        }

    // Note this returns the primary event when appropriate, not a copy.

    private fun makeEventImage(): EventImage {
        if (currentEntry == 0 && currentLoop == 0 && currentHand == evHand) {
            return EventImage(
                event = primaryEvent,
                primary = primaryEvent,
                pathPermFromPrimary = Permutation(numPaths)
            )
        }

        val pathPermFromPrimary = run {
            var ptemp = ea[currentJuggler][currentHand][currentEntry]
            var lp = loopPerm!!
            var pow = currentLoop
            if (pow < 0) {
                lp = lp.inverse
                pow = -pow
            }
            while (pow > 0) {
                ptemp = ptemp!!.composedWith(lp)
                --pow
            }
            ptemp!!
        }

        val newX = if (currentHand != evHand) -primaryEvent.x else primaryEvent.x
        val newT = (evTime
            + currentLoop.toDouble() * loopTime + currentEntry.toDouble() * (loopTime / numEntries.toDouble()))
        val newJuggler = currentJuggler + 1
        val newHand = if (currentHand == 0) JmlEvent.LEFT_HAND else JmlEvent.RIGHT_HAND

        val newEvent = primaryEvent.copy(
            x = newX,
            t = newT,
            juggler = newJuggler,
            hand = newHand,
            transitions = primaryEvent.transitions.map { tr ->
                tr.copy(path = pathPermFromPrimary.map(tr.path))
            }
        )
        return EventImage(
            event = newEvent,
            primary = primaryEvent,
            pathPermFromPrimary = pathPermFromPrimary
        )
    }

    fun resetPosition() {
        currentLoop = 0
        currentJuggler = evJuggler
        currentHand = evHand
        currentEntry = 0
    }

    // Determine if this event has any transitions for the specified hand, after
    // symmetries are applied.

    fun hasJmlTransitionForHand(jug: Int, han: Int): Boolean {
        for (i in 0..<numEntries) {
            if (ea[jug - 1][JmlEvent.handIndex(han)][i] != null) {
                return true
            }
        }
        return false
    }

    // Determine if this event has any velocity-defining transitions (e.g., throws)
    // for the specified hand, after symmetries are applied.

    fun hasVdJmlTransitionForHand(jug: Int, han: Int): Boolean {
        var i = 0
        while (i < numEntries) {
            if (ea[jug - 1][JmlEvent.handIndex(han)][i] != null) {
                break
            }
            ++i
        }
        if (i == numEntries) return false

        for (j in 0..<evTransitionCount) {
            if (transitionType[j] == JmlTransition.TRANS_THROW
                || transitionType[j] == JmlTransition.TRANS_SOFTCATCH
            ) {
                return true
            }
        }
        return false
    }

    fun hasJmlTransitionForPath(path: Int): Boolean {
        val cycle = loopPerm!!.cycleOf(path)

        for (k in 0..<evTransitionCount) {
            val transPath = primaryEvent.transitions[k].path
            for (i in 0..<numJugglers) {
                for (j in 0..<numEntries) {
                    for (h in 0..1) {
                        val permtemp = ea[i][h][j]
                        if (permtemp != null) {
                            if (permtemp.map(transPath) in cycle) return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun hasVdJmlTransitionForPath(path: Int): Boolean {
        val cycle = loopPerm!!.cycleOf(path)

        for (k in 0..<evTransitionCount) {
            if (transitionType[k] != JmlTransition.TRANS_THROW
                && transitionType[k] != JmlTransition.TRANS_SOFTCATCH
            ) {
                continue
            }
            val transPath = primaryEvent.transitions[k].path
            for (i in 0..<numJugglers) {
                for (j in 0..<numEntries) {
                    for (h in 0..1) {
                        val permtemp = ea[i][h][j]
                        if (permtemp != null) {
                            if (permtemp.map(transPath) in cycle) return true
                        }
                    }
                }
            }
        }
        return false
    }

    @Throws(JuggleExceptionUser::class)
    private fun calcArray() {
        numJugglers = pattern.numberOfJugglers
        numPaths = pattern.numberOfPaths
        loopTime = pattern.loopEndTime - pattern.loopStartTime
        loopPerm = pattern.pathPermutation

        evJuggler = primaryEvent.juggler - 1
        evHand = JmlEvent.handIndex(primaryEvent.hand)
        evTransitionCount = primaryEvent.transitions.size
        evTime = primaryEvent.t

        val numsyms = pattern.symmetries.size - 1
        val sym = arrayOfNulls<JmlSymmetry>(numsyms)
        val symperiod = IntArray(numsyms)
        val deltaentries = IntArray(numsyms)
        var invdelayperm: Permutation? = null

        numEntries = 1
        var index = 0
        for (temp in pattern.symmetries) {
            when (temp.type) {
                JmlSymmetry.TYPE_DELAY -> invdelayperm = temp.pathPerm.inverse
                JmlSymmetry.TYPE_SWITCH -> {
                    sym[index] = temp
                    symperiod[index] = temp.jugglerPerm.order
                    deltaentries[index] = 0
                    index++
                }

                JmlSymmetry.TYPE_SWITCHDELAY -> {
                    sym[index] = temp
                    symperiod[index] = temp.jugglerPerm.order
                    numEntries = lcm(numEntries, symperiod[index])
                    deltaentries[index] = -1
                    index++
                }
            }
        }
        for (i in 0..<numsyms) { // assume exactly one delay symmetry
            if (deltaentries[i] == -1) {
                // signals a switchdelay symmetry
                deltaentries[i] = numEntries / symperiod[i]
            }
        }

        ea = Array(numJugglers) { Array(2) { arrayOfNulls(numEntries) } }
        transitionType = IntArray(evTransitionCount)

        val idperm = Permutation(numPaths, reverses = false) // identity
        ea[evJuggler][evHand][0] = idperm
        for (i in 0..<evTransitionCount) {
            transitionType[i] = primaryEvent.transitions[i].type
        }

        var changed: Boolean
        do {
            changed = false

            for (i in 0..<numsyms) {
                for (j in 0..<numJugglers) {
                    for (k in 0..1) {
                        for (l in 0..<numEntries) {
                            // apply symmetry to event
                            var newj = sym[i]!!.jugglerPerm.map(j + 1)
                            if (newj == 0) {
                                continue
                            }

                            val newk = (if (newj < 0) (1 - k) else k)
                            if (newj < 0) {
                                newj = -newj
                            }
                            --newj

                            var p = ea[j][k][l] ?: continue

                            p = p.composedWith(sym[i]!!.pathPerm)

                            var newl = l + deltaentries[i]
                            // map back into range
                            if (newl >= numEntries) {
                                p = p.composedWith(invdelayperm)
                                newl -= numEntries
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
    }
}

data class EventImage(
    val event: JmlEvent,
    val primary: JmlEvent,
    val pathPermFromPrimary: Permutation
)
