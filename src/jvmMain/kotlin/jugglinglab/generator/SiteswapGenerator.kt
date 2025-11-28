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
//------------------------------------------------------------------------------
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.generator

import jugglinglab.core.Constants
import jugglinglab.generated.resources.*
import jugglinglab.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.JPanel
import kotlin.math.max

class SiteswapGenerator : Generator() {
    // generator configuration
    private lateinit var config: SiteswapGeneratorConfig

    // working variables, initialized at runtime
    private lateinit var state: Array<Array<IntArray>>
    private var lTarget: Int = 0
    private lateinit var rhythm: Array<Array<IntArray>>
    private lateinit var throwsLeft: Array<IntArray>
    private lateinit var holes: Array<IntArray>
    private lateinit var throwTo: Array<Array<IntArray>>
    private lateinit var throwValue: Array<Array<IntArray>>
    private lateinit var mpFilter: Array<Array<Array<IntArray>>>
    private lateinit var connections: BooleanArray
    private lateinit var startingSeq: String
    private lateinit var endingSeq: String
    private var maxNum: Int = 0 // maximum number of patterns to print
    private var maxTime: Double = 0.0 // maximum number of seconds
    private var maxTimeMillis: Long = 0 // maximum number of milliseconds
    private var startTimeMillis: Long = 0 // start time of run, in milliseconds
    private var loopCounter: Int = 0 // gen_loop() counter for checking timeout

    // other state variables
    private var target: GeneratorTarget? = null
    private val control: SiteswapGeneratorControl by lazy { SiteswapGeneratorControl() }

    //--------------------------------------------------------------------------
    // Generator overrides
    //--------------------------------------------------------------------------

    override val notationName: String = "Siteswap"

    override val startupMessage: String = "Welcome to the J2 Siteswap Generator"

    override val generatorControl: JPanel
        get() = control

    override fun resetGeneratorControl() {
        control.resetControl()
    }

    @Throws(JuggleExceptionUser::class)
    override fun initGenerator() {
        initGenerator(control.params)
    }

