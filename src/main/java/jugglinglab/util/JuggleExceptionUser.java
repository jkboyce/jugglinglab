// JuggleExceptionUser.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;


// This is our own exception class, which we use to handle exceptions resulting
// from user actions. Typically we respond to these by showing an error message
// to the user.

public class JuggleExceptionUser extends JuggleException {
    public JuggleExceptionUser() {
        super();
    }

    public JuggleExceptionUser(String s) {
        super(s);
    }
}

