//
// JMLSymmetry.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import jugglinglab.generated.resources.*
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Permutation
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.getStringResource

class JMLSymmetry {
    var symType: Int = 0
        private set
    var numberOfJugglers: Int = 0
        private set
    var numberOfPaths: Int = 0
        private set
    var jugglerPerm: Permutation? = null
        private set
    var pathPerm: Permutation? = null
        private set
    var delay: Double = -1.0

    constructor()

    constructor(
        symType: Int,
        numJugglers: Int,
        jugglerPerm: String?,
        numPaths: Int,
        pathPerm: String?,
        delay: Double
    ) {
        this.symType = symType
        setJugglerPerm(numJugglers, jugglerPerm)
        setPathPerm(numPaths, pathPerm)
        this.delay = delay
    }

    fun getType() = symType

    private fun setJugglerPerm(nj: Int, jp: String?) {
        numberOfJugglers = nj
        jugglerPerm = if (jp == null) {
            Permutation(nj, true)
        } else {
            Permutation(nj, jp, true)
        }
    }

    @Throws(JuggleExceptionUser::class)
    fun setPathPerm(np: Int, pp: String?) {
        numberOfPaths = np
        pathPerm = if (pp == null) {
            Permutation(np, false)
        } else {
            Permutation(np, pp, false)
        }
    }

    //--------------------------------------------------------------------------
    //  Reader/writer methods
    //--------------------------------------------------------------------------

    @Suppress("unused")
    @Throws(JuggleExceptionUser::class)
    fun readJML(current: JMLNode, numjug: Int, numpat: Int, version: String?) {
        val at = current.attributes
        val symtypenum: Int
        var delay = -1.0

        val symType = at.getAttribute("type")
        val jugglerPerm = at.getAttribute("jperm")
        val pathPerm = at.getAttribute("pperm")
        val delayString = at.getAttribute("delay")
        if (delayString != null) {
            try {
                delay = jlParseFiniteDouble(delayString)
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_symmetry_format)
                throw JuggleExceptionUser(message)
            }
        }

        if (symType == null) {
            val message = getStringResource(Res.string.error_symmetry_notype)
            throw JuggleExceptionUser(message)
        }
        symtypenum = if (symType.equals("delay", ignoreCase = true)) {
            TYPE_DELAY
        } else if (symType.equals("switch", ignoreCase = true)) {
            TYPE_SWITCH
        } else if (symType.equals("switchdelay", ignoreCase = true)) {
            TYPE_SWITCHDELAY
        } else {
            val message = getStringResource(Res.string.error_symmetry_type)
            throw JuggleExceptionUser(message)
        }

        this.symType = symtypenum
        setJugglerPerm(numjug, jugglerPerm)
        setPathPerm(numpat, pathPerm)
        this.delay = delay
    }

    fun writeJML(wr: Appendable) {
        var out = "<symmetry type=\""
        when (symType) {
            TYPE_DELAY -> out +=
                ("delay\" pperm=\""
                    + pathPerm!!.toString(true)
                    + "\" delay=\""
                    + jlToStringRounded(this.delay, 4)
                    + "\"/>\n")
            TYPE_SWITCH -> out +=
                ("switch\" jperm=\""
                    + jugglerPerm!!.toString(true)
                    + "\" pperm=\""
                    + pathPerm!!.toString(true)
                    + "\"/>\n")
            TYPE_SWITCHDELAY -> out +=
                ("switchdelay\" jperm=\""
                    + jugglerPerm!!.toString(true)
                    + "\" pperm=\""
                    + pathPerm!!.toString(true)
                    + "\"/>\n")
        }
        wr.append(out)
    }

    companion object {
        const val TYPE_DELAY: Int = 1 // types of symmetries
        const val TYPE_SWITCH: Int = 2
        const val TYPE_SWITCHDELAY: Int = 3
    }
}
