//
// HandLink.kt
//
// This class is used during JmlPattern layout for keeping track of the hand
// movement from one JmlEvent to another.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.layout

import jugglinglab.curve.Curve

class HandLink(
    var juggler: Int,
    var hand: Int,
    var startEvent: LayoutEvent,
    var endEvent: LayoutEvent
) {
    var startVelocityRef: VelocityRef? = null
    var endVelocityRef: VelocityRef? = null
    var handCurve: Curve? = null

    override fun toString(): String {
        val start = startEvent.event.localCoordinate
        val end = endEvent.event.localCoordinate
        val svr = startVelocityRef
        val evr = endVelocityRef
        val hp = handCurve
        val sb = StringBuilder()

        sb.append("Link from (x=${start.x},y=${start.y},z=${start.z},t=${startEvent.t}) ")
        sb.append("to (x=${end.x},y=${end.y},z=${end.z},t=${endEvent.t})")
        if (svr != null) {
            val vel = svr.velocity
            sb.append("\n      start velocity (x=${vel.x},y=${vel.y},z=${vel.z})")
        }
        if (evr != null) {
            val vel = evr.velocity
            sb.append("\n      end velocity (x=${vel.x},y=${vel.y},z=${vel.z})")
        }
        if (hp != null) {
            val maxcoord = hp.getMax(startEvent.t, endEvent.t)
            val mincoord = hp.getMin(startEvent.t, endEvent.t)
            sb.append("\n      minimum (x=${mincoord!!.x},y=${mincoord.y},z=${mincoord.z})")
            sb.append("\n      maximum (x=${maxcoord!!.x},y=${maxcoord.y},z=${maxcoord.z})")
        } else {
            sb.append("\n      no handpath")
        }
        return sb.toString()
    }
}
