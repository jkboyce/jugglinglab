//
// GeneratorTargetBasic.kt
//
// Adapter to handle generator output by invoking a lambda on each generator
// line, or by adding ach pattern to a list.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

class GeneratorTargetBasic: GeneratorTarget {
    var lambdaTarget: ((String) -> Unit)? = null
    var patternsTarget: MutableList<String>? = null

    // version that processes all displayed lines through a lambda
    constructor(target: (String) -> Unit) {
        lambdaTarget = target
    }

    // version that stores all patterns in a list
    constructor(target: MutableList<String>) {
        patternsTarget = target
    }

    override fun addResult(display: String, notation: String?, anim: String?) {
        lambdaTarget?.invoke(display)
        if (anim != null) {
            patternsTarget?.add(anim)
        }
    }
}
