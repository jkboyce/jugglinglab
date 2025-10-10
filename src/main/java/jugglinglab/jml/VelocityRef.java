//
// VelocityRef.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml;

import jugglinglab.path.*;
import jugglinglab.util.*;

public class VelocityRef {
  public static int VR_THROW = 0;
  public static int VR_CATCH = 1;
  public static int VR_SOFTCATCH = 2;

  protected Path pp;
  protected int src;

  public VelocityRef(Path path, int source) {
    pp = path;
    src = source;
  }

  public Coordinate getVelocity() {
    if (src == VR_THROW) {
      return pp.getStartVelocity();
    } else {
      return pp.getEndVelocity();
    }
  }

  public int getSource() {
    return src;
  }
}
