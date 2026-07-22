//
// SiteswapGenerator.kt
//
// This is the siteswap pattern generator component of Juggling Lab.
// It is mostly a port of an older program called J2, written in C.
//
//------------------------------------------------------------------------------
//   J version 2.3               by Jack Boyce        12/91
//                                  jboyce@tybalt.caltech.edu
//
//   This program finds all juggling siteswap patterns for a given
//   number of balls, maximum throw value, and pattern length.
//   A state graph approach is used in order to speed up computation.
//
//   It is a complete rewrite of an earlier program written in 11/90
//   which handled only non-multiplexed asynchronous solo siteswaps.
//   This version can generate multiplexed and nonmultiplexed tricks
//   for an arbitrary number of people, number of hands, and throwing
//   rhythm.  The built-in modes are asynchronous and synchronous solo
//   juggling, and two person asynchronous passing.
//
//   Include flag modified and the -simple flag added on 2/92
//   Extra check (for speed) added to gen_loops() on 01/19/98
//   Bug fix to find_start_end() on 02/18/99
//   Converted to a non-recursive algorithm on 06/13/26
//------------------------------------------------------------------------------
//
// Copyright 1991-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions", "EmptyRange", "LocalVariableName")

package org.jugglinglab.generator

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import org.jugglinglab.util.JuggleExceptionDone
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionInterrupted
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlCurrentTimeMillis
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlCharForDigit
import org.jugglinglab.util.jlToStringRounded
import org.jugglinglab.util.jlMaxMemoryBytes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.max

class SiteswapGenerator(arg: String) : Generator() {
    // generator configuration
    private val args = arg.split(' ', '\n').filter { it.isNotEmpty() }
    private val config = SiteswapGeneratorConfig(args)

    // working variables
    private lateinit var searchFrame: Array<SearchFrame>
    private lateinit var state: Array<Array<IntArray>>
    private var lTarget: Int = 0
    private lateinit var throwsLeft: Array<IntArray>
    private lateinit var holes: Array<IntArray>
    private lateinit var throwTo: Array<Array<IntArray>>
    private lateinit var throwValue: Array<Array<IntArray>>
    private lateinit var mpFilter: Array<Array<Array<IntArray>>>
    private lateinit var connections: BooleanArray
    private lateinit var startingSeq: String
    private lateinit var endingSeq: String
    private var maxNum: Int = -1 // maximum number of patterns to print
    private var patternsFound: Int = 0 // total patterns found during execution
    private var maxTime: Double = -1.0 // maximum number of seconds
    private var maxTimeMillis: Long = 0 // maximum number of milliseconds
    private var startTimeMillis: Long = 0 // start time of run, in milliseconds

    // other state variables
    private var target: GeneratorTarget? = null

    init {
        allocateWorkspace()
    }

    //--------------------------------------------------------------------------
    // Generator overrides
    //--------------------------------------------------------------------------

    override val notationName: String = "Siteswap"

    @Suppress("SimplifyBooleanWithConstants")
    @Throws(
        JuggleExceptionUser::class,
        JuggleExceptionInternal::class,
        kotlin.coroutines.cancellation.CancellationException::class
    )
    override suspend fun runGenerator(t: GeneratorTarget, maxNum: Int, maxTime: Double): Int {
        if (config.groundflag == 1 && config.groundStateLength > config.ht) {
            return 0
        }

        this.maxNum = maxNum
        patternsFound = 0
        this.maxTime = maxTime
        if (maxTime > 0 || Constants.DEBUG_GENERATOR_DETAILED) {
            maxTimeMillis = (1000.0 * maxTime).toLong()
            startTimeMillis = jlCurrentTimeMillis()
        }

        try {
            target = t

            var num = 0
            lTarget = config.lMin
            while (lTarget <= config.lMax) {
                num += findPatterns()
                lTarget += config.rhythmPeriod
            }

            if (config.numflag != 0) {
                if (num == 1) {
                    val message = jlGetStringResource(Res.string.gui_generator_patterns_1)
                    target?.addResult(message, null, null)
                } else {
                    val message = jlGetStringResource(Res.string.gui_generator_patterns_ne1, num)
                    target?.addResult(message, null, null)
                }
            }

            return num
        } finally {
            target?.completed()
            if (Constants.DEBUG_GENERATOR_SUMMARY) {
                val millis = jlCurrentTimeMillis() - startTimeMillis
                val secs = millis / 1000
                val ms = (millis % 1000).toString().padStart(3, '0')
                println("time elapsed: $secs.$ms s")
            }
        }
    }

    //--------------------------------------------------------------------------
    // Non-public methods below
    //--------------------------------------------------------------------------

    // Allocate working arrays for the states and throws in the pattern, plus
    // other incidental variables.

    private fun allocateWorkspace() {
        // First calculate total bytes of heap storage needed, ignoring overhead
        // of Array storage. Use Long values to avoid overflow.
        val searchFrameSize =
            config.lMax.toLong() * (config.hands.toLong() * config.maxOccupancy + 1) * SearchFrame.SIZE_BYTES
        val stateSize =
            (config.lMax.toLong() + 1) * config.hands * config.groundStateLength * Int.SIZE_BYTES
        val holesSize = config.hands.toLong() * (config.lMax.toLong() + config.ht) * Int.SIZE_BYTES
        val throwToSize =
            config.slotSize.toLong() * config.hands * config.maxOccupancy * Int.SIZE_BYTES
        val throwValueSize =
            config.slotSize.toLong() * config.hands * config.maxOccupancy * Int.SIZE_BYTES
        val mpFilterSize =
            if (config.mpflag != 0) ((config.lMax.toLong() + 1) * config.hands * config.slotSize * 3 * Int.SIZE_BYTES) else 0L
        val throwsLeftSize = config.lMax.toLong() * config.hands * Int.SIZE_BYTES
        val totalSize =
            searchFrameSize + stateSize + holesSize + throwToSize + throwValueSize + mpFilterSize + throwsLeftSize

        if (totalSize > jlMaxMemoryBytes) {
            val mem = jlToStringRounded(totalSize.toDouble() / (1024 * 1024), 0)
            val message = jlGetStringResource(Res.string.error_generator_memory, mem)
            throw JuggleExceptionUser(message)
        }

        try {
            // Array of SearchFrames for the iterative DFS algorithm
            // on each beat: at most (config.hands * config.maxOccupancy) throws,
            // with one frame each, plus a frame with status==2 to end the beat
            val maxDepth = config.lMax * (config.hands * config.maxOccupancy + 1)
            searchFrame = Array(maxDepth) { SearchFrame() }

            // last index below is not `config.ht` because of findStartEnd()
            state =
                Array(config.lMax + 1) { Array(config.hands) { IntArray(config.groundStateLength) } }
            holes =
                Array(config.hands) { IntArray(config.lMax + config.ht) }
            // first index below is not `config.lMax` because of findStartEnd()
            throwTo =
                Array(config.slotSize) { Array(config.hands) { IntArray(config.maxOccupancy) } }
            throwValue =
                Array(config.slotSize) { Array(config.hands) { IntArray(config.maxOccupancy) } }

            if (config.mpflag != 0) {
                mpFilter = Array(config.lMax + 1) {
                    Array(config.hands) {
                        Array(config.slotSize) {
                            IntArray(
                                3
                            )
                        }
                    }
                }
            }

            throwsLeft = Array(config.lMax) { IntArray(config.hands) }

            if (config.connectedPatternsFlag) {
                connections = BooleanArray(config.jugglers)
            }
        } catch (_: Throwable) {
            val message = jlGetStringResource(Res.string.error_generator_out_of_memory)
            throw JuggleExceptionUser(message)
        }
    }

