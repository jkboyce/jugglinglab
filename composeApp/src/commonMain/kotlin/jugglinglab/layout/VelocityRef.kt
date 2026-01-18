//
// VelocityRef.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.layout

import jugglinglab.path.Path
import jugglinglab.util.Coordinate

class VelocityRef(val pp: Path, val source: Int) {
    val velocity: Coordinate
        get() = if (this.source == VR_THROW) pp.startVelocity else pp.endVelocity

    companion object {
        var VR_THROW: Int = 0
        var VR_CATCH: Int = 1
        var VR_SOFTCATCH: Int = 2
    }
}
