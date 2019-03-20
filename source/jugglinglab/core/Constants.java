// Constants.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.core;

public class Constants {
    public static final String version = "1.1";
    public static final String year = "2019";

    public static final String site_URL = "http://jugglinglab.org";
    public static final String download_URL = "https://jugglinglab.org/#download";
    public static final String help_URL = "https://jugglinglab.org/#help";

    public static final boolean DEBUG_LAYOUT = false;
    public static final boolean DEBUG_PARSING = false;

    public static final int ANGLE_LAYOUT_METHOD = jugglinglab.curve.Curve.lineCurve;
    public static final int SPLINE_LAYOUT_METHOD = jugglinglab.curve.splineCurve.rmsaccel;
}
