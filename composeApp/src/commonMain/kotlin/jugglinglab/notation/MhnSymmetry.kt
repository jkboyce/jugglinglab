//
// MhnSymmetry.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.util.Permutation

data class MhnSymmetry(
    val type: Int,
    val numberOfJugglers: Int,
    val jugPerm: String?,
    val delay: Int
) {
    val jugglerPerm: Permutation = if (jugPerm == null) {
            Permutation(numberOfJugglers, reverses = true)
        } else {
            Permutation(numberOfJugglers, jugPerm, reverses = true)
        }

    companion object {
        const val TYPE_DELAY: Int = 1  // types of symmetries
        const val TYPE_SWITCH: Int = 2
        const val TYPE_SWITCHDELAY: Int = 3
    }
}
