//
// MHNSymmetry.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation;

import jugglinglab.util.*;

public class MHNSymmetry {
  int type;
  int numjugglers;
  Permutation jugglerperm;
  int delay = -1;

  public static final int TYPE_DELAY = 1; // types of symmetries
  public static final int TYPE_SWITCH = 2;
  public static final int TYPE_SWITCHDELAY = 3;

  public MHNSymmetry(int type, int numjugglers, String jugperm, int delay)
      throws JuggleExceptionInternal {
    setType(type);
    setJugglerPerm(numjugglers, jugperm);
    setDelay(delay);
  }

  public int getType() {
    return type;
  }

  protected void setType(int type) {
    this.type = type;
  }

  public int getNumberOfJugglers() {
    return numjugglers;
  }

  public Permutation getJugglerPerm() {
    return jugglerperm;
  }

  protected void setJugglerPerm(int nj, String jp) throws JuggleExceptionInternal {
    numjugglers = nj;
    try {
      if (jp == null) jugglerperm = new Permutation(numjugglers, true);
      else jugglerperm = new Permutation(numjugglers, jp, true);
    } catch (JuggleException je) {
      throw new JuggleExceptionInternal(je.getMessage());
    }
  }

  public int getDelay() {
    return delay;
  }

  protected void setDelay(int del) {
    delay = del;
  }
}
