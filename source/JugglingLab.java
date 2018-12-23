// JugglingLab.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.generator.*;
import jugglinglab.util.*;


public class JugglingLab {
    // command line arguments as an ArrayList that can be trimmed as sections
    // are parsed
    protected static ArrayList<String> jlargs = null;

    // output file path, if specified
    protected static String outpath = null;

    // Look at beginning of jlargs to see if there's a pattern, and if so then
    // parse it at return it. Otherwise print an error message and return null.
    protected static JMLPattern parse_pattern() {
        return null;
    }

    // Look in jlargs to see if there's an output path specified, and if so
    // then record it and trim out of jlargs.
    protected static void parse_outpath() {

    }

    // main entry point

    public static void main(String[] args) {
        // do some os-specific setup
        String osname = System.getProperty("os.name").toLowerCase();
        boolean isMacOS = osname.startsWith("mac os x");
        if (isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        if (args.length == 0) {
            // launch as application
            try {
                JugglingLabApplet.initAudioClips();
                JugglingLabApplet.initDefaultPropImages();
                new ApplicationWindow("Juggling Lab");
            } catch (JuggleExceptionUser jeu) {
                new ErrorDialog(null, jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleException(jei);
            }
            return;
        }

        JugglingLab.jlargs = new ArrayList<String>(Arrays.asList(args));
        String firstarg = jlargs.remove(0);

        if (firstarg.equalsIgnoreCase("anim")) {
            System.out.println("animate");
            return;
        }

        // Each of the modes below can send output to a file
        JugglingLab.parse_outpath();

        if (firstarg.equalsIgnoreCase("gen")) {
            // run the siteswap generator
            String[] genargs = jlargs.toArray(new String[jlargs.size()]);

            try {
                PrintStream ps = System.out;
                if (outpath != null)
                    ps = new PrintStream(new File(outpath));
                siteswapGenerator.runGeneratorCLI(genargs, new GeneratorTarget(ps));
            } catch (FileNotFoundException fnfe) {
                System.out.println("Error: cannot write to file path " + outpath);
            }
            return;
        }

        if (firstarg.equalsIgnoreCase("togif")) {
            System.out.println("to gif");
            return;
        }

        if (firstarg.equalsIgnoreCase("tojml")) {
            System.out.println("to JML");
            return;
        }

        System.out.println("Command line help message goes here");
    }
}
