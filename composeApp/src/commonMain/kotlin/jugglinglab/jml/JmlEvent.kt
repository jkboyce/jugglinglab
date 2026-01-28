//
// JmlEvent.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlGetStringResource

data class JmlEvent(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val t: Double = 0.0,
    val juggler: Int = 1,
    val hand: Int = 0,
    val transitions: List<JmlTransition> = emptyList()
) : Comparable<JmlEvent> {
    val localCoordinate: Coordinate
        get() = Coordinate(x, y, z)

    val truncatedTime: Double by lazy {
        jlToStringRounded(t, 4).toDouble()
    }

    fun getPathTransition(path: Int, transType: Int): JmlTransition? {
        return transitions.firstOrNull {
            it.path == path && (transType == JmlTransition.TRANS_ANY || transType == it.type)
        }
    }

    val hasThrow: Boolean by lazy {
        transitions.any {
            it.type == JmlTransition.TRANS_THROW
        }
    }

    val hasThrowOrCatch: Boolean by lazy {
        transitions.any {
            when (it.type) {
                JmlTransition.TRANS_THROW,
                JmlTransition.TRANS_CATCH,
                JmlTransition.TRANS_SOFTCATCH,
                JmlTransition.TRANS_GRABCATCH -> true

                else -> false
            }
        }
    }

    fun writeJml(wr: Appendable, startTagOnly: Boolean = false) {
        wr.append("<event x=\"${jlToStringRounded(x, 4)}\"")
        wr.append(" y=\"${jlToStringRounded(y, 4)}\"")
        wr.append(" z=\"${jlToStringRounded(z, 4)}\"")
        wr.append(" t=\"${jlToStringRounded(t, 4)}\"")
        wr.append(" hand=\"$juggler:")
        wr.append(if (hand == LEFT_HAND) "left" else "right")
        wr.append("\">\n")
        if (!startTagOnly) {
            transitions.forEach { it.writeJml(wr) }
            wr.append("</event>\n")
        }
    }

    private val cachedToString: String by lazy {
        val sb = StringBuilder()
        writeJml(sb)
        sb.toString()
    }

    override fun toString(): String = cachedToString

    // Event hash code for locating a particular event in a pattern.

    val jlHashCode: Int by lazy {
        val sb = StringBuilder()
        writeJml(sb, startTagOnly = true)
        sb.toString().hashCode()
    }

    override fun compareTo(other: JmlEvent): Int {
        if (truncatedTime != other.truncatedTime) {
            return truncatedTime.compareTo(other.truncatedTime)
        }
        if (juggler != other.juggler) {
            return juggler.compareTo(other.juggler)
        }
        if (hand != other.hand) {
            // ordering is so right hand sorts before left:
            return other.hand.compareTo(hand)
        }
        // shouldn't get here; shouldn't have multiple events for the same
        // juggler/hand at a single time
        return x.compareTo(other.x)
    }

    //--------------------------------------------------------------------------
    // Event transformations
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class)
    private fun withHandString(handString: String): JmlEvent {
        var newJuggler: Int
        var newHand: Int

        val index = handString.indexOf(":")
        if (index == -1) {
            newJuggler = 1
            newHand = if (handString.equals("left", ignoreCase = true)) {
                LEFT_HAND
            } else if (handString.equals("right", ignoreCase = true)) {
                RIGHT_HAND
            } else {
                val message = jlGetStringResource(Res.string.error_hand_name)
                throw JuggleExceptionUser("$message '$handString'")
            }
        } else {
            newJuggler = handString.take(index).toInt()
            val substr = handString.substring(index + 1)
            newHand = if (substr.equals("left", ignoreCase = true)) {
                LEFT_HAND
            } else if (substr.equals("right", ignoreCase = true)) {
                RIGHT_HAND
            } else {
                val message = jlGetStringResource(Res.string.error_hand_name)
                throw JuggleExceptionUser("$message '$handString'")
            }
        }
        return copy(juggler = newJuggler, hand = newHand)
    }


    fun withTransition(trans: JmlTransition): JmlEvent {
        return copy(transitions = transitions + trans)
    }

    @Suppress("unused")
    fun withoutTransitionAtIndex(index: Int): JmlEvent {
        return copy(
            transitions = transitions.filterIndexed { i, _ -> i != index }
        )
    }

    fun withoutTransition(trans: JmlTransition): JmlEvent {
        return copy(
            transitions = transitions.filter { it != trans }
        )
    }

    companion object {
        // hand descriptors
        const val LEFT_HAND: Int = 1
        const val RIGHT_HAND: Int = 2

        fun handIndex(handDescriptor: Int) = if (handDescriptor == LEFT_HAND) 0 else 1

        //----------------------------------------------------------------------
        // Constructing JmlEvents
        //----------------------------------------------------------------------

        @Throws(JuggleExceptionUser::class)
        fun fromJmlNode(
            current: JmlNode,
            numberOfJugglers: Int,
            numberOfPaths: Int,
            loadingJmlVersion: String = JmlDefs.CURRENT_JML_VERSION
        ): JmlEvent {
            var tempx = 0.0
            var tempy = 0.0
            var tempz = 0.0
            var tempt = 0.0
            var handstr: String? = null

            try {
                for ((name, value) in current.attributes.entries) {
                    if (name.equals("x", ignoreCase = true)) {
                        tempx = jlParseFiniteDouble(value)
                    } else if (name.equals("y", ignoreCase = true)) {
                        tempy = jlParseFiniteDouble(value)
                    } else if (name.equals("z", ignoreCase = true)) {
                        tempz = jlParseFiniteDouble(value)
                    } else if (name.equals("t", ignoreCase = true)) {
                        tempt = jlParseFiniteDouble(value)
                    } else if (name.equals("hand", ignoreCase = true)) {
                        handstr = value
                    }
                }
            } catch (_: NumberFormatException) {
                val message = jlGetStringResource(Res.string.error_event_coordinate)
                throw JuggleExceptionUser(message)
            }

            // JML version 1.0 used a different coordinate system -- convert
            if (loadingJmlVersion == "1.0") {
                tempy = tempz.also { tempz = tempy }
            }

            if (handstr == null) {
                val message = jlGetStringResource(Res.string.error_unspecified_hand)
                throw JuggleExceptionUser(message)
            }

            val result = JmlEvent(
                x = tempx,
                y = tempy,
                z = tempz,
                t = tempt
            ).withHandString(handstr)

            if (result.juggler !in 1..numberOfJugglers) {
                val message = jlGetStringResource(Res.string.error_juggler_out_of_range)
                throw JuggleExceptionUser(message)
            }

            val newTransitions = buildList {
                // process current event node children
                for (child in current.children) {
                    val childNodeType = child.nodeType
                    var childPath: String? = null
                    var childTranstype: String? = null
                    var childMod: String? = null

                    for ((name, value) in child.attributes.entries) {
                        if (name.equals("path", ignoreCase = true)) {
                            childPath = value
                        } else if (name.equals("type", ignoreCase = true)) {
                            childTranstype = value
                        } else if (name.equals("mod", ignoreCase = true)) {
                            childMod = value
                        }
                    }

                    if (childPath == null) {
                        val message = jlGetStringResource(Res.string.error_no_path)
                        throw JuggleExceptionUser(message)
                    }

                    val pathNum = childPath.toInt()
                    if (pathNum !in 1..numberOfPaths) {
                        val message = jlGetStringResource(Res.string.error_path_out_of_range)
                        throw JuggleExceptionUser(message)
                    }

                    if (childNodeType.equals("throw", ignoreCase = true)) {
                        add(
                            JmlTransition(
                                type = JmlTransition.TRANS_THROW,
                                path = pathNum,
                                throwType = childTranstype,
                                throwMod = childMod
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true) &&
                        childTranstype.equals("soft", ignoreCase = true)
                    ) {
                        add(
                            JmlTransition(
                                type = JmlTransition.TRANS_SOFTCATCH,
                                path = pathNum
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true) &&
                        childTranstype.equals("grab", ignoreCase = true)
                    ) {
                        add(
                            JmlTransition(
                                type = JmlTransition.TRANS_GRABCATCH,
                                path = pathNum
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true)) {
                        add(JmlTransition(JmlTransition.TRANS_CATCH, pathNum, null, null))
                    } else if (childNodeType.equals("holding", ignoreCase = true)) {
                        add(
                            JmlTransition(
                                type = JmlTransition.TRANS_HOLDING,
                                path = pathNum
                            )
                        )
                    }

                    if (child.children.isNotEmpty()) {
                        val message = jlGetStringResource(Res.string.error_event_subtag)
                        throw JuggleExceptionUser(message)
                    }
                }
            }

            return result.copy(transitions = newTransitions)
        }
    }
}