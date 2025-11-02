//
// JuggleExceptionUser.kt
//
// This exception type is for errors resulting from user actions. Typically we
// respond to these by showing an error message to the user.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

open class JuggleExceptionUser : JuggleException {
    constructor() : super()

    constructor(s: String?) : super(s)
}
