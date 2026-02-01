//
// JuggleException.kt
//
// These are Juggling Lab's exception types for handling various conditions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.jml.JmlPattern

open class JuggleException : Exception {
    constructor() : super()
    constructor(s: String) : super(s)
}

// This exception type is for errors resulting from user actions. Typically we
// respond to these by showing an error message to the user.

open class JuggleExceptionUser : JuggleException {
    constructor() : super()
    constructor(s: String) : super(s)
}

// This exception type is not raised in response to any error condition. It is
// used as a mechanism to abort from tasks.

class JuggleExceptionDone : JuggleExceptionUser {
    constructor(s: String) : super(s)
}

class JuggleExceptionInterrupted : JuggleExceptionUser {
    constructor() : super()
}

// This exception type is for errors that in principle should never occur (i.e.,
// no user action should be able to trigger these). We typically respond to
// these with the dialog box at jlHandleFatalException().
//
// Other exception types can be wrapped by an instance of this class so that
// a JmlPattern can be attached and displayed in the error dialog box.

open class JuggleExceptionInternal : JuggleException {
    var wrapped: Throwable? = null
    var pattern: JmlPattern? = null

    // for an original error
    constructor(s: String, pattern: JmlPattern? = null) : super(s) {
        this.pattern = pattern
    }

    // for wrapping another error and including a pattern
    constructor(e: Throwable, pattern: JmlPattern?) : super(e.message ?: "") {
        this.wrapped = e
        this.pattern = pattern
    }
}
