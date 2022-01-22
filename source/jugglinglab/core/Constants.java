// Constants.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.core;

public class Constants {
    public static final String version = "1.6";
    public static final String year = "2022";

    public static final String site_URL = "http://jugglinglab.org";
    public static final String download_URL = "https://jugglinglab.org/#download";
    public static final String help_URL = "https://jugglinglab.org/#help";

    // flags to print useful debugging info to stdout
    public static final boolean DEBUG_SITESWAP_PARSING = false;
    public static final boolean DEBUG_JML_PARSING = false;
    public static final boolean DEBUG_LAYOUT = false;
    public static final boolean DEBUG_TRANSITIONS = false;
    public static final boolean DEBUG_GENERATOR = false;
    public static final boolean DEBUG_OPTIMIZE = false;
    public static final boolean DEBUG_OPEN_SERVER = false;
    public static final boolean VALIDATE_GENERATED_PATTERNS = false;

    public static final int ANGLE_LAYOUT_METHOD = jugglinglab.curve.Curve.lineCurve;
    public static final int SPLINE_LAYOUT_METHOD = jugglinglab.curve.SplineCurve.rmsaccel;
    public static final int OPEN_FILES_METHOD = jugglinglab.util.OpenFilesServer.memorymappedfile;

    // for positioning windows on screen
    public static final int RESERVED_WIDTH_PIXELS = 1200;
}
