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
import jugglinglab.JugglingLab.errorstrings
import jugglinglab.JugglingLab.guistrings
import jugglinglab.util.*
import java.text.MessageFormat
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.JPanel
import kotlin.math.max

class SiteswapGenerator : Generator() {
    // configuration variables
    private var n: Int = 0
    private var jugglers: Int = 0
    private var ht: Int = 0
    private var lMin: Int = 0
    private var lMax: Int = 0
    private var exclude: ArrayList<Pattern>? = null
    private var include: ArrayList<Pattern>? = null
    private var numflag: Int = 0
    private var groundflag: Int = 0
    private var rotflag: Int = 0
    private var fullflag: Int = 0
    private var mpflag: Int = 0
    private var multiplex: Int = 0
    private var delaytime: Int = 0
    private var hands: Int = 0
    private var maxOccupancy: Int = 0
    private var leaderPerson: Int = 0
    private lateinit var rhythmRepunit: Array<IntArray>
    private var rhythmPeriod: Int = 0
    private lateinit var holdthrow: IntArray
    private lateinit var personNumber: IntArray
    private lateinit var groundState: Array<IntArray>
    private var groundStateLength: Int = 0
    private var mpClusteredFlag: Boolean = false
    private var lameFlag: Boolean = false
    private var sequenceFlag: Boolean = false
    private var connectedPatternsFlag: Boolean = false
    private var symmetricPatternsFlag: Boolean = false
    private var jugglerPermutationsFlag: Boolean = false
    private var mode: Int = 0
    private var slotSize: Int = 0

    // working variables, initialized at runtime
    private lateinit var state: Array<Array<IntArray>>
    private var lTarget: Int = 0
    private lateinit var rhythm: Array<Array<IntArray>>
    private lateinit var throwsLeft: Array<IntArray>
    private lateinit var holes: Array<IntArray>
    private lateinit var throwTo: Array<Array<IntArray>>
    private lateinit var throwValue: Array<Array<IntArray>>
    private lateinit var mpFilter: Array<Array<Array<IntArray>>>
    private var patternPrintx: Boolean = false
    private lateinit var scratch1: IntArray
    private lateinit var scratch2: IntArray
    private lateinit var output: CharArray
    private lateinit var connections: BooleanArray
    private lateinit var permScratch1: BooleanArray
    private lateinit var permScratch2: BooleanArray
    private lateinit var startingSeq: CharArray
    private lateinit var endingSeq: CharArray
    private var startingSeqLength: Int = 0
    private var endingSeqLength: Int = 0
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
        configGenerator(args)
        allocateWorkspace()
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun runGenerator(t: GeneratorTarget): Int {
        return runGenerator(t, -1, -1.0) // no limits
    }

