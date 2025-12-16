//
// PathLink.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.path.Path
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser

class PathLink(
    val pathNum: Int,
    val startGlobalCoordinate: Coordinate,
    val startEvent: JMLEvent,
    val endGlobalCoordinate: Coordinate,
    val endEvent: JMLEvent
) {
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
    fun setThrow(
        pathType: String,
        pathMod: String?
    ) {
        val newPath = Path.newPath(pathType)
        newPath.initPath(pathMod)
        newPath.setStart(startGlobalCoordinate, startEvent.t)
        newPath.setEnd(endGlobalCoordinate, endEvent.t)
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
        val start = startGlobalCoordinate
        val end = endGlobalCoordinate
        val sb = StringBuilder()
        sb.append(
            if (isInHand) "In hand, " else
                "Not in hand (type=\"$throwType\", mod=\"$throwMod\"), "
        )
        sb.append("from (x=${start.x},y=${start.y},z=${start.z},t=${startEvent.t}) ")
        sb.append("to (x=${end.x},y=${end.y},z=${end.z},t=${endEvent.t})")
        return sb.toString()
    }
}