    // Return the maximum number of balls allowed at position (hand, index)
    // in our state, on beat `beat`. This is dictated by our throwing rhythm and
    // the multiplexing settings.

    private fun rhythm(beat: Int, hand: Int, index: Int): Int {
        return config.multiplex *
                config.rhythmRepunit[hand][(beat + index) % config.rhythmPeriod]
    }

    // Generate all patterns.
    //
    // Do this by generating all possible starting states iteratively, then
    // calling findCycles() to find the loops for each one.

    @Throws(
        JuggleExceptionUser::class,
        JuggleExceptionInternal::class,
        kotlin.coroutines.cancellation.CancellationException::class
    )
    private suspend fun findPatterns(): Int {
        if (Constants.DEBUG_GENERATOR_SUMMARY) {
            println("starting findPatterns() with lTarget = $lTarget")
        }
        checkIfStopping()

        if (config.groundflag == 1) {
            // find only ground state patterns
            for (i in 0..<config.hands) {
                for (j in 0..<config.ht) {
                    state[0][i][j] = config.groundState[i][j]
                }
            }
            return processCompletedState()
        }

        // Initialize state[0] to all zeros
        for (i in 0..<config.hands) {
            for (j in 0..<config.ht) {
                state[0][i][j] = 0
            }
        }

        val numBalls = config.n
        if (numBalls == 0) {
            return processCompletedState()
        }

        val maxPos = config.ht * config.hands
        val pos = IntArray(numBalls)
        var b = 0
        var totalPatterns = 0

        // Initialize pos[0]
        pos[0] = 0

        var checkCounter = 0
        while (true) {
            if (++checkCounter > LOOPS_PER_CHECK) {
                checkCounter = 0
                checkIfStopping()
            }

            if (pos[b] < maxPos) {
                val p = pos[b]
                val i = p / config.hands
                val j = p % config.hands

                if (state[0][j][i] < rhythm(0, j, i)) {
                    state[0][j][i]++
                    if (i < lTarget || state[0][j][i] <= state[0][j][i - lTarget]) {
                        // This placement is valid. Note that a state with value
                        // at [j][i] greater than its value at [j][i - lTarget]
                        // cannot be part of a period `lTarget` pattern.
                        if (b == numBalls - 1) {
                            // All balls placed! Process state and add patterns
                            totalPatterns += processCompletedState()

                            // Now backtrack: undo the placement and try next position
                            state[0][j][i]--
                            pos[b]++
                        } else {
                            // Move to next ball level and start searching from
                            // the position of the previous ball
                            b++
                            pos[b] = pos[b - 1]
                        }
                    } else {
                        // Undo and try next position
                        state[0][j][i]--
                        pos[b]++
                    }
                } else {
                    // Cannot place ball here: try next position
                    pos[b]++
                }
            } else {
                // No more options for ball b. Backtrack!
                if (b == 0) {
                    // Done searching
                    break
                }
                b--
                // Undo the placement of the previous level (which is now the current level b)
                val prevP = pos[b]
                val prevI = prevP / config.hands
                val prevJ = prevP % config.hands
                state[0][prevJ][prevI]--
                // Try next position for this ball
                pos[b]++
            }
        }
        return totalPatterns
    }

    // Helper to initiate the search from a given starting state.
    //
    // Returns the number of patterns found.

    @Throws(
        JuggleExceptionUser::class,
        JuggleExceptionInternal::class,
        kotlin.coroutines.cancellation.CancellationException::class
    )
    private suspend fun processCompletedState(): Int {
        if (config.groundflag == 2 && areStatesEqual(state[0], config.groundState)) {
            // don't find ground state patterns
            return 0
        }
        if (Constants.DEBUG_GENERATOR_SUMMARY) {
            println("starting processCompletedState()")
        }

        // At this point our state is completed.  Set up the initial
        // multiplexing filter frame, if needed.
        for (i in 0..<config.hands) {
            var j = 0

            while (j < config.ht) {
                val k = state[0][i][j]

                if (config.mpflag != 0 && k == 0) {
                    mpFilter[0][i][j][TYPE] = MP_EMPTY
                } else {
                    if (config.mpflag != 0) {
                        mpFilter[0][i][j][VALUE] = j + 1
                        mpFilter[0][i][j][FROM] = i
                        mpFilter[0][i][j][TYPE] = MP_LOWER_BOUND
                    }

                    var m = j
                    var q: Int

                    while ((lTarget.let { m += it; m }) < config.ht) {
                        if ((state[0][i][m].also { q = it }) > k) {
                            return 0 // die (invalid state for this value of `l`)
                        }
                        if (config.mpflag != 0 && q != 0) {
                            if (q < k && j > config.holdthrow[i]) {
                                return 0 // different throws into same hand
                            }
                            mpFilter[0][i][j][VALUE] = m + 1 // new bound
                        }
                    }
                }
                ++j
            }

            if (config.mpflag != 0) {
                while (j < config.slotSize) {
                    mpFilter[0][i][j][TYPE] = MP_EMPTY // clear rest of slot
                    ++j
                }
            }
        }

        if (config.numflag != 2 && config.sequenceFlag) {
            findStartEnd()
        }

        if (Constants.DEBUG_GENERATOR_SUMMARY) {
            println("Starting findCycles() from state:")
            config.printState(state[0])
        }

        for (h in 0..<config.hands) {
            for (ti in 0..<lTarget + config.ht) {
                // calculate the number of throws we can make into a
                // particular (hand, target index) combo
                // maximum number of holes we have to fill...
                var numHoles: Int = if (ti < lTarget) {
                    config.multiplex * config.rhythmRepunit[h][ti % config.rhythmPeriod]
                } else {
                    state[0][h][ti - lTarget]
                }

                // ...less those filled by throws before beat 0
                if (ti < config.ht) {
                    numHoles -= state[0][h][ti]
                }

                holes[h][ti] = numHoles
            }
        }

        startBeat(0)
        return findCycles()  // find patterns thru state
    }

