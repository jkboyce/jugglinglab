//
// PathLink.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.path.Path
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser

class PathLink(val pathNum: Int, val startEvent: JMLEvent, val endEvent: JMLEvent) {
    var path: Path? = null
        private set
    var isInHand: Boolean = false
        private set
    // for paths corresponding to a throw (isInHand false):
    var throwType: String? = null
        private set
    var throwMod: String? = null
        private set
    // for paths corresponding to a carry (isInHand true):
    var holdingJuggler: Int = 0
        private set
    var holdingHand: Int = 0
        private set

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun setThrow(pathType: String, pathMod: String?) {
        val newPath = Path.newPath(pathType)
        newPath.initPath(pathMod)
        newPath.setStart(startEvent.globalCoordinate!!, startEvent.t)
        newPath.setEnd(endEvent.globalCoordinate!!, endEvent.t)
        newPath.calcPath()
        path = newPath
        throwType = pathType
        throwMod = pathMod
        isInHand = false
    }

    fun setInHand(juggler: Int, hand: Int) {
        isInHand = true
        holdingJuggler = juggler
        holdingHand = hand
    }

    override fun toString(): String {
        val start = startEvent.globalCoordinate
        val end = endEvent.globalCoordinate
        var sb = StringBuilder()

        sb.append(if (isInHand) "In hand, " else
            "Not in hand (type=\"$throwType\", mod=\"$throwMod\"), "
        )
        if (start != null) {
            sb.append("from (x=${start.x},y=${start.y},z=${start.z},t=${startEvent.t}) ")
        }
        if (end != null) {
            sb.append("to (x=${end.x},y=${end.y},z=${end.z},t=${endEvent.t})")
        }
        return sb.toString()
    }
}
