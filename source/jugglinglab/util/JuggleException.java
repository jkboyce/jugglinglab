// JuggleException.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;


// This is our own exception class, which we use to handle juggling-related
// problems that occur.

public class JuggleException extends Exception {
    public JuggleException() {
        super();
    }

    public JuggleException(String s) {
        super(s);
    }
}

