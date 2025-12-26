//
// LayoutEvent.kt
//
// Wraps JMLEvent with information used during layout.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("unused")

package jugglinglab.jml

import jugglinglab.util.Permutation

class LayoutEvent(
    val event: JMLEvent,
    val primary: JMLEvent,
    val pathPermFromPrimary: Permutation
) {
    val t = event.t
    val juggler = event.juggler
    val hand = event.hand

    val isPrimary = (event === primary)

    // for linking into event chains
    var previous: LayoutEvent? = null
    var next: LayoutEvent? = null

    fun isDelayOf(ev2: LayoutEvent): Boolean {
        if (primary !== ev2.primary) {
            return false
        }
        if (juggler != ev2.juggler || hand != ev2.hand) {
            return false
        }
        return true
    }
}