//
// JuggleExceptionDone.java
//
// This exception type is not raised in response to any error condition. It is
// used as a mechanism to abort from tasks.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

class JuggleExceptionDone : JuggleExceptionUser {
  constructor() : super()

  constructor(s: String) : super(s)
}
