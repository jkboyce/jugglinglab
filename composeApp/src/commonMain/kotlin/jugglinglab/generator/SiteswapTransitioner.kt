//
// SiteswapTransitioner.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.generator

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.notation.MHNThrow
import jugglinglab.notation.SiteswapPattern
import jugglinglab.util.JuggleExceptionDone
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionInterrupted
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.getStringResource
import jugglinglab.util.getCurrentPlatform
import kotlin.math.max
import kotlin.math.min

class SiteswapTransitioner : Transitioner() {
    // configuration variables
    private var n: Int = 0
    private var jugglers: Int = 0
    private var indexes: Int = 0
    private var lMin: Int = 0
    private var lMax: Int = 0
    private var targetOccupancy: Int = 0
    private var maxOccupancy: Int = 0
    private var mpAllowSimulcatchesFlag: Boolean = false
    private var mpAllowClustersFlag: Boolean = false
    private var noLimitsFlag: Boolean = false
    private var patternFrom: String? = null
    private var patternTo: String? = null
    private lateinit var siteswapFrom: SiteswapPattern
    private lateinit var siteswapTo: SiteswapPattern
    private lateinit var stateFrom: Array<Array<IntArray>>
    private lateinit var stateTo: Array<Array<IntArray>>

    // working variables, initialized at runtime
    private lateinit var state: Array<Array<Array<IntArray>>>
    private lateinit var stateTarget: Array<Array<IntArray>>
    private var lTarget: Int = 0
    private var lReturn: Int = 0
    private lateinit var th: Array<Array<Array<Array<MHNThrow?>>>>
    private lateinit var throwsLeft: Array<Array<IntArray>>
    private var findAll: Boolean = false
    private lateinit var out: Array<Array<String?>>
    private lateinit var shouldPrint: BooleanArray
    private lateinit var asyncHandRight: Array<BooleanArray>
    private lateinit var siteswapPrev: SiteswapPattern
    private var targetMaxFilledIndex: Int = 0
    private var maxNum: Int = 0 // maximum number of transitions to find
    private var maxTime: Double = 0.0 // maximum number of seconds
    private var maxTimeMillis: Long = 0 // maximum number of milliseconds
    private var startTimeMillis: Long = 0 // start time of run, in milliseconds
    private var loopCounter: Int = 0 // gen_loop() counter for checking timeout

    // other state variables
    private var target: GeneratorTarget? = null
    private var prefix: String = ""
    private var suffix: String = ""

    //--------------------------------------------------------------------------
    // Transitioner overrides
    //--------------------------------------------------------------------------

