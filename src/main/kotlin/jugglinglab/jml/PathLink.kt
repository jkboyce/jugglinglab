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
    // var catch: Int = 0
    var path: Path? = null
        private set
    var throwtype: String? = null
        private set
    var mod: String? = null
        private set
    var isInHand: Boolean = false
        private set
    var holdingJuggler: Int = 0
        private set
    var holdingHand: Int = 0
        private set

    /*
    var isMaster: Boolean = false
        private set
    private var master: PathLink? = null  // if duplicate
    */

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun setThrow(pathType: String, pathMod: String?) {
        val newPath = Path.newPath(pathType)
        newPath.initPath(pathMod)
        newPath.setStart(startEvent.globalCoordinate!!, startEvent.t)
        newPath.setEnd(endEvent.globalCoordinate!!, endEvent.t)
        newPath.calcPath()
        path = newPath
        throwtype = pathType
        mod = pathMod
        isInHand = false
    }

    fun setInHand(juggler: Int, hand: Int) {
        this.isInHand = true
        this.holdingJuggler = juggler
        this.holdingHand = hand
    }

    override fun toString(): String {
        var result: String = if (this.isInHand) {
            "In hand, "
        } else {
            "Not in hand (type=\"$throwtype\", mod=\"$mod\"), "
        }

        val start = startEvent.globalCoordinate
        if (start != null) {
            result += "from (x=" + start.x + ",y=" + start.y + ",z=" + start.z + ",t=" +
                startEvent.t + ") "
        }
        val end = endEvent.globalCoordinate
        if (end != null) {
            result += "to (x=" + end.x + ",y=" + end.y + ",z=" + end.z + ",t=" + endEvent.t + ")"
        }
        return result
    }
}
