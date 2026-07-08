//
// JmlProp.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.jml

import org.jugglinglab.prop.Prop
import org.jugglinglab.util.jlIsWeb

class JmlProp (
    type: String,
    val mod: String?
) {
    val type = if (jlIsWeb && type.equals("image", ignoreCase = true)) "ball" else type

    val prop: Prop by lazy {
        Prop.newProp(this.type).apply { initProp(mod) }
    }

    fun writeJml(wr: Appendable) {
        val modString = if (mod != null) " mod=\"$mod\"" else ""
        wr.append("<prop type=\"$type\"$modString/>\n")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JmlProp) return false
        return type == other.type && mod == other.mod
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (mod?.hashCode() ?: 0)
        return result
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