    override val notationName: String = "Siteswap"

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun initTransitioner(args: List<String>) {
        configTransitioner(args)
        allocateWorkspace()
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun runTransitioner(t: GeneratorTarget): Int {
        return runTransitioner(t, -1, -1.0) // negative values --> no limits
    }

    @Suppress("SimplifyBooleanWithConstants")
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun runTransitioner(t: GeneratorTarget, numLimit: Int, secsLimit: Double): Int {
        maxNum = numLimit
        maxTime = secsLimit
        if (maxTime > 0 || Constants.DEBUG_TRANSITIONS) {
            maxTimeMillis = (1000.0 * secsLimit).toLong()
            startTimeMillis = System.currentTimeMillis()
            loopCounter = 0
        }

        try {
            prefix = ""
            suffix = ""
            val returnTrans = findReturnTrans()
            prefix = "($patternFrom^2)"
            suffix = "($patternTo^2)$returnTrans"

            var num = 0
            target = t

            if (lMin == 0) {
                // no transitions needed
                target?.addResult(prefix + suffix, "siteswap", prefix + suffix)
                num = 1
            } else {
                siteswapPrev = siteswapFrom
                var l = lMin
                while (l <= lMax || num == 0) {
                    num += findTrans(stateFrom, stateTo, l, true)
                    ++l
                }
            }

            if (num == 1) {
                val message = getStringResource(Res.string.gui_generator_patterns_1)
                target?.addResult(message, null, null)
            } else {
                val message = getStringResource(Res.string.gui_generator_patterns_ne1, num)
                target?.addResult(message, null, null)
            }
            target?.completed()

            return num
        } finally {
            if (Constants.DEBUG_TRANSITIONS) {
                val millis = System.currentTimeMillis() - startTimeMillis
                System.out.printf("time elapsed: %d.%03d s%n", millis / 1000, millis % 1000)
            }
        }
    }

    //--------------------------------------------------------------------------
    // Non-public methods below
    //--------------------------------------------------------------------------

    // Set the transitioner configuration variables based on arguments.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun configTransitioner(args: List<String>) {
        if (Constants.DEBUG_TRANSITIONS) {
            println("-----------------------------------------------------")
            println("initializing transitioner with args:")
            for (arg in args) {
                print("$arg ")
            }
            print("\n")
        }

        if (args.size < 2) {
            val message = getStringResource(Res.string.error_trans_too_few_args)
            throw JuggleExceptionUser(message)
        }
        if (args[0] == "-") {
            val message = getStringResource(Res.string.error_trans_from_pattern)
            throw JuggleExceptionUser(message)
        }
        if (args[1] == "-") {
            val message = getStringResource(Res.string.error_trans_to_pattern)
            throw JuggleExceptionUser(message)
        }

        targetOccupancy = 1
        mpAllowSimulcatchesFlag = false
        mpAllowClustersFlag = true
        noLimitsFlag = false
        target = null

        var i = 2
        while (i < args.size) {
            when (args[i]) {
                "-mf" -> mpAllowSimulcatchesFlag = true
                "-mc" -> mpAllowClustersFlag = false
                "-m" -> {
                    if (i < (args.size - 1) && args[i + 1][0] != '-') {
                        try {
                            targetOccupancy = args[i + 1].toInt()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(
                                Res.string.error_number_format,
                                getStringResource(Res.string.gui_simultaneous_throws)
                            )
                            throw JuggleExceptionUser(message)
                        }
                        i++
                    }
                }

                "-limits" -> noLimitsFlag = true
                else -> {
                    val message = getStringResource(Res.string.error_unrecognized_option, args[i])
                    throw JuggleExceptionUser(message)
                }
            }
            ++i
        }

        patternFrom = args[0]
        patternTo = args[1]

        // parse patterns, error if either is invalid
        siteswapFrom = SiteswapPattern()
        siteswapTo = SiteswapPattern()

        try {
            siteswapFrom.fromString(patternFrom!!)
        } catch (jeu: JuggleExceptionUser) {
            val message = getStringResource(Res.string.error_trans_in_from_pattern, jeu.message)
            throw JuggleExceptionUser(message)
        }
        try {
            siteswapTo.fromString(patternTo!!)
        } catch (jeu: JuggleExceptionUser) {
            val message = getStringResource(Res.string.error_trans_in_to_pattern, jeu.message)
            throw JuggleExceptionUser(message)
        }

        // work out number of objects and jugglers, and beats (indexes) in states
        val fromN = siteswapFrom.numberOfPaths
        val toN = siteswapTo.numberOfPaths
        if (fromN != toN) {
            val message = getStringResource(Res.string.error_trans_unequal_objects, fromN, toN)
            throw JuggleExceptionUser(message)
        }
        n = fromN

        val fromJugglers = siteswapFrom.numberOfJugglers
        val toJugglers = siteswapTo.numberOfJugglers
        if (fromJugglers != toJugglers) {
            val message = getStringResource(
                Res.string.error_trans_unequal_jugglers,
                fromJugglers,
                toJugglers
            )
            throw JuggleExceptionUser(message)
        }
        jugglers = fromJugglers

        indexes = max(siteswapFrom.indexes, siteswapTo.indexes)
        maxOccupancy = max(
            targetOccupancy,
            max(siteswapFrom.maxOccupancy, siteswapTo.maxOccupancy)
        )

        // find (and store) starting states for each pattern
        stateFrom = siteswapFrom.getStartingState(indexes)
        stateTo = siteswapTo.getStartingState(indexes)

        // find length of transitions from A to B, and B to A
        lMin = findMinLength(stateFrom, stateTo)
        lMax = findMaxLength(stateFrom, stateTo)
        lReturn = findMinLength(stateTo, stateFrom) // may need to be longer

        if (Constants.DEBUG_TRANSITIONS) {
            println("from state:")
            printState(stateFrom)
            println("to state:")
            printState(stateTo)

            println("objects: $n")
            println("jugglers: $jugglers")
            println("indexes: $indexes")
            println("target_occupancy: $targetOccupancy")
            println("max_occupancy: $maxOccupancy")
            println("mp_allow_simulcatches: $mpAllowSimulcatchesFlag")
            println("mp_allow_clusters: $mpAllowClustersFlag")
            println("l_min: $lMin")
            println("l_max: $lMax")
            println("l_return (initial): $lReturn")
        }
    }