    // Generate cycles in the state graph, starting from state[0].
    //
    // Returns the number of cycles found.

    @Throws(
        JuggleExceptionUser::class,
        JuggleExceptionInternal::class,
        kotlin.coroutines.cancellation.CancellationException::class
    )
    private suspend fun findCycles(): Int {
        var sp = 0
        var frame = searchFrame[sp]
        frame.beat = 0
        frame.hand = -1
        frame.slot = -1
        frame.minThrow = 1
        frame.minHand = 0
        frame.throwValue = 1
        frame.targetHand = 0
        frame.num = 0
        frame.status = 0

        var latestReturnValue = 0
        var childReturned = false
        var checkCounter = 0

        while (sp >= 0) {
            if (++checkCounter > LOOPS_PER_CHECK) {
                checkCounter = 0
                checkIfStopping()
            }

            frame = searchFrame[sp]

            if (childReturned) {
                // do needed cleanup
                childReturned = false
                if (frame.status == 1) {
                    val ti = frame.beat + frame.throwValue
                    val k = frame.targetHand
                    holes[k][ti]++
                    frame.num += latestReturnValue
                    frame.targetHand++
                } else if (frame.status == 2) {
                    sp--
                    childReturned = true
                    continue
                }
            }

            if (frame.status == 0) {
                var h = 0
                while (h < config.hands && throwsLeft[frame.beat][h] == 0) {
                    h++
                }
                frame.hand = h

                if (h == config.hands) {
                    // Done assigning throws on this beat, for all hands
                    if (!areThrowsValid(frame.beat)) {
                        latestReturnValue = 0
                        sp--
                        childReturned = true
                        continue
                    }
                    if (config.mpflag != 0 && !isMultiplexingValid(frame.beat)) {
                        latestReturnValue = 0
                        sp--
                        childReturned = true
                        continue
                    }
                    calculateState(frame.beat + 1)
                    if (!isStateValid(frame.beat + 1)) {
                        latestReturnValue = 0
                        sp--
                        childReturned = true
                        continue
                    }

                    if (Constants.DEBUG_GENERATOR_DETAILED) {
                        val sb2 = StringBuilder()
                        sb2.append(".  ".repeat(frame.beat)) // Indent for debugging
                        outputBeat(frame.beat, sb2)
                        println(sb2)
                    }

                    if (frame.beat + 1 < lTarget) {
                        // continue to next beat
                        startBeat(frame.beat + 1)
                        frame.status = 2

                        sp++
                        val child = searchFrame[sp]
                        child.beat = frame.beat + 1
                        child.hand = -1
                        child.slot = -1
                        child.minThrow = 1
                        child.minHand = 0
                        child.throwValue = 1
                        child.targetHand = 0
                        child.num = 0
                        child.status = 0
                        continue
                    }

                    // at the target length
                    val patternString = createPatternString(lTarget)
                    val isValid = areStatesEqual(state[0], state[lTarget]) &&
                            isPatternValid(patternString)

                    val result = if (isValid) {
                        if (Constants.DEBUG_GENERATOR_DETAILED) {
                            println("got a pattern: $patternString")
                        }
                        if (config.numflag != 2) {
                            outputPattern(patternString)
                        }
                        patternsFound++
                        if (maxNum >= 0 && maxNum == patternsFound) {
                            val message =
                                jlGetStringResource(Res.string.gui_generator_spacelimit, maxNum)
                            throw JuggleExceptionDone(message)
                        }
                        1
                    } else {
                        0
                    }
                    latestReturnValue = result
                    sp--
                    childReturned = true
                    continue
                } else {
                    throwsLeft[frame.beat][h]--
                    frame.slot = throwsLeft[frame.beat][h]
                    frame.throwValue = frame.minThrow
                    frame.targetHand = frame.minHand
                    frame.status = 1
                }
            }

            if (frame.status == 1) {
                var foundChoice = false
                val beat = frame.beat
                val hand = frame.hand
                val slot = frame.slot

                while (frame.throwValue <= config.ht) {
                    val targetBeat = beat + frame.throwValue
                    while (frame.targetHand < config.hands) {
                        val targetHand = frame.targetHand
                        if (holes[targetHand][targetBeat] == 0) {
                            frame.targetHand++
                            continue
                        }

                        // Found a valid choice!
                        --holes[targetHand][targetBeat]
                        throwTo[beat][hand][slot] = targetHand
                        throwValue[beat][hand][slot] = frame.throwValue

                        val nextMinThrow = if (slot != 0) frame.throwValue else 1
                        val nextMinHand = if (slot != 0) targetHand else 0

                        foundChoice = true

                        sp++
                        val child = searchFrame[sp]
                        child.beat = beat
                        child.hand = -1
                        child.slot = -1
                        child.minThrow = nextMinThrow
                        child.minHand = nextMinHand
                        child.throwValue = nextMinThrow
                        child.targetHand = nextMinHand
                        child.num = 0
                        child.status = 0
                        break
                    }
                    if (foundChoice) {
                        break
                    }
                    frame.throwValue++
                    frame.targetHand = 0
                }

                if (!foundChoice) {
                    // Both loops completed! Clean up and return
                    throwsLeft[beat][hand]++
                    latestReturnValue = frame.num
                    sp--
                    childReturned = true
                }
            }
        }

        return latestReturnValue
    }

