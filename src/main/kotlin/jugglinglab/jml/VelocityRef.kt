//
// VelocityRef.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.path.Path
import jugglinglab.util.Coordinate

class VelocityRef(private var pp: Path, var source: Int) {
    val velocity: Coordinate
        get() = if (this.source == VR_THROW) pp.startVelocity else pp.endVelocity

    companion object {
        @JvmField
        var VR_THROW: Int = 0
        @JvmField
        var VR_CATCH: Int = 1
        @JvmField
        var VR_SOFTCATCH: Int = 2
    }
}
