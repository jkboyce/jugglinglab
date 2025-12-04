//
// JMLEvent.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.*
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.jlParseFiniteDouble

class JMLEvent {
    var t: Double = 0.0
    var juggler: Int = 0
        private set
    var hand: Int = 0
        private set
    var transitions: MutableList<JMLTransition> = ArrayList()
    var delay: Int = 0
    var delayunits: Int = 0
    var pathPermFromMaster: Permutation? = null

    // for use during layout
    var calcpos: Boolean = false

    // for linking into event chains
    var previous: JMLEvent? = null
    var next: JMLEvent? = null // for doubly-linked event list
    var masterEvent: JMLEvent? = null // null if this is a master event
    val master: JMLEvent
        get() = masterEvent ?: this
    val isMaster: Boolean
        get() = (masterEvent == null)

    // coordinates in local frame
    var x: Double = 0.0
        private set
    var y: Double = 0.0
        private set
    var z: Double = 0.0
        private set
    var localCoordinate: Coordinate
        get() = Coordinate(x, y, z)
        set(c) {
            x = c.x
            y = c.y
            z = c.z
            globalvalid = false
        }

    // coordinates in global frame
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

    @Throws(JuggleExceptionUser::class)
    fun setHand(strhand: String) {
        val index = strhand.indexOf(":")

        if (index == -1) {
            juggler = 1
            hand = if (strhand.equals("left", ignoreCase = true)) {
                HandLink.LEFT_HAND
            } else if (strhand.equals("right", ignoreCase = true)) {
                HandLink.RIGHT_HAND
            } else {
                val message = getStringResource(Res.string.error_hand_name)
                throw JuggleExceptionUser("$message '$strhand'")
            }
        } else {
            juggler = strhand.take(index).toInt()
            val substr = strhand.substring(index + 1)
            hand = if (substr.equals("left", ignoreCase = true)) {
                HandLink.LEFT_HAND
            } else if (substr.equals("right", ignoreCase = true)) {
                HandLink.RIGHT_HAND
            } else {
                val message = getStringResource(Res.string.error_hand_name)
                throw JuggleExceptionUser("$message '$strhand'")
            }
        }
    }

    fun setHand(j: Int, h: Int) {
        juggler = j
        hand = h // HandLink.LEFT_HAND or HandLink.RIGHT_HAND
    }

    val numberOfTransitions: Int
        get() = transitions.size

    fun getTransition(index: Int) = transitions[index]

    fun addTransition(trans: JMLTransition) = transitions.add(trans)

    fun removeTransition(index: Int) = transitions.removeAt(index)

    fun removeTransition(trans: JMLTransition) = transitions.remove(trans)

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

    fun getPathTransition(path: Int, transType: Int): JMLTransition? {
        return transitions.firstOrNull {
            it.path == path && (transType == JMLTransition.TRANS_ANY || transType == it.type)
        }
    }

    val hasThrow: Boolean
        get() = transitions.any { it.type == JMLTransition.TRANS_THROW }

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

    fun duplicate(delay: Int, delayunits: Int): JMLEvent {
        val dup = JMLEvent()
        dup.localCoordinate = localCoordinate
        dup.t = t
        dup.setHand(juggler, hand)
        dup.delay = delay
        dup.delayunits = delayunits
        dup.calcpos = calcpos

        for (tr in transitions) {
            dup.addTransition(tr.copy())
        }

        dup.masterEvent = if (isMaster) this else masterEvent
        return dup
    }

    // Return the event hash code, for locating a particular event in a pattern.

    val hashCode: Int
        get() {
            val c = localCoordinate
            val s =
                ("<event x=\""
                    + jlToStringRounded(c.x, 4)
                    + "\" y=\""
                    + jlToStringRounded(c.y, 4)
                    + "\" z=\""
                    + jlToStringRounded(c.z, 4)
                    + "\" t=\""
                    + jlToStringRounded(t, 4)
                    + "\" hand=\""
                    + juggler
                    + ":"
                    + (if (hand == HandLink.LEFT_HAND) "left" else "right")
                    + "\">")
            return s.hashCode()
        }

