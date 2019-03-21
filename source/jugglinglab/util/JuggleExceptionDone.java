// JuggleExceptionDone.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;


// This is our own exception class, which we use to handle juggling-related
// problems that occur.

public class JuggleExceptionDone extends JuggleExceptionUser {
    public JuggleExceptionDone()            { super();  }
    public JuggleExceptionDone(String s)    { super(s); }
}

