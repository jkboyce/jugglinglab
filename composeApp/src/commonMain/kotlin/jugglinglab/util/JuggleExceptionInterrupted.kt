//
// JuggleExceptionInterrupted.kt
//
// This is our own exception class, which we use to handle juggling-related
// problems that occur.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

class JuggleExceptionInterrupted : JuggleExceptionUser {
    constructor() : super()

    constructor(s: String) : super(s)
}
