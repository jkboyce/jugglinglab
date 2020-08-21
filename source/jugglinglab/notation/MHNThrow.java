// MHNThrow.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import jugglinglab.util.*;


public class MHNThrow {
    public int juggler;             // indexed from 0
    public int hand;                // MHNPattern.RIGHT_HAND or LEFT_HAND
    public int index;
    public int slot;
    public int targetjuggler;       // indexed from 0
    public int targethand;          // MHNPattern.RIGHT_HAND or LEFT_HAND
    public int targetindex;
    public int targetslot;
    public int handsindex;          // index of throw in hands sequence, if one exists
    public int pathnum = -1;
    public String mod;
    public MHNThrow master;
    public MHNThrow source;
    public MHNThrow target;
    public boolean catching = false;    // are we catching just before this throw?
    public int catchnum = -1;       // order (starting at 1) to make catches

    public MHNThrow() {}

    public MHNThrow(int j, int h, int i, int s, int tj, int th, int ti, int ts, String m) {
        this.juggler = j;
        this.hand = h;
        this.index = i;
        this.slot = s;
        this.targetjuggler = tj;
        this.targethand = th;   // 0 for right hand, 1 for left hand
        this.targetindex = ti;
        this.targetslot = ts;
        this.mod = m;
    }

    /*
    public String toString() {
        String s = "(" + juggler + ", " + hand + ", " + index + ", " + slot;
        s = s + " -> " + targetjuggler + ", " + targethand + ", " + targetindex + ", " + targetslot + ")";
        if (master == this)
            s = s + "*";
        return s;
    }
    */

    // Indicates whether a throw will be treated as a hold, when rendered
    public boolean isHold() {
        if ((targetindex - index) > 2 || hand != targethand || juggler != targetjuggler)
            return false;

        if (mod != null && mod.indexOf('T') != -1)
            return false;

        return true;
    }

    // Establishes an ordering relation for throws.
    //
    // Returns 1 if mhnt1 > mhnt2, -1 if mhnt1 < mhnt2, and 0 iff the throws
    // are identical.
    public static int compareThrows(MHNThrow mhnt1, MHNThrow mhnt2) {
        int beats1 = mhnt1.targetindex - mhnt1.index;
        int beats2 = mhnt2.targetindex - mhnt2.index;

        if (beats1 > beats2)
            return 1;
        else if (beats1 < beats2)
            return -1;


        return 0;
    }
}
