//
// Constants.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.curve.Curve

object Constants {
    const val VERSION: String = "1.6.8"
    const val YEAR: String = "2026"

    const val SITE_URL: String = "http://jugglinglab.org"
    const val DOWNLOAD_URL: String = "https://jugglinglab.org/#download"
    const val HELP_URL: String = "https://jugglinglab.org/#help"

    // how juggler angles are interpolated
    const val ANGLE_LAYOUT_METHOD: Int = Curve.CURVE_LINE

    // for positioning windows on screen; scale to a box of this pixel width,
    // centered on the screen
    const val RESERVED_WIDTH_PIXELS: Int = 1200

    // flags to print useful debugging info to stdout
    const val DEBUG_SITESWAP_PARSING: Boolean = false
    const val DEBUG_JML_PARSING: Boolean = false
    const val DEBUG_PATTERN_CREATION: Boolean = false
    const val DEBUG_LAYOUT: Boolean = false
    const val DEBUG_TRANSITIONS: Boolean = false
    const val DEBUG_GENERATOR: Boolean = false
    const val DEBUG_OPTIMIZE: Boolean = false
    const val DEBUG_OPEN_SERVER: Boolean = false
    const val VALIDATE_GENERATED_PATTERNS: Boolean = false
}
