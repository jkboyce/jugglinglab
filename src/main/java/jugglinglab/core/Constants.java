// Constants.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

public class Constants {
    public static final String version = "1.6.4";
    public static final String year = "2023";

    public static final String site_URL = "http://jugglinglab.org";
    public static final String download_URL = "https://jugglinglab.org/#download";
    public static final String help_URL = "https://jugglinglab.org/#help";

    // how splines (e.g., juggler hand movements) are calculated
    public static final int SPLINE_LAYOUT_METHOD = jugglinglab.curve.SplineCurve.MINIMIZE_RMSACCEL;

    // how juggler angles are interpolated
    public static final int ANGLE_LAYOUT_METHOD = jugglinglab.curve.Curve.CURVE_LINE;

    // method for communicating open-file messages between two running instances
    // of Juggling Lab (used on Windows when the user double-clicks a JML file)
    public static final int OPEN_FILES_METHOD = jugglinglab.util.OpenFilesServer.SERVER_MMF;

    // for positioning windows on screen; scale to a box of this pixel width,
    // centered on the screen
    public static final int RESERVED_WIDTH_PIXELS = 1200;

    // flags to print useful debugging info to stdout
    public static final boolean DEBUG_SITESWAP_PARSING = false;
    public static final boolean DEBUG_JML_PARSING = false;
    public static final boolean DEBUG_LAYOUT = false;
    public static final boolean DEBUG_TRANSITIONS = false;
    public static final boolean DEBUG_GENERATOR = false;
    public static final boolean DEBUG_OPTIMIZE = false;
    public static final boolean DEBUG_OPEN_SERVER = false;
    public static final boolean VALIDATE_GENERATED_PATTERNS = false;
}
