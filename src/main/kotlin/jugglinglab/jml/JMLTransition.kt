//
// JMLTransition.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

import java.io.IOException
import java.io.PrintWriter

class JMLTransition(
    @JvmField var transType: Int,
    @JvmField var path: Int,
    var throwType: String?,
    @JvmField var mod: String?
) {
    var incomingPathLink: PathLink? = null
    var outgoingPathLink: PathLink? = null

    fun getType() = transType
    fun setType(newtype: Int) {
        transType = newtype
    }

    fun copy() = JMLTransition(transType, path, throwType, mod)

    @Throws(IOException::class)
    fun writeJML(wr: PrintWriter) {
        when (transType) {
            TRANS_THROW -> {
                var out = "<throw path=\"$path\""
                if (this.throwType != null) {
                    out += " type=\"$throwType\""
                }
                if (this.mod != null) {
                    out += " mod=\"$mod\""
                }
                wr.println("$out/>")
            }

            TRANS_CATCH -> wr.println("<catch path=\"$path\"/>")
            TRANS_SOFTCATCH -> wr.println("<catch path=\"$path\" type=\"soft\"/>")
            TRANS_GRABCATCH -> wr.println("<catch path=\"$path\" type=\"grab\"/>")
            TRANS_HOLDING -> wr.println("<holding path=\"$path\"/>")
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
