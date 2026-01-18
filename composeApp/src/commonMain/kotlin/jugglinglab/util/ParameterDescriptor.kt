//
// ParameterDescriptor.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

class ParameterDescriptor(
    val name: String,
    val type: Int,
    val range: List<String>?,
    val defaultValue: Any?,
    var value: Any?
) {
    companion object {
        const val TYPE_BOOLEAN: Int = 1
        const val TYPE_FLOAT: Int = 2
        const val TYPE_CHOICE: Int = 3
        const val TYPE_INT: Int = 4
        const val TYPE_ICON: Int = 5
    }
}