    // Check if the timer has expired, or the user has stopped the generator.

    private suspend fun checkIfStopping() {
        if (maxTime > 0) {
            if ((jlCurrentTimeMillis() - startTimeMillis) > maxTimeMillis) {
                val message =
                    jlGetStringResource(Res.string.gui_generator_timeout, maxTime.toInt())
                throw JuggleExceptionDone(message)
            }
        }
        if (!currentCoroutineContext().isActive) {
            if (Constants.DEBUG_GENERATOR_SUMMARY) {
                println("generator stopping in checkIfStopping()")
            }
            throw JuggleExceptionInterrupted()
        }
        kotlinx.coroutines.yield()
    }

    // Calculate the state based on previous beat's state and throws.

    private fun calculateState(beat: Int) {
        if (beat == 0) {
            return
        }

        for (j in 0..<config.hands) {  // shift state to the left
            for (k in 0..<config.ht - 1) {
                state[beat][j][k] = state[beat - 1][j][k + 1]
            }
            state[beat][j][config.ht - 1] = 0
        }

        for (j in 0..<config.hands) {  // add on the last throw(s)
            for (k in 0..<config.maxOccupancy) {
                val v = throwValue[beat - 1][j][k]
                if (v == 0) {
                    break
                }
                ++state[beat][throwTo[beat - 1][j][k]][v - 1]
            }
        }
    }

    // Check if the state is valid at a given position in the pattern.

    private fun isStateValid(beat: Int): Boolean {
        // Check if this is a valid state for a period-L pattern.
        // This check added 01/19/98.
        if (config.ht > lTarget) {
            for (j in 0..<config.hands) {
                for (k in 0..<lTarget) {
                    var o = k
                    while (o < config.ht - lTarget) {
                        if (state[beat][j][o + lTarget] > state[beat][j][o]) {
                            return false
                        }
                        o += lTarget
                    }
                }
            }
        }

        if (beat % config.rhythmPeriod == 0) {
            if (areStatesEqual(state[0], state[beat])) {
                if (config.fullflag != 0 && beat != lTarget) {
                    return false // intersection
                }
            } else {
                if (config.rotflag == 0 && compareStates(state[0], state[beat]) == 1) {
                    return false // bad rotation
                }
            }
        }

        if (config.fullflag == 2) {  // list only simple loops?
            for (j in 1..<beat) {
                if ((beat - j) % config.rhythmPeriod == 0) {
                    if (areStatesEqual(state[j], state[beat])) {
                        return false
                    }
                }
            }
        }

        return true
    }

    // Update the multiplexing filter with the throws at position `pos`, and
    // check whether the combination of throws is valid.
    //
    // The filter ensures that, other than holds, objects from only one source
    // are landing in any given hand (for example, a cluster of 3's).

    private fun isMultiplexingValid(beat: Int): Boolean {
        for (j in 0..<config.hands) { // shift filter frame to left
            for (k in 0..<(config.slotSize - 1)) {
                mpFilter[beat + 1][j][k][TYPE] = mpFilter[beat][j][k + 1][TYPE]
                mpFilter[beat + 1][j][k][FROM] = mpFilter[beat][j][k + 1][FROM]
                mpFilter[beat + 1][j][k][VALUE] = mpFilter[beat][j][k + 1][VALUE]
            }
            mpFilter[beat + 1][j][config.slotSize - 1][TYPE] = MP_EMPTY

            // empty slots shift in
            if (addThrowMPFilter(
                    mpFilter[beat + 1][j][lTarget - 1],
                    j,
                    mpFilter[beat][j][0][TYPE],
                    mpFilter[beat][j][0][VALUE],
                    mpFilter[beat][j][0][FROM]
                )
                != 0
            ) {
                return false
            }
        }

        for (j in 0..<config.hands) { // add on last throw
            for (k in 0..<config.maxOccupancy) {
                val m = throwValue[beat][j][k]
                if (m == 0) {
                    break
                }

                if (addThrowMPFilter(
                        mpFilter[beat + 1][throwTo[beat][j][k]][m - 1],
                        throwTo[beat][j][k],
                        MP_THROW,
                        m,
                        j
                    )
                    != 0
                ) {
                    return false
                }
            }
        }

        return true
    }

    // Initialize data structures to start filling in pattern at position `pos`.

    private fun startBeat(beat: Int) {
        for (i in 0..<config.hands) {
            throwsLeft[beat][i] = state[beat][i][0]
            for (j in 0..<config.maxOccupancy) {
                throwTo[beat][i][j] = i  // clear throw matrix
                throwValue[beat][i][j] = 0
            }
        }
    }

    // Check if the throws made on a given beat are valid.
    //
    // Test for clustered throws and a passing communication delay.

    private fun areThrowsValid(beat: Int): Boolean {
        // check #1: if multiplexing, look for clustered throws if disallowed
        if (!config.mpClusteredFlag) {
            for (i in 0..<config.hands) {
                if (rhythm(beat, i, 0) != 0) {
                    var j = 0
                    while (j < config.maxOccupancy && throwValue[beat][i][j] != 0) {
                        for (l in 0..<j) {
                            if (throwValue[beat][i][j] == throwValue[beat][i][l]
                                && throwTo[beat][i][j] == throwTo[beat][i][l]
                            ) {
                                return false
                            }
                        }
                        ++j
                    }
                }
            }
        }

        // check #2: if passing, look for an adequate communication delay
        if (config.jugglers > 1 && beat < config.delaytime) {
            // Count the number of balls being thrown on this beat, assuming no
            // multiplexing. Also check if leader is forcing others to multiplex
            // or make no throw.
            var ballsThrown = 0
            for (h in 0..<config.hands) {
                if (rhythm(beat, h, 0) != 0) {
                    ++ballsThrown
                    if (state[beat][h][0] != 1 && config.personNumber[h] != config.leaderPerson) {
                        return false
                    }
                }
            }
            check(ballsThrown <= config.hands)

            // figure out where the jugglers would throw objects on this beat
            // in the "base" ground state pattern
            val basePatternHand = IntArray(config.hands)
            val basePatternValue = IntArray(config.hands)
            var ballsLeft = config.n
            placeballs@ for (i in 0..<config.ht) {
                for (h in 0..<config.hands) {
                    if (rhythm(beat + 1, h, i) == 0)
                        continue
                    --ballsLeft
                    if (ballsLeft < ballsThrown) {
                        basePatternHand[ballsLeft] = h  // dest hand #
                        basePatternValue[ballsLeft] = i + 1  // dest value
                    }
                    if (ballsLeft == 0) {
                        break@placeballs
                    }
                }
            }
            if (ballsLeft != 0) {
                return false  // shouldn't happen, but die anyway
            }

            // check whether non-leader jugglers are continuing with their base
            // pattern throws
            for (h in 0..<config.hands) {
                if (state[beat][h][0] == 0 || config.personNumber[h] == config.leaderPerson)
                    continue

                var foundSpot = false
                for (b in 0..<ballsThrown) {
                    if (basePatternHand[b] == throwTo[beat][h][0] &&
                        basePatternValue[b] == throwValue[beat][h][0]
                    ) {
                        basePatternValue[b] = 0  // don't throw to spot again
                        foundSpot = true
                        break
                    }
                }
                if (!foundSpot) {
                    return false
                }
            }
        }

        return true
    }

