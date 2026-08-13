//
// HandSiteswap.kt
//
// Implements Hand Siteswap (HSS) functionality.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.Permutation
import kotlin.math.max

// This function is the external interface for the Hand Siteswap processor. It
// takes the HSS parameters as inputs and produces an equivalent sync passing
// pattern in Juggling Lab's siteswap notation.
//
// See SiteswapPattern.fromParameters().

class ConvertedPatternInfo(
    val convertedPattern: String,
    val dwellBeatsArray: DoubleArray
)

@Throws(JuggleExceptionUser::class)
fun processHandSiteswap(
    objectPatternString: String,
    handPatternString: String,
    hold: Boolean,
    dwellmax: Boolean,
    handspec: String?,
    dwell: Double
): ConvertedPatternInfo {
    // object siteswap
    val (objectPattern, bounceInfo) = parseObjectPattern(objectPatternString)
    validateObjectPattern(objectPattern)

    // hand siteswap
    val (handPattern, numHands) = parseHandPattern(handPatternString)
    val handOrbitPeriod = validateHandPattern(handPattern)

    val handMap = if (handspec != null) {
        parseHandspec(handspec, numHands)
    } else {
        defaultHandspec(numHands)
    }
    val numJugglers = max(1, handMap.maxOfOrNull { it[0] } ?: 1)

    return convertPatternFromHss(
        objectPattern,
        handPattern,
        handOrbitPeriod,
        handMap,
        numJugglers,
        hold,
        dwellmax,
        dwell,
        bounceInfo
    )
}

//------------------------------------------------------------------------------
// Process the object pattern
//------------------------------------------------------------------------------

// Convert input pattern string to a list of throws made on each beat.
//
// Also, ensure the object pattern is async (multiplex is allowed), and do the
// average test.

private data class OssPatternInfo(
    val objectPattern: List<List<Char>>,
    val bounceInfo: List<List<String?>>
)

@Suppress("unused", "VariableNeverRead", "AssignedValueIsNeverRead")
@Throws(JuggleExceptionUser::class)
private fun parseObjectPattern(objectPatternString: String): OssPatternInfo {
    var muxThrow = false
    var muxThrowFound = false
    var minOneThrow = false
    var throwSum = 0
    var numBeats = 0
    var subBeats = 0 // for multiplex throws
    var numObj = 0
    val objectPattern = mutableListOf<MutableList<Char>>()
    val bounceInfo = mutableListOf<MutableList<String?>>()
    val bounceState = BounceState()

    for ((i, c) in objectPatternString.withIndex()) {
        if (muxThrow) {
            if (c in '0'..'9' || c in 'a'..'z') {
                minOneThrow = true
                muxThrowFound = true
                objectPattern[numBeats - 1].add(subBeats, c)
                bounceInfo[numBeats - 1].add(subBeats, "null")
                subBeats++
                throwSum += c.siteswapValue
                bounceState.onThrowChar()
            } else if (c == ']') {
                if (muxThrowFound) {
                    muxThrow = false
                    muxThrowFound = false
                    subBeats = 0
                    bounceState.reset()
                } else {
                    val message = jlGetStringResource(
                        Res.string.error_hss_object_syntax_error_at_pos,
                        i + 1
                    )
                    throw JuggleExceptionUser(message)
                }
            } else if (c.isWhitespace()) {
                bounceState.reset()
            } else if (!bounceState.processBounceChar(c, bounceInfo, numBeats, subBeats - 1, i + 1)) {
                val message = jlGetStringResource(
                    Res.string.error_hss_object_syntax_error_at_pos,
                    i + 1
                )
                throw JuggleExceptionUser(message)
            }
        } else {
            if (c in '0'..'9' || c in 'a'..'z') {
                minOneThrow = true
                objectPattern.add(numBeats, mutableListOf(c))
                bounceInfo.add(numBeats, mutableListOf("null"))
                numBeats++
                throwSum += c.siteswapValue
                bounceState.onThrowChar()
            } else if (c == '[') {
                muxThrow = true
                objectPattern.add(numBeats, mutableListOf())
                bounceInfo.add(numBeats, mutableListOf())
                numBeats++
                bounceState.reset()
            } else if (c.isWhitespace()) {
                bounceState.reset()
            } else if (!bounceState.processBounceChar(c, bounceInfo, numBeats, subBeats, i + 1)) {
                val message = jlGetStringResource(
                    Res.string.error_hss_object_syntax_error_at_pos,
                    i + 1
                )
                throw JuggleExceptionUser(message)
            }
        }
    }

    if (!muxThrow && minOneThrow) {
        if (throwSum % numBeats == 0) {
            numObj = throwSum / numBeats
        } else {
            val message = jlGetStringResource(Res.string.error_hss_bad_average_object)
            throw JuggleExceptionUser(message)
        }
    } else {
        val message = jlGetStringResource(Res.string.error_hss_syntax_error)
        throw JuggleExceptionUser(message)
    }
    // append a space after setting the bounceInfo list. Eventually bounceInfo
    // is appended to iph so the space will get transferred there.
    for (strings in bounceInfo) {
        for (j in strings.indices) {
            strings[j] = if (strings[j] == "null") " " else "${strings[j]} "
        }
    }
    return OssPatternInfo(objectPattern, bounceInfo)
}

