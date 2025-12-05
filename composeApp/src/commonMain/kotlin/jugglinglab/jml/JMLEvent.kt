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
    val juggler: Int = 0,
    val hand: Int = 0,
    val transitions: List<JMLTransition> = emptyList()
) {
    val localCoordinate: Coordinate
        get() = Coordinate(x, y, z)

    fun getPathTransition(path: Int, transType: Int): JMLTransition? {
        return transitions.firstOrNull {
            it.path == path && (transType == JMLTransition.TRANS_ANY || transType == it.type)
        }
    }

    val hasThrow: Boolean
        get() = transitions.any {
            it.type == JMLTransition.TRANS_THROW
        }

    val hasThrowOrCatch: Boolean
        get() = transitions.any {
            when (it.type) {
                JMLTransition.TRANS_THROW,
                JMLTransition.TRANS_CATCH,
                JMLTransition.TRANS_SOFTCATCH,
                JMLTransition.TRANS_GRABCATCH -> true
                else -> false
            }
        }

    fun writeJML(wr: Appendable, startTagOnly: Boolean = false) {
        val c = localCoordinate
        wr.append("<event x=\"${jlToStringRounded(c.x, 4)}\"")
        wr.append(" y=\"${jlToStringRounded(c.y, 4)}\"")
        wr.append(" z=\"${jlToStringRounded(c.z, 4)}\"")
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

    override fun toString(): String {
        val sb = StringBuilder()
        writeJML(sb)
        return sb.toString()
    }

    // Event hash code for locating a particular event in a pattern.

    val hashCode: Int
        get() {
            val sb = StringBuilder()
            writeJML(sb, startTagOnly = true)
            return sb.toString().hashCode()
        }

    //--------------------------------------------------------------------------
    // Items below here are for layout and animation - target removal
    //--------------------------------------------------------------------------

    // for linking into event chains
    var previous: JMLEvent? = null
    var next: JMLEvent? = null // for doubly-linked event list

    // for "image" JMLEvents that are created from other "master" events
    // during layout
    var masterEvent: JMLEvent? = null // null if this is a master event
    var delay: Int = 0
    var delayunits: Int = 0
    var pathPermFromMaster: Permutation? = null
    val master: JMLEvent
        get() = masterEvent ?: this
    val isMaster: Boolean
        get() = (masterEvent == null)

    // used by MHNPattern during layout
    var calcpos: Boolean = false

    // coordinates in global frame, used during animation
    private var gx: Double = 0.0
    private var gy: Double = 0.0
    private var gz: Double = 0.0 // coordinates in global frame
    private var globalvalid: Boolean = false // global coordinates need to be recalced?
    var globalCoordinate: Coordinate?
        get() = if (globalvalid) Coordinate(gx, gy, gz) else null
        set(c) {
            gx = c!!.x
            gy = c.y
            gz = c.z
            globalvalid = true
        }

    val previousForHand: JMLEvent?
        get() {
            var ev = previous
            while (ev != null) {
                if (ev.juggler == juggler && ev.hand == hand) {
                    return ev
                }
                ev = ev.previous
            }
            return null
        }

    val nextForHand: JMLEvent?
        get() {
            var ev = next
            while (ev != null) {
                if (ev.juggler == juggler && ev.hand == hand) {
                    return ev
                }
                ev = ev.next
            }
            return null
        }

    fun isDelayOf(ev2: JMLEvent): Boolean {
        if (!hasSameMasterAs(ev2)) {
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

    fun hasSameMasterAs(ev2: JMLEvent): Boolean {
        val mast1 = if (masterEvent == null) this else masterEvent
        val mast2 = if (ev2.masterEvent == null) ev2 else ev2.masterEvent
        return (mast1 === mast2)
    }

    // Return true if the event contains a throw transition to another juggler.
    //
    // Note this will only work after pattern layout.

    val hasPassingThrow: Boolean
        get() = transitions.any { tr ->
            tr.type == JMLTransition.TRANS_THROW &&
                tr.outgoingPathLink?.endEvent?.juggler != juggler
        }

    // Return true if the event contains a catch transition from another juggler.
    //
    // Note this will only work after pattern layout.

    val hasPassingCatch: Boolean
        get() = transitions.any { tr ->
            when (tr.type) {
                JMLTransition.TRANS_CATCH,
                JMLTransition.TRANS_SOFTCATCH,
                JMLTransition.TRANS_GRABCATCH -> true
                else -> false
            } && tr.incomingPathLink?.startEvent?.juggler != juggler
        }

    // Note this only works after pattern layout.

    val hasPassingTransition: Boolean
        get() = (hasPassingThrow || hasPassingCatch)

    // Temporary fix, eventually remove

    fun copyLayoutDataFrom(ev: JMLEvent, newDelay: Int, newDelayunits: Int) {
        delay = newDelay
        delayunits = newDelayunits
        calcpos = ev.calcpos
        masterEvent = if (ev.isMaster) ev else ev.masterEvent
    }

    companion object {
        // Factory methods to create JMLEvents

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
            ).setHand(handstr)

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
                                JMLTransition.TRANS_THROW,
                                pathNum,
                                childTranstype,
                                childMod
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true) &&
                        childTranstype.equals("soft", ignoreCase = true)
                    ) {
                        add(
                            JMLTransition(
                                JMLTransition.TRANS_SOFTCATCH,
                                pathNum,
                                null,
                                null
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true) &&
                        childTranstype.equals("grab", ignoreCase = true)
                    ) {
                        add(
                            JMLTransition(
                                JMLTransition.TRANS_GRABCATCH,
                                pathNum,
                                null,
                                null
                            )
                        )
                    } else if (childNodeType.equals("catch", ignoreCase = true)) {
                        add(JMLTransition(JMLTransition.TRANS_CATCH, pathNum, null, null))
                    } else if (childNodeType.equals("holding", ignoreCase = true)) {
                        add(
                            JMLTransition(
                                JMLTransition.TRANS_HOLDING,
                                pathNum,
                                null,
                                null
                            )
                        )
                    }

                    if (child.numberOfChildren != 0) {
                        val message = getStringResource(Res.string.error_event_subtag)
                        throw JuggleExceptionUser(message)
                    }
                }
            }

            return result.copy(transitions = newTransitions)
        }

        @Throws(JuggleExceptionUser::class)
        fun JMLEvent.setHand(handString: String): JMLEvent {
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