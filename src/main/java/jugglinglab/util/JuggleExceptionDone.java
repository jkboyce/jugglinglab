//
// JuggleExceptionDone.java
//
// This exception type is not raised in response to any error condition. It is
// used as a mechanism to abort from tasks.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util;

public class JuggleExceptionDone extends JuggleExceptionUser {
  public JuggleExceptionDone() {
    super();
  }

  public JuggleExceptionDone(String s) {
    super(s);
  }
}
