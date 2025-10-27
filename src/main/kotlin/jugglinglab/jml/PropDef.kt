//
// PropDef.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//
package jugglinglab.jml

import jugglinglab.prop.Prop
import jugglinglab.util.JuggleExceptionUser
import java.io.IOException
import java.io.PrintWriter

class PropDef() {
    var type: String? = null
        private set
    var mod: String? = null
        private set
    @JvmField
    var prop: Prop? = null

    constructor(propType: String?, propMod: String?) : this() {
        type = propType
        mod = propMod
    }

    @Throws(JuggleExceptionUser::class)
    fun layoutProp() {
        val newprop = Prop.newProp(type!!)
        newprop.initProp(mod)
        prop = newprop
    }

    @Suppress("unused")
    fun readJML(current: JMLNode, version: String?) {
        val at = current.attributes
        type = at.getAttribute("type")
        mod = at.getAttribute("mod")
    }

    @Throws(IOException::class)
    fun writeJML(wr: PrintWriter) {
        var out = "<prop type=\"$type\""
        out += if (mod != null) " mod=\"$mod\"/>" else "/>"
        wr.println(out)
    }
}
