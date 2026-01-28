//
// SiteswapParseException.kt
//
// Exception type for reporting syntax errors during parsing.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation.ssparser

import jugglinglab.util.JuggleException

open class SiteswapParseException : JuggleException {
    constructor(s: String) : super(s)
}