    // Test if a completed pattern is valid.

    private suspend fun isPatternValid(patternString: String): Boolean {
        // check #1: test against exclusions.
        for (regex in config.exclude) {
            if (patternString.matches(regex)) {
                if (Constants.DEBUG_GENERATOR_DETAILED) {
                    println("   pattern invalid: matches exclusion")
                }
                return false
            }
        }

        // check #2: verify against inclusions.
        for (regex in config.include) {
            if (!patternString.matches(regex)) {
                if (Constants.DEBUG_GENERATOR_DETAILED) {
                    println("   pattern invalid: missing inclusion")
                }
                return false
            }
        }

        // check #3: look for '11' sequence.
        if (config.mode == SiteswapGeneratorConfig.ASYNC && config.lameFlag && config.maxOccupancy == 1) {
            for (i in 0..<(lTarget - 1)) {
                for (j in 0..<config.hands) {
                    if (throwValue[i][j][0] == 1 &&
                        config.personNumber[throwTo[i][j][0]] == config.personNumber[j] &&
                        throwValue[i + 1][j][0] == 1 &&
                        config.personNumber[throwTo[i + 1][j][0]] == config.personNumber[j]
                    ) {
                        if (Constants.DEBUG_GENERATOR_DETAILED) {
                            println("  pattern invalid: 11 sequence")
                        }
                        return false
                    }
                }
            }
        }

        // check #4: if pattern is composite, ensure we only print one rotation of it.
        // (Added 12/4/2002)
        if (config.fullflag == 0 && config.rotflag == 0) {
            for (i in 1..<lTarget) {
                if (i % config.rhythmPeriod == 0) { // can we compare states?
                    if (areStatesEqual(state[0], state[i])) {
                        if (compareRotations(0, i) < 0) {
                            if (Constants.DEBUG_GENERATOR_DETAILED) {
                                println("   pattern invalid: bad rotation")
                            }
                            return false
                        }
                    }
                }
            }
        }

        // check #5: if passing, test whether pattern is connected if enabled.
        if (config.jugglers > 1 && config.connectedPatternsFlag) {
            for (i in 0..<config.jugglers) {
                connections[i] = false
            }
            connections[0] = true

            var changed = true
            while (changed) {
                changed = false

                for (i in 0..<lTarget) {
                    for (j in 0..<config.hands) {
                        if (connections[config.personNumber[j] - 1]) {
                            continue
                        }
                        var k = 0
                        while (k < config.maxOccupancy && throwValue[i][j][k] > 0) {
                            val p = config.personNumber[throwTo[i][j][k]]

                            if (connections[p - 1]) {
                                connections[config.personNumber[j] - 1] = true
                                changed = true
                            }
                            ++k
                        }
                    }
                }
            }
            for (i in 0..<config.jugglers) {
                if (!connections[i]) {
                    if (Constants.DEBUG_GENERATOR_DETAILED) {
                        println("   pattern invalid: not connected")
                    }
                    return false
                }
            }
        }

        // check #6: See if there is a better permutation of jugglers.
        //
        // This algorithm is not guaranteed to eliminate all permuted duplicates,
        // but will do so in the vast majority of cases.
        if (config.jugglers > 1 && !config.jugglerPermutationsFlag) {
            val isDoneJugglerMAtBeat = BooleanArray(config.lMax)
            val isDoneJugglerMP1AtBeat = BooleanArray(config.lMax)

            loop@ for (m in 1..<config.jugglers) {
                // compare juggler m against juggler (m + 1), by calculating a
                // scoring function for each
                for (i in 0..<lTarget) {
                    // which beats have been compared so far
                    isDoneJugglerMAtBeat[i] = false
                    isDoneJugglerMP1AtBeat[i] = false
                }
                repeat(lTarget) {
                    var scorem = -1
                    var scoremp1 = -1
                    var maxm = 0
                    var maxmp1 = 0

                    for (i in 0..<lTarget) {
                        if (!isDoneJugglerMAtBeat[i]) {
                            var scoretemp = 0

                            for (j in 0..<config.hands) {
                                if (config.personNumber[j] != m) {
                                    continue
                                }
                                var k = 0
                                while (k < config.maxOccupancy && throwValue[i][j][k] > 0) {
                                    scoretemp += 4 * throwValue[i][j][k] * (2 * config.maxOccupancy) * (2 * config.maxOccupancy)
                                    if (throwTo[i][j][k] != j) {
                                        scoretemp += 2 * (2 * config.maxOccupancy)
                                        if (config.personNumber[throwTo[i][j][k]] != m) {
                                            scoretemp += 1
                                        }
                                    }
                                    ++k
                                }
                            }
                            if (scoretemp > scorem) {
                                scorem = scoretemp
                                maxm = i
                            }
                        }
                        if (!isDoneJugglerMP1AtBeat[i]) {
                            var scoretemp = 0
                            for (j in 0..<config.hands) {
                                if (config.personNumber[j] != (m + 1)) {
                                    continue
                                }
                                var k = 0
                                while (k < config.maxOccupancy && throwValue[i][j][k] > 0) {
                                    scoretemp += 4 * throwValue[i][j][k] * (2 * config.maxOccupancy) *
                                            (2 * config.maxOccupancy)
                                    if (throwTo[i][j][k] != j) {
                                        scoretemp += 2 * (2 * config.maxOccupancy)
                                        if (config.personNumber[throwTo[i][j][k]] != (m + 1)) {
                                            scoretemp += 1
                                        }
                                    }
                                    ++k
                                }
                            }
                            if (scoretemp > scoremp1) {
                                scoremp1 = scoretemp
                                maxmp1 = i
                            }
                        }
                    }

                    if (scoremp1 > scorem) {
                        if (Constants.DEBUG_GENERATOR_DETAILED) {
                            println("   pattern invalid: bad juggler permutation")
                        }
                        return false
                    }
                    if (scoremp1 < scorem) {
                        continue@loop  // go to the next pair of jugglers
                    }

                    isDoneJugglerMAtBeat[maxm] = true
                    isDoneJugglerMP1AtBeat[maxmp1] = true
                }
            }
        }

        // check #7: if passing, test whether pattern is symmetric if enabled.
        //
        // Example: jlab gen 6 4 3 -j 2 -f -se -sym -cp
        if (config.jugglers > 1 && config.symmetricPatternsFlag) {
            js@ for (j in 2..config.jugglers) {
                offsets@ for (offset in 0..<lTarget) {
                    // compare juggler `j` to juggler 1 with beat offset `offset`

                    for (i in 0..<lTarget) {
                        var hJuggler1 = 0
                        val index = (i + offset) % lTarget

                        for (h in 1..<config.hands) {
                            if (config.personNumber[h] != j) {
                                continue
                            }
                            for (k in 0..<config.maxOccupancy) {
                                val val1 = throwValue[i][hJuggler1][k]
                                val self1 = (config.personNumber[throwTo[i][hJuggler1][k]] == 1)
                                val same1 = (throwTo[i][hJuggler1][k] == hJuggler1)

                                val valJ = throwValue[index][h][k]
                                val selfJ = (config.personNumber[throwTo[index][h][k]] == j)
                                val sameJ = (throwTo[index][h][k] == h)

                                if (val1 == 0 && valJ == 0) {
                                    break
                                }
                                if (val1 != valJ || self1 != selfJ || same1 != sameJ) {
                                    continue@offsets
                                }
                            }
                            ++hJuggler1
                        }
                    }

                    continue@js  // offset is a match; go to next juggler
                }

                return false
            }
        }

        return true
    }

