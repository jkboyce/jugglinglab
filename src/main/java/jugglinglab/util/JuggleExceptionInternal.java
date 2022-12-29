// JuggleExceptionInternal.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;


// This is our own exception class, which we use to handle errors that in
// principle should never occur (i.e., no user action should be able to
// trigger these). We typically respond to these with the dialog box at
// ErrorDialog.handleFatalException().

public class JuggleExceptionInternal extends JuggleException {
    public JuggleExceptionInternal() {
        super();
    }

    public JuggleExceptionInternal(String s) {
        super(s);
    }
}