    @Throws(JuggleExceptionUser::class)
    override fun initGenerator(args: Array<String>) {
        config = SiteswapGeneratorConfig(args)
        allocateWorkspace()
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun runGenerator(t: GeneratorTarget): Int {
        return runGenerator(t, -1, -1.0)  // no limits
    }

    @Suppress("SimplifyBooleanWithConstants")
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun runGenerator(t: GeneratorTarget, maxNum: Int, secs: Double): Int {
        if (config.groundflag == 1 && config.groundStateLength > config.ht) {
            return 0
        }

        this.maxNum = maxNum
        maxTime = secs
        if (maxTime > 0 || Constants.DEBUG_GENERATOR) {
            maxTimeMillis = (1000.0 * secs).toLong()
            startTimeMillis = System.currentTimeMillis()
            loopCounter = 0
        }

        try {
            target = t

            var num = 0
            lTarget = config.lMin
            while (lTarget <= config.lMax) {
                num += findPatterns(0, 0, 0)
                lTarget += config.rhythmPeriod
            }

            if (config.numflag != 0) {
                if (num == 1) {
                    target!!.setStatus(getStringResource(Res.string.gui_generator_patterns_1))
                } else {
                    val message = getStringResource(Res.string.gui_generator_patterns_ne1, num)
                    target!!.setStatus(message)
                }
            }

            return num
        } finally {
            if (Constants.DEBUG_GENERATOR) {
                val millis = System.currentTimeMillis() - startTimeMillis
                System.out.printf("time elapsed: %d.%03d s%n", millis / 1000, millis % 1000)
            }
        }
    }

    //--------------------------------------------------------------------------
    // Non-public methods below
    //--------------------------------------------------------------------------

    // Allocate space for the states, rhythms, and throws in the pattern, plus
    // other incidental variables.

    private fun allocateWorkspace() {
        // last index below is not `ht` because of findStartEnd()
        state =
            Array(config.lMax + 1) { Array(config.hands) { IntArray(config.groundStateLength) } }
        holes =
            Array(config.hands) { IntArray(config.lMax + config.ht) }
        // first index below is not `l` because of findStartEnd()
        throwTo =
            Array(config.slotSize) { Array(config.hands) { IntArray(config.maxOccupancy) } }
        throwValue =
            Array(config.slotSize) { Array(config.hands) { IntArray(config.maxOccupancy) } }

        rhythm = Array(config.slotSize + 1) { Array(config.hands) { IntArray(config.ht) } }
        for (i in 0..<(config.slotSize + 1)) {
            for (j in 0..<config.hands) {
                for (k in 0..<config.ht) {
                    rhythm[i][j][k] = config.multiplex *
                        config.rhythmRepunit[j][(k + i) % config.rhythmPeriod]
                }
            }
        }

        if (config.mpflag != 0) {
            // space for filter variables
            mpFilter =
                Array(config.lMax + 1) { Array(config.hands) { Array(config.slotSize) { IntArray(3) } } }
        }

        throwsLeft = Array(config.lMax) { IntArray(config.hands) }

        if (config.connectedPatternsFlag) {
            connections = BooleanArray(config.jugglers)
        }
    }

    // Generate all patterns.
    //
    // Do this by generating all possible starting states recursively, then
    // calling findCycles() to find the loops for each one.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun findPatterns(ballsPlaced: Int, minValue: Int, minTo: Int): Int {
        if (Thread.interrupted()) {
            throw JuggleExceptionInterrupted()
        }

        // check if we're done making the state
        if (ballsPlaced == config.n || config.groundflag == 1) {
            if (config.groundflag == 1) {
                // find only ground state patterns
                for (i in 0..<config.hands) {
                    for (j in 0..<config.ht) {
                        state[0][i][j] = config.groundState[i][j]
                    }
                }
            } else if (config.groundflag == 2 && compareStates(state[0], config.groundState) == 0) {
                // don't find ground state patterns
                return 0
            }

            // At this point our state is completed.  Check to see if it's
            // valid. (Position X must be at least as large as position X+L,
            // where L = pattern length.) Also set up the initial multiplexing
            // filter frame, if needed.
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

            if (Constants.DEBUG_GENERATOR) {
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
            return findCycles(0, 1, 0, StringBuilder())  // find patterns thru state
        }

        if (ballsPlaced == 0) {  // startup, clear state
            for (i in 0..<config.hands) {
                for (j in 0..<config.ht) {
                    state[0][i][j] = 0
                }
            }
        }

        var num = 0

        var j = minTo // ensures each state is generated only once
        for (i in minValue..<config.ht) {
            while (j < config.hands) {
                if (state[0][j][i] < rhythm[0][j][i]) {
                    state[0][j][i] = state[0][j][i] + 1
                    if (i < lTarget || state[0][j][i] <= state[0][j][i - lTarget]) {
                        num += findPatterns(ballsPlaced + 1, i, j) // next ball
                    }
                    state[0][j][i] = state[0][j][i] - 1
                }
                ++j
            }
            j = 0
        }

        return num
    }

    // Generate cycles in the state graph, starting from some given state.
    //
    // Inputs:
    // pos: Int              // beat number in pattern that we're constructing
    // min_throw: Int        // lowest we can throw this time
    // min_hand: Int         // lowest hand we can throw to this time
    // sb: StringBuilder     // for accumulating string representation as we go
    //
    // Returns the number of cycles found.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun findCycles(beat: Int, minThrow: Int, minHand: Int, sb: StringBuilder): Int {
        if (Thread.interrupted()) {
            throw JuggleExceptionInterrupted()
        }
        if (maxTime > 0) {
            if (loopCounter++ > LOOP_COUNTER_MAX) {
                loopCounter = 0
                if ((System.currentTimeMillis() - startTimeMillis) > maxTimeMillis) {
                    val message = getStringResource(Res.string.gui_generator_timeout, maxTime.toInt())
                    throw JuggleExceptionDone(message)
                }
            }
        }

        // invariant: always reset `sb` to its original length when we return
        // from this function
        val originalLength = sb.length

        var h = 0
        while (throwsLeft[beat][h] == 0) {
            ++h
            if (h < config.hands) {
                continue
            }

            // done assigning throws on this beat, for all hands
            outputBeat(beat, sb)

            // conditions that may cause search to backtrack
            if (!areThrowsValid(beat, sb.toString())) {
                sb.setLength(originalLength)
                return 0
            }
            if (config.mpflag != 0 && !isMultiplexingValid(beat)) {
                sb.setLength(originalLength)
                return 0
            }
            calculateState(beat + 1)
            if (!isStateValid(beat + 1)) {
                sb.setLength(originalLength)
                return 0
            }

            if (Constants.DEBUG_GENERATOR) {
                val sb2 = StringBuilder()
                sb2.append(".  ".repeat(beat)) // Indent for debugging
                sb2.append(sb.substring(originalLength))
                println(sb2)
            }

            if (beat + 1 < lTarget) {
                // continue recursively to next beat
                startBeat(beat + 1)
                val patCount = findCycles(beat + 1, 1, 0, sb)
                sb.setLength(originalLength)
                return patCount
            }

            // at the target length
            val isValid =
                (compareStates(state[0], state[lTarget]) == 0 && isPatternValid(sb.toString()))

            return if (isValid) {
                if (Constants.DEBUG_GENERATOR) {
                    println("got a pattern: $sb")
                }
                if (config.numflag != 2) {
                    outputPattern(sb.toString())
                }
                sb.setLength(originalLength)
                1
            } else {
                sb.setLength(originalLength)
                0
            }
        }

        // have a throw to assign, iterate over possibilities
        --throwsLeft[beat][h]

        val slot = throwsLeft[beat][h]
        var k = minHand
        var num = 0

        for (j in minThrow..config.ht) {
            val ti = beat + j  // target beat for throw

            while (k < config.hands) {
                if (holes[k][ti] == 0) {
                    // no space at target position
                    ++k
                    continue
                }

                --holes[k][ti]
                throwTo[beat][h][slot] = k
                throwValue[beat][h][slot] = j
                num += if (slot != 0) {
                    findCycles(beat, j, k, sb)  // enforces ordering on multiplexed throws
                } else {
                    findCycles(beat, 1, 0, sb)
                }
                ++holes[k][ti]

                if (maxNum in 0..num) {
                    val message = getStringResource(Res.string.gui_generator_spacelimit, maxNum)
                    throw JuggleExceptionDone(message)
                }
                ++k
            }
            k = 0
        }

        ++throwsLeft[beat][h]

        sb.setLength(originalLength)
        return num
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
            val cs = compareStates(state[0], state[beat])

            if (config.fullflag != 0 && beat != lTarget && cs == 0) {
                return false // intersection
            }
            if (config.rotflag == 0 && cs == 1) {
                return false // bad rotation
            }
        }