// Helper class for parseObjectPattern()

private class BounceState {
    var b1 = false
    var b2 = false
    var b3 = false

    fun reset() {
        b1 = false
        b2 = false
        b3 = false
    }

    fun onThrowChar() {
        b1 = true
        b2 = false
        b3 = false
    }

    @Throws(JuggleExceptionUser::class)
    fun processBounceChar(
        c: Char,
        bounceInfo: MutableList<MutableList<String?>>,
        numBeats: Int,
        subIdx: Int,
        pos: Int
    ): Boolean {
        if (c == 'B' || c == 'F' || c == 'L' || c == 'H') {
            if (numBeats <= 0) {
                val message =
                    jlGetStringResource(Res.string.error_hss_object_syntax_error_at_pos, pos)
                throw JuggleExceptionUser(message)
            }
            val bounceList = bounceInfo[numBeats - 1]
            when (c) {
                'B' -> {
                    if (b1) {
                        bounceList[subIdx] = "B"
                        b1 = false
                        b2 = true
                        return true
                    }
                }

                'F', 'L' -> {
                    if (b2) {
                        bounceList[subIdx] = "B$c"
                        b2 = false
                        return true
                    } else if (b3) {
                        bounceList[subIdx] = "BH$c"
                        b3 = false
                        return true
                    }
                }

                'H' -> {
                    if (b2) {
                        bounceList[subIdx] = "BH"
                        b2 = false
                        b3 = true
                        return true
                    }
                }
            }
            val message =
                jlGetStringResource(Res.string.error_hss_object_syntax_error_at_pos, pos)
            throw JuggleExceptionUser(message)
        }
        return false
    }
}

// Validate the object pattern using the permutation test.

@Throws(JuggleExceptionUser::class)
private fun validateObjectPattern(objectPattern: List<List<Char>>) {
    val numBeats = objectPattern.size
    val cmp = IntArray(numBeats)
    for (i in 0..<numBeats) {
        for (c in objectPattern[i]) {
            val modulo = (c.siteswapValue + i) % numBeats
            cmp[modulo]++
        }
    }

    for (i in 0..<numBeats) {
        if (cmp[i] != objectPattern[i].size) {
            val message = jlGetStringResource(Res.string.error_hss_object_pattern_invalid)
            throw JuggleExceptionUser(message)
        }
    }
}

//------------------------------------------------------------------------------
// Process the hand pattern
//------------------------------------------------------------------------------

// Convert input hand pattern string to a list of throws made on each beat.
// Returns number of hands, used later to build handMap.
//
// Also, ensure hand pattern is a vanilla pattern (i.e., no multiplex), and do
// the average test.

private data class HssPatternInfo(
    val handPattern: List<Char>,
    val numHands: Int
)

