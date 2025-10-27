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
    var type: String?
        protected set
    var mod: String? = null
        protected set
    @JvmField
    var prop: Prop?

    init {
        this.type = mod
        prop = null
    }

    constructor(proptype: String?, mod: String?) : this() {
        this.type = proptype
        this.mod = mod
    }

    @Throws(JuggleExceptionUser::class)
    fun layoutProp() {
        prop = Prop.newProp(this.type!!)
        prop!!.initProp(this.mod)
    }

    fun readJML(current: JMLNode, version: String?) {
        val at = current.attributes

        this.type = at.getAttribute("type")
        this.mod = at.getAttribute("mod")
    }

    @Throws(IOException::class)
    fun writeJML(wr: PrintWriter) {
        var out = "<prop type=\"" + this.type + "\""
        if (mod != null) {
            out += " mod=\"" + mod + "\"/>"
        } else {
            out += "/>"
        }
        wr.println(out)
    }
}