        if (config.fullflag == 2) {  // list only simple loops?
            for (j in 1..<beat) {
                if ((beat - j) % config.rhythmPeriod == 0) {
                    if (compareStates(state[j], state[beat]) == 0) {
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
    // Test for excluded throws and a passing communication delay, as well as
    // a custom filter (if in CUSTOM mode).

    private fun areThrowsValid(beat: Int, patternString: String): Boolean {
        // check #1: test against exclusions
        for (regex in config.exclude) {
            if (Constants.DEBUG_GENERATOR) {
                println(
                    "test exclusions for string $patternString = ${
                        regex.matcher(patternString).matches()
                    }"
                )
            }
            if (regex.matcher(patternString).matches()) {
                return false
            }
        }

        // check #2: if multiplexing, look for clustered throws if disallowed
        if (!config.mpClusteredFlag) {
            for (i in 0..<config.hands) {
                if (rhythm[beat][i][0] != 0) {
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

        // check #3: if passing, look for an adequate communication delay
        if (config.jugglers > 1 && beat < config.delaytime) {
            // Count the number of balls being thrown on this beat, assuming no
            // multiplexing. Also check if leader is forcing others to multiplex
            // or make no throw.
            var ballsThrown = 0
            for (h in 0..<config.hands) {
                if (rhythm[beat][h][0] != 0) {
                    ++ballsThrown
                    if (state[beat][h][0] != 1 && config.personNumber[h] != config.leaderPerson) {
                        return false
                    }
                }
            }
            assert(ballsThrown <= config.hands)

            // figure out where the jugglers would throw objects on this beat
            // in the "base" ground state pattern
            val basePatternHand = IntArray(config.hands)
            val basePatternValue = IntArray(config.hands)
            var ballsLeft = config.n
            placeballs@ for (i in 0..<config.ht) {
                for (h in 0..<config.hands) {
                    if (rhythm[beat + 1][h][i] == 0)
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

    private fun isPatternValid(patternString: String): Boolean {
        // check #1: verify against inclusions.
        for (regex in config.include) {
            if (!regex.matcher(patternString).matches()) {
                if (Constants.DEBUG_GENERATOR) {
                    println("   pattern invalid: missing inclusion")
                }
                return false
            }
        }

        // check #2: look for '11' sequence.
        if (config.mode == SiteswapGeneratorConfig.ASYNC && config.lameFlag && config.maxOccupancy == 1) {
            for (i in 0..<(lTarget - 1)) {
                for (j in 0..<config.hands) {
                    if (throwValue[i][j][0] == 1 &&
                        config.personNumber[throwTo[i][j][0]] == config.personNumber[j] &&
                        throwValue[i + 1][j][0] == 1 &&
                        config.personNumber[throwTo[i + 1][j][0]] == config.personNumber[j]
                    ) {
                        if (Constants.DEBUG_GENERATOR) {
                            println("  pattern invalid: 11 sequence")
                        }
                        return false
                    }
                }
            }
        }

        // check #3: if pattern is composite, ensure we only print one rotation of it.
        // (Added 12/4/2002)
        if (config.fullflag == 0 && config.rotflag == 0) {
            for (i in 1..<lTarget) {
                if (i % config.rhythmPeriod == 0) { // can we compare states?
                    if (compareStates(state[0], state[i]) == 0) {
                        if (compareRotations(0, i) < 0) {
                            if (Constants.DEBUG_GENERATOR) {
                                println("   pattern invalid: bad rotation")
                            }
                            return false
                        }
                    }
                }
            }
        }

        // check #4: if passing, test whether pattern is connected if enabled.
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
                    if (Constants.DEBUG_GENERATOR) {
                        println("   pattern invalid: not connected")
                    }
                    return false
                }
            }
        }

        // check #5: See if there is a better permutation of jugglers.
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
                        if (Constants.DEBUG_GENERATOR) {
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

        // check #6: if passing, test whether pattern is symmetric if enabled.
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
    private fun compareRotations(beat1: Int, beat2: Int): Int {
        var i = 0
        while (i < lTarget) {
            val res = compareLoops((beat1 + i) % lTarget, (beat2 + i) % lTarget)
            if (res > 0) {
                return 1
            } else if (res < 0) {
                return -1
            }

            ++i
            while (i < lTarget) {
                if (compareStates(state[beat1], state[(beat1 + i) % lTarget]) == 0) {
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

            if (result == 0) {
                result = compareThrows(currentBeat1, currentBeat2)
            }

            if (i % config.rhythmPeriod == 0) {
                val cs1 = compareStates(state[currentBeat1 + 1], stateStart)
                val cs2 = compareStates(state[currentBeat2 + 1], stateStart)

                if (cs1 == 0) {
                    if (cs2 == 0) {
                        return result
                    }
                    return -1
                }
                if (cs2 == 0) {
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
    // This method assumes the throws are comparable, i.e., that beat1 is congruent
    // to beat2 mod rhythm_period.

    private fun compareThrows(beat1: Int, beat2: Int): Int {
        val value1 = throwValue[beat1]
        val to1 = throwTo[beat1]
        val value2 = throwValue[beat2]
        val to2 = throwTo[beat2]
        val rhy = rhythm[beat1] // same as beat2 since throws comparable

        for (i in 0..<config.hands) {
            for (j in 0..<rhy[i][0]) {
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
    // Return 1 if state1 > state2, -1 if state1 < state2, and 0 iff state1
    // and state2 are identical.

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
            sb.append(Character.forDigit(value, 36).lowercaseChar())
        }
    }

    // Output the throws for a given beat to a StringBuilder.

    private fun outputBeat(beat: Int, sb: StringBuilder) {
        val canThrow = rhythm[beat].any { it[0] != 0 }
        if (!canThrow) {
            return  // skip output for this beat
        }

        var xSpace = sb.isNotEmpty()  // for printing 'x'-valued throws

        if (config.jugglers > 1) {
            sb.append('<')
            xSpace = false
        }

        for (i in 1..config.jugglers) {
            // find hand numbers [loHand, hiHand) corresponding to juggler `i`
            val loHand = config.personNumber.indexOfFirst { it == i }
            var hiHand = loHand
            while (hiHand < config.hands && config.personNumber[hiHand] == i) {
                ++hiHand
            }

            val numHandsThrowing = (loHand..<hiHand).count { rhythm[beat][it][0] != 0 }
            if (numHandsThrowing > 0) {
                var parens = false

                if (numHandsThrowing > 1) {
                    sb.append('(')
                    xSpace = false
                    parens = true
                }

                for (j in loHand..<hiHand) {
                    if (rhythm[beat][j][0] == 0) {
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

                            if (targetJuggler != i) {
                                // print pass modifier and person number
                                sb.append('p')
                                if (config.jugglers > 2) {
                                    sb.append(targetJuggler)
                                }
                            }
                            /*
                              // destination person has 1 hand, don't print
                              if ((ch != 'a') || ((q < (config.hands - 2)) &&
                                                  (person_number[q + 2] == m)))
                              out[outpos++] = ch;             // print it
                              */
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
            if (i < config.jugglers) {
                // another person throwing next
                sb.append('|')
                xSpace = false
            }
        }

        if (config.jugglers > 1) {
            sb.append('>')
        }
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
                isExcited = (compareStates(config.groundState, state[0]) != 0)
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

        target!!.writePattern(
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
    //
    // Rewritten on 12/31/03

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
        startingSeq = run {
            val startingSeqSB = StringBuilder()
            (0..<startBeats).forEach { outputBeat(it, startingSeqSB) }
            startingSeqSB.toString()
        }

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

        endingSeq = run {
            val endingSeqSB = StringBuilder()
            (0..<endBeats).forEach { outputBeat(it, endingSeqSB) }
            endingSeqSB.toString()
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

        private const val LOOP_COUNTER_MAX = 20000

        // Run the generator from command-line input.

        fun runGeneratorCLI(args: Array<String>, target: GeneratorTarget) {
            if (args.size < 3) {
                val version = getStringResource(Res.string.gui_version, Constants.VERSION)
                val copyright = getStringResource(Res.string.gui_copyright_message, Constants.YEAR)
                var output = "Juggling Lab ${version.lowercase()}\n"
                output += "$copyright\n"
                output += getStringResource(Res.string.gui_gpl_message) + "\n\n"
                output += getStringResource(Res.string.gui_generator_intro)
                println(output)
                return
            }

            try {
                val ssg = SiteswapGenerator()
                ssg.initGenerator(args)
                ssg.runGenerator(target)
            } catch (e: Exception) {
                val message = getStringResource(Res.string.error) + ": " + e.message
                println(message)
            }
        }
    }
}

// Top-level function to run the generator from command line input.

fun main(args: Array<String>) {
    SiteswapGenerator.runGeneratorCLI(args, GeneratorTarget(System.out))
}

//------------------------------------------------------------------------------
// Data type for configuring the generator
//
// This is uniquely determined by command line arguments
//------------------------------------------------------------------------------

class SiteswapGeneratorConfig {
    var n: Int = 0
    var jugglers: Int = 1
    var ht: Int = 0
    var lMin: Int = 0
    var lMax: Int = 0
    var exclude: ArrayList<Pattern> = ArrayList()
    var include: ArrayList<Pattern> = ArrayList()
    var numflag: Int = 0
    var groundflag: Int = 0
    var rotflag: Int = 0
    var fullflag: Int = 1
    var mpflag: Int = 1
    var multiplex: Int = 1
    var delaytime: Int = 0
    var hands: Int = 0
    var maxOccupancy: Int = 0
    var leaderPerson: Int = 1
    lateinit var rhythmRepunit: Array<IntArray>
    var rhythmPeriod: Int = 0
    lateinit var holdthrow: IntArray
    lateinit var personNumber: IntArray
    lateinit var groundState: Array<IntArray>
    var groundStateLength: Int = 0
    var mpClusteredFlag: Boolean = true
    var lameFlag: Boolean = false
    var sequenceFlag: Boolean = true
    var connectedPatternsFlag: Boolean = false
    var symmetricPatternsFlag: Boolean = false
    var jugglerPermutationsFlag: Boolean = false
    var mode: Int = ASYNC
    var slotSize: Int = 0

    // Initialize from command line arguments.
    //
    // In the event of an error, throw a JuggleExceptionUser error with a
    // relevant error message.

    @Throws(JuggleExceptionUser::class)
    constructor(args: Array<String>) {
        if (Constants.DEBUG_GENERATOR) {
            println("-----------------------------------------------------")
            println("initializing generator with args:")
            for (arg in args) {
                print("$arg ")
            }
            print("\n")
        }
        if (args.size < 3) {
            val message = getStringResource(Res.string.error_generator_insufficient_input)
            throw JuggleExceptionUser(message)
        }

        val trueMultiplex = parseInputFlags(args)
        configMode()
        parseInputConfig(args)
        findGround()
        configMultiplexing(trueMultiplex)
    }

    // Parse the input parameters beyond the first three command line arguments.
    //
    // In the event of an error, throw a JuggleExceptionUser error with a
    // relevant error message.

    @Throws(JuggleExceptionUser::class)
    private fun parseInputFlags(args: Array<String>): Boolean {
        var trueMultiplex = false
        var i = 3
        while (i < args.size) {
            when (args[i]) {
                "-n" -> numflag = 1
                "-no" -> numflag = 2
                "-g" -> groundflag = 1
                "-ng" -> groundflag = 2
                "-f" -> fullflag = 0
                "-prime" -> fullflag = 2
                "-rot" -> rotflag = 1
                "-jp" -> jugglerPermutationsFlag = true
                "-lame" -> lameFlag = true
                "-se" -> sequenceFlag = false
                "-s" -> mode = SYNC
                "-cp" -> connectedPatternsFlag = true
                "-sym" -> symmetricPatternsFlag = true
                "-mf" -> mpflag = 0
                "-mc" -> mpClusteredFlag = false
                "-mt" -> trueMultiplex = true
                "-m" -> {
                    if (i < (args.size - 1) && args[i + 1][0] != '-') {
                        try {
                            multiplex = args[i + 1].toInt()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(
                                Res.string.error_number_format,
                                getStringResource(Res.string.gui_simultaneous_throws)
                            )
                            throw JuggleExceptionUser(message)
                        }
                        ++i
                    }
                }

                "-j" -> {
                    if (i < (args.size - 1) && args[i + 1][0] != '-') {
                        try {
                            jugglers = args[i + 1].toInt()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(
                                Res.string.error_number_format,
                                getStringResource(Res.string.gui_jugglers)
                            )
                            throw JuggleExceptionUser(message)
                        }
                        ++i
                    }
                }

                "-d" -> {
                    if (i < (args.size - 1) && args[i + 1][0] != '-') {
                        try {
                            delaytime = args[i + 1].toInt()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(
                                Res.string.error_number_format,
                                getStringResource(Res.string.gui_passing_communication_delay)
                            )
                            throw JuggleExceptionUser(message)
                        }
                        groundflag = 1 // find only ground state tricks
                        ++i
                    }
                }

                "-l" -> {
                    if (i < (args.size - 1) && args[i + 1][0] != '-') {
                        try {
                            leaderPerson = args[i + 1].toInt()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(
                                Res.string.error_number_format,
                                getStringResource(Res.string.error_passing_leader_number)
                            )
                            throw JuggleExceptionUser(message)
                        }
                        ++i
                    }
                }

                "-x" -> {
                    ++i
                    while (i < args.size && args[i][0] != '-') {
                        try {
                            var re: String = makeStandardRegex(args[i])
                            if (!re.contains("^")) {
                                re = ".*$re.*"
                            }
                            if (Constants.DEBUG_GENERATOR) {
                                println("adding exclusion $re")
                            }
                            exclude.add(Pattern.compile(re))
                        } catch (_: PatternSyntaxException) {
                            val message = getStringResource(Res.string.error_excluded_throws)
                            throw JuggleExceptionUser(message)
                        }
                        ++i
                    }
                    --i
                }

                "-i" -> {
                    ++i
                    while (i < args.size && args[i][0] != '-') {
                        try {
                            var re: String = makeStandardRegex(args[i])
                            if (!re.contains("^")) {
                                re = ".*$re"
                            }
                            if (!re.contains("$")) {
                                re = "$re.*"
                            }
                            include.add(Pattern.compile(re))
                        } catch (_: PatternSyntaxException) {
                            val message = getStringResource(Res.string.error_included_throws)
                            throw JuggleExceptionUser(message)
                        }
                        ++i
                    }
                    --i
                }

                else -> {
                    val message = getStringResource(Res.string.error_unrecognized_option, args[i])
                    throw JuggleExceptionUser(message)
                }
            }
            ++i
        }

        return trueMultiplex
    }

    // Initialize config data structures to reflect operating mode.

    private fun configMode() {
        when (mode) {
            ASYNC -> {
                rhythmRepunit = Array(jugglers) { IntArray(1) }
                holdthrow = IntArray(jugglers)
                personNumber = IntArray(jugglers)
                hands = jugglers
                rhythmPeriod = 1
                var i = 0
                while (i < hands) {
                    rhythmRepunit[i][0] = async_rhythm_repunit[0][0]
                    holdthrow[i] = 2
                    personNumber[i] = i + 1
                    ++i
                }
            }

            SYNC -> {
                rhythmRepunit = Array(2 * jugglers) { IntArray(2) }
                holdthrow = IntArray(2 * jugglers)
                personNumber = IntArray(2 * jugglers)
                hands = 2 * jugglers
                rhythmPeriod = 2
                var i = 0
                while (i < hands) {
                    var j = 0
                    while (j < rhythmPeriod) {
                        rhythmRepunit[i][j] = sync_rhythm_repunit[i % 2][j]
                        ++j
                    }
                    holdthrow[i] = 2
                    personNumber[i] = (i / 2) + 1
                    ++i
                }
            }
        }
    }

    // Parse the first three command line arguments: number of objects, max.
    // throw value, and period.
    //
    // In the event of an error, throw a JuggleExceptionUser error with a
    // relevant error message.

    @Throws(JuggleExceptionUser::class)
    private fun parseInputConfig(args: Array<String>) {
        try {
            n = args[0].toInt()
        } catch (_: NumberFormatException) {
            val message = getStringResource(
                Res.string.error_number_format,
                getStringResource(Res.string.gui_balls)
            )
            throw JuggleExceptionUser(message)
        }
        ht = try {
            if (args[1] == "-") {
                // signal to not specify a maximum throw
                -1
            } else if (args[1].matches("^[0-9]+$".toRegex())) {
                args[1].toInt()  // numbers only
            } else {
                args[1].toInt(36)  // 'a' = 10, 'b' = 11, ...
            }
        } catch (_: NumberFormatException) {
            val message = getStringResource(
                Res.string.error_number_format,
                getStringResource(Res.string.gui_max__throw)
            )
            throw JuggleExceptionUser(message)
        }
        try {
            if (args[2] == "-") {
                lMin = rhythmPeriod
                lMax = -1
            } else {
                val divider = args[2].indexOf('-')
                if (divider == 0) {
                    lMin = rhythmPeriod
                    lMax = args[2].substring(1).toInt()
                } else if (divider == (args[2].length - 1)) {
                    lMin = args[2].substring(0, divider).toInt()
                    lMax = -1
                } else if (divider > 0) {
                    lMin = args[2].substring(0, divider).toInt()
                    lMax = args[2].substring(divider + 1).toInt()
                } else {
                    lMax = args[2].toInt()
                    lMin = lMax
                }
            }
        } catch (_: NumberFormatException) {
            val message = getStringResource(
                Res.string.error_number_format,
                getStringResource(Res.string.gui_period)
            )
            throw JuggleExceptionUser(message)
        }

        if (n < 1) {
            val message = getStringResource(Res.string.error_generator_too_few_balls)
            throw JuggleExceptionUser(message)
        }
        if (lMax == -1) {
            if (fullflag != 2) {
                val message = getStringResource(Res.string.error_generator_must_be_prime_mode)
                throw JuggleExceptionUser(message)
            }
            if (ht == -1) {
                val message = getStringResource(Res.string.error_generator_underspecified)
                throw JuggleExceptionUser(message)
            }
            lMax = jlBinomial(ht * hands, n)
            lMax -= (lMax % rhythmPeriod)
        }
        if (ht == -1) {
            ht = n * lMax
        }
        if (ht < 1) {
            val message = getStringResource(Res.string.error_generator_height_too_small)
            throw JuggleExceptionUser(message)
        }
        if (lMin < 1 || lMax < 1 || lMin > lMax) {
            val message = getStringResource(Res.string.error_generator_period_problem)
            throw JuggleExceptionUser(message)
        }

        if (jugglers > 1 && !jugglerPermutationsFlag && groundflag != 0) {
            val message = getStringResource(Res.string.error_juggler_permutations)
            throw JuggleExceptionUser(message)
        }

        if ((lMin % rhythmPeriod) != 0 || (lMax % rhythmPeriod) != 0) {
            val message = getStringResource(Res.string.error_period_multiple, rhythmPeriod)
            throw JuggleExceptionUser(message)
        }

        if (Constants.DEBUG_GENERATOR) {
            println("objects: $n")
            println("height: $ht")
            println("period_min: $lMin")
            println("period_max: $lMax")
            println("hands: $hands")
            println("rhythm_period: $rhythmPeriod")
        }
    }

    // Find the ground state for our rhythm. It does so by putting the balls
    // into the lowest possible slots, with no multiplexing.

    private fun findGround() {
        var ballsLeft = n
        var i = 0
        findlength@ while (true) {
            for (j in 0..<hands) {
                if (rhythmRepunit[j][i % rhythmPeriod] != 0) {
                    if (--ballsLeft != 0)
                        continue
                    groundStateLength = max(i + 1, ht)
                    break@findlength
                }
            }
            ++i
        }

        groundState = Array(hands) { IntArray(groundStateLength) }

        ballsLeft = n
        i = 0
        findstate@ while (true) {
            for (j in 0..<hands) {
                if (rhythmRepunit[j][i % rhythmPeriod] != 0) {
                    // available slot
                    groundState[j][i] = 1
                    if (--ballsLeft == 0)
                        break@findstate
                }
            }
            ++i
        }

        if (Constants.DEBUG_GENERATOR) {
            println("ground state length: $groundStateLength")
            println("ground state:")
            printState(groundState)
        }
    }

    // Configure the multiplexing-related items.

    private fun configMultiplexing(trueMultiplex: Boolean) {
        // The following variable slot_size serves two functions. It is the size
        // of a slot used in the multiplexing filter, and it is the number of
        // throws allocated in memory. The number of throws needs to be larger
        // than L sometimes, since these same structures are used to find
        // starting and ending sequences (containing as many as HT elements).
        slotSize = max(ht, lMax)
        slotSize += rhythmPeriod - (slotSize % rhythmPeriod)

        for (i in 0..<hands) {
            for (j in 0..<rhythmPeriod) {
                maxOccupancy = max(maxOccupancy, rhythmRepunit[i][j])
            }
        }

        maxOccupancy *= multiplex
        if (maxOccupancy == 1) {
            // no multiplexing, turn off filter
            mpflag = 0
        }

        // Include the regular expressions that define "true multiplexing"
        if (trueMultiplex) {
            var includeRe: String? = null

            if (jugglers == 1) {
                if (mode == ASYNC) {
                    includeRe = ".*\\[[^2]*\\].*"
                } else if (mode == SYNC) {
                    includeRe = ".*\\[([^2\\]]*2x)*[^2\\]]*\\].*"
                }
            } else {
                if (mode == ASYNC) {
                    includeRe = ".*\\[([^2\\]]*(2p|.p2|2p.))*[^2\\]]*\\].*"
                } else if (mode == SYNC) {
                    includeRe = ".*\\[([^2\\]]*(2p|.p2|2p.|2x|2xp|.xp2|2xp.))*[^2\\]]*\\].*"
                }
            }

            if (includeRe != null) {
                include.add(Pattern.compile(includeRe))
            }
        }
    }

    // Output a state to the command line (useful for debugging).

    fun printState(st: Array<IntArray>) {
        var lastIndex = 0
        for (i in 0..<groundStateLength) {
            for (j in 0..<hands) {
                if (st[j][i] != 0) {
                    lastIndex = i
                }
            }
        }
        for (i in 0..lastIndex) {
            for (j in 0..<hands) {
                println("  s[$j][$i] = ${st[j][i]}")
            }
        }
    }

    /*
      // Read a custom rhythm file and parses it. If there is an error it
      // prints a message and exits.

      void custom_initialize(char *custom_file) {
          int i, j, k, left_delim, right_delim;
          int last_period, last_person, person, hold, second_pass;
          char ch, *file_buffer;
          FILE *fp;

          if ((fp = fopen(custom_file, "r")) == NULL) {
              printf("File error: cannot open '%s'\n", custom_file);
              exit(0);
          }
          if ((file_buffer = (char *)malloc(BUFFER_SIZE * sizeof(char))) == 0)
              die();

          for (second_pass = 0; second_pass < 2; second_pass++) {
              hands = j = 0;
              jugglers = last_person = 1;

              do {
                  ch = (char)(i = fgetc(fp));

                  if ((ch == (char)10) || (i == EOF)) {
                      file_buffer[j] = (char)0;

                      for (j = 0, k = 0; (ch = file_buffer[j]) && (ch != ';'); ++j)
                          if (ch == '|') {
                              if (++k == 1)
                                  left_delim = j;
                              else if (k == 2)
                                  right_delim = j;
                          }
                              if (ch == ';')
                                  file_buffer[j] = (char)0;        // terminate at comment

                      if (k) {
                          if (k != 2) {
                              printf("File error: need two rhythm delimiters per hand\n");
                              exit(0);
                          }
                          // At this point the line checks out.  See if
                          // period is what we got last time.
                          if (hands && ((right_delim-left_delim-1) != last_period)) {
                              printf("File error: rhythm period not constant\n");
                              exit(0);
                          }
                          last_period = right_delim - left_delim - 1;

                          // Now parse the line we've read in

                          file_buffer[left_delim] = (char)0;
                          person = atoi(file_buffer);

                          if (hands) {
                              if (person == (last_person + 1)) {
                                  jugglers++;
                                  last_person = person;
                              } else if (person != last_person) {
                                  printf("File error: person numbers goofed up\n");
                                  exit(0);
                              }
                          } else if (person != 1) {
                              printf("File error: must start with person number 1\n");
                              exit(0);
                          }

                          // Now put stuff in the allocated arrays

                          if (second_pass) {
                              person_number[hands] = person;
                              hold = atoi(file_buffer + right_delim + 1);
                              holdthrow[hands] = (hold ? hold : 2);

                              // Fill the rhythm matrix
                              for (j = 0; j < rhythm_period; ++j) {
                                  ch = file_buffer[j + left_delim + 1];
                                  if (((ch < '0') || (ch > '9')) && (ch != ' ')) {
                                      printf("File error: bad character in rhythm\n");
                                      exit(0);
                                  }
                                  if (ch == ' ')
                                      ch = '0';
                                  rhythm_repunit[hands][j] = (int)(ch - '0');
                              }
                          }

                          hands++;   // got valid line, increment counter
                      }
                      j = 0;    // reset buffer pointer for next read
                  } else {
                      file_buffer[j] = ch;
                      if (++j >= BUFFER_SIZE) {
                          printf("File error: input buffer overflow\n");
                          exit(0);
                      }
                  }
              } while (i != EOF);

              if (!hands) {
                  printf("File error: must have at least one hand\n");
                  exit(0);
              }

              if (!second_pass) {        // allocate space after first pass
                  rhythm_period = last_period;
                  rhythm_repunit = alloc_array(hands, rhythm_period);
                  if ((holdthrow = (int *)malloc(hands * sizeof(int))) == 0)
                      die();
                  if ((person_number = (int *)malloc(hands * sizeof(int))) == 0)
                      die();
                  rewind(fp);          // go back to start of file
              }

          }

          (void)fclose(fp);        // close file and free memory
          free(file_buffer);
      }
      */

    companion object {
        // modes
        const val ASYNC: Int = 0
        const val SYNC: Int = 1
        // const val CUSTOM: Int = 2;

        private val async_rhythm_repunit: Array<IntArray> = arrayOf(
            intArrayOf(1),
        )
        private val sync_rhythm_repunit: Array<IntArray> = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(1, 0),
        )

        // Reformat the exclude/include terms into standard regular expressions.
        // Exchange "\x" for "x", where x is one of the RE metacharacters that conflicts
        // with siteswap notation: []()|

        private fun makeStandardRegex(term: String): String {
            var res: String = Pattern.compile("\\\\\\[").matcher(term).replaceAll("@")
            res = Pattern.compile("\\[").matcher(res).replaceAll("\\\\[")
            res = Pattern.compile("@").matcher(res).replaceAll("[")
            res = Pattern.compile("\\\\]").matcher(res).replaceAll("@")
            res = Pattern.compile("]").matcher(res).replaceAll("\\\\]")
            res = Pattern.compile("@").matcher(res).replaceAll("]")

            res = Pattern.compile("\\\\\\(").matcher(res).replaceAll("@")
            res = Pattern.compile("\\(").matcher(res).replaceAll("\\\\(")
            res = Pattern.compile("@").matcher(res).replaceAll("(")
            res = Pattern.compile("\\\\\\)").matcher(res).replaceAll("@")
            res = Pattern.compile("\\)").matcher(res).replaceAll("\\\\)")
            res = Pattern.compile("@").matcher(res).replaceAll(")")

            res = Pattern.compile("\\\\\\|").matcher(res).replaceAll("@")
            res = Pattern.compile("\\|").matcher(res).replaceAll("\\\\|")
            res = Pattern.compile("@").matcher(res).replaceAll("|")
            return res
        }
    }
}