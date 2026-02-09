//
// JmlSymmetry.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.jml

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.JuggleException
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.Permutation
import org.jugglinglab.util.jlParseFiniteDouble
import org.jugglinglab.util.jlToStringRounded
import org.jugglinglab.util.jlGetStringResource

data class JmlSymmetry(
    val type: Int,
    val numberOfJugglers: Int,
    val numberOfPaths: Int,
    val jugglerPerm: Permutation,
    val pathPerm: Permutation,
    val delay: Double = -1.0
) {
    fun writeJml(wr: Appendable) {
        val output = when (type) {
            TYPE_DELAY -> "<symmetry type=\"delay\" pperm=\"$pathPerm\" delay=\"${jlToStringRounded(delay, 4)}\"/>\n"
            TYPE_SWITCH -> "<symmetry type=\"switch\" jperm=\"$jugglerPerm\" pperm=\"$pathPerm\"/>\n"
            TYPE_SWITCHDELAY -> "<symmetry type=\"switchdelay\" jperm=\"$jugglerPerm\" pperm=\"$pathPerm\"/>\n"
            else -> ""
        }
        wr.append(output)
    }

    companion object {
        const val TYPE_DELAY: Int = 1 // types of symmetries
        const val TYPE_SWITCH: Int = 2
        const val TYPE_SWITCHDELAY: Int = 3

        @Suppress("unused")
        @Throws(JuggleExceptionUser::class)
        fun fromJmlNode(
            current: JmlNode,
            numberOfJugglers: Int,
            numberOfPaths: Int,
            loadingJmlVersion: String = JmlDefs.CURRENT_JML_VERSION
        ): JmlSymmetry {
            val at = current.attributes
            val symTypeString = at.getValueOf("type")
                ?: throw JuggleExceptionUser(jlGetStringResource(Res.string.error_symmetry_notype))

            val symType = when {
                symTypeString.equals("delay", ignoreCase = true) -> TYPE_DELAY
                symTypeString.equals("switch", ignoreCase = true) -> TYPE_SWITCH
                symTypeString.equals("switchdelay", ignoreCase = true) -> TYPE_SWITCHDELAY
                else -> throw JuggleExceptionUser(jlGetStringResource(Res.string.error_symmetry_type))
            }

            val delay = at.getValueOf("delay")?.let {
                try {
                    jlParseFiniteDouble(it)
                } catch (_: NumberFormatException) {
                    throw JuggleExceptionUser(jlGetStringResource(Res.string.error_symmetry_format))
                }
            } ?: -1.0

            return try {
                val jugglerPerm = createPermutation(numberOfJugglers, at.getValueOf("jperm"), true)
                val pathPerm = createPermutation(numberOfPaths, at.getValueOf("pperm"), false)
                JmlSymmetry(
                    type = symType,
                    numberOfJugglers = numberOfJugglers,
                    numberOfPaths = numberOfPaths,
                    jugglerPerm = jugglerPerm,
                    pathPerm = pathPerm,
                    delay = delay
                )
            } catch (je: JuggleException) {
                // error creating permutation
                throw JuggleExceptionUser(je.message!!)
            }
        }

        @Throws(JuggleException::class)
        private fun createPermutation(
            size: Int,
            permString: String?,
            reverses: Boolean
        ): Permutation {
            return if (permString == null) {
                Permutation(size, reverses)
            } else {
                Permutation(size, permString, reverses)
            }
        }
    }
}
