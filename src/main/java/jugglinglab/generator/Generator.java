// Generator.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.generator;

import java.util.ResourceBundle;
import java.util.StringTokenizer;
import javax.swing.JPanel;

import jugglinglab.util.*;


// This class defines a general object that is capable of generating tricks
// and converting them into commands that the animator understands.

public abstract class Generator {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // The built-in generators
    public static final String[] builtinGenerators = { "Siteswap" };

    public static Generator newGenerator(String name) {
        if (name.equalsIgnoreCase("siteswap"))
            return new SiteswapGenerator();
        return null;
    }

    public void initGenerator(String arg) throws JuggleExceptionUser {
        int i, numargs;
        StringTokenizer st = new StringTokenizer(arg, " \n");

        numargs = st.countTokens();
        String args[] = new String[numargs];

        for (i = 0; i < numargs; i++)
            args[i] = st.nextToken();

        initGenerator(args);
    }

    // return the notation name
    public abstract String getNotationName();

    // return a startup text message
    public abstract String getStartupMessage();

    // return a JPanel to be used by ApplicationPanel in the UI
    public abstract JPanel getGeneratorControl();

    // reset control values to defaults
    public abstract void resetGeneratorControl();

    // use parameters from generator control
    public abstract void initGenerator() throws JuggleExceptionUser;

    // use command line args
    public abstract void initGenerator(String[] args) throws JuggleExceptionUser;

    // run the generator with no limits
    public abstract int runGenerator(GeneratorTarget t) throws JuggleExceptionUser, JuggleExceptionInternal;

    // run the generator with bounds on space and time
    public abstract int runGenerator(GeneratorTarget t, int max_num,
                                     double secs) throws JuggleExceptionUser, JuggleExceptionInternal;
}
