//
// MHNHands.kt
//
// This class parses the "hands" parameter in MHN notation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.*

data class MHNHands(
    val str: String
) {
    val numberOfJugglers: Int
    private val size: IntArray
    private val coords: Array<IntArray>
    private val catches: Array<IntArray>
    private val handpath: Array<Array<Array<DoubleArray?>>>

    init {
        val cleanStr = jlExpandRepeats(str).filterNot { it in "<>{}" }
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
                                val message = getStringResource(Res.string.error_hands_tnotstart)
                                throw JuggleExceptionUser(message)
                            }
                            if (gotThrow) {
                                val message = getStringResource(Res.string.error_hands_toomanycoords)
                                throw JuggleExceptionUser(message)
                            }
                            gotThrow = true
                            ++pos
                        }
                        'C', 'c' -> {
                            // marks a catch
                            if (coordTokens.isEmpty()) {
                                val message = getStringResource(Res.string.error_hands_catstart)
                                throw JuggleExceptionUser(message)
                            }
                            if (catchIndex != -1) {
                                val message = getStringResource(Res.string.error_hands_toomanycatches)
                                throw JuggleExceptionUser(message)
                            }
                            catchIndex = coordTokens.size
                            ++pos
                        }
                        '(' -> {
                            // position coordinate specified
                            val closeIndex = beatStr.indexOf(')', pos + 1)
                            if (closeIndex < 0) {
                                val message = getStringResource(Res.string.error_hands_noparen)
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
                                val message = getStringResource(Res.string.error_hands_coordinate)
                                throw JuggleExceptionUser(message)
                            }
                            pos = closeIndex + 1
                        }
                        else -> {
                            val message = getStringResource(Res.string.error_hands_character, ch.toString())
                            throw JuggleExceptionUser(message)
                        }
                    }
                }

                if (coordTokens.size < 2) {
                    val message = getStringResource(Res.string.error_hands_toofewcoords)
                    throw JuggleExceptionUser(message)
                }
                if (coordTokens[0] == null) {
                    val message = getStringResource(Res.string.error_hands_nothrow)
                    throw JuggleExceptionUser(message)
                }

                val finalCatchIndex = if (catchIndex != -1) catchIndex else coordTokens.size - 1
                if (coordTokens[finalCatchIndex] == null) {
                    val message = getStringResource(Res.string.error_hands_nocatch)
                    throw JuggleExceptionUser(message)
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
        return if (handpath[j][pos][index] == null) {
            null
        } else {
            Coordinate(
                handpath[j][pos][index]!![0],
                handpath[j][pos][index]!![1],
                handpath[j][pos][index]!![2]
            )
        }
    }
}