@Throws(JuggleExceptionUser::class)
private fun parseHandPattern(handPatternString: String): HssPatternInfo {
    var throwSum = 0
    var numBeats = 0
    val numHands: Int
    val handPattern = mutableListOf<Char>()
    for ((i, c) in handPatternString.withIndex()) {
        if (c in '0'..'9' || c in 'a'..'z') {
            handPattern.add(numBeats, c)
            numBeats++
            throwSum += c.siteswapValue
            continue
        } else if (c.isWhitespace()) {
            continue
        } else {
            val message =
                jlGetStringResource(Res.string.error_hss_hand_syntax_error_at_pos, i + 1)
            throw JuggleExceptionUser(message)
        }
    }
    if (throwSum % numBeats == 0) {
        numHands = throwSum / numBeats
    } else {
        val message = jlGetStringResource(Res.string.error_hss_bad_average_hand)
        throw JuggleExceptionUser(message)
    }
    return HssPatternInfo(handPattern, numHands)
}

// Validate the hand pattern using the permutation test.
//
// Returns the overall hand orbit period, which is lcm of individual hand orbit
// periods.

@Throws(JuggleExceptionUser::class)
private fun validateHandPattern(handPattern: List<Char>): Int {
    val numBeats = handPattern.size
    val mods = IntArray(numBeats)
    val cmp = IntArray(numBeats)

    for (i in 0..<numBeats) {
        val modulo = (handPattern[i].siteswapValue + i) % numBeats
        mods[i] = modulo
        cmp[modulo]++
    }
    for (i in 0..<numBeats) {
        if (cmp[i] != 1) {
            val message = jlGetStringResource(Res.string.error_hss_hand_pattern_invalid)
            throw JuggleExceptionUser(message)
        }
    }

    val orb = IntArray(numBeats)
    val touched = BooleanArray(numBeats)
    var handOrbitPeriod = 1

    for (i in 0..<numBeats) {
        if (!touched[i]) {
            orb[i] = handPattern[i].siteswapValue
            touched[i] = true
            var j = mods[i]
            while (j != i) {
                orb[i] += handPattern[j].siteswapValue
                touched[j] = true
                j = mods[j]
            }
        }
        if (orb[i] != handOrbitPeriod && orb[i] != 0) {
            handOrbitPeriod = Permutation.lcm(orb[i], handOrbitPeriod)
        }
    }
    return handOrbitPeriod
}

//------------------------------------------------------------------------------
// Process the handspec (optional)
//------------------------------------------------------------------------------

// Read and validate user defined handspec. If valid, convert to handmap
// assigning juggler number and that juggler's left or right hand to each hand.

private val HANDSPEC_PAIR_REGEX = Regex("""\(\s*(\d*)\s*,\s*(\d*)\s*\)""")

