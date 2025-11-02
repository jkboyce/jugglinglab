//
// MHNHands.kt
//
// This class parses the "hands" parameter in MHN notation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.*
import java.text.MessageFormat

class MHNHands(str: String) {
    var numberOfJugglers: Int
        private set
    private var size: IntArray
    private var coords: Array<IntArray>
    private var catches: Array<IntArray>
    private var handpath: Array<Array<Array<DoubleArray?>>>

    init {
        val cleanStr = jlExpandRepeats(str).replace("[<>{}]".toRegex(), "")
        val jugglerStrings = cleanStr.split(Regex("[|!]"))

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
                                throw JuggleExceptionUser(errorstrings.getString("Error_hands_Tnotstart"))
                            }
                            if (gotThrow) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_hands_toomanycoords"))
                            }
                            gotThrow = true
                            ++pos
                        }
                        'C', 'c' -> {
                            // marks a catch
                            if (coordTokens.isEmpty()) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_hands_Catstart"))
                            }
                            if (catchIndex != -1) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_hands_toomanycatches"))
                            }
                            catchIndex = coordTokens.size
                            ++pos
                        }
                        '(' -> {
                            // position coordinate specified
                            val closeIndex = beatStr.indexOf(')', pos + 1)
                            if (closeIndex < 0) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_hands_noparen"))
                            }

                            val coordStr = beatStr.substring(pos + 1, closeIndex)
                            try {
                                val parts = coordStr.split(',').map { jlParseDouble(it.trim()) }
                                val coord = DoubleArray(3)
                                coord[0] = parts.getOrElse(0) { 0.0 }
                                // Note: y and z are swapped from input
                                coord[2] = parts.getOrElse(1) { 0.0 }
                                coord[1] = parts.getOrElse(2) { 0.0 }
                                coordTokens.add(coord)
                            } catch (_: NumberFormatException) {
                                throw JuggleExceptionUser(errorstrings.getString("Error_hands_coordinate"))
                            }
                            pos = closeIndex + 1
                        }
                        else -> {
                            val template: String = errorstrings.getString("Error_hands_character")
                            val arguments = arrayOf<Any?>(ch.toString())
                            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                        }
                    }
                }

                if (coordTokens.size < 2) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_hands_toofewcoords"))
                }
                if (coordTokens[0] == null) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_hands_nothrow"))
                }

                val finalCatchIndex = if (catchIndex != -1) catchIndex else coordTokens.size - 1
                if (coordTokens[finalCatchIndex] == null) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_hands_nocatch"))
                }

                Triple(coordTokens.size, finalCatchIndex, coordTokens.toTypedArray())
            }
        }
        size = IntArray(numberOfJugglers) { jugglerData[it].size }
        coords = Array(numberOfJugglers) { jugglerIndex ->
            IntArray(jugglerData[jugglerIndex].size) { beatIndex ->
                jugglerData[jugglerIndex][beatIndex].first
            }
        }
        catches = Array(numberOfJugglers) { jugglerIndex ->
            IntArray(jugglerData[jugglerIndex].size) { beatIndex ->
                jugglerData[jugglerIndex][beatIndex].second
            }
        }
        handpath = Array(numberOfJugglers) { jugglerIndex ->
            Array(jugglerData[jugglerIndex].size) { beatIndex ->
                jugglerData[jugglerIndex][beatIndex].third
            }
        }
    }

    fun getPeriod(juggler: Int): Int {
        val j = (juggler - 1) % numberOfJugglers
        return size[j]
    }

    fun getNumberOfCoordinates(juggler: Int, pos: Int): Int {
        val j = (juggler - 1) % numberOfJugglers
        return coords[j][pos]
    }

    // Return the index value for juggler `juggler` and beat `pos`, when the
    // catch is made.
    //
    // Note the throw is always assumed to happen at index 0.

    fun getCatchIndex(juggler: Int, pos: Int): Int {
        val j = (juggler - 1) % numberOfJugglers
        return catches[j][pos]
    }

    // Return the hand coordinate at `index`, for juggler `juggler` on beat `pos`.
    // Note that both `pos` and `index` are indexed from 0.

    fun getCoordinate(juggler: Int, pos: Int, index: Int): Coordinate? {
        if (pos >= getPeriod(juggler) || index >= getNumberOfCoordinates(juggler, pos)) {
            return null
        }
        val j = (juggler - 1) % numberOfJugglers
        if (handpath[j][pos][index] == null) {
            return null
        }
        return Coordinate(
            handpath[j][pos][index]!![0], handpath[j][pos][index]!![1], handpath[j][pos][index]!![2]
        )
    }
}
