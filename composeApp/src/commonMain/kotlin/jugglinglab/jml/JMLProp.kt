//
// JMLProp.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.prop.Prop
import jugglinglab.util.JuggleExceptionUser

data class JMLProp (
    val type: String,
    val mod: String?
) {
    @get:Throws(JuggleExceptionUser::class)
    val prop: Prop by lazy {
        Prop.newProp(type).apply { initProp(mod) }
    }
    
    @get:Throws(JuggleExceptionUser::class)
    val isColorable: Boolean
        get() = prop.isColorable

    fun writeJML(wr: Appendable) {
        val modString = if (mod != null) " mod=\"$mod\"" else ""
        wr.append("<prop type=\"$type\"$modString/>\n")
    }

    companion object {
        @Suppress("unused")
        fun fromJMLNode(current: JMLNode, version: String?): JMLProp {
            return JMLProp(
                type = current.attributes.getValueOf("type")!!,
                mod = current.attributes.getValueOf("mod")
            )
        }
    }
}
