//
// JuggleExceptionInternal.kt
//
// This exception type is for errors that in principle should never occur (i.e.,
// no user action should be able to trigger these). We typically respond to
// these with the dialog box at jlHandleFatalException().
//
// Other exception types can be wrapped by an instance of this class so that
// a JMLPattern can be attached and displayed in the error dialog box.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.jml.JMLPattern

open class JuggleExceptionInternal : JuggleException {
    var wrapped: Exception? = null
    var pattern: JMLPattern? = null

    // for an original error
    constructor(s: String, pattern: JMLPattern? = null) : super(s) {
        this.pattern = pattern
    }

    // for wrapping another error and including a pattern
    constructor(e: Exception, pattern: JMLPattern?) : super(e.message ?: "") {
        this.wrapped = e
        this.pattern = pattern
    }
}
