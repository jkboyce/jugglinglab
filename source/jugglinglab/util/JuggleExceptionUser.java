// JuggleExceptionUser.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;


// This is our own exception class, which we use to handle juggling-related
// problems that occur.

public class JuggleExceptionUser extends JuggleException {
    public JuggleExceptionUser()            { super();  }
    public JuggleExceptionUser(String s)    { super(s); }
}

