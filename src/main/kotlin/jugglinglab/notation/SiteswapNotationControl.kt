//
// SiteswapNotationControl.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

class SiteswapNotationControl : MHNNotationControl() {
    override fun newPattern(): Pattern {
        return SiteswapPattern()
    }
}
