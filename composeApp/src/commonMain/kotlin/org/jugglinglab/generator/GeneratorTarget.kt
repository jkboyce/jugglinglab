//
// GeneratorTarget.kt
//
// Interface to receive results from a generator.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.generator

interface GeneratorTarget {
    // new result from the generator
    // - `display` is for visual display
    // - `notation` and `anim`, if non-null, describe a pattern
    fun addResult(display: String, notation: String?, anim: String?)

    // called when the generator is done
    fun completed() {}
}
