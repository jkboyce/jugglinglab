// JuggleExceptionInternal.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;


// This is our own exception class, which we use to handle juggling-related
// problems that occur.

public class JuggleExceptionInternal extends JuggleException {
    public JuggleExceptionInternal()            { super();  }
    public JuggleExceptionInternal(String s)    { super(s); }
}
