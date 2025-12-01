//
// JuggleException.kt
//
// This is our own exception class, which we use to handle juggling-related
// problems that occur.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

open class JuggleException : Exception {
    constructor() : super()

    constructor(s: String?) : super(s)
}
