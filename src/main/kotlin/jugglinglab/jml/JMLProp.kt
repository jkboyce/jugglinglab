//
// JMLProp.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.prop.Prop
import jugglinglab.util.JuggleExceptionUser
import java.io.IOException
import java.io.PrintWriter

data class JMLProp @Throws(JuggleExceptionUser::class) constructor(
    val type: String,
    val mod: String?
) {
    // constructor for reading JML
    @Suppress("unused")
    @Throws(JuggleExceptionUser::class)
    constructor(current: JMLNode, version: String?) : this(
        type = current.attributes.getAttribute("type")!!,
        mod = current.attributes.getAttribute("mod")
    )

    val prop: Prop by lazy {
        Prop.newProp(type).apply { initProp(mod) }
    }
    
    val isColorable: Boolean
        get() = prop.isColorable

    @Throws(IOException::class)
    fun writeJML(wr: PrintWriter) {
        val modString = if (mod != null) " mod=\"$mod\"" else ""
        wr.println("<prop type=\"$type\"$modString/>")
    }
}
