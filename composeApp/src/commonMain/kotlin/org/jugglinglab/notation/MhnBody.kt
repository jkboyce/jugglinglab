//
// MhnBody.kt
//
// This class parses the "body" parameter in MHN notation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.jml.JmlPosition
import org.jugglinglab.util.jlParseFiniteDouble
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlExpandRepeats
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlSplitOnCharOutsideParens

data class MhnBody(
    val config: String
) {
    val numberOfJugglers: Int
    private val numberOfBeats: IntArray
    private val numberOfPositionsPerBeat: Array<IntArray>
    private val bodyPositions: Array<Array<Array<DoubleArray?>>>

    init {
        // parse the 'body' string to define juggler movements
        val cleanStr = jlExpandRepeats(config).filterNot { it in "<>{}" }
        val jugglerStrings = cleanStr.split('|', '!')

        numberOfJugglers = jugglerStrings.size
        numberOfBeats = IntArray(numberOfJugglers)
        numberOfPositionsPerBeat = Array(numberOfJugglers) { IntArray(0) }
        bodyPositions = Array(numberOfJugglers) { emptyArray() }

        for ((jugglerIndex, jugglerStr) in jugglerStrings.withIndex()) {
            val beatStrings = jlSplitOnCharOutsideParens(jugglerStr.trim(), '.')
            val beatSize = beatStrings.size
            numberOfBeats[jugglerIndex] = beatSize
            numberOfPositionsPerBeat[jugglerIndex] = IntArray(beatSize)
            bodyPositions[jugglerIndex] = Array(beatSize) { emptyArray() }

            for ((beatIndex, beatStr) in beatStrings.withIndex()) {
                val coordTokens = mutableListOf<DoubleArray?>()
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

                        '(' -> {
                            // position coordinate specified
                            val closeIndex = beatStr.indexOf(')', pos + 1)
                            if (closeIndex < 0) {
                                val message = jlGetStringResource(Res.string.error_body_noparen)
                                throw JuggleExceptionUser(message)
                            }
                            val coordStr = beatStr.substring(pos + 1, closeIndex)
                            try {
                                val parts =
                                    coordStr.split(',').map { jlParseFiniteDouble(it.trim()) }
                                // default z (elevation) value is 100.0 cm
                                val coord = doubleArrayOf(0.0, 0.0, 0.0, 100.0)
                                for ((partsIndex, partsVal) in parts.withIndex()) {
                                    coord[partsIndex] = partsVal
                                }
                                coordTokens.add(coord)
                            } catch (_: NumberFormatException) {
                                val message = jlGetStringResource(Res.string.error_body_coordinate)
                                throw JuggleExceptionUser(message)
                            }
                            pos = closeIndex + 1
                        }

                        else -> {
                            val message =
                                jlGetStringResource(Res.string.error_body_character, ch.toString())
                            throw JuggleExceptionUser(message)
                        }
                    }
                }

                if (coordTokens.isEmpty()) {
                    // A beat with no coordinates implies a single resting position
                    numberOfPositionsPerBeat[jugglerIndex][beatIndex] = 1
                    bodyPositions[jugglerIndex][beatIndex] = arrayOf(null)
                } else {
                    numberOfPositionsPerBeat[jugglerIndex][beatIndex] = coordTokens.size
                    bodyPositions[jugglerIndex][beatIndex] = coordTokens.toTypedArray()
                }
            }
        }
    }

    fun getPeriod(juggler: Int): Int {
        return numberOfBeats[(juggler - 1) % numberOfJugglers]
    }

    fun getNumberOfPositions(juggler: Int, pos: Int): Int {
        return numberOfPositionsPerBeat[(juggler - 1) % numberOfJugglers][pos]
    }

    // Position and index start from 0.

    fun getPosition(juggler: Int, pos: Int, index: Int): JmlPosition? {
        if (pos >= getPeriod(juggler) || index >= getNumberOfPositions(juggler, pos)) {
            return null
        }
        val coord = bodyPositions[(juggler - 1) % numberOfJugglers][pos][index]
        return coord?.let {
            JmlPosition(
                x = it[1],
                y = it[2],
                z = it[3],
                angle = it[0],
                juggler = juggler
            )
        }
    }
}
