//
// MhnHands.kt
//
// This class parses the "hands" parameter in MHN notation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlExpandRepeats
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlParseFiniteDouble
import org.jugglinglab.util.jlSplitOnCharOutsideParens
import org.jugglinglab.util.jlToStringRounded

data class MhnHands(
    val config: String
) {
    private val numberOfJugglers: Int
    private val numberOfBeats: IntArray
    private val numberOfCoordsPerBeat: Array<IntArray>
    private val catchIndexPerBeat: Array<IntArray>
    private val handCoords: Array<Array<Array<DoubleArray?>>>

    init {
        val cleanStr = jlExpandRepeats(config).filterNot { it in "<>{}" }
        val jugglerStrings = cleanStr.split('|', '!')

        numberOfJugglers = jugglerStrings.size

        val jugglerData = jugglerStrings.map { jugglerStr ->
            val beatStrings = jlSplitOnCharOutsideParens(jugglerStr.trim(), '.')
            beatStrings.map { beatStr ->
                val coordTokens = mutableListOf<DoubleArray?>()
                var catchIndex = -1
                var gotThrow = false

                var pos = 0
                while (pos < beatStr.length) {
                    when (val ch = beatStr[pos]) {
                        ' ' -> {
                            // character is ignored
                            ++pos
                        }
                        '-' -> {
                            // placeholder; interpolate from other nearby coordinates
                            coordTokens.add(null)
                            ++pos
                        }
                        'T', 't' -> {
                            // marks a throw
                            if (coordTokens.isNotEmpty()) {
                                val message = jlGetStringResource(Res.string.error_hands_tnotstart)
                                throw JuggleExceptionUser(message)
                            }
                            if (gotThrow) {
                                val message = jlGetStringResource(Res.string.error_hands_toomanycoords)
                                throw JuggleExceptionUser(message)
                            }
                            gotThrow = true
                            ++pos
                        }
                        'C', 'c' -> {
                            // marks a catch
                            if (coordTokens.isEmpty()) {
                                val message = jlGetStringResource(Res.string.error_hands_catstart)
                                throw JuggleExceptionUser(message)
                            }
                            if (catchIndex != -1) {
                                val message = jlGetStringResource(Res.string.error_hands_toomanycatches)
                                throw JuggleExceptionUser(message)
                            }
                            catchIndex = coordTokens.size
                            ++pos
                        }
                        '(' -> {
                            // position coordinate specified
                            val closeIndex = beatStr.indexOf(')', pos + 1)
                            if (closeIndex < 0) {
                                val message = jlGetStringResource(Res.string.error_hands_noparen)
                                throw JuggleExceptionUser(message)
                            }

                            val coordStr = beatStr.substring(pos + 1, closeIndex)
                            try {
                                val parts = coordStr.split(',').map { jlParseFiniteDouble(it.trim()) }
                                val coord = DoubleArray(3)
                                coord[0] = parts.getOrElse(0) { 0.0 }
                                // Note: y and z are swapped from input
                                coord[2] = parts.getOrElse(1) { 0.0 }
                                coord[1] = parts.getOrElse(2) { 0.0 }
                                coordTokens.add(coord)
                            } catch (_: NumberFormatException) {
                                val message = jlGetStringResource(Res.string.error_hands_coordinate)
                                throw JuggleExceptionUser(message)
                            }
                            pos = closeIndex + 1
                        }
                        else -> {
                            val message = jlGetStringResource(Res.string.error_hands_character, ch.toString())
                            throw JuggleExceptionUser(message)
                        }
                    }
                }

                if (coordTokens.size < 2) {
                    val message = jlGetStringResource(Res.string.error_hands_toofewcoords)
                    throw JuggleExceptionUser(message)
                }
                if (coordTokens[0] == null) {
                    val message = jlGetStringResource(Res.string.error_hands_nothrow)
                    throw JuggleExceptionUser(message)
                }

                val finalCatchIndex = if (catchIndex != -1) catchIndex else coordTokens.size - 1
                if (coordTokens[finalCatchIndex] == null) {
                    val message = jlGetStringResource(Res.string.error_hands_nocatch)
                    throw JuggleExceptionUser(message)
                }

                Triple(coordTokens.size, finalCatchIndex, coordTokens.toTypedArray())
            }
        }
        numberOfBeats = IntArray(numberOfJugglers) { jugglerData[it].size }
        numberOfCoordsPerBeat = Array(numberOfJugglers) { jugglerIndex ->
            IntArray(jugglerData[jugglerIndex].size) { beatIndex ->
                jugglerData[jugglerIndex][beatIndex].first
            }
        }
        catchIndexPerBeat = Array(numberOfJugglers) { jugglerIndex ->
            IntArray(jugglerData[jugglerIndex].size) { beatIndex ->
                jugglerData[jugglerIndex][beatIndex].second
            }
        }
        handCoords = Array(numberOfJugglers) { jugglerIndex ->
            Array(jugglerData[jugglerIndex].size) { beatIndex ->
                jugglerData[jugglerIndex][beatIndex].third
            }
        }
    }

    fun getPeriod(juggler: Int): Int {
        return numberOfBeats[(juggler - 1) % numberOfJugglers]
    }

    fun getNumberOfCoordinates(juggler: Int, beat: Int): Int {
        return numberOfCoordsPerBeat[(juggler - 1) % numberOfJugglers][beat]
    }

    // Return the index value for juggler `juggler` and beat `beat`, when the
    // catch is made.
    //
    // Note the throw is always assumed to happen at index 0.

    fun getCatchIndex(juggler: Int, beat: Int): Int {
        return catchIndexPerBeat[(juggler - 1) % numberOfJugglers][beat]
    }

    // Return the hand coordinate at `index`, for juggler `juggler` on beat `beat`.
    // Note that both `beat` and `index` are indexed from 0.

    fun getCoordinate(juggler: Int, beat: Int, index: Int): Coordinate? {
        if (beat >= getPeriod(juggler) || index >= getNumberOfCoordinates(juggler, beat)) {
            return null
        }
        val coord = handCoords[(juggler - 1) % numberOfJugglers][beat][index]
        return coord?.let { Coordinate(it[0], it[1], it[2]) }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        if (numberOfJugglers > 1) {
            sb.append("<")
        }
        for (j in 0..<numberOfJugglers) {
            if (j != 0) {
                sb.append("|")
            }
            for (i in 0..<numberOfBeats[j]) {
                val catchIndex = catchIndexPerBeat[j][i]
                for (k in 0..<numberOfCoordsPerBeat[j][i]) {
                    if (k == catchIndex && k != numberOfCoordsPerBeat[j][i] - 1) {
                        sb.append("c")
                    }
                    val coord = handCoords[j][i][k]
                    if (coord == null) {
                        sb.append("-")
                    } else {
                        val c0 = jlToStringRounded(coord[0], 4)
                        val c1 = jlToStringRounded(coord[2], 4)
                        val c2 = jlToStringRounded(coord[1], 4)
                        sb.append("(").append(c0)
                        if (c1 != "0" || c2 != "0") {
                            sb.append(",").append(c1)
                        }
                        if (c2 != "0") {
                            sb.append(",").append(c2)
                        }
                        sb.append(")")
                    }
                }
                sb.append(".")
            }
        }
        if (numberOfJugglers > 1) {
            sb.append(">")
        }
        return sb.toString()
    }
}
