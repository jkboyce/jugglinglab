// MHNThrow.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.notation;

import jugglinglab.util.*;

// This class represents an element in the juggling matrix. Often this represents
// the throw of a specific object to some later element in the matrix. It can
// also have a value of zero (`targetindex` == `index`), meaning no throw.

public class MHNThrow {
    // filled in during initial pattern definition:

    public int juggler;  // indexed from 1
    public int hand;  // MHNPattern.RIGHT_HAND or LEFT_HAND
    public int index;
    public int slot;
    public int targetjuggler;  // indexed from 1
    public int targethand;  // MHNPattern.RIGHT_HAND or LEFT_HAND
    public int targetindex;
    public int targetslot;
    public String mod;

    // filled in during buildJugglingMatrix():

    public MHNThrow master;
    public MHNThrow source;
    public MHNThrow target;
    public int pathnum = -1;
    public boolean catching = false;  // are we catching just before this throw?
    public int catchnum = -1;  // order (starting at 1) to make catches
    // # of beats prior of the previous nonzero element for this hand
    public int dwellwindow;

    // filled in during asJMLPattern():

    public double throwtime;  // in seconds
    public double catchtime;  // time of catch prior to throw
    public int handsindex;  // index of throw in hands sequence, if one exists


    public MHNThrow() {}

    public MHNThrow(int j, int h, int i, int s, int tj, int th, int ti, int ts, String m) {
        juggler = j;
        hand = h;
        index = i;
        slot = s;
        targetjuggler = tj;
        targethand = th;   // 0 for right hand, 1 for left hand
        targetindex = ti;
        targetslot = ts;
        mod = m;  // "T" is the default modifier
    }

    @Override
    public String toString() {
        String s = "(" + juggler + ", " + hand + ", " + index + ", " + slot;
        s = s + " -> " + targetjuggler + ", " + targethand + ", " + targetindex + ", " + targetslot + ")";
        if (master == this)
            s = s + "*";
        return s;
    }

    // Indicates whether a throw will be treated as a hold, when rendered
    public boolean isHold() {
        if ((targetindex - index) > 2 || hand != targethand || juggler != targetjuggler)
            return false;

        if (mod != null && mod.indexOf('T') != -1)
            return false;

        return true;
    }

    public boolean isZero() {
        return (throwValue() == 0 /* pathnum == -1 */);
    }

    public int throwValue() {
        return (targetindex - index);
    }

    public boolean isThrownOne() {
        return (mod != null && mod.charAt(0) != 'H' && throwValue() == 1);
    }

    // Establishes an ordering relation for throws.
    //
    // This assumes the throws are coming from the same juggler+hand+index
    // combination, i.e., as part of a multiplex throw.
    //
    // Returns 1 if mhnt1 > mhnt2, -1 if mhnt1 < mhnt2, and 0 iff the throws
    // are identical.
    public static int compareThrows(MHNThrow mhnt1, MHNThrow mhnt2) {
        int beats1 = mhnt1.targetindex - mhnt1.index;
        int beats2 = mhnt2.targetindex - mhnt2.index;

        // more beats > fewer beats
        if (beats1 > beats2)
            return 1;
        else if (beats1 < beats2)
            return -1;

        boolean is_pass1 = (mhnt1.targetjuggler != mhnt1.juggler);
        boolean is_pass2 = (mhnt2.targetjuggler != mhnt2.juggler);
        boolean is_cross1 = (mhnt1.targethand == mhnt1.hand) ^ (beats1 % 2 == 0);
        boolean is_cross2 = (mhnt2.targethand == mhnt2.hand) ^ (beats2 % 2 == 0);

        // passes > self-throws
        if (is_pass1 && !is_pass2)
            return 1;
        else if (!is_pass1 && is_pass2)
            return -1;

        // for two passes, lower target juggler number wins
        if (is_pass1) {
            if (mhnt1.targetjuggler < mhnt2.targetjuggler)
                return 1;
            else if (mhnt1.targetjuggler > mhnt2.targetjuggler)
                return -1;
        }

        // crossed throws beat non-crossed throws
        if (is_cross1 && !is_cross2)
            return 1;
        else if (!is_cross1 && is_cross2)
            return -1;

        // if we get here then {targetjuggler, targethand, targetindex} are all
        // equal

        boolean has_mod1 = (mhnt1.mod != null);
        boolean has_mod2 = (mhnt2.mod != null);

        if (has_mod1 && !has_mod2)
            return 1;  // throw with modifier beats one without
        else if (!has_mod1 && has_mod2)
            return -1;
        else if (has_mod1 && has_mod2) {
            int c = mhnt1.mod.compareTo(mhnt2.mod);

            if (c < 0)
                return 1;  // mhnt1.mod lexicographically precedes mhnt2.mod
            else if (c > 0)
                return -1;
        }

        return 0;
    }
}