@Throws(JuggleExceptionUser::class)
private fun parseHandspec(handspec: String, numHands: Int): Array<IntArray> {
    val handMap = Array(numHands) { IntArray(2) }
    var jugglerNumber = 0
    var matchFound = false
    var lastEnd = 0

    for (match in HANDSPEC_PAIR_REGEX.findAll(handspec)) {
        val prefix = handspec.substring(lastEnd, match.range.first)
        if (prefix.any { !it.isWhitespace() }) {
            val firstErrIdx = lastEnd + prefix.indexOfFirst { !it.isWhitespace() }
            val message = jlGetStringResource(
                Res.string.error_hss_handspec_syntax_error_at_pos,
                firstErrIdx + 1
            )
            throw JuggleExceptionUser(message)
        }
        lastEnd = match.range.last + 1
        matchFound = true
        jugglerNumber++

        val (leftStr, rightStr) = match.destructured

        if (leftStr.isEmpty() && rightStr.isEmpty()) {
            val message =
                jlGetStringResource(Res.string.error_hss_at_least_one_hand_per_juggler)
            throw JuggleExceptionUser(message)
        }

        if (leftStr.isNotEmpty()) {
            val leftHand = leftStr.toInt()
            if (leftHand !in 1..numHands) {
                val message = jlGetStringResource(
                    Res.string.error_hss_hand_number_out_of_range,
                    leftHand
                )
                throw JuggleExceptionUser(message)
            }
            if (handMap[leftHand - 1][0] != 0) {
                val message = jlGetStringResource(
                    Res.string.error_hss_hand_assigned_more_than_once,
                    leftHand
                )
                throw JuggleExceptionUser(message)
            }
            handMap[leftHand - 1][0] = jugglerNumber
            handMap[leftHand - 1][1] = 0 // left hand
        }

        if (rightStr.isNotEmpty()) {
            val rightHand = rightStr.toInt()
            if (rightHand !in 1..numHands) {
                val message = jlGetStringResource(
                    Res.string.error_hss_hand_number_out_of_range,
                    rightHand
                )
                throw JuggleExceptionUser(message)
            }
            if (handMap[rightHand - 1][0] != 0) {
                val message = jlGetStringResource(
                    Res.string.error_hss_hand_assigned_more_than_once,
                    rightHand
                )
                throw JuggleExceptionUser(message)
            }
            handMap[rightHand - 1][0] = jugglerNumber
            handMap[rightHand - 1][1] = 1 // right hand
        }
    }

    val trailing = handspec.substring(lastEnd)
    if (trailing.any { !it.isWhitespace() }) {
        val firstErrIdx = lastEnd + trailing.indexOfFirst { !it.isWhitespace() }
        val message = jlGetStringResource(
            Res.string.error_hss_handspec_syntax_error_at_pos,
            firstErrIdx + 1
        )
        throw JuggleExceptionUser(message)
    }

    if (!matchFound) {
        val message = jlGetStringResource(Res.string.error_hss_handspec_syntax_error)
        throw JuggleExceptionUser(message)
    }

    if (jugglerNumber > numHands) {
        val message = jlGetStringResource(
            Res.string.error_hss_handspec_too_many_jugglers,
            numHands
        )
        throw JuggleExceptionUser(message)
    }

    for (i in 0..<numHands) {
        if (handMap[i][0] == 0) {
            val message = jlGetStringResource(
                Res.string.error_hss_juggler_not_assigned_to_hand,
                i + 1
            )
            throw JuggleExceptionUser(message)
        }
    }

    return handMap
}

// Build a default handmap in the absence of user defined handspec.
//
// - assume numHands/2 jugglers if numHands even, else (numHands+1)/2 jugglers
// - assign hand 1 to J1 right hand, hand 2 to J2 right hand and so on
// - once all right hands assigned, come back to J1 and start assigning left hand

private fun defaultHandspec(numHands: Int): Array<IntArray> {
    val handMap = Array(numHands) { IntArray(2) }
    val numJugglers: Int = if (numHands % 2 == 0) {
        numHands / 2
    } else {
        (numHands + 1) / 2
    }

    for (i in 0..<numHands) {
        if (i < numJugglers) {
            handMap[i][0] = i + 1  // juggler number
            handMap[i][1] = 1  // 0 for left hand, 1 for right
        } else {
            handMap[i][0] = i + 1 - numJugglers
            handMap[i][1] = 0
        }
    }
    return handMap
}

//------------------------------------------------------------------------------
// Convert to Juggling Lab sync passing notation
//------------------------------------------------------------------------------

// Convert oss hss format to Juggling Lab synchronous passing notation with
// suppressed empty beats so that odd synchronous throws are also allowed.

@Throws(JuggleExceptionUser::class)
private fun convertPatternFromHss(
    objectPattern: List<List<Char>>,
    handPattern: List<Char>,
    handOrbitPeriod: Int,
    handMap: Array<IntArray>,
    numJugglers: Int,
    hold: Boolean,
    dwellmax: Boolean,
    dwell: Double,
    bounceInfo: List<List<String?>>
): ConvertedPatternInfo {
    val fullPeriod = Permutation.lcm(objectPattern.size, handOrbitPeriod)

    // extend patterns to full period
    val osPat = List(fullPeriod) { i -> objectPattern[i % objectPattern.size] }
    val bncList = List(fullPeriod) { i -> bounceInfo[i % objectPattern.size] }
    val hssPat = List(fullPeriod) { i -> handPattern[i % handPattern.size] }

    val jugglerInfo = computeJugglerHandMap(osPat, hssPat, handMap, fullPeriod)
    val dwellBeatsArray = computeDwellBeats(osPat, jugglerInfo, fullPeriod, dwellmax, dwell)
    val throwModifiers =
        computeThrowModifiers(osPat, hssPat, jugglerInfo, bncList, fullPeriod, hold)
    val convertedPattern =
        buildPatternString(osPat, jugglerInfo, throwModifiers, fullPeriod, numJugglers)

    return ConvertedPatternInfo(convertedPattern, dwellBeatsArray)
}

