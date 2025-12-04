//
// JMLSymmetry.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Permutation
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.getStringResource

/**
 * An immutable data class representing a symmetry definition in a JML pattern.
 */
data class JMLSymmetry(
    val type: Int,
    val numberOfJugglers: Int,
    val numberOfPaths: Int,
    val jugglerPerm: Permutation?,
    val pathPerm: Permutation?,
    val delay: Double = -1.0
) {
    fun writeJML(wr: Appendable) {
        val output = when (type) {
            TYPE_DELAY -> "<symmetry type=\"delay\" pperm=\"${pathPerm!!.toString(true)}\" delay=\"${jlToStringRounded(delay, 4)}\"/>\n"
            TYPE_SWITCH -> "<symmetry type=\"switch\" jperm=\"${jugglerPerm!!.toString(true)}\" pperm=\"${pathPerm!!.toString(true)}\"/>\n"
            TYPE_SWITCHDELAY -> "<symmetry type=\"switchdelay\" jperm=\"${jugglerPerm!!.toString(true)}\" pperm=\"${pathPerm!!.toString(true)}\"/>\n"
            else -> ""
        }
        wr.append(output)
    }

    companion object {
        const val TYPE_DELAY: Int = 1 // types of symmetries
        const val TYPE_SWITCH: Int = 2
        const val TYPE_SWITCHDELAY: Int = 3

        /**
         * Factory method to create a JMLSymmetry instance by parsing a JMLNode.
         */
        @Throws(JuggleExceptionUser::class)
        fun fromJMLNode(current: JMLNode, numberOfJugglers: Int, numberOfPaths: Int): JMLSymmetry {
            val at = current.attributes

            val symTypeString = at.getValueOf("type")
                ?: throw JuggleExceptionUser(getStringResource(Res.string.error_symmetry_notype))

            val symType = when {
                symTypeString.equals("delay", ignoreCase = true) -> TYPE_DELAY
                symTypeString.equals("switch", ignoreCase = true) -> TYPE_SWITCH
                symTypeString.equals("switchdelay", ignoreCase = true) -> TYPE_SWITCHDELAY
                else -> throw JuggleExceptionUser(getStringResource(Res.string.error_symmetry_type))
            }

            val delay = at.getValueOf("delay")?.let {
                try {
                    jlParseFiniteDouble(it)
                } catch (_: NumberFormatException) {
                    throw JuggleExceptionUser(getStringResource(Res.string.error_symmetry_format))
                }
            } ?: -1.0

            val jugglerPerm = createPermutation(numberOfJugglers, at.getValueOf("jperm"), true)
            val pathPerm = createPermutation(numberOfPaths, at.getValueOf("pperm"), false)

            return JMLSymmetry(
                type = symType,
                numberOfJugglers = numberOfJugglers,
                numberOfPaths = numberOfPaths,
                jugglerPerm = jugglerPerm,
                pathPerm = pathPerm,
                delay = delay
            )
        }

        /** Helper to create a Permutation from a nullable string. */
        private fun createPermutation(size: Int, permString: String?, reverses: Boolean): Permutation {
            return if (permString == null) {
                Permutation(size, reverses)
            } else {
                Permutation(size, permString, reverses)
            }
        }
    }
}