    //--------------------------------------------------------------------------
    // Reader/writer methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class)
    fun readJML(current: JMLNode, jmlvers: String, njugglers: Int, npaths: Int) {
        val at = current.attributes
        var tempx = 0.0
        var tempy = 0.0
        var tempz = 0.0
        var tempt = 0.0
        var handstr: String? = null

        try {
            for (i in 0..<at.numberOfAttributes) {
                // System.out.println("att. "+i+" = "+at.getAttributeValue(i));
                if (at.getAttributeName(i).equals("x", ignoreCase = true)) {
                    tempx = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("y", ignoreCase = true)) {
                    tempy = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("z", ignoreCase = true)) {
                    tempz = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("t", ignoreCase = true)) {
                    tempt = jlParseFiniteDouble(at.getAttributeValue(i))
                } else if (at.getAttributeName(i).equals("hand", ignoreCase = true)) {
                    handstr = at.getAttributeValue(i)
                }
            }
        } catch (_: NumberFormatException) {
            val message = getStringResource(Res.string.error_event_coordinate)
            throw JuggleExceptionUser(message)
        }

        // JML version 1.0 used a different coordinate system -- convert
        if (jmlvers == "1.0") {
            tempy = tempz.also { tempz = tempy }
        }

        localCoordinate = Coordinate(tempx, tempy, tempz)
        t = tempt
        if (handstr == null) {
            val message = getStringResource(Res.string.error_unspecified_hand)
            throw JuggleExceptionUser(message)
        }
        setHand(handstr)
        if (juggler !in 1..njugglers) {
            val message = getStringResource(Res.string.error_juggler_out_of_range)
            throw JuggleExceptionUser(message)
        }

        // process current event node children
        for (i in 0..<current.numberOfChildren) {
            val child = current.getChildNode(i)
            val childNodeType = child.nodeType
            val childAt = child.attributes
            var childPath: String? = null
            var childTranstype: String? = null
            var childMod: String? = null

            for (j in 0..<childAt.numberOfAttributes) {
                val value = childAt.getAttributeValue(j)
                if (childAt.getAttributeName(j).equals("path", ignoreCase = true)) {
                    childPath = value
                } else if (childAt.getAttributeName(j).equals("type", ignoreCase = true)) {
                    childTranstype = value
                } else if (childAt.getAttributeName(j).equals("mod", ignoreCase = true)) {
                    childMod = value
                }
            }

            if (childPath == null) {
                val message = getStringResource(Res.string.error_no_path)
                throw JuggleExceptionUser(message)
            }

            val pnum = childPath.toInt()
            if (pnum !in 1..npaths) {
                val message = getStringResource(Res.string.error_path_out_of_range)
                throw JuggleExceptionUser(message)
            }

            if (childNodeType.equals("throw", ignoreCase = true)) {
                addTransition(
                    JMLTransition(
                        JMLTransition.TRANS_THROW,
                        pnum,
                        childTranstype,
                        childMod
                    )
                )
            } else if (childNodeType.equals("catch", ignoreCase = true) &&
                childTranstype.equals("soft", ignoreCase = true)
            ) {
                addTransition(JMLTransition(JMLTransition.TRANS_SOFTCATCH, pnum, null, null))
            } else if (childNodeType.equals("catch", ignoreCase = true) &&
                childTranstype.equals("grab", ignoreCase = true)
            ) {
                addTransition(JMLTransition(JMLTransition.TRANS_GRABCATCH, pnum, null, null))
            } else if (childNodeType.equals("catch", ignoreCase = true)) {
                addTransition(JMLTransition(JMLTransition.TRANS_CATCH, pnum, null, null))
            } else if (childNodeType.equals("holding", ignoreCase = true)) {
                addTransition(JMLTransition(JMLTransition.TRANS_HOLDING, pnum, null, null))
            }

            if (child.numberOfChildren != 0) {
                val message = getStringResource(Res.string.error_event_subtag)
                throw JuggleExceptionUser(message)
            }
        }
    }

    fun writeJML(wr: Appendable) {
        val c = localCoordinate
        wr.append(
            ("<event x=\""
                + jlToStringRounded(c.x, 4)
                + "\" y=\""
                + jlToStringRounded(c.y, 4)
                + "\" z=\""
                + jlToStringRounded(c.z, 4)
                + "\" t=\""
                + jlToStringRounded(t, 4)
                + "\" hand=\""
                + juggler
                + ":"
                + (if (hand == HandLink.LEFT_HAND) "left" else "right")
                + "\">\n")
        )
        for (tr in transitions) {
            tr.writeJML(wr)
        }
        wr.append("</event>\n")
    }

    override fun toString(): String {
        val sw = StringBuilder()
        writeJML(sw)
        return sw.toString()
    }
}
