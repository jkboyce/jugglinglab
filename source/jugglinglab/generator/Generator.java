// Generator.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

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

    public abstract String getNotationName();
    public abstract String getStartupMessage();
    public abstract JPanel getGeneratorControls();
    public abstract void resetGeneratorControls();

    // use parameters from controller frame
    public abstract void initGenerator() throws JuggleExceptionUser;

    // use command line args
    public abstract void initGenerator(String[] args) throws JuggleExceptionUser;

    public void initGenerator(String arg) throws JuggleExceptionUser {
        int i, numargs;
        StringTokenizer st = new StringTokenizer(arg, " \n");

        numargs = st.countTokens();
        String args[] = new String[numargs];

        for (i = 0; i < numargs; i++)
            args[i] = st.nextToken();

        this.initGenerator(args);
    }

    public abstract int runGenerator(GeneratorTarget t);
    // The following is identical to the above, but imposes
    // bounds in space and time
    public abstract int runGenerator(GeneratorTarget t, int max_num,
                                     double secs) throws JuggleExceptionUser;
}