@Throws(JuggleExceptionUser::class)
private fun computeJugglerHandMap(
    objectPattern: List<List<Char>>,
    handPattern: List<Char>,
    handMap: Array<IntArray>,
    fullPeriod: Int
): List<JugglerHand> {
    val ah = IntArray(fullPeriod)
    val assignDone = BooleanArray(fullPeriod)
    val ji = mutableListOf<JugglerHand>()

    var currHand = 0
    for (i in 0..<fullPeriod) {
        if (handPattern[i] == '0') {
            for (j in objectPattern[i].indices) {
                if (objectPattern[i][j] != '0') {
                    val message = jlGetStringResource(
                        Res.string.error_hss_no_hand_to_throw_at_beat, i + 1
                    )
                    throw JuggleExceptionUser(message)
                }
            }
            ji.add(JugglerHand(0, -1))
            assignDone[i] = true
        } else {
            if (!assignDone[i]) {
                currHand++
                ah[i] = currHand
                assignDone[i] = true
                var next = (i + handPattern[i].siteswapValue) % fullPeriod
                while (next != i) {
                    ah[next] = currHand
                    assignDone[next] = true
                    next = (next + handPattern[next].siteswapValue) % fullPeriod
                }
            }
            ji.add(JugglerHand(handMap[ah[i] - 1][0], handMap[ah[i] - 1][1]))
        }
    }
    return ji
}

private fun computeDwellBeats(
    objectPattern: List<List<Char>>,
    jugglerInfo: List<JugglerHand>,
    fullPeriod: Int,
    dwellmax: Boolean,
    dwell: Double
): DoubleArray {
    val dwellBeatsArray = DoubleArray(fullPeriod)
    val mincaught = IntArray(fullPeriod)

    for (i in 0..<fullPeriod) {
        for (j in objectPattern[i].indices) {
            val curThrow = objectPattern[i][j].siteswapValue
            val tgtIdx = (i + curThrow) % fullPeriod
            if (curThrow > 0) {
                if (mincaught[tgtIdx] == 0 || curThrow < mincaught[tgtIdx]) {
                    mincaught[tgtIdx] = curThrow
                }
            }
        }
    }

    if (!dwellmax) {
        val flag = (0..<fullPeriod).any { i ->
            jugglerInfo[i] == jugglerInfo[(i + 1) % fullPeriod]
        }
        for (i in 0..<fullPeriod) {
            dwellBeatsArray[i] = if (flag) HSS_DWELL_DEFAULT else dwell
            if (dwellBeatsArray[i] >= mincaught[i].toDouble()) {
                dwellBeatsArray[i] = mincaught[i].toDouble() - (1 - HSS_DWELL_DEFAULT)
            }
        }
    } else {
        for (i in 0..<fullPeriod) {
            var j = (i + 1) % fullPeriod
            var diff = 1
            while (jugglerInfo[i] != jugglerInfo[j]) {
                j = (j + 1) % fullPeriod
                diff++
            }
            dwellBeatsArray[j] = diff.toDouble() - (1 - HSS_DWELL_DEFAULT)
        }
        for (i in 0..<fullPeriod) {
            if (dwellBeatsArray[i] >= mincaught[i].toDouble()) {
                dwellBeatsArray[i] = mincaught[i].toDouble() - (1 - HSS_DWELL_DEFAULT)
            } else if (dwellBeatsArray[i] <= 0) {
                dwellBeatsArray[i] = HSS_DWELL_DEFAULT
            }
        }
    }

    val clash = BooleanArray(fullPeriod)
    var clashcnt = 0
    for (i in 0..<fullPeriod) {
        for (j in 1..<fullPeriod) {
            if ((dwellBeatsArray[(i + j) % fullPeriod] - dwellBeatsArray[i] - j) % fullPeriod == 0.0) {
                clash[(i + j) % fullPeriod] = true
                clashcnt++
            }
        }
        while (clashcnt != 0) {
            for (k in 0..<fullPeriod) {
                if (clash[k]) {
                    dwellBeatsArray[k] += HSS_DWELL_DEFAULT / clashcnt
                    clashcnt--
                    clash[k] = false
                }
            }
        }
    }
    return dwellBeatsArray
}

