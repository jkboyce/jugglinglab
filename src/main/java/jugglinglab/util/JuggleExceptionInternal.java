//
// JuggleExceptionInternal.java
//
// This exception type is for errors that in principle should never occur (i.e.,
// no user action should be able to trigger these). We typically respond to
// these with the dialog box at ErrorDialog.handleFatalException().
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util;

public class JuggleExceptionInternal extends JuggleException {
  public JuggleExceptionInternal() {
    super();
  }

  public JuggleExceptionInternal(String s) {
    super(s);
  }
}
