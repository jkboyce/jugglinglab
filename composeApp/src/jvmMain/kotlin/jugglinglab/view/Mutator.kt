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

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.jml.HandLink
import jugglinglab.jml.JMLEvent
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.JMLPattern.Companion.withScaledTime
import jugglinglab.jml.JMLTransition
import jugglinglab.jml.PatternBuilder
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlConstraints
import jugglinglab.util.getStringResource
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
                return JMLPattern.fromJMLPattern(pat)
            }

            this.rate = sliderRates[sliderRate.value]

            var mutant: JMLPattern?
            var tries = 0

            do {
                val clone = JMLPattern.fromJMLPattern(pat)
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

            return mutant ?: JMLPattern.fromJMLPattern(pat)
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
        val ev = pickPrimaryEvent(pat)
        var pos = ev.localCoordinate
        pos = pickNewPosition(ev.hand, rate * MUTATION_POSITION_CM, pos)

        val record = PatternBuilder.fromJMLPattern(pat)
        val index = record.events.indexOf(ev)
        record.events[index] = ev.copy(x = pos.x, y = pos.y, z = pos.z)
        return JMLPattern.fromPatternBuilder(record)
    }

    // Pick a random event and mutate its time.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateEventTime(pat: JMLPattern): JMLPattern? {
        val ev = pickPrimaryEvent(pat)
        val (evPrev, _) = pat.prevForHandFromEvent(ev)
        val tmin = max(pat.loopStartTime, evPrev.t) + MUTATION_MIN_EVENT_DELTA_SEC
        val (evNext, _) = pat.nextForHandFromEvent(ev)
        val tmax = min(pat.loopEndTime, evNext.t) - MUTATION_MIN_EVENT_DELTA_SEC

        if (tmax <= tmin) {
            return null
        }

        // Sample t from two one-sided triangular distributions: Event time has
        // equal probability of going down or up.
        val r = Math.random()
        val tnow = ev.t
        val tnew = if (r < 0.5) {
            tmin + (tnow - tmin) * sqrt(2 * r)
        } else {
            tmax - (tmax - tnow) * sqrt(2 * (1 - r))
        }

        val record = PatternBuilder.fromJMLPattern(pat)
        val index = record.events.indexOf(ev)
        record.events[index] = ev.copy(t = tnew)
        return JMLPattern.fromPatternBuilder(record)
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

        return pat.withScaledTime(scale)
    }

    // Add an event with no transitions to a randomly-selected juggler/hand, with
    // a mutated position.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateAddEvent(pat: JMLPattern): JMLPattern? {
        var tmin: Double
        var tmax: Double
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
            val targetT = tmin + (tmax - tmin) * Math.random()

            var ev = pat.eventSequence(startTime = targetT).first {
                it.event.juggler == juggler && it.event.hand == hand
            }.event
            tmax = ev.t - MUTATION_MIN_EVENT_DELTA_SEC

            ev = pat.prevForHandFromEvent(ev).event
            tmin = ev.t + MUTATION_MIN_EVENT_DELTA_SEC

            ++tries
        } while (tmin > tmax && tries < 5)

        if (tries == 5) {
            return null
        }

        val r = Math.random()
        var t = if (r < 0.5) {
            tmin + (tmax - tmin) * sqrt(0.5 * r)
        } else {
            tmax - (tmax - tmin) * sqrt(0.5 * (1 - r))
        }

        // want its time to be within this range since it's a primary event
        while (t < pat.loopStartTime) {
            t += (pat.loopEndTime - pat.loopStartTime)
        }
        while (t > pat.loopEndTime) {
            t -= (pat.loopEndTime - pat.loopStartTime)
        }

        // Now choose a spatial location for the event. Figure out where the
        // hand is currently and adjust it.
        var pos = Coordinate()
        pat.layout.getHandCoordinate(juggler, hand, t, pos)
        pos = pat.layout.convertGlobalToLocal(pos, juggler, t)
        pos = pickNewPosition(hand, rate * MUTATION_NEW_EVENT_POSITION_CM, pos)

        val newEvent = JMLEvent(
            x = pos.x,
            y = pos.y,
            z = pos.z,
            t = t,
            juggler = juggler,
            hand = hand,
            transitions = buildList {
                // Last step: add a "holding" transition for every path that the hand
                // is holding at the chosen time
                for (path in 1..pat.numberOfPaths) {
                    if (pat.layout.isHandHoldingPath(juggler, hand, t, path)) {
                        add(JMLTransition(type = JMLTransition.TRANS_HOLDING, path = path))
                    }
                }
            }
        )
        val record = PatternBuilder.fromJMLPattern(pat)
        record.events.add(newEvent)
        return JMLPattern.fromPatternBuilder(record)
    }

    // Remove a randomly-selected primary event with only holding transitions.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun mutateRemoveEvent(pat: JMLPattern): JMLPattern? {
        val evHolding = pat.events.filter {
            it.transitions.all { tr ->
                tr.type == JMLTransition.TRANS_HOLDING
            }
        }.toList()
        if (evHolding.isEmpty()) {
            return null
        }

        // pick one to remove
        val ev = evHolding[(evHolding.size * Math.random()).toInt()]

        val record = PatternBuilder.fromJMLPattern(pat)
        record.events.remove(ev)
        return JMLPattern.fromPatternBuilder(record)
    }

    //--------------------------------------------------------------------------
    // Helpers
    //--------------------------------------------------------------------------

    // Return a random primary event from the pattern.

    private fun pickPrimaryEvent(pat: JMLPattern): JMLEvent {
        return pat.events[(Math.random() * pat.events.size).toInt()]
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

        var lab = JLabel(getStringResource(Res.string.gui_mutator_header1))
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 0, 10, 0))
        )
        controls.add(lab)

        this.cb = ArrayList<JCheckBox>(5)
        this.cb.add(JCheckBox(getStringResource(Res.string.gui_mutator_type1), true))
        gb.setConstraints(cb[0], jlConstraints(GridBagConstraints.LINE_START, 0, 1, null))
        this.cb.add(JCheckBox(getStringResource(Res.string.gui_mutator_type2), true))
        gb.setConstraints(cb[1], jlConstraints(GridBagConstraints.LINE_START, 0, 2, null))
        this.cb.add(JCheckBox(getStringResource(Res.string.gui_mutator_type3), true))
        gb.setConstraints(cb[2], jlConstraints(GridBagConstraints.LINE_START, 0, 3, null))
        this.cb.add(JCheckBox(getStringResource(Res.string.gui_mutator_type4), true))
        gb.setConstraints(cb[3], jlConstraints(GridBagConstraints.LINE_START, 0, 4, null))
        this.cb.add(JCheckBox(getStringResource(Res.string.gui_mutator_type5), true))
        gb.setConstraints(cb[4], jlConstraints(GridBagConstraints.LINE_START, 0, 5, null))
        for (checkbox in cb) {
            controls.add(checkbox)
        }

        lab = JLabel(getStringResource(Res.string.gui_mutator_header2))
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_START, 0, 6, Insets(20, 0, 10, 0))
        )
        controls.add(lab)

        this.sliderRate = JSlider(SwingConstants.HORIZONTAL, 0, 6, 3)
        val gbc: GridBagConstraints = jlConstraints(GridBagConstraints.LINE_START, 0, 7, null)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gb.setConstraints(sliderRate, gbc)
        sliderRate.setMajorTickSpacing(1)
        sliderRate.setPaintTicks(true)
        sliderRate.setSnapToTicks(true)
        val labels = Hashtable<Int?, JComponent?>()
        labels[0] = JLabel(getStringResource(Res.string.gui_mutation_rate_low))
        labels[3] = JLabel(getStringResource(Res.string.gui_mutation_rate_medium))
        labels[6] = JLabel(getStringResource(Res.string.gui_mutation_rate_high))
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
