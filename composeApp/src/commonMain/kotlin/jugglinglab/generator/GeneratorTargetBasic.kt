//
// GeneratorTargetBasic.kt
//
// Adapter to handle generator output by invoking a lambda on each generator
// line, or by adding each pattern to a list.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

class GeneratorTargetBasic(
    var lambdaTarget: ((String) -> Unit)? = null,
    var listTarget: MutableList<String>? = null
) : GeneratorTarget {
    override fun addResult(display: String, notation: String?, anim: String?) {
        lambdaTarget?.invoke(display)
        if (anim != null) {
            listTarget?.add(anim)
        }
    }
}
