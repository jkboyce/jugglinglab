// VelocityRef.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.jml;

import jugglinglab.util.*;
import jugglinglab.path.*;


public class VelocityRef {
    protected Path  pp;
    protected boolean   start;

    public VelocityRef(Path pp, boolean start) {
        this.pp = pp;
        this.start = start;
    }

    public Coordinate getVelocity() {
        if (start)
            return pp.getStartVelocity();
        else
            return pp.getEndVelocity();
    }
}
