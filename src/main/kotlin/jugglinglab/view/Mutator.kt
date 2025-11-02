//
// Mutator.kt
//
// This class is used by SelectionView to create random variations of a pattern.
// It does this by selecting from the following list of operations:
//
//   small mutations:
//   - change position of a randomly-selected event (but keep in-plane)
//   - change time of a randomly-selected event
//   - change overall timing of pattern (uniform speedup/slowdown)
//
//   moderate mutations:
//   - add a new event with no transitions to a hand, at a random time and
//     changed position
//   - remove a randomly-selected event with only holding transitions
//
//   large mutations (NOT IMPLEMENTED):
//   - add throw/catch pair
//   - delete a throw/catch pair (turn into a hold)
//   - move a catch/throw pair to the opposite hand
//
//   not for consideration:
//   - remove symmetries
//   - change throw types
//   - change positions of events out of plane
//   - change # of objects
//   - change # of jugglers
//   - change positions or angles of jugglers
//   - change props
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view

import jugglinglab.JugglingLab.guistrings
import jugglinglab.jml.HandLink
import jugglinglab.jml.JMLEvent
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.JMLTransition
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.constraints
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.*
import javax.swing.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Mutator {
    private var rate: Double = 0.0
    private lateinit var cb: MutableList<JCheckBox>
    private lateinit var sliderRate: JSlider
    var controlPanel: JPanel
        private set

    init {
        controlPanel = makeControlPanel()
    }

    // Return a mutated version of the input pattern.
    //
    // Important: This should not change the input pattern in any way.

    @Throws(JuggleExceptionInternal::class)
    fun mutatePattern(pat: JMLPattern): JMLPattern {
        val cdf = DoubleArray(5)
        var freqSum = 0.0
        for (i in 0..4) {
            freqSum += (if (cb[i].isSelected) mutationFreq[i] else 0.0)
            cdf[i] = freqSum
        }

        try {
            if (freqSum == 0.0) {
                return JMLPattern(pat)
            }

            this.rate = sliderRates[sliderRate.value]

            var mutant: JMLPattern?
            var tries = 0

            do {
                val clone = JMLPattern(pat)
                val r = freqSum * Math.random()
                tries++

                mutant = if (r < cdf[0]) {
                    mutateEventPosition(clone)
                } else if (r < cdf[1]) {
                    mutateEventTime(clone)
                } else if (r < cdf[2]) {
                    mutatePatternTiming(clone)
                } else if (r < cdf[3]) {
                    mutateAddEvent(clone)
                } else {
                    mutateRemoveEvent(clone)
                }
            } while (mutant == null && tries < 5)

            return mutant ?: JMLPattern(pat)
        } catch (jeu: JuggleExceptionUser) {
            // this shouldn't be able to happen, so treat it as an internal error
            throw JuggleExceptionInternal("Mutator: User error: " + jeu.message)
        }
    }

    //--------------------------------------------------------------------------
    // Mutation functions
    //--------------------------------------------------------------------------

    // Pick a random event and mutate its position.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateEventPosition(pat: JMLPattern): JMLPattern {
        val ev = pickMasterEvent(pat)
        var pos = ev.localCoordinate
        pos = pickNewPosition(ev.hand, rate * MUTATION_POSITION_CM, pos)
        ev.localCoordinate = pos
        pat.setNeedsLayout()
        return pat
    }

    // Pick a random event and mutate its time.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateEventTime(pat: JMLPattern): JMLPattern? {
        val ev = pickMasterEvent(pat)

        var evPrev = ev.previous
        while (evPrev != null) {
            if (evPrev.juggler == ev.juggler && evPrev.hand == ev.hand) {
                break
            }
            evPrev = evPrev.previous
        }
        val tmin =
            (if (evPrev == null)
                pat.loopStartTime
            else
                max(pat.loopStartTime, evPrev.t) + MUTATION_MIN_EVENT_DELTA_SEC)

        var evNext = ev.next
        while (evNext != null) {
            if (evNext.juggler == ev.juggler && evNext.hand == ev.hand) {
                break
            }
            evNext = evNext.next
        }
        val tmax =
            (if (evNext == null)
                pat.loopEndTime
            else
                min(pat.loopEndTime, evNext.t) - MUTATION_MIN_EVENT_DELTA_SEC)

        if (tmax <= tmin) {
            return null
        }

        // Sample t from two one-sided triangular distributions: Event time has
        // equal probability of going down or up.
        val r = Math.random()
        val tnow = ev.t
        val t = if (r < 0.5) {
            tmin + (tnow - tmin) * sqrt(2 * r)
        } else {
            tmax - (tmax - tnow) * sqrt(2 * (1 - r))
        }

        ev.t = t
        pat.setNeedsLayout()
        return pat
    }

    // Rescale overall pattern timing faster or slower.

    @Throws(JuggleExceptionUser::class)
    private fun mutatePatternTiming(pat: JMLPattern): JMLPattern {
        // sample new scale from two one-sided triangular distributions: Scale has
        // equal probability of going up or down
        val r = Math.random()
        val scalemin: Double = 1.0 / (1.0 + rate * MUTATION_TIMING_SCALE)
        val scalemax: Double = 1.0 + rate * MUTATION_TIMING_SCALE
        val scale = if (r < 0.5) {
            scalemin + (1.0 - scalemin) * sqrt(2 * r)
        } else {
            scalemax - (scalemax - 1.0) * sqrt(2 * (1 - r))
        }

        var ev = pat.eventList
        while (ev != null) {
            if (ev.isMaster) {
                ev.t = ev.t * scale
            }
            ev = ev.next
        }
        var pos = pat.positionList
        while (pos != null) {
            pos.t = pos.t * scale
            pos = pos.next
        }

        for (sym in pat.symmetries) {
            val delay = sym.delay
            if (delay > 0) {
                sym.delay = delay * scale
            }
        }

        pat.setNeedsLayout()
        return pat
    }

    // Add an event with no transitions to a randomly-selected juggler/hand, with
    // a mutated position.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateAddEvent(pat: JMLPattern): JMLPattern? {
        pat.layoutPattern()

        var ev: JMLEvent?
        var tmin: Double
        var tmax: Double
        var t: Double
        var juggler: Int
        var hand: Int
        var tries = 0

        do {
            juggler = 1 + (pat.numberOfJugglers * Math.random()).toInt()
            hand = if (Math.random() < 0.5) HandLink.LEFT_HAND else HandLink.RIGHT_HAND

            // Choose the time at which to add the event. We want to bias the
            // selection so that we tend to pick times not too close to other
            // events for that same juggler/hand. Find the bracketing events and
            // pick from a triangular distribution.
            tmin = pat.loopStartTime
            tmax = pat.loopEndTime
            t = tmin + (tmax - tmin) * Math.random()

            ev = pat.eventList
            while (ev != null) {
                if (ev.juggler == juggler && ev.hand == hand && ev.t >= t) {
                    break
                }
                ev = ev.next
            }
            if (ev == null) {
                return null
            }
            tmax = ev.t - MUTATION_MIN_EVENT_DELTA_SEC

            while (ev != null) {
                if (ev.juggler == juggler && ev.hand == hand && ev.t <= t) {
                    break
                }
                ev = ev.previous
            }
            if (ev == null) {
                return null
            }
            tmin = ev.t + MUTATION_MIN_EVENT_DELTA_SEC

            tries++
        } while (tmin > tmax && tries < 5)

        if (tries == 5) {
            return null
        }

        val r = Math.random()
        t = if (r < 0.5) {
            tmin + (tmax - tmin) * sqrt(0.5 * r)
        } else {
            tmax - (tmax - tmin) * sqrt(0.5 * (1 - r))
        }

        // want its time to be within this range since it's a master event
        while (t < pat.loopStartTime) {
            t += (pat.loopEndTime - pat.loopStartTime)
        }
        while (t > pat.loopEndTime) {
            t -= (pat.loopEndTime - pat.loopStartTime)
        }

        ev = JMLEvent()
        ev.setHand(juggler, hand)
        ev.t = t
        ev.masterEvent = null  // null signifies a master event

        // Now choose a spatial location for the event. Figure out where the
        // hand is currently and adjust it.
        var pos = Coordinate()
        pat.getHandCoordinate(juggler, hand, t, pos)
        pos = pat.convertGlobalToLocal(pos, juggler, t)
        pos = pickNewPosition(hand, rate * MUTATION_NEW_EVENT_POSITION_CM, pos)
        ev.localCoordinate = pos

        // Last step: add a "holding" transition for every path that the hand
        // is holding at the chosen time
        for (path in 1..pat.numberOfPaths) {
            if (pat.isHandHoldingPath(juggler, hand, t, path)) {
                val trans = JMLTransition(JMLTransition.TRANS_HOLDING, path, null, null)
                ev.addTransition(trans)
            }
        }

        pat.addEvent(ev)
        pat.setNeedsLayout()
        return pat
    }

    // Remove a randomly-selected master event with only holding transitions.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateRemoveEvent(pat: JMLPattern): JMLPattern? {
        // first count the number of such events
        var count = 0
        var ev = pat.eventList

        while (ev != null) {
            if (ev.isMaster) {
                var holdingOnly = true
                for (tr in ev.transitions) {
                    val type = tr.transType
                    if (type != JMLTransition.TRANS_NONE && type != JMLTransition.TRANS_HOLDING) {
                        holdingOnly = false
                        break
                    }
                }
                if (holdingOnly) {
                    ++count
                }
            }
            ev = ev.next
        }

        if (count == 0) {
            return null
        }

        // pick one to remove, then go back through event list and find it
        count = (count * Math.random()).toInt()

        ev = pat.eventList

        while (ev != null) {
            if (ev.isMaster) {
                var holdingOnly = true
                for (tr in ev.transitions) {
                    val type = tr.transType
                    if (type != JMLTransition.TRANS_NONE && type != JMLTransition.TRANS_HOLDING) {
                        holdingOnly = false
                        break
                    }
                }
                if (holdingOnly) {
                    if (count == 0) {
                        pat.removeEvent(ev)
                        pat.setNeedsLayout()
                        return pat
                    }
                    --count
                }
            }
            ev = ev.next
        }

        throw JuggleExceptionInternal("mutateRemoveEvent error")
    }

    //--------------------------------------------------------------------------
    // Helpers
    //--------------------------------------------------------------------------

    // Return a random master event from the pattern.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun pickMasterEvent(pat: JMLPattern): JMLEvent {
        pat.layoutPattern()

        val eventlist = pat.eventList
        var masterCount = 0

        var current = eventlist
        do {
            if (current!!.isMaster) {
                ++masterCount
            }
            current = current.next
        } while (current != null)

        // pick a number from 0 to (master_count - 1) inclusive
        var eventNum = (Math.random() * masterCount).toInt()

        current = eventlist
        do {
            if (current!!.isMaster) {
                if (eventNum == 0) {
                    return current
                }
                --eventNum
            }
            current = current.next
        } while (current != null)

        throw JuggleExceptionInternal("Mutator: pickEvent() failed")
    }

    private fun pickNewPosition(hand: Int, scaleDistance: Double, pos: Coordinate): Coordinate {
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
        var result: Coordinate
        var outsideBox: Boolean

        do {
            result = Coordinate(pos.x, pos.y, pos.z)
            // leave y component unchanged to maintain plane of juggling
            result.x += 2.0 * scaleDistance * (Math.random() - 0.5)
            result.z += 2.0 * scaleDistance * (Math.random() - 0.5)

            outsideBox = if (hand == HandLink.LEFT_HAND) {
                (result.x < -75 || result.x > 40 || result.z < -20 || result.z > 80)
            } else {
                (result.x < -40 || result.x > 75 || result.z < -20 || result.z > 80)
            }
        } while (outsideBox && Math.random() < 0.5)

        return result
    }

    private fun makeControlPanel(): JPanel {
        val controls = JPanel()
        val gb = GridBagLayout()
        controls.setLayout(gb)
        controls.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15))

        var lab = JLabel(guistrings.getString("Mutator_header1"))
        gb.setConstraints(
            lab, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 0, 10, 0))
        )
        controls.add(lab)

        this.cb = ArrayList<JCheckBox>(5)
        this.cb.add(JCheckBox(guistrings.getString("Mutator_type1"), true))
        gb.setConstraints(cb[0], constraints(GridBagConstraints.LINE_START, 0, 1, null))
        this.cb.add(JCheckBox(guistrings.getString("Mutator_type2"), true))
        gb.setConstraints(cb[1], constraints(GridBagConstraints.LINE_START, 0, 2, null))
        this.cb.add(JCheckBox(guistrings.getString("Mutator_type3"), true))
        gb.setConstraints(cb[2], constraints(GridBagConstraints.LINE_START, 0, 3, null))
        this.cb.add(JCheckBox(guistrings.getString("Mutator_type4"), true))
        gb.setConstraints(cb[3], constraints(GridBagConstraints.LINE_START, 0, 4, null))
        this.cb.add(JCheckBox(guistrings.getString("Mutator_type5"), true))
        gb.setConstraints(cb[4], constraints(GridBagConstraints.LINE_START, 0, 5, null))
        for (checkbox in cb) {
            controls.add(checkbox)
        }

        lab = JLabel(guistrings.getString("Mutator_header2"))
        gb.setConstraints(
            lab, constraints(GridBagConstraints.LINE_START, 0, 6, Insets(20, 0, 10, 0))
        )
        controls.add(lab)

        this.sliderRate = JSlider(SwingConstants.HORIZONTAL, 0, 6, 3)
        val gbc: GridBagConstraints = constraints(GridBagConstraints.LINE_START, 0, 7, null)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gb.setConstraints(sliderRate, gbc)
        sliderRate.setMajorTickSpacing(1)
        sliderRate.setPaintTicks(true)
        sliderRate.setSnapToTicks(true)
        val labels = Hashtable<Int?, JComponent?>()
        labels[0] = JLabel(guistrings.getString("Mutation_rate_low"))
        labels[3] = JLabel(guistrings.getString("Mutation_rate_medium"))
        labels[6] = JLabel(guistrings.getString("Mutation_rate_high"))
        sliderRate.setLabelTable(labels)
        sliderRate.setPaintLabels(true)
        controls.add(sliderRate)

        return controls
    }

    companion object {
        // baseline amounts that various mutations can adjust events
        const val MUTATION_POSITION_CM: Double = 40.0
        const val MUTATION_MIN_EVENT_DELTA_SEC: Double = 0.03
        const val MUTATION_TIMING_SCALE: Double = 0.5
        const val MUTATION_NEW_EVENT_POSITION_CM: Double = 40.0

        // baseline relative frequency of each mutation type
        val mutationFreq: DoubleArray = doubleArrayOf(0.4, 0.1, 0.1, 0.2, 0.2)

        // overall scale of adjustment, per mutation
        val sliderRates: DoubleArray = doubleArrayOf(0.2, 0.4, 0.7, 1.0, 1.3, 1.6, 2.0)
    }
}
