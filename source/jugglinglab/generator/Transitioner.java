// Transitioner.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.generator;

import java.util.ResourceBundle;
import java.util.StringTokenizer;
import javax.swing.JPanel;

import jugglinglab.util.*;


// Base class for all Transitioner objects, which find transitions between
// two patterns in a given notation.

public abstract class Transitioner {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // The built-in transitioners
    public static final String[] builtinTransitioners = { "Siteswap" };

    public static Transitioner newTransitioner(String name) {
        if (name.equalsIgnoreCase("siteswap"))
            return new SiteswapTransitioner();
        return null;
    }

    public void initTransitioner(String arg) throws JuggleExceptionUser, JuggleExceptionInternal {
        int i, numargs;
        StringTokenizer st = new StringTokenizer(arg, " \n");

        numargs = st.countTokens();
        String args[] = new String[numargs];

        for (i = 0; i < numargs; i++)
            args[i] = st.nextToken();

        initTransitioner(args);
    }

    // return the notation name
    public abstract String getNotationName();

    // return a JPanel to be used by ApplicationPanel in the UI
    public abstract JPanel getTransitionerControl();

    // reset control values to defaults
    public abstract void resetTransitionerControl();

    // use parameters from transitioner control
    public abstract void initTransitioner() throws JuggleExceptionUser, JuggleExceptionInternal;

    // use command line args
    public abstract void initTransitioner(String[] args) throws JuggleExceptionUser, JuggleExceptionInternal;

    // run the transitioner with no limits
    public abstract int runTransitioner(GeneratorTarget t) throws JuggleExceptionUser, JuggleExceptionInternal;

    // run the transitioner with bounds on space and time
    public abstract int runTransitioner(GeneratorTarget t, int num_limit,
                    double secs_limit) throws JuggleExceptionUser, JuggleExceptionInternal;
}