    // Allocate space for the states and throws in the transition, plus other
    // incidental variables.

    private fun allocateWorkspace() {
        val size = max(lMax, lReturn)

        state = Array(size + 1) { Array(jugglers) { Array(2) { IntArray(indexes) } } }
        stateTarget = Array(jugglers) { Array(2) { IntArray(indexes) } }
        th = Array(jugglers) { Array(2) { Array(size) { arrayOfNulls(maxOccupancy) } } }
        throwsLeft = Array(size + 1) { Array(jugglers) { IntArray(2) } }
        out = Array(jugglers) { arrayOfNulls(size) }
        shouldPrint = BooleanArray(size + 1)
        asyncHandRight = Array(jugglers) { BooleanArray(size + 1) }
    }

    // Find the shortest return transition from `to` back to `from`.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun findReturnTrans(): String {
        if (lReturn == 0) {
            return ""
        }

        val sb = StringBuilder()
        target = GeneratorTargetBasic { sb.append(it) }
        siteswapPrev = siteswapTo

        while (true) {
            val num = findTrans(stateTo, stateFrom, lReturn, false)
            if (Constants.DEBUG_TRANSITIONS) {
                println("l_return = $lReturn --> num = $num")
            }
            when (num) {
                0 -> {
                    ++lReturn
                    allocateWorkspace()
                    continue
                }

                1 -> {
                    target?.completed()
                    break
                }

                else -> throw JuggleExceptionInternal("Too many transitions in findReturnTrans()")
            }
        }

        // if we added a hands modifier at the end, such as 'R' or '<R|R>',
        // then remove it (unneeded at end of overall pattern)
        val returnTrans = sb.toString()
            .replace("\n", "")
            .removeSuffix("R")
            .replace(Regex("<(R\\|)+R>$"), "")

