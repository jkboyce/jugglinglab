// mhnSymmetry.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.notation;

import jugglinglab.util.*;


public class mhnSymmetry {
    int		type;
    int		numjugglers;
    Permutation jugglerperm = null;
    int		delay = -1;

    static final public int TYPE_DELAY = 1;		// types of symmetries
    static final public int TYPE_SWITCH = 2;
    static final public int TYPE_SWITCHDELAY = 3;


    public mhnSymmetry(int type, int numjugglers, String jugperm, int delay) throws JuggleExceptionUser {
        setType(type);
        setJugglerPerm(numjugglers, jugperm);
        setDelay(delay);
    }
    
    public int getType()		{ return type; }
    protected void setType(int type)	{ this.type = type; }
    public int getNumberOfJugglers()	{ return numjugglers; }
    public Permutation getJugglerPerm()	{ return jugglerperm; }
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
    public int getDelay()		{ return delay; }
    protected void setDelay(int del)	{ this.delay = del; }
}