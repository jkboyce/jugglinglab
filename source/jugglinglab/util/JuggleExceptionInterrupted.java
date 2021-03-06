// JuggleExceptionInterrupted.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;


// This is our own exception class, which we use to handle juggling-related
// problems that occur.

public class JuggleExceptionInterrupted extends JuggleExceptionUser {
    public JuggleExceptionInterrupted()            { super();  }
    public JuggleExceptionInterrupted(String s)    { super(s); }
}

