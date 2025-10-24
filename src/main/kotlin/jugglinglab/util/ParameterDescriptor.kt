//
// ParameterDescriptor.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

class ParameterDescriptor(
    @JvmField var name: String,
    @JvmField var type: Int,
    @JvmField var range: ArrayList<String>?,
    @JvmField var defaultValue: Any?,
    @JvmField var value: Any?
) {
    companion object {
        const val TYPE_BOOLEAN: Int = 1
        const val TYPE_FLOAT: Int = 2
        const val TYPE_CHOICE: Int = 3
        const val TYPE_INT: Int = 4
        const val TYPE_ICON: Int = 5
    }
}
