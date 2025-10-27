//
// HandLink.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.curve.Curve

class HandLink(var juggler: Int, var hand: Int, var startEvent: JMLEvent, var endEvent: JMLEvent) {
    var startVelocityRef: VelocityRef? = null
    var endVelocityRef: VelocityRef? = null
    var handCurve: Curve? = null

    /*
    var isMaster: Boolean = true
        protected set
     */

    private var duplicates: Array<HandLink?>? = null // if master
    private var master: HandLink? = null // if duplicate

    override fun toString(): String {
        val start = startEvent.globalCoordinate
        val end = endEvent.globalCoordinate
        var result =
            ("Link from (x=${start!!.x},y=${start.y},z=${start.z},t=${startEvent.t}) ")
        result += "to (x=${end!!.x},y=${end.y},z=${end.z},t=${endEvent.t})"

        val svr = startVelocityRef
        if (svr != null) {
            val vel = svr.velocity
            result += "\n      start velocity (x=${vel.x},y=${vel.y},z=${vel.z})"
        }
        val evr = endVelocityRef
        if (evr != null) {
            val vel = evr.velocity
            result += "\n      end velocity (x=${vel.x},y=${vel.y},z=${vel.z})"
        }
        val hp = handCurve
        if (hp != null) {
            val maxcoord = hp.getMax(startEvent.t, endEvent.t)
            val mincoord = hp.getMin(startEvent.t, endEvent.t)
            result += "\n      minimum (x=${mincoord!!.x},y=${mincoord.y},z=${mincoord.z})"
            result += "\n      maximum (x=${maxcoord!!.x},y=${maxcoord.y},z=${maxcoord.z})"
        } else {
            result += "\n      no handpath"
        }
        return result
    }

    companion object {
        @Suppress("unused")
        const val NO_HAND: Int = 0
        const val LEFT_HAND: Int = 1
        const val RIGHT_HAND: Int = 2

        @JvmStatic
        fun index(handdescription: Int) = if (handdescription == LEFT_HAND) 0 else 1
    }
}
