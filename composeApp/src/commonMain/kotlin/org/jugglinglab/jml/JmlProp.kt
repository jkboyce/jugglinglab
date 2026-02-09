//
// JmlProp.java
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.jml

import org.jugglinglab.prop.Prop
import org.jugglinglab.util.JuggleExceptionUser

data class JmlProp (
    val type: String,
    val mod: String?
) {
    @get:Throws(JuggleExceptionUser::class)
    val prop: Prop by lazy {
        Prop.Companion.newProp(type).apply { initProp(mod) }
    }

    fun writeJml(wr: Appendable) {
        val modString = if (mod != null) " mod=\"$mod\"" else ""
        wr.append("<prop type=\"$type\"$modString/>\n")
    }

    companion object {
        @Suppress("unused")
        fun fromJmlNode(current: JmlNode, version: String?): JmlProp {
            return JmlProp(
                type = current.attributes.getValueOf("type")!!,
                mod = current.attributes.getValueOf("mod")
            )
        }
    }
}
