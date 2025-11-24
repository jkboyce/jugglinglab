//
// JMLTransition.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

class JMLTransition(
    var transType: Int,
    var path: Int,
    var throwType: String?,
    var mod: String?
) {
    var incomingPathLink: PathLink? = null
    var outgoingPathLink: PathLink? = null

    fun copy() = JMLTransition(transType, path, throwType, mod)

    fun writeJML(wr: Appendable) {
        when (transType) {
            TRANS_THROW -> {
                var out = "<throw path=\"$path\""
                if (throwType != null) {
                    out += " type=\"$throwType\""
                }
                if (mod != null) {
                    out += " mod=\"$mod\""
                }
                wr.append("$out/>\n")
            }
            TRANS_CATCH -> wr.append("<catch path=\"$path\"/>\n")
            TRANS_SOFTCATCH -> wr.append("<catch path=\"$path\" type=\"soft\"/>\n")
            TRANS_GRABCATCH -> wr.append("<catch path=\"$path\" type=\"grab\"/>\n")
            TRANS_HOLDING -> wr.append("<holding path=\"$path\"/>\n")
        }
    }

    companion object {
        const val TRANS_NONE: Int = 0
        const val TRANS_THROW: Int = 1
        const val TRANS_CATCH: Int = 2
        const val TRANS_SOFTCATCH: Int = 3
        const val TRANS_GRABCATCH: Int = 4
        const val TRANS_HOLDING: Int = 5
        const val TRANS_ANY: Int = 6
    }
}
