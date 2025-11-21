//
// JuggleExceptionInternal.kt
//
// This exception type is for errors that in principle should never occur (i.e.,
// no user action should be able to trigger these). We typically respond to
// these with the dialog box at ErrorDialog.handleFatalException().
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.jml.JMLPattern

class JuggleExceptionInternal : JuggleException {
    var pat: JMLPattern? = null

    constructor() : super()

    constructor(s: String?) : super(s)

    // Constructor that includes a reference to the JMLPattern where the error
    // occurred. See ErrorDialog::handleFatalException().

    constructor(s: String?, pat: JMLPattern?) : super(s) {
        this.pat = pat
    }

    fun attachPattern(pat: JMLPattern?) {
        this.pat = pat
    }
}
