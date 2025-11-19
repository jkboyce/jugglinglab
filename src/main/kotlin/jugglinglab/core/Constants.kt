//
// Constants.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.curve.Curve
import jugglinglab.curve.SplineCurve
import jugglinglab.util.OpenFilesServer

object Constants {
    const val VERSION: String = "1.6.7"
    const val YEAR: String = "2025"

    const val SITE_URL: String = "http://jugglinglab.org"
    const val DOWNLOAD_URL: String = "https://jugglinglab.org/#download"
    const val HELP_URL: String = "https://jugglinglab.org/#help"

    // how splines (e.g., juggler hand movements) are calculated
    const val SPLINE_LAYOUT_METHOD: Int = SplineCurve.MINIMIZE_RMSACCEL

    // how juggler angles are interpolated
    const val ANGLE_LAYOUT_METHOD: Int = Curve.CURVE_LINE

    // method for communicating open-file messages between two running instances
    // of Juggling Lab (used on Windows when the user double-clicks a JML file)
    const val OPEN_FILES_METHOD: Int = OpenFilesServer.SERVER_MMF

    // for positioning windows on screen; scale to a box of this pixel width,
    // centered on the screen
    const val RESERVED_WIDTH_PIXELS: Int = 1200

    // flags to print useful debugging info to stdout
    const val DEBUG_SITESWAP_PARSING: Boolean = false
    const val DEBUG_JML_PARSING: Boolean = false
    const val DEBUG_LAYOUT: Boolean = false
    const val DEBUG_TRANSITIONS: Boolean = false
    const val DEBUG_GENERATOR: Boolean = false
    const val DEBUG_OPTIMIZE: Boolean = false
    const val DEBUG_OPEN_SERVER: Boolean = false
    const val VALIDATE_GENERATED_PATTERNS: Boolean = false
}