    // Compare two rotations of the same pattern.
    //
    // This method assumes the throws are comparable, i.e., that beat1 is
    // congruent to pos2 mod rhythm_period.

    @Suppress("SameParameterValue")
    private suspend fun compareRotations(beat1: Int, beat2: Int): Int {
        var i = 0
        while (i < lTarget) {
            checkIfStopping()
            val res = compareLoops((beat1 + i) % lTarget, (beat2 + i) % lTarget)
            if (res > 0) {
                return 1
            } else if (res < 0) {
                return -1
            }

            ++i
            while (i < lTarget) {
                if (areStatesEqual(state[beat1], state[(beat1 + i) % lTarget])) {
                    break
                }
                ++i
            }
        }
        return 0
    }

    // Compare two generated loops.

    private fun compareLoops(beat1: Int, beat2: Int): Int {
        var currentBeat1 = beat1
        var currentBeat2 = beat2
        val stateStart = state[beat1]
        var result = 0
        var i = 0

        // Rule 1:  The longer loop is always greater
        // Rule 2:  For loops of equal length, use throw-by-throw comparison
        // Rule 3:  Loops are equal only if the respective throws are identical
        while (true) {
            ++i

            if (currentBeat1 + 1 >= state.size || currentBeat2 + 1 >= state.size) {
                return result
            }

            if (result == 0) {
                result = compareThrows(currentBeat1, currentBeat2)
            }

            if (i % config.rhythmPeriod == 0) {
                val eq1 = areStatesEqual(state[currentBeat1 + 1], stateStart)
                val eq2 = areStatesEqual(state[currentBeat2 + 1], stateStart)

                if (eq1) {
                    if (eq2) {
                        return result
                    }
                    return -1
                }
                if (eq2) {
                    return 1
                }
            }

            ++currentBeat1
            ++currentBeat2
        }
    }

    // Compare two throws.
    //
    // Return 1 if the throw at beat1 is greater than the throw at beat2,
    // -1 if lesser, and 0 iff the throws are identical.
    //
    // This method assumes the throws are comparable, i.e., that beat1 is
    // congruent to beat2 mod rhythm_period.

    private fun compareThrows(beat1: Int, beat2: Int): Int {
        check(beat1 >= 0 && beat1 <= config.lMax)
        check(beat2 >= 0 && beat2 <= config.lMax)

        val value1 = throwValue[beat1]
        val to1 = throwTo[beat1]
        val value2 = throwValue[beat2]
        val to2 = throwTo[beat2]

        for (i in 0..<config.hands) {
            for (j in 0..<rhythm(beat1, i, 0)) {
                if (value1[i][j] > value2[i][j]) {
                    return 1
                } else if (value1[i][j] < value2[i][j]) {
                    return -1
                } else if (to1[i][j] > to2[i][j]) {
                    return 1
                } else if (to1[i][j] < to2[i][j]) {
                    return -1
                }
            }
        }

        return 0
    }

    // Compare two states.
    //
    // Return true iff two states are equal.

    private fun areStatesEqual(state1: Array<IntArray>, state2: Array<IntArray>): Boolean {
        for (i in 0..<config.hands) {
            if (!state1[i].contentEquals(state2[i])) {
                return false
            }
        }
        return true
    }

    // Return 1 if state1 > state2, -1 if state1 < state2, and 0 iff state1 and
    // state are equal.

    private fun compareStates(state1: Array<IntArray>, state2: Array<IntArray>): Int {
        var mo1 = 0
        var mo2 = 0
        for (i in 0..<config.hands) {
            for (j in 0..<config.ht) {
                if (state1[i][j] > mo1) {
                    mo1 = state1[i][j]
                }
                if (state2[i][j] > mo2) {
                    mo2 = state2[i][j]
                }
            }
        }

        if (mo1 > mo2) {
            return 1
        }
        if (mo1 < mo2) {
            return -1
        }

        for (j in (config.ht - 1) downTo 0) {
            for (i in (config.hands - 1) downTo 0) {
                mo1 = state1[i][j]
                mo2 = state2[i][j]
                if (mo1 > mo2) {
                    return 1
                }
                if (mo1 < mo2) {
                    return -1
                }
            }
        }

        return 0
    }

