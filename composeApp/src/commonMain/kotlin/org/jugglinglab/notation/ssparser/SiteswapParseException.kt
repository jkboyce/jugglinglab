//
// SiteswapParseException.kt
//
// Exception type for reporting syntax errors during parsing.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation.ssparser

import org.jugglinglab.util.JuggleException

open class SiteswapParseException : JuggleException {
    constructor(s: String) : super(s)
}
