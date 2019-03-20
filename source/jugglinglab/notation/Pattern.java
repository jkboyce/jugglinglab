// Pattern.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.awt.*;
import java.util.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public abstract class Pattern {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // The built-in notations
    public static final String[] builtinNotations = { "Siteswap" };

    // these should be in the same order as in the builtinNotations array
    public static final int NOTATION_NONE = 0;
    public static final int NOTATION_SITESWAP = 1;

    public static Pattern newPattern(String notation) throws JuggleExceptionUser,
                                                JuggleExceptionInternal {
        if (notation.equalsIgnoreCase("siteswap"))
            return new SiteswapPattern();
        return null;
    }

    public abstract String getNotationName();

    // read pattern from textual representation
    public abstract void fromString(String config) throws
                        JuggleExceptionUser, JuggleExceptionInternal;

    // output pattern to textual representation
    public abstract String toString();

    // convert pattern to JML
    public abstract JMLPattern getJMLPattern() throws
                        JuggleExceptionUser, JuggleExceptionInternal;
}