        if (Constants.DEBUG_TRANSITIONS) {
            println("return trans = $returnTrans")
        }
        return returnTrans
    }

    // Find transitions from one state to another, with the number of beats
    // given by `l`.
    //
    // Returns the number of transitions found.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun findTrans(
        fromSt: Array<Array<IntArray>>,
        toSt: Array<Array<IntArray>>,
        l: Int,
        all: Boolean
    ): Int {
        lTarget = l

        for (j in 0..<jugglers) {
            for (h in 0..1) {
                for (i in 0..<indexes) {
                    state[0][j][h][i] = fromSt[j][h][i]
                    stateTarget[j][h][i] = toSt[j][h][i]
                }
            }
        }

        targetMaxFilledIndex = getMaxFilledIndex(stateTarget)
        if (Constants.DEBUG_TRANSITIONS) {
            println("-----------------------------------------------------")
            println("starting findTrans()...")
            println("l_target = $lTarget")
            println("target_max_filled_index = $targetMaxFilledIndex")
        }

        startBeat(0)
        findAll = all
        val num = recurse(0, 0, 0)
        if (Constants.DEBUG_TRANSITIONS) {
            println("$num patterns found")
        }
        return num
    }

    // Find valid transitions of length `l_target` from a given position in the
    // pattern, to state `state_target`, and outputs them to GeneratorTarget
    // `target`.
    //
    // Returns the number of transitions found.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun recurse(pos: Int, j: Int, h: Int): Int {
        var pos = pos
        var j = j
        var h = h
        if (Thread.interrupted()) {
            throw JuggleExceptionInterrupted()
        }

        // do a time check
        if (maxTime > 0) {
            if (++loopCounter > LOOP_COUNTER_MAX) {
                loopCounter = 0
                if ((System.currentTimeMillis() - startTimeMillis) > maxTimeMillis) {
                    val message = getStringResource(
                        Res.string.gui_generator_timeout,
                        maxTime.toInt()
                    )
                    throw JuggleExceptionDone(message)
                }
            }
        }

        // find the next position with a throw to make
        while (throwsLeft[pos][j][h] == 0) {
            if (h == 1) {
                h = 0
                ++j
            } else {
                h = 1
            }

            if (j == jugglers) {
                ++pos // move to next beat

                if (pos < lTarget) {
                    startBeat(pos)
                    h = 0
                    j = h
                    continue
                }

                // at the target length; does the transition work?
                if (statesEqual(state[pos], stateTarget)) {
                    if (Constants.DEBUG_TRANSITIONS) {
                        println("got a pattern")
                    }
                    outputPattern()
                    return 1
                } else {
                    return 0
                }
            }
        }

        // iterate over all possible outgoing throws
        val mhnt = MHNThrow()
        mhnt.juggler = j + 1 // source (juggler, hand, index, slot)
        mhnt.hand = h
        mhnt.index = pos
        mhnt.slot = 0
        while (th[j][h][pos][mhnt.slot] != null) {
            ++mhnt.slot
        }

        // loop over target (index, juggler, hand)
        //
        // Iterate over indices in a certain way to get the output ordering we
        // want. The most "natural" transitions are where each throw fills the
        // target state directly, so start with throws that are high enough to
        // do this. Then loop around to small indices.
        val tiMin = pos + 1
        val tiMax = min(pos + min(indexes, 35), lTarget + targetMaxFilledIndex)
        val tiThreshold = min(max(lTarget, tiMin), tiMax)

        var ti = tiThreshold
        var num = 0

        while (true) {
            for (tj in 0..<jugglers) {
                for (th in 0..1) {
                    val ts = state[pos + 1][tj][th][ti - pos - 1]  // target slot
                    val finali = ti - lTarget  // target index in final state

                    if (finali in 0..<indexes) {
                        if (ts >= stateTarget[tj][th][finali]) {
                            continue   // inconsistent with final state
                        }
                    } else if (ts >= targetOccupancy) {
                        continue
                    }

                    mhnt.targetjuggler = tj + 1
                    mhnt.targethand = th
                    mhnt.targetindex = ti
                    mhnt.targetslot = ts

                    if (Constants.DEBUG_TRANSITIONS) {
                        println("trying throw $mhnt")
                    }

                    if (!isThrowValid(pos, mhnt)) {
                        continue
                    }

                    if (Constants.DEBUG_TRANSITIONS) {
                        val sb = ".  ".repeat(pos) +
                            mhnt
                        println(sb)
                    }

                    addThrow(pos, mhnt)
                    num += recurse(pos, j, h)
                    removeThrow(pos, mhnt)

                    if (!findAll && num > 0) {
                        return num
                    }

                    if (maxNum in 1..num) {
                        val message = getStringResource(Res.string.gui_generator_spacelimit, maxNum)
                        throw JuggleExceptionDone(message)
                    }
                }
            }

            ++ti
            if (ti > tiMax) {
                ti = tiMin
            }
            if (ti == tiThreshold) {
                break
            }
        }

        return num
    }

    // Do additional validation that a throw is allowed at a given position in the
    // pattern.
    //
    // Note this is called prior to adding the throw to the pattern, so future
    // states do not reflect the impact of this throw, nor does th[].

    private fun isThrowValid(pos: Int, mhnt: MHNThrow): Boolean {
        val j = mhnt.juggler - 1
        val h = mhnt.hand
        val i = mhnt.index
        val targetj = mhnt.targetjuggler - 1
        val targeth = mhnt.targethand
        val targeti = mhnt.targetindex

        // check #1: throw can't be more than 35 beats long
        if (targeti - i > 35) {
            return false
        }

        // check #2: if we're going to throw on the next beat from the same
        // hand, throw can only be a 1x (i.e. a short hold)
        val nextSt = (if (pos + 1 == lTarget) stateTarget else state[pos + 1])
        if (nextSt[j][h][0] > 0) {
            if (targetj != j || targeth != h || targeti != i + 1) {
                if (Constants.DEBUG_TRANSITIONS) {
                    println("  failed check 2")
                }
                return false
            }
        }

        // check #3: if we threw from the same hand on the previous beat,
        // cannot throw a 1x (would have successive 1x throws, which are
        // equivalent to a long hold (2))
        if (pos > 0 && state[pos - 1][j][h][0] > 0) {
            if (targetj == j && targeth == h && targeti == i + 1) {
                if (Constants.DEBUG_TRANSITIONS) {
                    println("  failed check 3")
                }
                return false
            }
        }

        // check #4: if multiplexing, throw cannot be greater than any
        // preceding throw from the same hand
        for (s in 0..<maxOccupancy) {
            val prev = th[j][h][pos][s] ?: break

            if (MHNThrow.compareThrows(mhnt, prev) == 1) {
                if (Constants.DEBUG_TRANSITIONS) {
                    println("  failed check 4")
                }
                return false
            }
        }

        // check #5: if multiplexing, check for cluster throws if that setting
        // is enabled
        if (maxOccupancy > 1 && !mpAllowClustersFlag) {
            for (s in 0..<maxOccupancy) {
                val prev = th[j][h][pos][s] ?: break

                if (MHNThrow.compareThrows(mhnt, prev) == 0) {
                    if (Constants.DEBUG_TRANSITIONS) {
                        println("  failed check 5")
                    }
                    return false
                }
            }
        }

        // check #6: if multiplexing, check for simultaneous catches if that
        // setting is enabled
        if (targetOccupancy > 1 && !mpAllowSimulcatchesFlag && th[j][h][pos][0] != null) {
            // count how many incoming throws are not holds
            var numNotHolds = 0

            // case 1: incoming throws from within the transition itself
            for (j2 in 0..<jugglers) {
                for (h2 in 0..1) {
                    for (i2 in 0..<pos) {
                        for (s2 in 0..<maxOccupancy) {
                            val mhnt2 = th[j2][h2][i2][s2] ?: break

                            if (mhnt2.targetjuggler == mhnt.juggler &&
                                mhnt2.targethand == mhnt.hand &&
                                mhnt2.targetindex == pos && !mhnt2.isHold
                            ) {
                                ++numNotHolds
                            }
                        }
                    }
                }
            }

            // case 2: incoming throws from the previous pattern
            val th2 = siteswapPrev.th
            val period = siteswapPrev.period
            val slots = siteswapPrev.maxOccupancy

            for (j2 in 0..<jugglers) {
                for (h2 in 0..1) {
                    for (i2 in 0..<period) {
                        for (s2 in 0..<slots) {
                            val mhnt2 = th2[j2][h2][i2][s2] ?: break

                            // Figure out if the throw is landing at the desired time.
                            // The time index for the previous pattern runs from 0 to
                            // `period`. Our transition tacks on to the end, so we
                            // need to add `period` to our transition index to get the
                            // index in the reference frame of the previous pattern.
                            val indexOvershoot = mhnt2.targetindex - (pos + period)

                            // If the overshoot is not negative, and is some even
                            // multiple of the previous pattern's period, then on
                            // some earlier repetition of the pattern it will land at
                            // the target index.
                            val correctIndex =
                                (indexOvershoot >= 0 && (indexOvershoot % period == 0))

                            if (correctIndex && mhnt2.targetjuggler == mhnt.juggler &&
                                mhnt2.targethand == mhnt.hand && !mhnt2.isHold
                            ) {
                                // System.out.println("got a fill from previous pattern");
                                ++numNotHolds
                            }
                        }
                    }
                }
            }

            if (numNotHolds > 1) {
                // System.out.println("filtered out a pattern");
                if (Constants.DEBUG_TRANSITIONS) {
                    println("  failed check 6")
                }
                return false
            }
        }

        // check #7: if throw is not a short hold (1x) and there is a nonzero
        // state element S on the beat immediately preceding the target index,
        // then must reserve S slots in the target for the 1x's.
        if (targeti - i != 1 || targetj != j || targeth != h) {
            if (targeti - pos - 2 >= 0) {
                // # of filled slots one beat before
                val reserved = state[pos + 1][targetj][targeth][targeti - pos - 2]

                // maximum allowed slot number
                var maxSlot = targetOccupancy - 1

                val finali = targeti - lTarget // target index in final state
                if (finali in 0..<indexes) {
                    maxSlot = stateTarget[targetj][targeth][finali] - 1
                }

                if (mhnt.targetslot > maxSlot - reserved) {
                    if (Constants.DEBUG_TRANSITIONS) {
                        println("  failed check 7")
                    }
                    return false
                }
            }
        }

        return true
    }

    // Add a throw to the pattern, updating all data structures.

    private fun addThrow(pos: Int, mhnt: MHNThrow) {
        val j = mhnt.juggler - 1
        val h = mhnt.hand
        val i = mhnt.index
        val s = mhnt.slot
        val dj = mhnt.targetjuggler - 1
        val dh = mhnt.targethand
        val di = mhnt.targetindex

        th[j][h][i][s] = mhnt
        throwsLeft[pos][j][h] = throwsLeft[pos][j][h] - 1

        // update future states
        var pos2 = pos + 1
        while (pos2 <= lTarget && pos2 <= di) {
            state[pos2][dj][dh][di - pos2] = state[pos2][dj][dh][di - pos2] + 1
            ++pos2
        }
    }

    // Undo the actions of addThrow().

    private fun removeThrow(pos: Int, mhnt: MHNThrow) {
        val j = mhnt.juggler - 1
        val h = mhnt.hand
        val i = mhnt.index
        val s = mhnt.slot
        val dj = mhnt.targetjuggler - 1
        val dh = mhnt.targethand
        val di = mhnt.targetindex

        th[j][h][i][s] = null
        throwsLeft[pos][j][h] = throwsLeft[pos][j][h] + 1

        // update future states
        var pos2 = pos + 1
        while (pos2 <= lTarget && pos2 <= di) {
            state[pos2][dj][dh][di - pos2] = state[pos2][dj][dh][di - pos2] - 1
            ++pos2
        }
    }

    // Output a completed pattern.

    @Throws(JuggleExceptionInternal::class)
    private fun outputPattern() {
        if (target == null) return
        for (pos in 0..<lTarget) {
            outputBeat(pos)
        }
        val sb = StringBuilder()
        if (jugglers > 1) {
            sb.append('<')
        }
        for (j in 0..<jugglers) {
            for (i in 0..<lTarget) {
                sb.append(out[j][i])
            }
            // if we ended with an unneeded separator, remove it
            if (!sb.isEmpty() && sb[sb.length - 1] == '/') {
                sb.deleteCharAt(sb.length - 1)
            }
            if (j < jugglers - 1) {
                sb.append('|')
            }
        }
        if (jugglers > 1) {
            sb.append('>')
        }

        // If, for any of the jugglers, the parser would assign an async throw
        // on the next beat to the left hand, then add on a hands modifier to
        // force it to reset.
        //
        // We laid out the "from" and "to" patterns starting with the right
        // hands, so we want to preserve this or the patterns and transitions
        // won't glue together into a working whole.
        var needsHandsModifier = false
        for (j in 0..<jugglers) {
            if (!asyncHandRight[j][lTarget]) {
                needsHandsModifier = true
                break
            }
        }

        if (needsHandsModifier) {
            if (jugglers > 1) {
                sb.append('<')
            }
            for (j in 0..<jugglers) {
                sb.append('R')
                if (j < jugglers - 1) {
                    sb.append('|')
                }
            }
            if (jugglers > 1) {
                sb.append('>')
            }
        }

        try {
            target!!.addResult(
                prefix + sb.toString() + suffix,
                "siteswap",
                prefix + sb.toString().trim { it <= ' ' } + suffix
            )
        } catch (jei: JuggleExceptionInternal) {
            if (Constants.VALIDATE_GENERATED_PATTERNS) {
                println("#################")
                printThrowSet()
                println("#################")
            }
            throw jei
        }
    }

    // Creates the string form of the assigned throws at this position in the
    // pattern, and saves into the out[][] array. This also determines the
    // values of async_hand_right[][] for the next position in the pattern.
    //
    // This output must be accurately parsable by SiteswapPattern.

    private fun outputBeat(pos: Int) {
        /*
        Rules:

        - Any juggler with throws for left and right makes a sync throw;
          otherwise an async throw.
        - If any juggler has a sync throw, check if there are throws (by
          any juggler) on the following beat. If there aren't, then output
          two beats together using two-beat sync throws; otherwise output one
          beat and any sync throws are single-beat (with ! after).
        */

        if (!shouldPrint[pos]) {
            // skipping this beat because we already printed on the previous one
            shouldPrint[pos + 1] = true

            for (j in 0..<jugglers) {
                asyncHandRight[j][pos + 1] = !asyncHandRight[j][pos]
                out[j][pos] = ""
            }
            return
        }

        // logic for deciding whether to print next beat along with this one
        var haveSyncThrow = false
        var haveThrowNextBeat = false

        for (j in 0..<jugglers) {
            if (th[j][0][pos][0] != null && th[j][1][pos][0] != null) {
                haveSyncThrow = true
            }
            if (state[pos + 1][j][0][0] > 0 || state[pos + 1][j][1][0] > 0) {
                haveThrowNextBeat = true
            }
        }

        val printDoubleBeat = haveSyncThrow && !haveThrowNextBeat
        shouldPrint[pos + 1] = !printDoubleBeat

        for (j in 0..<jugglers) {
            val sb = StringBuilder()
            var asyncHandRightNext = !asyncHandRight[j][pos]
            var handsThrowing = 0
            if (th[j][0][pos][0] != null) {
                ++handsThrowing
            }
            if (th[j][1][pos][0] != null) {
                ++handsThrowing
            }

            when (handsThrowing) {
                0 -> {
                    if (pos == 0 && siteswapPrev.hasHandsSpecifier) {
                        sb.append('R')
                    }
                    sb.append('0')
                    if (printDoubleBeat) {
                        sb.append('0')
                    }
                }

                1 -> {
                    val needsSlash: Boolean
                    if (th[j][0][pos][0] != null) {
                        if (!asyncHandRight[j][pos]) {
                            sb.append('R')
                            asyncHandRightNext = false
                        } else if (pos == 0 && siteswapPrev.hasHandsSpecifier) {
                            sb.append('R')
                        }
                        needsSlash = outputMultiThrow(pos, j, 0, sb)
                    } else {
                        if (asyncHandRight[j][pos]) {
                            sb.append('L')
                            asyncHandRightNext = true
                        } else if (pos == 0 && siteswapPrev.hasHandsSpecifier) {
                            sb.append('R')
                        }
                        needsSlash = outputMultiThrow(pos, j, 1, sb)
                    }
                    if (needsSlash) {
                        sb.append('/')
                    }
                    if (printDoubleBeat) {
                        sb.append('0')
                    }
                }

                2 -> {
                    if (pos == 0 && siteswapPrev.hasHandsSpecifier) {
                        sb.append('R')
                    }
                    sb.append('(')
                    outputMultiThrow(pos, j, 1, sb)
                    sb.append(',')
                    outputMultiThrow(pos, j, 0, sb)
                    sb.append(')')
                    if (!printDoubleBeat || pos == lTarget - 1) {
                        sb.append('!')
                    }
                }
            }

            asyncHandRight[j][pos + 1] = asyncHandRightNext

            out[j][pos] = sb.toString()
        }
    }

    // Print the set of throws (assumed non-empty) for a given juggler+hand
    // combination.
    //
    // Returns true if a following throw will need a '/' separator, false if not.

    private fun outputMultiThrow(pos: Int, j: Int, h: Int, sb: StringBuilder): Boolean {
        var needsSlash = false

        var numThrows = 0
        for (s in 0..<maxOccupancy) {
            if (th[j][h][pos][s] != null) {
                ++numThrows
            }
        }

        if (numThrows == 0) {
            return false  // should never happen
        }

        if (numThrows > 1) {
            sb.append('[')
        }

        for (s in 0..<maxOccupancy) {
            val mhnt = th[j][h][pos][s] ?: break
            val beats = mhnt.targetindex - mhnt.index
            val isCrossed = (mhnt.hand == mhnt.targethand) xor (beats % 2 == 0)
            val isPass = (mhnt.targetjuggler != mhnt.juggler)

            if (beats < 36) {
                sb.append(Character.forDigit(beats, 36).lowercaseChar())
            } else {
                sb.append('?') // wildcard will parse but not animate
            }

            if (isCrossed) {
                sb.append('x')
            }
            if (isPass) {
                sb.append('p')
                if (jugglers > 2) {
                    sb.append(mhnt.targetjuggler)
                }

                val anotherThrow =
                    ((s + 1) < maxOccupancy && th[j][h][pos][s + 1] != null)
                if (anotherThrow) {
                    sb.append('/')
                }

                needsSlash = true
            } else {
                needsSlash = false
            }
        }

        if (numThrows > 1) {
            sb.append(']')
            needsSlash = false
        }

        return needsSlash
    }

    // Initialize data structures to start filling in pattern at position `pos`.

    private fun startBeat(pos: Int) {
        if (pos == 0) {
            shouldPrint[0] = true

            for (j in 0..<jugglers) {
                asyncHandRight[j][0] = true
            }
        }

        for (j in 0..<jugglers) {
            for (h in 0..1) {
                if (indexes - 1 >= 0) {
                    System.arraycopy(
                        state[pos][j][h],
                        1,
                        state[pos + 1][j][h],
                        0,
                        indexes - 1
                    )
                }
                state[pos + 1][j][h][indexes - 1] = 0

                throwsLeft[pos][j][h] = state[pos][j][h][0]
            }
        }
    }

    // Test if two states are equal.

    private fun statesEqual(s1: Array<Array<IntArray>>, s2: Array<Array<IntArray>>): Boolean {
        for (j in 0..<jugglers) {
            for (h in 0..1) {
                for (i in 0..<indexes) {
                    if (s1[j][h][i] != s2[j][h][i]) {
                        return false
                    }
                }
            }
        }
        return true
    }

    // Output the state to the command line (useful for debugging).

    private fun printState(state: Array<Array<IntArray>>) {
        var lastIndex = 0
        for (i in 0..<indexes) {
            for (j in 0..<jugglers) {
                for (h in 0..1) {
                    if (state[j][h][i] != 0) {
                        lastIndex = i
                    }
                }
            }
        }
        for (i in 0..lastIndex) {
            for (j in 0..<jugglers) {
                for (h in 0..1) {
                    println("  s[" + j + "][" + h + "][" + i + "] = " + state[j][h][i])
                }
            }
        }
    }

    // Find the minimum length of transition, in beats.

    private fun findMinLength(
        fromSt: Array<Array<IntArray>>,
        toSt: Array<Array<IntArray>>
    ): Int {
        var length = 0

        while (true) {
            var done = true

            for (j in 0..<jugglers) {
                for (h in 0..1) {
                    for (i in 0..<indexes - length) {
                        if (fromSt[j][h][i + length] > toSt[j][h][i]) {
                            done = false
                        }
                    }
                }
            }

            if (done) {
                return length
            }
            ++length
        }
    }

    // Find the maximum length of transition, in beats.

    @Suppress("unused")
    private fun findMaxLength(
        fromSt: Array<Array<IntArray>>,
        toSt: Array<Array<IntArray>>
    ): Int {
        var length = 0

        for (i in 0..<indexes) {
            for (j in 0..<jugglers) {
                for (h in 0..1) {
                    if (fromSt[j][h][i] > 0) {
                        length = i + 1
                    }
                }
            }
        }

        return length
    }

    // Find the maximum index of a nonzero element in the target state.

    private fun getMaxFilledIndex(toSt: Array<Array<IntArray>>): Int {
        for (i in indexes - 1 downTo 0) {
            for (j in 0..<jugglers) {
                for (h in 0..1) {
                    if (toSt[j][h][i] > 0) {
                        return i
                    }
                }
            }
        }

        return 0
    }

    // Print the throw matrix to standard output (useful for debugging).

    private fun printThrowSet() {
        val sb = StringBuilder()

        for (pos in 0..<lTarget) {
            for (j in 0..<jugglers) {
                for (h in 0..1) {
                    for (s in 0..<maxOccupancy) {
                        val mhnt = th[j][h][pos][s] ?: continue
                        sb.append(".  ".repeat(pos)).append(mhnt).append('\n')
                    }
                }
            }
        }
        println(sb)
    }

    companion object {
        private const val LOOP_COUNTER_MAX: Int = 20000

        // Execution limits
        private const val TRANS_MAX_PATTERNS: Int = 1000
        private const val TRANS_MAX_TIME: Double = 15.0

        // Run the transitioner from command-line input.

        fun runTransitionerCLI(args: List<String>, target: GeneratorTargetBasic?) {
            if (args.size < 2) {
                val version = getStringResource(Res.string.gui_version, Constants.VERSION)
                val copyright = getStringResource(Res.string.gui_copyright_message, Constants.YEAR)
                var output = "Juggling Lab ${version.lowercase()}\n"
                output += "$copyright\n"
                output += getStringResource(Res.string.gui_gpl_message) + "\n\n"

                var intro = getStringResource(Res.string.gui_transitioner_intro)
                if (getCurrentPlatform().startsWith("windows", ignoreCase = true)) {
                    // replace single quotes with double quotes in Windows examples
                    intro = intro.replace("'", "\"")
                }
                output += intro
                println(output)
                return
            }

            if (target == null) {
                return
            }

            try {
                val sst = SiteswapTransitioner()
                sst.initTransitioner(args)

                if (sst.noLimitsFlag) {
                    sst.runTransitioner(target)
                } else {
                    sst.runTransitioner(target, TRANS_MAX_PATTERNS, TRANS_MAX_TIME)
                }
            } catch (e: JuggleExceptionDone) {
                println(e.message)
            } catch (e: Exception) {
                val message = getStringResource(Res.string.error) + ": " + e.message
                println(message)
            }
        }
    }
}
