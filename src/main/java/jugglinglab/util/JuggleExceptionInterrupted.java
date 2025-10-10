//
// JuggleExceptionInterrupted.java
//
// This is our own exception class, which we use to handle juggling-related
// problems that occur.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util;

public class JuggleExceptionInterrupted extends JuggleExceptionUser {
  public JuggleExceptionInterrupted() {
    super();
  }

  public JuggleExceptionInterrupted(String s) {
    super(s);
  }
}
