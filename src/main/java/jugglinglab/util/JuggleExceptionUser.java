//
// JuggleExceptionUser.java
//
// This exception type is for errors resulting from user actions. Typically we
// respond to these by showing an error message to the user.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util;

public class JuggleExceptionUser extends JuggleException {
  public JuggleExceptionUser() {
    super();
  }

  public JuggleExceptionUser(String s) {
    super(s);
  }
}