    // Output a single throw value to a StringBuilder.

    private fun outputThrowValue(value: Int, sb: StringBuilder) {
        if (value > 35) {
            sb.append('{').append(value).append('}')
        } else {
            sb.append(jlCharForDigit(value, 36).lowercaseChar())
        }
    }

    // Output the throws for juggler `juggler` (1-indexed) on beat `beat` to a
    // StringBuilder.

    @Suppress("AssignedValueIsNeverRead")
    private fun outputJugglerBeat(beat: Int, juggler: Int, sb: StringBuilder) {
        var xSpace = false
        val loHand = config.personNumber.indexOfFirst { it == juggler }
        var hiHand = loHand
        while (hiHand < config.hands && config.personNumber[hiHand] == juggler) {
            ++hiHand
        }

        val numHandsThrowing = (loHand..<hiHand).count { rhythm(beat, it, 0) != 0 }
        if (numHandsThrowing > 0) {
            var parens = false

            if (numHandsThrowing > 1) {
                sb.append('(')
                xSpace = false
                parens = true
            }

            for (j in loHand..<hiHand) {
                if (rhythm(beat, j, 0) == 0) {
                    continue  // hand isn't supposed to throw
                }

                val isMultiplex = if (config.maxOccupancy > 1 && throwValue[beat][j][1] > 0) {
                    sb.append('[')
                    xSpace = false
                    true
                } else false

                // loop over the throws coming out of this hand
                var gotThrow = false

                var k = 0
                while (k < config.maxOccupancy && throwValue[beat][j][k] > 0) {
                    gotThrow = true

                    if (throwValue[beat][j][k] == 33 && xSpace) {
                        sb.append(' ')
                    }

                    outputThrowValue(throwValue[beat][j][k], sb)
                    xSpace = true

                    if (config.hands > 1) {
                        // potential ambiguity about destination
                        val targetJuggler = config.personNumber[throwTo[beat][j][k]]

                        if (config.mode == SiteswapGeneratorConfig.SYNC) {
                            // print destination hand
                            var q = throwTo[beat][j][k] - 1
                            var destHand = 0
                            while (q >= 0 && config.personNumber[q] == targetJuggler) {
                                --q
                                ++destHand
                            }
                            if (destHand != (j - loHand)) {
                                sb.append('x')
                            }
                        }

                        if (targetJuggler != juggler) {
                            // print pass modifier and person number
                            sb.append('p')
                            if (config.jugglers > 2) {
                                sb.append(targetJuggler)
                            }
                        }
                    }

                    if (isMultiplex && config.jugglers > 1 && k != (config.maxOccupancy - 1) &&
                        throwValue[beat][j][k + 1] > 0
                    ) {
                        // another multiplexed throw in this group
                        sb.append('/')
                        xSpace = false
                    }
                    ++k
                }

                if (!gotThrow) {
                    sb.append('0')
                    xSpace = true
                }

                if (isMultiplex) {
                    sb.append(']')
                    xSpace = false
                }

                if (j < (hiHand - 1) && parens) {
                    // put comma between hands
                    sb.append(',')
                    xSpace = false
                }
            }
            if (parens) {
                sb.append(')')
                xSpace = false
            }
        }
    }

    // Output all throws for a given beat to a StringBuilder.

    private fun outputBeat(beat: Int, sb: StringBuilder) {
        val canThrow = (0..<config.hands).any { rhythm(beat, it, 0) != 0 }
        if (!canThrow) {
            return  // skip output for this beat
        }

        if (config.jugglers > 1) {
            sb.append('<')
        }

        for (i in 1..config.jugglers) {
            outputJugglerBeat(beat, i, sb)
            if (i < config.jugglers) {
                sb.append('|')
            }
        }

        if (config.jugglers > 1) {
            sb.append('>')
        }
    }

    // Return the string representation of the first `beats` beats of the
    // current pattern.

    private fun createPatternString(beats: Int): String {
        val sb = StringBuilder()
        if (config.groupByJuggler && config.jugglers > 1) {
            sb.append('<')
            for (juggler in 1..config.jugglers) {
                if (juggler > 1) {
                    sb.append('|')
                }
                var prevThrowStr = ""
                for (b in 0..<beats) {
                    val beatSB = StringBuilder()
                    outputJugglerBeat(b, juggler, beatSB)
                    val throwStr = beatSB.toString()
                    if (throwStr.isNotEmpty()) {
                        if (prevThrowStr.matches(Regex(".*p[0-9]*$")) &&
                            (throwStr[0].isDigit() || throwStr[0] == '{')
                        ) {
                            sb.append(' ')
                        }
                        sb.append(throwStr)
                        prevThrowStr = throwStr
                    }
                }
            }
            sb.append('>')
        } else {
            for (i in 0..<beats) {
                outputBeat(i, sb)
            }
        }
        return sb.toString()
    }

    // Output a completed pattern `pat` to the correct target.

    @Throws(JuggleExceptionInternal::class)
    private fun outputPattern(pat: String) {
        var isExcited = false
        val outputline = StringBuilder()
        val outputline2 = StringBuilder()

        if (config.groundflag != 1) {
            if (config.sequenceFlag) {
                if (config.mode == SiteswapGeneratorConfig.ASYNC) {
                    outputline.append(" ".repeat(max(0, config.n - startingSeq.length)))
                }
                outputline.append(startingSeq)
                outputline.append("  ")
            } else {
                isExcited = !areStatesEqual(config.groundState, state[0])
                if (isExcited) {
                    outputline.append("* ")
                } else {
                    outputline.append("  ")
                }
            }
        }

        outputline.append(pat)
        outputline2.append(pat)

        if (config.groundflag != 1) {
            if (config.sequenceFlag) {
                outputline.append("  ")
                outputline.append(endingSeq)
                // add proper number of trailing spaces too, so formatting is
                // aligned in RTL languages
                if (config.mode == SiteswapGeneratorConfig.ASYNC) {
                    outputline.append(" ".repeat(max(0, config.n - endingSeq.length)))
                }
            } else {
                if (isExcited) {
                    outputline.append(" *")
                } else {
                    outputline.append("  ")
                }
            }
        }

        target!!.addResult(
            outputline.toString(),
            "siteswap",
            outputline2.toString().trim { it <= ' ' })
    }

