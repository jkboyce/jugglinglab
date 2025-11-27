//
// MHNBody.kt
//
// This class parses the "body" parameter in MHN notation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.generated.resources.*
import jugglinglab.jml.JMLPosition
import jugglinglab.util.*
import jugglinglab.util.jlParseFiniteDouble

class MHNBody(str: String) {
    var numberOfJugglers: Int
        private set
    private var size: IntArray
    private var coords: Array<IntArray>
    private var bodypath: Array<Array<Array<DoubleArray?>>>

    init {
        // parse the 'body' string to define juggler movements
        val cleanStr = jlExpandRepeats(str).replace("[<>{}]".toRegex(), "")
        val jugglerStrings = cleanStr.split(Regex("[|!]"))

        numberOfJugglers = jugglerStrings.size
        size = IntArray(numberOfJugglers)
        coords = Array(numberOfJugglers) { IntArray(0) }
        bodypath = Array(numberOfJugglers) { emptyArray() }

        for ((jugglerIndex, jugglerStr) in jugglerStrings.withIndex()) {
            val beatStrings = jlSplitOnCharOutsideParens(jugglerStr.trim(), '.')
            val beatSize = beatStrings.size
            size[jugglerIndex] = beatSize
            coords[jugglerIndex] = IntArray(beatSize)
            bodypath[jugglerIndex] = Array(beatSize) { emptyArray() }

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
                                val message = getStringResource(Res.string.error_body_noparen)
                                throw JuggleExceptionUser(message)
                            }
                            val coordStr = beatStr.substring(pos + 1, closeIndex)
                            try {
                                val parts = coordStr.split(',').map { jlParseFiniteDouble(it.trim()) }
                                // default z (elevation) value is 100.0 cm
                                val coord = doubleArrayOf(0.0, 0.0, 0.0, 100.0)
                                for ((partsIndex, partsVal) in parts.withIndex()) {
                                    coord[partsIndex] = partsVal
                                }
                                coordTokens.add(coord)
                            } catch (_: NumberFormatException) {
                                val message = getStringResource(Res.string.error_body_coordinate)
                                throw JuggleExceptionUser(message)
                            }
                            pos = closeIndex + 1
                        }
                        else -> {
                            val message = getStringResource(Res.string.error_body_character, ch.toString())
                            throw JuggleExceptionUser(message)
                        }
                    }
                }

                if (coordTokens.isEmpty()) {
                    // A beat with no coordinates implies a single resting position
                    coords[jugglerIndex][beatIndex] = 1
                    bodypath[jugglerIndex][beatIndex] = arrayOf(null)
                } else {
                    coords[jugglerIndex][beatIndex] = coordTokens.size
                    bodypath[jugglerIndex][beatIndex] = coordTokens.toTypedArray()
                }
            }
        }
    }

    fun getPeriod(juggler: Int): Int {
        val j = (juggler - 1) % numberOfJugglers
        return size[j]
    }

    fun getNumberOfPositions(juggler: Int, pos: Int): Int {
        val j = (juggler - 1) % numberOfJugglers
        return coords[j][pos]
    }

    // Position and index start from 0.
    
    fun getPosition(juggler: Int, pos: Int, index: Int): JMLPosition? {
        if (pos >= getPeriod(juggler) || index >= getNumberOfPositions(juggler, pos)) {
            return null
        }
        val j = (juggler - 1) % this.numberOfJugglers
        if (bodypath[j][pos][index] == null) {
            return null
        }
        val result = JMLPosition()
        result.juggler = juggler
        result.coordinate = Coordinate(
            bodypath[j][pos][index]!![1], bodypath[j][pos][index]!![2], bodypath[j][pos][index]!![3]
        )
        result.angle = bodypath[j][pos][index]!![0]
        return result
    }
}
