//
// SiteswapGeneratorConfig.kt
//
// Configuration data class for SiteswapGenerator.
//
// Copyright 1991-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.generator

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlBinomial
import org.jugglinglab.util.jlGetStringResource
import kotlin.math.max
import kotlin.math.min

// Initialize from command line arguments.
//
// In the event of an error, throw a JuggleExceptionUser with a relevant message.

class SiteswapGeneratorConfig @Throws(JuggleExceptionUser::class) constructor(args: List<String>) {
    var n: Int = 0
    var jugglers: Int = 1
    var ht: Int = 0
    var lMin: Int = 0
    var lMax: Int = 0
    var exclude: ArrayList<Regex> = ArrayList()
    var include: ArrayList<Regex> = ArrayList()
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

    init {
        if (Constants.DEBUG_GENERATOR_SUMMARY) {
            println("-----------------------------------------------------")
            println("initializing generator with args:")
            for (arg in args) {
                print("$arg ")
            }
            print("\n")
        }
        if (args.size < 3) {
            val message = jlGetStringResource(Res.string.error_generator_insufficient_input)
            throw JuggleExceptionUser(message)
        }

        val trueMultiplex = parseInputFlags(args)
        configMode()
        parseInputConfig(args)
        findGround()
        configMultiplexing(trueMultiplex)
    }

    // Parse the input parameters beyond the first three command line arguments.

    @Throws(JuggleExceptionUser::class)
    private fun parseInputFlags(args: List<String>): Boolean {
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
                            val message = jlGetStringResource(
                                Res.string.error_number_format,
                                jlGetStringResource(Res.string.gui_simultaneous_throws)
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
                            val message = jlGetStringResource(
                                Res.string.error_number_format,
                                jlGetStringResource(Res.string.gui_jugglers)
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
                            val message = jlGetStringResource(
                                Res.string.error_number_format,
                                jlGetStringResource(Res.string.gui_passing_communication_delay)
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
                            val message = jlGetStringResource(
                                Res.string.error_number_format,
                                jlGetStringResource(Res.string.error_passing_leader_number)
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
                            if (Constants.DEBUG_GENERATOR_DETAILED) {
                                println("adding exclusion $re")
                            }
                            exclude.add(Regex(re))
                        } catch (_: IllegalArgumentException) {
                            val message = jlGetStringResource(Res.string.error_excluded_throws)
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
                            include.add(Regex(re))
                        } catch (_: IllegalArgumentException) {
                            val message = jlGetStringResource(Res.string.error_included_throws)
                            throw JuggleExceptionUser(message)
                        }
                        ++i
                    }
                    --i
                }

                else -> {
                    val message = jlGetStringResource(Res.string.error_unrecognized_option, args[i])
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

    @Throws(JuggleExceptionUser::class)
    private fun parseInputConfig(args: List<String>) {
        try {
            n = args[0].toInt()
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(
                Res.string.error_number_format,
                jlGetStringResource(Res.string.gui_balls)
            )
            throw JuggleExceptionUser(message)
        }
        ht = try {
            if (args[1] == "-") {
                // signal to not specify a maximum throw
                -1
            } else if (args[1].matches("^[0-9]+$".toRegex())) {
                args[1].toInt()  // numbers only
            } else if (args[1].length == 1 && args[1][0] in 'a'..'z') {
                args[1].toInt(36)  // 'a' = 10, 'b' = 11, ...
            } else {
                throw NumberFormatException()
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(
                Res.string.error_number_format,
                jlGetStringResource(Res.string.gui_max__throw)
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
            val message = jlGetStringResource(
                Res.string.error_number_format,
                jlGetStringResource(Res.string.gui_period)
            )
            throw JuggleExceptionUser(message)
        }

        if (n < 1) {
            val message = jlGetStringResource(Res.string.error_generator_too_few_balls)
            throw JuggleExceptionUser(message)
        }
        if (lMax == -1) {
            if (fullflag != 2) {
                val message = jlGetStringResource(Res.string.error_generator_must_be_prime_mode)
                throw JuggleExceptionUser(message)
            }
            if (ht == -1) {
                val message = jlGetStringResource(Res.string.error_generator_underspecified)
                throw JuggleExceptionUser(message)
            }
            val binomialVal = jlBinomial(ht.toLong() * hands, n)
            lMax = min(binomialVal, Int.MAX_VALUE.toLong()).toInt()
            lMax -= (lMax % rhythmPeriod)
        }
        if (ht == -1) {
            ht = min(Int.MAX_VALUE.toLong(), n.toLong() * lMax).toInt()
        }
        // no throws greater than `n * lMax` due to average rule
        ht = min(ht.toLong(), n.toLong() * lMax).toInt()
        if (ht < 1) {
            val message = jlGetStringResource(Res.string.error_generator_height_too_small)
            throw JuggleExceptionUser(message)
        }
        if (lMin < 1 || lMax < 1 || lMin > lMax) {
            val message = jlGetStringResource(Res.string.error_generator_period_problem)
            throw JuggleExceptionUser(message)
        }

        if (jugglers > 1 && !jugglerPermutationsFlag && groundflag != 0) {
            val message = jlGetStringResource(Res.string.error_juggler_permutations)
            throw JuggleExceptionUser(message)
        }

        if ((lMin % rhythmPeriod) != 0 || (lMax % rhythmPeriod) != 0) {
            val message = jlGetStringResource(Res.string.error_period_multiple, rhythmPeriod)
            throw JuggleExceptionUser(message)
        }

        if (Constants.DEBUG_GENERATOR_SUMMARY) {
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

        if (Constants.DEBUG_GENERATOR_DETAILED) {
            println("ground state length: $groundStateLength")
            println("ground state:")
            printState(groundState)
        }
    }

    // Configure the multiplexing-related items.

    private fun configMultiplexing(trueMultiplex: Boolean) {
        // The following variable slotSize serves two functions. It is the size
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
                include.add(Regex(includeRe))
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

    companion object {
        // modes
        const val ASYNC: Int = 0
        const val SYNC: Int = 1

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
            var res = term.replace("\\[", "@")
            res = res.replace("[", "\\[")
            res = res.replace("@", "[")
            res = res.replace("\\]", "@")
            res = res.replace("]", "\\]")
            res = res.replace("@", "]")

            res = res.replace("\\(", "@")
            res = res.replace("(", "\\(")
            res = res.replace("@", "(")
            res = res.replace("\\)", "@")
            res = res.replace(")", "\\)")
            res = res.replace("@", ")")

            res = res.replace("\\|", "@")
            res = res.replace("|", "\\|")
            res = res.replace("@", "|")
            return res
        }
    }
}