    // Add a throw to a multiplexing filter slot (part of the multiplexing
    // filter).
    //
    // Returns 1 if there is a collision, 0 otherwise.

    private fun addThrowMPFilter(
        destSlot: IntArray,
        slotHand: Int,
        type: Int,
        value: Int,
        from: Int
    ): Int {
        when (type) {
            MP_EMPTY -> return 0
            MP_LOWER_BOUND -> {
                if (destSlot[TYPE] == MP_EMPTY) {
                    destSlot[TYPE] = MP_LOWER_BOUND
                    destSlot[VALUE] = value
                    destSlot[FROM] = from
                }
                return 0
            }

            MP_THROW -> {
                if (from == slotHand && value == config.holdthrow[slotHand]) {
                    return 0 // throw is a hold, so ignore it
                }

                when (destSlot[TYPE]) {
                    MP_EMPTY -> {
                        destSlot[TYPE] = MP_THROW
                        destSlot[VALUE] = value
                        destSlot[FROM] = from
                        return 0
                    }

                    MP_LOWER_BOUND -> if (destSlot[VALUE] <= value ||
                        destSlot[VALUE] <= config.holdthrow[slotHand]
                    ) {
                        destSlot[TYPE] = MP_THROW
                        destSlot[VALUE] = value
                        destSlot[FROM] = from
                        return 0
                    }

                    MP_THROW -> if (destSlot[FROM] == from && destSlot[VALUE] == value) {
                        return 0 // throws from same place (cluster)
                    }
                }
            }
        }

        return 1
    }

    // Find valid starting and ending sequences for excited state patterns.
    // Note that these sequences are not unique.

    private fun findStartEnd() {
        // find the number of beats in starting sequence
        var startBeats = 0

        findstarting1@ while (true) {
            for (j in 0..<config.hands) {
                for (k in 0..<config.ht) {
                    state[1][j][k] = if ((k + startBeats) < config.groundStateLength) {
                        config.groundState[j][k + startBeats]
                    } else {
                        0
                    }
                    if (state[1][j][k] > state[0][j][k]) {
                        startBeats += config.rhythmPeriod
                        continue@findstarting1
                    }
                    state[1][j][k] = state[0][j][k] - state[1][j][k]
                }
            }
            break
        }

        for (i in 0..<startBeats) {
            for (j in 0..<config.hands) {
                for (k in 0..<config.maxOccupancy) {
                    throwValue[i][j][k] = 0
                    throwTo[i][j][k] = j
                }
                if (i >= config.groundStateLength || config.groundState[j][i] == 0) {
                    continue
                }
                findstarting2@ for (k in 0..<config.ht) {
                    for (m in 0..<config.hands) {
                        if (state[1][m][k] > 0) {
                            --state[1][m][k]
                            throwValue[i][j][0] = k + startBeats - i
                            throwTo[i][j][0] = m
                            break@findstarting2
                        }
                    }
                }
            }
        }

        // write starting sequence to buffer
        startingSeq = createPatternString(startBeats)

        // Construct an ending sequence. This time work forward to ground state.
        var endBeats = 0

        findending1@ while (true) {
            for (j in 0..<config.hands) {
                for (k in 0..<config.groundStateLength) {
                    // use state[1] as scratch
                    state[1][j][k] = if ((k + endBeats) < config.ht) {
                        state[0][j][k + endBeats]
                    } else {
                        0
                    }
                    if (state[1][j][k] > config.groundState[j][k]) {
                        endBeats += config.rhythmPeriod
                        continue@findending1
                    }
                    state[1][j][k] = config.groundState[j][k] - state[1][j][k]
                }
            }
            break
        }

        for (i in 0..<endBeats) {
            for (j in 0..<config.hands) {
                for (k in 0..<config.maxOccupancy) {
                    throwValue[i][j][k] = 0
                    throwTo[i][j][k] = j
                }
                if (i >= config.ht) {
                    continue
                }
                for (q in 0..<state[0][j][i]) {
                    findending2@ for (k in 0..<config.groundStateLength) {
                        for (m in 0..<config.hands) {
                            if (state[1][m][k] > 0) {
                                --state[1][m][k]
                                throwValue[i][j][q] = k + endBeats - i
                                throwTo[i][j][q] = m
                                break@findending2
                            }
                        }
                    }
                }
            }
        }

        endingSeq = createPatternString(endBeats)
    }

    //--------------------------------------------------------------------------
    // Helper type during search execution (depth first search)
    //--------------------------------------------------------------------------

    private class SearchFrame {
        // (beat, hand, multiplexing slot) we're assigning a throw to
        var beat: Int = 0
        var hand: Int = -1
        var slot: Int = -1

        // limits on values so that multiplexed throws are always generated in a
        // certain ordering
        var minThrow: Int = 0
        var minHand: Int = 0

        // throw value and target hand assigned for this (beat, hand, slot)
        var throwValue: Int = 0
        var targetHand: Int = 0

        // patterns (cycles) found so far by all "child" frames
        var num: Int = 0

        /**
         * Search status transition indicator:
         * - 0: Initial status on frame entry. Checks if all hands' throws have
         *      been assigned for the current beat.
         *      If so, processes the completed beat and transitions to status 2,
         *      then outputs a completed pattern or advances to the next beat.
         *      If not, transitions to status 1 to assign throws.
         * - 1: Assigning a throw for the current hand/slot. Iterates over possible
         *      throw values (`j`) and target hands (`k`).
         *      When a multiplexed throw child frame completes, execution returns
         *      here to backtrack and continue the loop.
         * - 2: Recursion into the next beat (`beat + 1`). Waiting for the next
         *      beat's frame to return, then propagate the result.
         */
        var status: Int = 0

        companion object {
            const val SIZE_BYTES = 9 * Int.SIZE_BYTES
        }
    }

    companion object {
        // types of multiplexing filter slots
        private const val MP_EMPTY = 0
        private const val MP_THROW = 1
        private const val MP_LOWER_BOUND = 2
        private const val TYPE = 0
        private const val FROM = 1
        private const val VALUE = 2

        // frequency of checking for timeout
        private const val LOOPS_PER_CHECK = 100
    }
}