private fun computeThrowModifiers(
    objectPattern: List<List<Char>>,
    handPattern: List<Char>,
    jugglerInfo: List<JugglerHand>,
    bounceInfo: List<List<String?>>,
    fullPeriod: Int,
    hold: Boolean
): List<List<String?>> {
    val iph = mutableListOf<MutableList<String?>>()

    for (i in 0..<fullPeriod) {
        iph.add(mutableListOf())
        for (j in objectPattern[i].indices) {
            val throwVal = objectPattern[i][j].siteswapValue

            val sourceJug = jugglerInfo[i].juggler
            val sourceHnd = jugglerInfo[i].hand
            val targetJug = jugglerInfo[(i + throwVal) % fullPeriod].juggler
            val targetHnd = jugglerInfo[(i + throwVal) % fullPeriod].hand

            var modifier: String? = null
            if (throwVal % 2 == 0 && sourceHnd != targetHnd) {
                modifier = "x"
            } else if (throwVal % 2 != 0 && sourceHnd == targetHnd) {
                modifier = "x"
            }
            if (sourceJug != targetJug) {
                modifier = if (modifier != "x") "p$targetJug" else "xp$targetJug"
            } else if (hold) {
                if (throwVal == handPattern[i].siteswapValue) {
                    modifier = if (modifier != "x") "H" else "xH"
                }
            }
            iph[i].add(modifier)
        }
    }

    for (i in 0..<fullPeriod) {
        for (j in bounceInfo[i].indices) {
            val bnc = bounceInfo[i][j]
            if (bnc != null) {
                iph[i][j] = (iph[i][j] ?: "") + bnc
            }
        }
    }

    return iph
}

private fun buildPatternString(
    objectPattern: List<List<Char>>,
    jugglerInfo: List<JugglerHand>,
    throwModifiers: List<List<String?>>,
    fullPeriod: Int,
    numJugglers: Int
): String = buildString {
    for (i in 0..<fullPeriod) {
        append('<')
        for (currJug in 0..<numJugglers) {
            val isThrowing = (jugglerInfo[i].juggler == currJug + 1)
            val throwingHand = jugglerInfo[i].hand

            fun appendThrowContent() {
                if (objectPattern[i].size > 1) {
                    append('[')
                    for (j in objectPattern[i].indices) {
                        append(objectPattern[i][j])
                        throwModifiers[i][j]?.let { append(it) }
                    }
                    append(']')
                } else {
                    append(objectPattern[i].first())
                    throwModifiers[i].first()?.let { append(it) }
                }
            }

            if (throwingHand == 0) { // left hand
                append('(')
                if (isThrowing) {
                    appendThrowContent()
                    append(",0)!")
                } else {
                    append("0,0)!")
                }
            } else { // right hand (or no hand)
                append("(0,")
                if (isThrowing) {
                    appendThrowContent()
                    append(")!")
                } else {
                    append("0)!")
                }
            }

            append(if (currJug == numJugglers - 1) '>' else '|')
        }
    }
}

private const val HSS_DWELL_DEFAULT: Double = 0.3

private val Char.siteswapValue: Int
    get() = when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'z' -> this - 'a' + 10
        in 'A'..'Z' -> this - 'A' + 10
        else -> -1
    }

private data class JugglerHand(val juggler: Int, val hand: Int)
