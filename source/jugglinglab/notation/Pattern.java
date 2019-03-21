// Pattern.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.util.ResourceBundle;

import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;


// This is the base class for all non-JML pattern types in Juggling Lab.
// This is used to parse strings to create the JMLPatterns that are animated.

public abstract class Pattern {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // The built-in notations
    public static final String[] builtinNotations = { "Siteswap" };

    // these should be in the same order as in the builtinNotations array
    public static final int NOTATION_NONE = 0;
    public static final int NOTATION_SITESWAP = 1;

    // creates a new blank pattern in the given notation
    public static Pattern newPattern(String notation) throws JuggleExceptionUser,
                                                JuggleExceptionInternal {
        if (notation == null)
            throw new JuggleExceptionUser("Notation type not specified");

        if (notation.equalsIgnoreCase("siteswap"))
            return new SiteswapPattern();

        throw new JuggleExceptionUser("Notation type '"+notation+"' not recognized");
    }

    // return the notation name
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
