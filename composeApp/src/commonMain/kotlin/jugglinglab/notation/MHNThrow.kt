//
// MHNThrow.kt
//
// This class represents an element in the juggling matrix. Often this represents
// the throw of a specific object to some later element in the matrix. It can
// also have a value of zero (`targetindex` == `index`), meaning no throw.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

data class MHNThrow(
    var juggler: Int = 0,  // indexed from 1
    var hand: Int = 0,  // MHNPattern.RIGHT_HAND or LEFT_HAND
    var index: Int = 0,
    var slot: Int = 0,
    var targetJuggler: Int = 0,  // indexed from 1
    var targetHand: Int = 0,  // MHNPattern.RIGHT_HAND or LEFT_HAND
    var targetIndex: Int = 0,
    var targetSlot: Int = 0,
    var mod: String? = null
) {
    // filled in during buildJugglingMatrix():
    var primary: MHNThrow? = null
    var source: MHNThrow? = null
    var target: MHNThrow? = null
    var pathnum: Int = -1
    var catching: Boolean = false  // are we catching just before this throw?
    var catchnum: Int = -1  // order (starting at 1) to make catches

    // # of beats prior of the previous nonzero element for this hand
    var dwellwindow: Int = 0

    // filled in during asJMLPattern():
    var throwtime: Double = 0.0  // in seconds
    var catchtime: Double = 0.0  // time of catch prior to throw
    var handsindex: Int = 0  // index of throw in hands sequence, if one exists

    override fun toString(): String {
        var s = "($juggler, $hand, $index, $slot"
        s = "$s -> $targetJuggler, $targetHand, $targetIndex, $targetSlot)"
        if (primary === this) {
            s = "$s*"
        }
        return s
    }

    // Return whether a throw will be treated as a hold, when rendered.
    @Suppress("unused")
    val isHold: Boolean
        get() {
            if ((targetIndex - index) > 2 || hand != targetHand || juggler != targetJuggler) {
                return false
            }
            return mod == null || mod!!.indexOf('T') == -1
        }

    val isZero: Boolean
        get() = (throwValue == 0 /* pathnum == -1 */)

    val throwValue: Int
        get() = (targetIndex - index)

    val isThrownOne: Boolean
        get() = (mod != null && mod!![0] != 'H' && throwValue == 1)

    companion object {
        // Define an ordering relation for throws.
        //
        // This assumes the throws are coming from the same juggler+hand+index
        // combination, i.e., as part of a multiplex throw.
        //
        // Returns 1 if mhnt1 > mhnt2, -1 if mhnt1 < mhnt2, and 0 iff the throws
        // are identical.

        fun compareThrows(mhnt1: MHNThrow, mhnt2: MHNThrow): Int {
            val beats1 = mhnt1.targetIndex - mhnt1.index
            val beats2 = mhnt2.targetIndex - mhnt2.index

            // more beats > fewer beats
            if (beats1 > beats2) {
                return 1
            } else if (beats1 < beats2) {
                return -1
            }

            val isPass1 = (mhnt1.targetJuggler != mhnt1.juggler)
            val isPass2 = (mhnt2.targetJuggler != mhnt2.juggler)
            val isCross1 = (mhnt1.targetHand == mhnt1.hand) xor (beats1 % 2 == 0)
            val isCross2 = (mhnt2.targetHand == mhnt2.hand) xor (beats2 % 2 == 0)

            // passes > self-throws
            if (isPass1 && !isPass2) {
                return 1
            } else if (!isPass1 && isPass2) {
                return -1
            }

            // for two passes, lower target juggler number wins
            if (isPass1) {
                if (mhnt1.targetJuggler < mhnt2.targetJuggler) {
                    return 1
                } else if (mhnt1.targetJuggler > mhnt2.targetJuggler) {
                    return -1
                }
            }

            // crossed throws beat non-crossed throws
            if (isCross1 && !isCross2) {
                return 1
            } else if (!isCross1 && isCross2) {
                return -1
            }

            // if we get here then {targetjuggler, targethand, targetindex} are all equal
            val hasMod1 = (mhnt1.mod != null)
            val hasMod2 = (mhnt2.mod != null)

            if (hasMod1 && !hasMod2) {
                return 1 // throw with modifier beats one without
            } else if (!hasMod1 && hasMod2) {
                return -1
            } else if (hasMod1) {
                val c = mhnt1.mod!!.compareTo(mhnt2.mod!!)
                if (c < 0) {
                    return 1 // mhnt1.mod lexicographically precedes mhnt2.mod
                } else if (c > 0) {
                    return -1
                }
            }
            return 0
        }
    }
}
