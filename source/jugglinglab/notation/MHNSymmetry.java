// MHNSymmetry.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import jugglinglab.util.*;


public class MHNSymmetry {
    int         type;
    int         numjugglers;
    Permutation jugglerperm = null;
    int         delay = -1;

    static final public int TYPE_DELAY = 1;     // types of symmetries
    static final public int TYPE_SWITCH = 2;
    static final public int TYPE_SWITCHDELAY = 3;


    public MHNSymmetry(int type, int numjugglers, String jugperm, int delay) throws JuggleExceptionUser {
        setType(type);
        setJugglerPerm(numjugglers, jugperm);
        setDelay(delay);
    }

    public int getType()                { return type; }
    protected void setType(int type)    { this.type = type; }
    public int getNumberOfJugglers()    { return numjugglers; }
    public Permutation getJugglerPerm() { return jugglerperm; }
    protected void setJugglerPerm(int nj, String jp) throws JuggleExceptionUser {
        this.numjugglers = nj;
        try {
            if (jp == null)
                this.jugglerperm = new Permutation(numjugglers, true);
            else
                this.jugglerperm = new Permutation(numjugglers, jp, true);
        } catch (JuggleException je) {
            throw new JuggleExceptionUser(je.getMessage());
        }
    }
    public int getDelay()               { return delay; }
    protected void setDelay(int del)    { this.delay = del; }
}
