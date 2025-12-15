//
// JMLEvent.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.Permutation
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.getStringResource

data class JMLEvent(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val t: Double = 0.0,
    val juggler: Int = 1,
    val hand: Int = 0,
    val transitions: List<JMLTransition> = emptyList()
): Comparable<JMLEvent> {
    val localCoordinate: Coordinate by lazy {
        Coordinate(x, y, z)
    }

    fun getPathTransition(path: Int, transType: Int): JMLTransition? {
        return transitions.firstOrNull {
            it.path == path && (transType == JMLTransition.TRANS_ANY || transType == it.type)
        }
    }

    val hasThrow: Boolean by lazy {
        transitions.any {
            it.type == JMLTransition.TRANS_THROW
        }
    }

    val hasThrowOrCatch: Boolean by lazy {
        transitions.any {
            when (it.type) {
                JMLTransition.TRANS_THROW,
                JMLTransition.TRANS_CATCH,
                JMLTransition.TRANS_SOFTCATCH,
                JMLTransition.TRANS_GRABCATCH -> true

                else -> false
            }
        }
    }

    fun writeJML(wr: Appendable, startTagOnly: Boolean = false) {
        wr.append("<event x=\"${jlToStringRounded(x, 4)}\"")
        wr.append(" y=\"${jlToStringRounded(y, 4)}\"")
        wr.append(" z=\"${jlToStringRounded(z, 4)}\"")
        wr.append(" t=\"${jlToStringRounded(t, 4)}\"")
        wr.append(" hand=\"$juggler:")
        wr.append(if (hand == HandLink.LEFT_HAND) "left" else "right")
        wr.append("\">\n")
        if (!startTagOnly) {
            for (tr in transitions) {
                tr.writeJML(wr)
            }
            wr.append("</event>\n")
        }
    }

    private val cachedToString: String by lazy {
        val sb = StringBuilder()
        writeJML(sb)
        sb.toString()
    }

    override fun toString(): String = cachedToString

    // Event hash code for locating a particular event in a pattern.

    private val cachedHashCode: Int by lazy {
        val sb = StringBuilder()
        writeJML(sb, startTagOnly = true)
        sb.toString().hashCode()
    }

    override fun hashCode(): Int = cachedHashCode

    override fun compareTo(other: JMLEvent): Int {
        if (t != other.t) {
            return t.compareTo(other.t)
        }
        if (juggler != other.juggler) {
            return juggler.compareTo(other.juggler)
        }
        if (hand != other.hand) {
            return hand.compareTo(other.hand)
        }
        // shouldn't get here; shouldn't have multiple events for the same
        // juggler/hand at a single time
        return x.compareTo(other.x)
    }

    override fun equals(other: Any?): Boolean {
        return hashCode() == other.hashCode()
    }

    //--------------------------------------------------------------------------
    // Items below here are for layout and animation - target removal
    //--------------------------------------------------------------------------

    fun clearLayoutData() {
        previous = null
        next = null
        primaryEvent = null
        delay = 0
        delayunits = 0
        pathPermFromPrimary = null
        calcpos = false
        globalCoordinate = null
        transitions.forEach { it.clearLayoutData() }
    }

    // for linking into event chains
    var previous: JMLEvent? = null
    var next: JMLEvent? = null // for doubly-linked event list

    // for "image" JMLEvents that are created from other "primary" events
    // during layout
    var primaryEvent: JMLEvent? = null // null if this is a primary event
    var delay: Int = 0
    var delayunits: Int = 0
    var pathPermFromPrimary: Permutation? = null
    val primary: JMLEvent
        get() = primaryEvent ?: this
    val isPrimary: Boolean
        get() = (primaryEvent == null)

    // used by MHNPattern during layout
    var calcpos: Boolean = false

    // coordinates in global frame, used during animation
    var globalCoordinate: Coordinate? = null

    fun isDelayOf(ev2: JMLEvent): Boolean {
        if (!hasSamePrimaryAs(ev2)) {
            return false
        }
        if (juggler != ev2.juggler || hand != ev2.hand) {
            return false
        }

        var totaldelay = delay - ev2.delay
        if (totaldelay < 0) {
            totaldelay = -totaldelay
        }
        return (totaldelay % delayunits) == 0
    }

    fun hasSamePrimaryAs(ev2: JMLEvent): Boolean {
        val primary1 = if (primaryEvent == null) this else primaryEvent
        val primary2 = if (ev2.primaryEvent == null) ev2 else ev2.primaryEvent
        return (primary1 === primary2)
    }

    // Temporary fix, eventually remove

    fun copyLayoutDataFrom(ev: JMLEvent, newDelay: Int, newDelayunits: Int) {
        delay = newDelay
        delayunits = newDelayunits
        calcpos = ev.calcpos
        primaryEvent = if (ev.isPrimary) ev else ev.primaryEvent
    }

    //--------------------------------------------------------------------------
    // Constructing JMLEvents
    //--------------------------------------------------------------------------

    companion object {
        @Throws(JuggleExceptionUser::class)
        fun fromJMLNode(
            current: JMLNode,
            version: String,
            numberOfJugglers: Int,
            numberOfPaths: Int
        ): JMLEvent {
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
                val message = getStringResource(Res.string.error_event_coordinate)
                throw JuggleExceptionUser(message)
            }

            // JML version 1.0 used a different coordinate system -- convert
            if (version == "1.0") {
                tempy = tempz.also { tempz = tempy }
            }

            if (handstr == null) {
                val message = getStringResource(Res.string.error_unspecified_hand)
                throw JuggleExceptionUser(message)
            }

            val result = JMLEvent(
                x = tempx,
                y = tempy,
                z = tempz,
                t = tempt
            ).withHandString(handstr)

            if (result.juggler !in 1..numberOfJugglers) {
                val message = getStringResource(Res.string.error_juggler_out_of_range)
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
                        val message = getStringResource(Res.string.error_no_path)
                        throw JuggleExceptionUser(message)
                    }

                    val pathNum = childPath.toInt()
                    if (pathNum !in 1..numberOfPaths) {
                        val message = getStringResource(Res.string.error_path_out_of_range)
                        throw JuggleExceptionUser(message)
                    }

                    if (childNodeType.equals("throw", ignoreCase = true)) {
                        add(
                            JMLTransition(
                                type = JMLTransition.TRANS_THROW,
                                path = pathNum,
                                throwType = childTranstype,
                                throwMod = childMod
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true) &&
                        childTranstype.equals("soft", ignoreCase = true)
                    ) {
                        add(
                            JMLTransition(
                                type = JMLTransition.TRANS_SOFTCATCH,
                                path = pathNum
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true) &&
                        childTranstype.equals("grab", ignoreCase = true)
                    ) {
                        add(
                            JMLTransition(
                                type = JMLTransition.TRANS_GRABCATCH,
                                path = pathNum
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true)) {
                        add(JMLTransition(JMLTransition.TRANS_CATCH, pathNum, null, null))
                    } else if (childNodeType.equals("holding", ignoreCase = true)) {
                        add(
                            JMLTransition(
                                type = JMLTransition.TRANS_HOLDING,
                                path = pathNum
                            )
                        )
                    }

                    if (child.children.isNotEmpty()) {
                        val message = getStringResource(Res.string.error_event_subtag)
                        throw JuggleExceptionUser(message)
                    }
                }
            }

            return result.copy(transitions = newTransitions)
        }

        @Throws(JuggleExceptionUser::class)
        private fun JMLEvent.withHandString(handString: String): JMLEvent {
            var newJuggler: Int
            var newHand: Int

            val index = handString.indexOf(":")
            if (index == -1) {
                newJuggler = 1
                newHand = if (handString.equals("left", ignoreCase = true)) {
                    HandLink.LEFT_HAND
                } else if (handString.equals("right", ignoreCase = true)) {
                    HandLink.RIGHT_HAND
                } else {
                    val message = getStringResource(Res.string.error_hand_name)
                    throw JuggleExceptionUser("$message '$handString'")
                }
            } else {
                newJuggler = handString.take(index).toInt()
                val substr = handString.substring(index + 1)
                newHand = if (substr.equals("left", ignoreCase = true)) {
                    HandLink.LEFT_HAND
                } else if (substr.equals("right", ignoreCase = true)) {
                    HandLink.RIGHT_HAND
                } else {
                    val message = getStringResource(Res.string.error_hand_name)
                    throw JuggleExceptionUser("$message '$handString'")
                }
            }
            return copy(juggler = newJuggler, hand = newHand)
        }


        fun JMLEvent.addTransition(trans: JMLTransition): JMLEvent {
            return copy(transitions = transitions + trans)
        }

        fun JMLEvent.removeTransition(index: Int): JMLEvent {
            return copy(
                transitions = transitions.filterIndexed { i, _ -> i != index }
            )
        }

        fun JMLEvent.removeTransition(trans: JMLTransition): JMLEvent {
            return copy(
                transitions = transitions.filter { it != trans }
            )
        }
    }
}