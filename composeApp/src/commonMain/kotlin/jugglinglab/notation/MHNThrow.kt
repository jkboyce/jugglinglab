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
    val juggler: Int = 0,  // indexed from 1
    val hand: Int = 0,  // MHNPattern.RIGHT_HAND or LEFT_HAND
    val index: Int = 0,
    val slot: Int = 0,
    val targetJuggler: Int = 0,  // indexed from 1
    val targetHand: Int = 0,  // MHNPattern.RIGHT_HAND or LEFT_HAND
    val targetIndex: Int = 0,
    val targetSlot: Int = 0,
    val mod: String? = null
) : Comparable<MHNThrow> {
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
            return mod == null || mod.indexOf('T') == -1
        }

    val isZero: Boolean
        get() = (throwValue == 0 /* pathnum == -1 */)

    val throwValue: Int
        get() = (targetIndex - index)

    val isThrownOne: Boolean
        get() = (mod != null && mod[0] != 'H' && throwValue == 1)

    override fun compareTo(other: MHNThrow): Int {
        val beats1 = targetIndex - index
        val beats2 = other.targetIndex - other.index

        // more beats > fewer beats
        if (beats1 > beats2) {
            return 1
        } else if (beats1 < beats2) {
            return -1
        }

        val isPass1 = (targetJuggler != juggler)
        val isPass2 = (other.targetJuggler != other.juggler)
        val isCross1 = (targetHand == hand) xor (beats1 % 2 == 0)
        val isCross2 = (other.targetHand == other.hand) xor (beats2 % 2 == 0)

        // passes > self-throws
        if (isPass1 && !isPass2) {
            return 1
        } else if (!isPass1 && isPass2) {
            return -1
        }

        // for two passes, lower target juggler number wins
        if (isPass1) {
            if (targetJuggler < other.targetJuggler) {
                return 1
            } else if (targetJuggler > other.targetJuggler) {
                return -1
            }
        }

        // crossed throws beat non-crossed throws
        if (isCross1 && !isCross2) {
            return 1
        } else if (!isCross1 && isCross2) {
            return -1
        }

        // if we get here then {beats, isPass, isCross} are all equal
        val hasMod1 = (mod != null)
        val hasMod2 = (other.mod != null)

        if (hasMod1 && !hasMod2) {
            return 1 // throw with modifier beats one without
        } else if (!hasMod1 && hasMod2) {
            return -1
        } else if (hasMod1) {
            val c = mod.compareTo(other.mod!!)
            if (c < 0) {
                return 1 // mhnt1.mod lexicographically precedes mhnt2.mod
            } else if (c > 0) {
                return -1
            }
        }

        // if we get here then {beats, isPass, isCross, mod} are equal;
        // compare on the basis of position in the pattern

        if (index != other.index) {
            return index.compareTo(other.index)
        }
        if (juggler != other.juggler) {
            return juggler.compareTo(other.juggler)
        }
        if (hand != other.hand) {
            // ordering is so right hand sorts before left:
            return other.hand.compareTo(hand)
        }
        // identical throws at different slots compare as equal; this is for
        // detecting clustered multiplex throws
        return 0
    }
}