    @Suppress("SimplifyBooleanWithConstants")
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun runGenerator(t: GeneratorTarget, maxNum: Int, secs: Double): Int {
        if (groundflag == 1 && groundStateLength > ht) {
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
            lTarget = lMin
            while (lTarget <= lMax) {
                num += findPatterns(0, 0, 0)
                lTarget += rhythmPeriod
            }

            if (numflag != 0) {
                if (num == 1) {
                    target!!.setStatus(guistrings.getString("Generator_patterns_1"))
                } else {
                    val template: String = guistrings.getString("Generator_patterns_ne1")
                    val arguments = arrayOf<Any?>(num)
                    target!!.setStatus(MessageFormat.format(template, *arguments))
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

    // Set the generator configuration variables based on arguments.
    @Throws(JuggleExceptionUser::class)
    private fun configGenerator(args: Array<String>) {
        if (Constants.DEBUG_GENERATOR) {
            println("-----------------------------------------------------")
            println("initializing generator with args:")
            for (arg in args) {
                print("$arg ")
            }
            print("\n")
        }

        if (args.size < 3) {
            throw JuggleExceptionUser(errorstrings.getString("Error_generator_insufficient_input"))
        }

        maxOccupancy = 0
        leaderPerson = 1
        numflag = 0
        groundflag = 0
        rotflag = 0
        mpflag = 1
        fullflag = mpflag
        mpClusteredFlag = true
        multiplex = 1
        delaytime = 0
        lameFlag = false
        connectedPatternsFlag = false
        symmetricPatternsFlag = false
        jugglerPermutationsFlag = false
        sequenceFlag = true
        mode = ASYNC // default mode
        jugglers = 1
        target = null
        exclude = ArrayList<Pattern>()
        include = ArrayList<Pattern>()

        var trueMultiplex = false

        run {
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
                                val template: String = errorstrings.getString("Error_number_format")
                                val str: String? = guistrings.getString("simultaneous_throws")
                                val arguments = arrayOf<Any?>(str)
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                            }
                            ++i
                        }
                    }

                    "-j" -> {
                        if (i < (args.size - 1) && args[i + 1][0] != '-') {
                            try {
                                jugglers = args[i + 1].toInt()
                            } catch (_: NumberFormatException) {
                                val template: String = errorstrings.getString("Error_number_format")
                                val str: String? = guistrings.getString("Jugglers")
                                val arguments = arrayOf<Any?>(str)
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                            }
                            ++i
                        }
                    }

                    "-d" -> {
                        if (i < (args.size - 1) && args[i + 1][0] != '-') {
                            try {
                                delaytime = args[i + 1].toInt()
                            } catch (_: NumberFormatException) {
                                val template: String = errorstrings.getString("Error_number_format")
                                val str: String? = guistrings.getString("Passing_communication_delay")
                                val arguments = arrayOf<Any?>(str)
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
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
                                val template: String = errorstrings.getString("Error_number_format")
                                val str: String? = guistrings.getString("Error_passing_leader_number")
                                val arguments = arrayOf<Any?>(str)
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
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
                                exclude!!.add(Pattern.compile(re))
                            } catch (_: PatternSyntaxException) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_excluded_throws"))
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
                                include!!.add(Pattern.compile(re))
                            } catch (_: PatternSyntaxException) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_included_throws"))
                            }
                            ++i
                        }
                        --i
                    }

                    else -> {
                        val template: String = errorstrings.getString("Error_unrecognized_option")
                        val arguments = arrayOf<Any?>(args[i])
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                }
                ++i
            }
        }

        configMode()

        try {
            n = args[0].toInt()
        } catch (_: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val str: String? = guistrings.getString("balls")
            val arguments = arrayOf<Any?>(str)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
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
            val template: String = errorstrings.getString("Error_number_format")
            val str: String? = guistrings.getString("max._throw")
            val arguments = arrayOf<Any?>(str)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
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
            val template: String = errorstrings.getString("Error_number_format")
            val str: String? = guistrings.getString("period")
            val arguments = arrayOf<Any?>(str)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }

        if (Constants.DEBUG_GENERATOR) {
            println("objects: $n")
            println("height: $ht")
            println("period_min: $lMin")
            println("period_max: $lMax")
            println("hands: $hands")
            println("rhythm_period: $rhythmPeriod")
        }

        if (n < 1) {
            throw JuggleExceptionUser(errorstrings.getString("Error_generator_too_few_balls"))
        }
        if (lMax == -1) {
            if (fullflag != 2) {
                throw JuggleExceptionUser(errorstrings.getString("Error_generator_must_be_prime_mode"))
            }
            if (ht == -1) {
                throw JuggleExceptionUser(errorstrings.getString("Error_generator_underspecified"))
            }
            lMax = jlBinomial(ht * hands, n)
            lMax -= (lMax % rhythmPeriod)
        }
        if (ht == -1) {
            ht = n * lMax
        }
        if (ht < 1) {
            throw JuggleExceptionUser(errorstrings.getString("Error_generator_height_too_small"))
        }
        if (lMin < 1 || lMax < 1 || lMin > lMax) {
            throw JuggleExceptionUser(errorstrings.getString("Error_generator_period_problem"))
        }

        output = CharArray(lMax * CHARS_PER_THROW)

        if (jugglers > 1 && !jugglerPermutationsFlag && groundflag != 0) {
            throw JuggleExceptionUser(errorstrings.getString("Error_juggler_permutations"))
        }

        if ((lMin % rhythmPeriod) != 0 || (lMax % rhythmPeriod) != 0) {
            val template: String = errorstrings.getString("Error_period_multiple")
            val arguments = arrayOf<Any?>(rhythmPeriod)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }

        findGround()

        if (Constants.DEBUG_GENERATOR) {
            println("ground state length: $groundStateLength")
            println("ground state:")
            printState(groundState)
        }

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
                include!!.add(Pattern.compile(includeRe))
            }
        }
    }

    // Initialize configuration data structures to reflect operating mode.

    private fun configMode() {
        when (mode) {
            ASYNC -> {
                rhythmRepunit = Array(jugglers) { IntArray(1) }
                holdthrow = IntArray(jugglers)
                personNumber = IntArray(jugglers)
                hands = jugglers
                rhythmPeriod = 1
                patternPrintx = false
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
                patternPrintx = true
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

    // Allocate space for the states, rhythms, and throws in the pattern, plus
    // other incidental variables.

    private fun allocateWorkspace() {
        // last index below is not `ht` because of findStartEnd()
        state = Array(lMax + 1) { Array(hands) { IntArray(groundStateLength) } }
        holes = Array(hands) { IntArray(lMax + ht) }
        // first index below is not `l` because of findStartEnd()
        throwTo = Array(slotSize) { Array(hands) { IntArray(maxOccupancy) } }
        throwValue = Array(slotSize) { Array(hands) { IntArray(maxOccupancy) } }

        rhythm = Array(slotSize + 1) { Array(hands) { IntArray(ht) } }
        for (i in 0..<(slotSize + 1)) {
            for (j in 0..<hands) {
                for (k in 0..<ht) {
                    rhythm[i][j][k] = multiplex * rhythmRepunit[j][(k + i) % rhythmPeriod]
                }
            }
        }

        if (mpflag != 0) {
            // space for filter variables
            mpFilter = Array(lMax + 1) { Array(hands) { Array(slotSize) { IntArray(3) } } }
        }

        throwsLeft = Array(lMax) { IntArray(hands) }

        if (jugglers > 1) {
            // passing communication delay variables
            scratch1 = IntArray(hands)
            scratch2 = IntArray(hands)
        }

        if (connectedPatternsFlag) {
            connections = BooleanArray(jugglers)
        }

        if (jugglers > 1 && !jugglerPermutationsFlag) {
            permScratch1 = BooleanArray(lMax)
            permScratch2 = BooleanArray(lMax)
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
        if (ballsPlaced == n || groundflag == 1) {
            if (groundflag == 1) {
                // find only ground state patterns
                for (i in 0..<hands) {
                    for (j in 0..<ht) {
                        state[0][i][j] = groundState[i][j]
                    }
                }
            } else if (groundflag == 2 && compareStates(state[0], groundState) == 0) {
                // don't find ground state patterns
                return 0
            }

            // At this point our state is completed.  Check to see if it's
            // valid. (Position X must be at least as large as position X+L,
            // where L = pattern length.) Also set up the initial multiplexing
            // filter frame, if needed.
            for (i in 0..<hands) {
                var j = 0

                while (j < ht) {
                    val k = state[0][i][j]

                    if (mpflag != 0 && k == 0) {
                        mpFilter[0][i][j][TYPE] = MP_EMPTY
                    } else {
                        if (mpflag != 0) {
                            mpFilter[0][i][j][VALUE] = j + 1
                            mpFilter[0][i][j][FROM] = i
                            mpFilter[0][i][j][TYPE] = MP_LOWER_BOUND
                        }

                        var m = j
                        var q: Int

                        while ((lTarget.let { m += it; m }) < ht) {
                            if ((state[0][i][m].also { q = it }) > k) {
                                return 0 // die (invalid state for this value of `l`)
                            }
                            if (mpflag != 0 && q != 0) {
                                if (q < k && j > holdthrow[i]) {
                                    return 0 // different throws into same hand
                                }
                                mpFilter[0][i][j][VALUE] = m + 1 // new bound
                            }
                        }
                    }
                    ++j
                }

                if (mpflag != 0) {
                    while (j < slotSize) {
                        mpFilter[0][i][j][TYPE] = MP_EMPTY // clear rest of slot
                        ++j
                    }
                }
            }

            if (numflag != 2 && sequenceFlag) {
                findStartEnd()
            }

            if (Constants.DEBUG_GENERATOR) {
                println("Starting findCycles() from state:")
                printState(state[0])
            }

            for (h in 0..<hands) {
                for (ti in 0..<lTarget + ht) {
                    // calculate the number of throws we can make into a
                    // particular (hand, target index) combo
                    // maximum number of holes we have to fill...
                    var numHoles: Int = if (ti < lTarget) {
                        multiplex * rhythmRepunit[h][ti % rhythmPeriod]
                    } else {
                        state[0][h][ti - lTarget]
                    }

                    // ...less those filled by throws before beat 0
                    if (ti < ht) {
                        numHoles -= state[0][h][ti]
                    }

                    holes[h][ti] = numHoles
                }
            }

            startBeat(0)
            return findCycles(0, 1, 0, 0) // find patterns thru state
        }

        if (ballsPlaced == 0) {  // startup, clear state
            for (i in 0..<hands) {
                for (j in 0..<ht) {
                    state[0][i][j] = 0
                }
            }
        }

        var num = 0

        var j = minTo // ensures each state is generated only once
        for (i in minValue..<ht) {
            while (j < hands) {
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

    // Generate cycles in the state graph, starting from some given vertex.
    //
    // Inputs:
    // int pos;              // beat number in pattern that we're constructing
    // int min_throw;        // lowest we can throw this time
    // int min_hand;         // lowest hand we can throw to this time
    // int outputpos;        // current position in the char[] output buffer
    //
    // Returns the number of cycles found.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun findCycles(pos: Int, minThrow: Int, minHand: Int, outputpos: Int): Int {
        var pos = pos
        if (Thread.interrupted()) {
            throw JuggleExceptionInterrupted()
        }

        // System.out.println("starting findCycles with pos=" + pos + ", min_throw="
        //        + min_throw + ", min_hand=" + min_hand);

        // do a time check
        if (maxTime > 0) {
            if (loopCounter++ > LOOP_COUNTER_MAX) {
                loopCounter = 0
                if ((System.currentTimeMillis() - startTimeMillis) > maxTimeMillis) {
                    val template: String = guistrings.getString("Generator_timeout")
                    val arguments = arrayOf<Any?>(maxTime.toInt())
                    throw JuggleExceptionDone(MessageFormat.format(template, *arguments))
                }
            }
        }

        // find the next hand with something to throw
        var h = 0

        while (throwsLeft[pos][h] == 0) {
            ++h

            if (h < hands) {
                continue
            }

            // Done with this beat. Do some checks to see if things are valid so far.

            // output the throw as a string so we can test for exclusions
            val outputposNew = outputBeat(pos, output, outputpos)

            if (!areThrowsValid(pos, outputposNew)) {
                return 0
            }
            if (mpflag != 0 && !isMultiplexingValid(pos)) {
                return 0
            }

            calculateState(pos + 1)
            if (!isStateValid(pos + 1)) {
                return 0
            }

            if (Constants.DEBUG_GENERATOR) {
                val sb = StringBuilder()
                sb.append(".  ".repeat(pos))
                for (t in outputpos..<outputposNew) {
                    sb.append(output[t])
                }
                println(sb)
            }

            ++pos // move to next beat

            if (pos < lTarget) {
                startBeat(pos)
                return findCycles(pos, 1, 0, outputposNew)
            }

            // at the target length; does the pattern work?
            if (compareStates(state[0], state[lTarget]) == 0 && isPatternValid(outputposNew)) {
                if (Constants.DEBUG_GENERATOR) {
                    val sb = StringBuilder()
                    for (t in 0..<outputposNew) {
                        sb.append(output[t])
                    }
                    println("got a pattern: $sb")
                }
                if (numflag != 2) {
                    outputPattern(outputposNew)
                }
                return 1
            } else {
                return 0
            }
        }

        // Have a throw to assign. Iterate over all possibilities.
        throwsLeft[pos][h] = throwsLeft[pos][h] - 1

        val slot = throwsLeft[pos][h]
        var k = minHand
        var num = 0

        // System.out.println("check 1: k=" + k + ", min_throw=" + min_throw + ", slot=" + slot);
        for (j in minThrow..ht) {
            val ti = pos + j // target index

            while (k < hands) {
                if (holes[k][ti] == 0) {
                    ++k
                    continue  // can't throw to position
                }

                holes[k][ti] = holes[k][ti] - 1

                throwTo[pos][h][slot] = k
                throwValue[pos][h][slot] = j

                num += if (slot != 0) {
                    findCycles(pos, j, k, outputpos)  // enforces ordering on multiplexed throws
                } else {
                    findCycles(pos, 1, 0, outputpos)
                }

                holes[k][ti] = holes[k][ti] + 1

                if (maxNum in 0..num) {
                    val template: String = guistrings.getString("Generator_spacelimit")
                    val arguments = arrayOf<Any?>(maxNum)
                    throw JuggleExceptionDone(MessageFormat.format(template, *arguments))
                }
                ++k
            }

            k = 0
        }

        // System.out.println("check 2");
        throwsLeft[pos][h] = throwsLeft[pos][h] + 1
        return num
    }

    // Calculate the state based on previous beat's state and throws.

    private fun calculateState(pos: Int) {
        if (pos == 0) {
            return
        }

        for (j in 0..<hands) {  // shift state to the left
            for (k in 0..<ht - 1) {
                state[pos][j][k] = state[pos - 1][j][k + 1]
            }
            state[pos][j][ht - 1] = 0
        }

        for (j in 0..<hands) {  // add on the last throw(s)
            for (k in 0..<maxOccupancy) {
                val v = throwValue[pos - 1][j][k]
                if (v == 0) {
                    break
                }

                state[pos][throwTo[pos - 1][j][k]][v - 1] = state[pos][throwTo[pos - 1][j][k]][v - 1] + 1
            }
        }
    }

    // Check if the state is valid at a given position in the pattern.

    private fun isStateValid(pos: Int): Boolean {
        // Check if this is a valid state for a period-L pattern.
        // This check added 01/19/98.
        if (ht > lTarget) {
            for (j in 0..<hands) {
                for (k in 0..<lTarget) {
                    var o = k
                    while (o < ht - lTarget) {
                        if (state[pos][j][o + lTarget] > state[pos][j][o]) {
                            return false
                        }
                        o += lTarget
                    }
                }
            }
        }

        if (pos % rhythmPeriod == 0) {
            val cs = compareStates(state[0], state[pos])

            if (fullflag != 0 && pos != lTarget && cs == 0) {
                return false // intersection
            }

            if (rotflag == 0 && cs == 1) {
                return false // bad rotation
            }
        }

        if (fullflag == 2) {  // list only simple loops?
            for (j in 1..<pos) {
                if ((pos - j) % rhythmPeriod == 0) {
                    if (compareStates(state[j], state[pos]) == 0) {
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

    private fun isMultiplexingValid(pos: Int): Boolean {
        for (j in 0..<hands) { // shift filter frame to left
            for (k in 0..<(slotSize - 1)) {
                mpFilter[pos + 1][j][k][TYPE] = mpFilter[pos][j][k + 1][TYPE]
                mpFilter[pos + 1][j][k][FROM] = mpFilter[pos][j][k + 1][FROM]
                mpFilter[pos + 1][j][k][VALUE] = mpFilter[pos][j][k + 1][VALUE]
            }
            mpFilter[pos + 1][j][slotSize - 1][TYPE] = MP_EMPTY

            // empty slots shift in
            if (addThrowMPFilter(
                    mpFilter[pos + 1][j][lTarget - 1],
                    j,
                    mpFilter[pos][j][0][TYPE],
                    mpFilter[pos][j][0][VALUE],
                    mpFilter[pos][j][0][FROM]
                )
                != 0
            ) {
                return false
            }
        }

        for (j in 0..<hands) { // add on last throw
            for (k in 0..<maxOccupancy) {
                val m = throwValue[pos][j][k]
                if (m == 0) {
                    break
                }

                if (addThrowMPFilter(
                        mpFilter[pos + 1][throwTo[pos][j][k]][m - 1],
                        throwTo[pos][j][k],
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

    private fun startBeat(pos: Int) {
        for (i in 0..<hands) {
            throwsLeft[pos][i] = state[pos][i][0]
            for (j in 0..<maxOccupancy) {
                throwTo[pos][i][j] = i  // clear throw matrix
                throwValue[pos][i][j] = 0
            }
        }
    }

    // Check if the throws made on a given beat are valid.
    //
    // Test for excluded throws and a passing communication delay, as well as
    // a custom filter (if in CUSTOM mode).

    private fun areThrowsValid(pos: Int, outputpos: Int): Boolean {
        // check #1: test against exclusions
        for (regex in exclude!!) {
            if (Constants.DEBUG_GENERATOR) {
                println(
                    ("test exclusions for string "
                            + (String(output, 0, outputpos))
                            + " = "
                            + regex.matcher(String(output, 0, outputpos)).matches())
                )
            }
            if (regex.matcher(String(output, 0, outputpos)).matches()) {
                return false
            }
        }

        // check #2: if multiplexing, look for clustered throws if disallowed
        if (!mpClusteredFlag) {
            for (i in 0..<hands) {
                if (rhythm[pos][i][0] != 0) {
                    var j = 0
                    while (j < maxOccupancy && throwValue[pos][i][j] != 0) {
                        for (l in 0..<j) {
                            if (throwValue[pos][i][j] == throwValue[pos][i][l]
                                && throwTo[pos][i][j] == throwTo[pos][i][l]
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
        if (jugglers > 1 && pos < delaytime) {
            // Count the number of balls being thrown, assuming no
            // multiplexing. Also check if leader is forcing others to
            // multiplex or make no throw.
            var ballsThrown = 0
            for (i in 0..<hands) {
                if (rhythm[pos][i][0] != 0) {
                    ++ballsThrown
                    if (state[pos][i][0] != 1 && personNumber[i] != leaderPerson) {
                        return false
                    }
                }
            }
            var ballsLeft = n
            for (i in 0..<ht) {
                if (ballsLeft == 0) break
                for (j in 0..<hands) {
                    if (ballsLeft == 0) break
                    if (rhythm[pos + 1][j][i] != 0) {
                        if (--ballsLeft < ballsThrown) {
                            scratch1[ballsLeft] = j // dest hand #
                            scratch2[ballsLeft] = i + 1 // dest value
                        }
                    }
                }
            }
            if (ballsLeft != 0) {
                return false  // shouldn't happen, but die anyway
            }
            for (i in 0..<hands) {
                if (state[pos][i][0] != 0 && personNumber[i] != leaderPerson) {
                    var foundSpot = false

                    for (j in 0..<ballsThrown) {
                        if (scratch1[j] == throwTo[pos][i][0] &&
                            scratch2[j] == throwValue[pos][i][0])
                        {
                            scratch2[j] = 0  // don't throw to spot again
                            foundSpot = true
                            break
                        }
                    }
                    if (!foundSpot) {
                        return false
                    }
                }
            }
        }
        return true
    }

    // Test if a completed pattern is valid.

    private fun isPatternValid(outputpos: Int): Boolean {
        // check #1: verify against inclusions.
        for (regex in include!!) {
            if (!regex.matcher(String(output, 0, outputpos)).matches()) {
                if (Constants.DEBUG_GENERATOR) {
                    println("   pattern invalid: missing inclusion")
                }
                return false
            }
        }

        // check #2: look for '11' sequence.
        if (mode == ASYNC && lameFlag && maxOccupancy == 1) {
            for (i in 0..<(lTarget - 1)) {
                for (j in 0..<hands) {
                    if (throwValue[i][j][0] == 1 &&
                        personNumber[throwTo[i][j][0]] == personNumber[j] &&
                        throwValue[i + 1][j][0] == 1 &&
                        personNumber[throwTo[i + 1][j][0]] == personNumber[j])
                    {
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
        if (fullflag == 0 && rotflag == 0) {
            for (i in 1..<lTarget) {
                if (i % rhythmPeriod == 0) { // can we compare states?
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
        if (jugglers > 1 && connectedPatternsFlag) {
            for (i in 0..<jugglers) {
                connections[i] = false
            }
            connections[0] = true

            var changed = true
            while (changed) {
                changed = false

                for (i in 0..<lTarget) {
                    for (j in 0..<hands) {
                        if (connections[personNumber[j] - 1]) {
                            continue
                        }
                        var k = 0
                        while (k < maxOccupancy && throwValue[i][j][k] > 0) {
                            val p = personNumber[throwTo[i][j][k]]

                            if (connections[p - 1]) {
                                connections[personNumber[j] - 1] = true
                                changed = true
                            }
                            ++k
                        }
                    }
                }
            }
            for (i in 0..<jugglers) {
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
        if (jugglers > 1 && !jugglerPermutationsFlag) {
            loop@ for (m in 1..<jugglers) {
                // compare juggler m against juggler (m + 1)
                for (i in 0..<lTarget) {
                    permScratch2[i] = false
                    permScratch1[i] = false
                }
                repeat (lTarget) {
                    var scorem = -1
                    var scoremp1 = -1
                    var maxm = 0
                    var maxmp1 = 0

                    for (i in 0..<lTarget) {
                        if (!permScratch1[i]) {
                            var scoretemp = 0

                            for (j in 0..<hands) {
                                if (personNumber[j] != m) {
                                    continue
                                }
                                var k = 0
                                while (k < maxOccupancy && throwValue[i][j][k] > 0) {
                                    scoretemp += 4 * throwValue[i][j][k] * (2 * maxOccupancy) * (2 * maxOccupancy)
                                    if (throwTo[i][j][k] != j) {
                                        scoretemp += 2 * (2 * maxOccupancy)
                                        if (personNumber[throwTo[i][j][k]] != m) {
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
                        if (!permScratch2[i]) {
                            var scoretemp = 0
                            for (j in 0..<hands) {
                                if (personNumber[j] != (m + 1)) {
                                    continue
                                }
                                var k = 0
                                while (k < maxOccupancy && throwValue[i][j][k] > 0) {
                                    scoretemp += 4 * throwValue[i][j][k] * (2 * maxOccupancy) *
                                        (2 * maxOccupancy)
                                    if (throwTo[i][j][k] != j) {
                                        scoretemp += 2 * (2 * maxOccupancy)
                                        if (personNumber[throwTo[i][j][k]] != (m + 1)) {
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

                    permScratch2[maxmp1] = true
                    permScratch1[maxm] = true
                }
            }
        }

        // check #6: if passing, test whether pattern is symmetric if enabled.
        //
        // Example: jlab gen 6 4 3 -j 2 -f -se -sym -cp
        if (jugglers > 1 && symmetricPatternsFlag) {
            js@ for (j in 2..jugglers) {
                offsets@ for (offset in 0..<lTarget) {
                    // compare juggler `j` to juggler 1 with beat offset `offset`

                    for (i in 0..<lTarget) {
                        var hJuggler1 = 0
                        val index = (i + offset) % lTarget

                        for (h in 1..<hands) {
                            if (personNumber[h] != j) {
                                continue
                            }
                            for (k in 0..<maxOccupancy) {
                                val val1 = throwValue[i][hJuggler1][k]
                                val self1 = (personNumber[throwTo[i][hJuggler1][k]] == 1)
                                val same1 = (throwTo[i][hJuggler1][k] == hJuggler1)

                                val valJ = throwValue[index][h][k]
                                val selfJ = (personNumber[throwTo[index][h][k]] == j)
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
    // This method assumes the throws are comparable, i.e., that pos1 is
    // congruent to pos2 mod rhythm_period.

    @Suppress("SameParameterValue")
    private fun compareRotations(pos1: Int, pos2: Int): Int {
        var i = 0
        while (i < lTarget) {
            val res = compareLoops((pos1 + i) % lTarget, (pos2 + i) % lTarget)
            if (res > 0) {
                return 1
            } else if (res < 0) {
                return -1
            }

            ++i
            while (i < lTarget) {
                if (compareStates(state[pos1], state[(pos1 + i) % lTarget]) == 0) {
                    break
                }
                ++i
            }
        }
        return 0
    }

    // Compare two generated loops.

    private fun compareLoops(pos1: Int, pos2: Int): Int {
        var pos1 = pos1
        var pos2 = pos2
        val stateStart = state[pos1]
        var result = 0
        var i = 0

        // Rule 1:  The longer loop is always greater
        // Rule 2:  For loops of equal length, use throw-by-throw comparison
        // Rule 3:  Loops are equal only if the respective throws are identical
        while (true) {
            ++i

            if (result == 0) {
                result = compareThrows(pos1, pos2)
            }

            if (i % rhythmPeriod == 0) {
                val cs1 = compareStates(state[pos1 + 1], stateStart)
                val cs2 = compareStates(state[pos2 + 1], stateStart)

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

            ++pos1
            ++pos2
        }
    }

    // Compare two throws.
    //
    // Return 1 if the throw at pos1 is greater than the throw at pos2,
    // -1 if lesser, and 0 iff the throws are identical.
    //
    // This method assumes the throws are comparable, i.e., that pos1 is congruent
    // to pos2 mod rhythm_period.

    private fun compareThrows(pos1: Int, pos2: Int): Int {
        val value1 = throwValue[pos1]
        val to1 = throwTo[pos1]
        val value2 = throwValue[pos2]
        val to2 = throwTo[pos2]
        val rhy = rhythm[pos1] // same as pos2 since throws comparable

        for (i in 0..<hands) {
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

        for (i in 0..<hands) {
            for (j in 0..<ht) {
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

        for (j in (ht - 1) downTo 0) {
            for (i in (hands - 1) downTo 0) {
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

    // Print the throws for a given beat.

    private fun outputBeat(pos: Int, out: CharArray, outpos: Int): Int {
        var outpos = outpos
        var noThrow = true
        for (i in rhythm[pos].indices) if (rhythm[pos][i][0] != 0) {
            noThrow = false
            break
        }
        if (noThrow) {
            return outpos // no throw on this beat
        }

        var xSpace = (outpos > 0) // for printing 'x'-valued throws

        if (jugglers > 1) {
            out[outpos++] = '<'
            xSpace = false
        }

        for (i in 1..jugglers) {
            // first find the hand numbers corresponding to the person
            var loHand = 0
            while (personNumber[loHand] != i) {
                ++loHand
            }

            var hiHand = loHand
            while (hiHand < hands && personNumber[hiHand] == i) {
                ++hiHand
            }

            // check rhythm to see how many hands are throwing
            var numHandsThrowing = 0
            for (j in loHand..<hiHand) {
                if (rhythm[pos][j][0] != 0) {
                    ++numHandsThrowing
                }
            }

            if (numHandsThrowing > 0) {
                var parens = false

                if (numHandsThrowing > 1) {
                    out[outpos++] = '('
                    xSpace = false
                    parens = true
                }

                for (j in loHand..<hiHand) {
                    if (rhythm[pos][j][0] == 0) {
                        continue  // hand isn't supposed to throw
                    }

                    var isMultiplex = false

                    if (maxOccupancy > 1 && throwValue[pos][j][1] > 0) {
                        out[outpos++] = '[' // multiplexing?
                        xSpace = false
                        isMultiplex = true
                    }

                    // loop over the throws coming out of this hand
                    var gotThrow = false

                    var k = 0
                    while (k < maxOccupancy && throwValue[pos][j][k] > 0) {
                        gotThrow = true

                        if (throwValue[pos][j][k] == 33 && xSpace) {
                            out[outpos++] = ' '
                        }

                        out[outpos++] = convertNumber(throwValue[pos][j][k]) // print throw value
                        xSpace = true

                        if (hands > 1) {  // potential ambiguity about destination?
                            val targetJuggler = personNumber[throwTo[pos][j][k]]

                            // print destination hand, if needed
                            if (patternPrintx) {
                                // find hand # of destination person
                                var q = throwTo[pos][j][k] - 1
                                var destHand = 0
                                while (q >= 0 && personNumber[q] == targetJuggler) {
                                    --q
                                    ++destHand
                                }

                                if (destHand != (j - loHand)) {
                                    out[outpos++] = 'x'
                                }
                            }

                            // print pass modifier and person number, if needed
                            if (targetJuggler != i) {
                                out[outpos++] = 'p'
                                if (jugglers > 2) {
                                    out[outpos++] = convertNumber(targetJuggler)
                                }
                            }
                            /*
                              // destination person has 1 hand, don't print
                              if ((ch != 'a') || ((q < (hands - 2)) &&
                                                  (person_number[q + 2] == m)))
                              out[outpos++] = ch;             // print it
                              */
                        }

                        // another multiplexed throw?
                        if (isMultiplex && jugglers > 1 && k != (maxOccupancy - 1) &&
                            throwValue[pos][j][k + 1] > 0
                        ) {
                            out[outpos++] = '/'
                            xSpace = false
                        }
                        ++k
                    }

                    if (!gotThrow) {
                        out[outpos++] = '0'
                        xSpace = true
                    }

                    if (isMultiplex) {
                        out[outpos++] = ']'
                        xSpace = false
                    }

                    if (j < (hiHand - 1) && parens) {  // put comma between hands
                        out[outpos++] = ','
                        xSpace = false
                    }
                }
                if (parens) {
                    out[outpos++] = ')'
                    xSpace = false
                }
            }
            if (i < jugglers) {  // another person throwing next?
                out[outpos++] = '|'
                xSpace = false
            }
        }

        if (jugglers > 1) {
            out[outpos++] = '>'
        }

        return outpos
    }

    @Throws(JuggleExceptionInternal::class)
    private fun outputPattern(outputpos: Int) {
        var isExcited = false
        val outputline: StringBuilder =
            StringBuilder(hands * (2 * groundStateLength + lTarget) * CHARS_PER_THROW + 10)
        val outputline2: StringBuilder =
            StringBuilder(hands * (2 * groundStateLength + lTarget) * CHARS_PER_THROW + 10)

        if (groundflag != 1) {
            if (sequenceFlag) {
                if (mode == ASYNC) {
                    outputline.append(" ".repeat(max(0, n - startingSeqLength)))
                }
                outputline.append(startingSeq, 0, startingSeqLength)
                outputline.append("  ")
            } else {
                isExcited = (compareStates(groundState, state[0]) != 0)

                if (isExcited) {
                    outputline.append("* ")
                } else {
                    outputline.append("  ")
                }
            }
        }

        outputline.append(output, 0, outputpos)
        outputline2.append(output, 0, outputpos)

        if (groundflag != 1) {
            if (sequenceFlag) {
                outputline.append("  ")
                outputline.append(endingSeq, 0, endingSeqLength)
                // add proper number of trailing spaces too, so formatting is
                // aligned in RTL languages
                if (mode == ASYNC) {
                    outputline.append(" ".repeat(max(0, n - endingSeqLength)))
                }
            } else {
                if (isExcited) {
                    outputline.append(" *")
                } else {
                    outputline.append("  ")
                }
            }
        }

        target!!.writePattern(outputline.toString(), "siteswap", outputline2.toString().trim { it <= ' ' })
    }

    // Add a throw to a multiplexing filter slot (part of the multiplexing
    // filter).
    //
    // Returns 1 if there is a collision, 0 otherwise.

    private fun addThrowMPFilter(destSlot: IntArray, slotHand: Int, type: Int, value: Int, from: Int): Int {
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
                if (from == slotHand && value == holdthrow[slotHand]) {
                    return 0 // throw is a hold, so ignore it
                }

                when (destSlot[TYPE]) {
                    MP_EMPTY -> {
                        destSlot[TYPE] = MP_THROW
                        destSlot[VALUE] = value
                        destSlot[FROM] = from
                        return 0
                    }

                    MP_LOWER_BOUND -> if (destSlot[VALUE] <= value || destSlot[VALUE] <= holdthrow[slotHand]) {
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

        findstarting1@ do {
            for (j in 0..<hands) {
                for (k in 0..<ht) {
                    // use p_s[1] as scratch
                    if ((k + startBeats) < groundStateLength) {
                        state[1][j][k] = groundState[j][k + startBeats]
                    } else {
                        state[1][j][k] = 0
                    }

                    if (state[1][j][k] > state[0][j][k]) {
                        startBeats += rhythmPeriod
                        continue@findstarting1
                    }

                    state[1][j][k] = state[0][j][k] - state[1][j][k]
                }
            }

            break
        } while (true)

        for (i in 0..<startBeats) {
            for (j in 0..<hands) {
                for (k in 0..<maxOccupancy) {
                    throwValue[i][j][k] = 0
                    throwTo[i][j][k] = j
                }

                if (i >= groundStateLength || groundState[j][i] == 0) {
                    continue
                }

                findstarting2@ for (k in 0..<ht) {
                    for (m in 0..<hands) {
                        if (state[1][m][k] > 0) {
                            state[1][m][k] = state[1][m][k] - 1

                            throwValue[i][j][0] = k + startBeats - i
                            throwTo[i][j][0] = m
                            break@findstarting2
                        }
                    }
                }
            }
        }

        // write starting sequence to buffer
        startingSeq = CharArray(hands * startBeats * CHARS_PER_THROW)
        startingSeqLength = 0

        for (i in 0..<startBeats) {
            startingSeqLength = outputBeat(i, startingSeq, startingSeqLength)
        }

        // Construct an ending sequence. Unlike the starting sequence above,
        // this time work forward to ground state.
        var endBeats = 0

        findending1@ do {
            for (j in 0..<hands) {
                for (k in 0..<groundStateLength) {
                    // use state[1] as scratch
                    if ((k + endBeats) < ht) {
                        state[1][j][k] = state[0][j][k + endBeats]
                    } else {
                        state[1][j][k] = 0
                    }

                    if (state[1][j][k] > groundState[j][k]) {
                        endBeats += rhythmPeriod
                        continue@findending1
                    }

                    state[1][j][k] = groundState[j][k] - state[1][j][k]
                }
            }

            break
        } while (true)

        for (i in 0..<endBeats) {
            for (j in 0..<hands) {
                for (k in 0..<maxOccupancy) {
                    throwValue[i][j][k] = 0
                    throwTo[i][j][k] = j
                }

                if (i >= ht) {
                    continue
                }

                for (q in 0..<state[0][j][i]) {
                    findending2@ for (k in 0..<groundStateLength) {
                        for (m in 0..<hands) {
                            if (state[1][m][k] > 0) {
                                state[1][m][k] = state[1][m][k] - 1

                                throwValue[i][j][q] = k + endBeats - i
                                throwTo[i][j][q] = m
                                break@findending2
                            }
                        }
                    }
                }
            }
        }

        endingSeq = CharArray(hands * endBeats * CHARS_PER_THROW)
        endingSeqLength = 0

        for (i in 0..<endBeats) {
            endingSeqLength = outputBeat(i, endingSeq, endingSeqLength)
        }
    }

    // Find the ground state for our rhythm. It does so by putting the balls
    // into the lowest possible slots, with no multiplexing.

    private fun findGround() {
        var ballsLeft = n

        run {
            var i = 0
            while (ballsLeft != 0) {
                var j = 0
                while (j < hands && ballsLeft != 0) {
                    if (rhythmRepunit[j][i % rhythmPeriod] != 0) {
                        --ballsLeft
                        if (ballsLeft == 0) {
                            groundStateLength = i + 1
                        }
                    }
                    ++j
                }
                ++i
            }
        }

        if (groundStateLength < ht) {
            groundStateLength = ht
        }

        groundState = Array(hands) { IntArray(groundStateLength) }
        for (i in 0..<hands) {
            for (j in 0..<groundStateLength) {
                groundState[i][j] = 0
            }
        }

        ballsLeft = n
        var i = 0
        while (ballsLeft != 0) {
            var j = 0
            while (j < hands && ballsLeft != 0) {
                if (rhythmRepunit[j][i % rhythmPeriod] != 0) {  // available slots
                    groundState[j][i] = 1
                    --ballsLeft
                }
                ++j
            }
            ++i
        }
    }

    // Output the state to the command line (useful for debugging).

    private fun printState(st: Array<IntArray>) {
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
                println("  s[" + j + "][" + i + "] = " + st[j][i])
            }
        }
    }

    companion object {
        // modes
        private const val ASYNC: Int = 0
        private const val SYNC: Int = 1

        // protected final static int CUSTOM = 2;
        // types of multiplexing filter slots
        private const val MP_EMPTY = 0
        private const val MP_THROW = 1
        private const val MP_LOWER_BOUND = 2
        private const val TYPE = 0
        private const val FROM = 1
        private const val VALUE = 2

        // max. # of chars. printed per throw
        private const val CHARS_PER_THROW = 50

        private val async_rhythm_repunit: Array<IntArray> = arrayOf<IntArray>(intArrayOf(1))
        private val sync_rhythm_repunit: Array<IntArray> = arrayOf<IntArray>(intArrayOf(1, 0), intArrayOf(1, 0))
        private const val LOOP_COUNTER_MAX = 20000

        // Return throw value as single character.

        private fun convertNumber(value: Int): Char {
            return Character.forDigit(value, 36).lowercaseChar()
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

        //----------------------------------------------------------------------
        // Static methods to run the generator from the command line
        //----------------------------------------------------------------------

        fun runGeneratorCLI(args: Array<String>, target: GeneratorTarget) {
            if (args.size < 3) {
                var template: String = guistrings.getString("Version")
                val arg1 = arrayOf<Any?>(Constants.VERSION)
                var output: String =
                    "Juggling Lab " + MessageFormat.format(template, *arg1).lowercase(Locale.getDefault()) + "\n"

                template = guistrings.getString("Copyright_message")
                val arg2 = arrayOf<Any?>(Constants.YEAR)
                output += MessageFormat.format(template, *arg2) + "\n"
                output += guistrings.getString("GPL_message") + "\n\n"
                output += guistrings.getString("Generator_intro")

                println(output)
                return
            }

            try {
                val ssg = SiteswapGenerator()
                ssg.initGenerator(args)
                ssg.runGenerator(target)
            } catch (e: Exception) {
                println(errorstrings.getString("Error") + ": " + e.message)
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            runGeneratorCLI(args, GeneratorTarget(System.out))
        }
    }
}
