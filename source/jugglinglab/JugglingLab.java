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

package jugglinglab;

import java.awt.Dimension;
import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import org.xml.sax.SAXException;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.generator.*;
import jugglinglab.notation.*;
import jugglinglab.util.*;


public class JugglingLab {
    public static ResourceBundle guistrings;
    public static ResourceBundle errorstrings;
    public static Path base_dir;

    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");

        // Decide on a working directory to act as a base for file operations.
        // First look for working directory set by an enclosing script, which
        // indicates the user is running Juggling Lab from the command line.
        String working_dir = System.getenv("JL_working_dir");
        System.out.println("JL_working_dir = " + working_dir);

        if (working_dir == null) {
            // If not found, then user.dir (current working directory when Java
            // was invoked) is the most logical choice, UNLESS we're running in
            // an application bundle. For bundled apps user.dir is buried inside
            // the bundled JRE so we default to user.home instead.
            String isBundle = System.getProperty("JL_run_as_bundle");
            System.out.println("JL_run_as_bundle = " + isBundle);
        
            if (isBundle == null || !isBundle.equals("true"))
                working_dir = System.getProperty("user.dir");
            else
                working_dir = System.getProperty("user.home");
        }

        JugglingLab.base_dir = Paths.get(working_dir);
        System.out.println("base directory = " + base_dir.toString());
    }

    // command line arguments as an ArrayList that we trim as portions are parsed
    protected static ArrayList<String> jlargs = null;

    // Look in jlargs to see if there's an output path specified, and if so
    // then record it and trim out of jlargs. Otherwise return null.
    protected static String parse_outpath() {
        for (int i = 0; i < jlargs.size(); i++) {
            if (jlargs.get(i).equalsIgnoreCase("-out")) {
                jlargs.remove(i);
                if (i == jlargs.size()) {
                    System.out.println("Warning: no output path specified after -out flag; ignoring");
                    return null;
                }

                return jlargs.remove(i);
            }
        }
        return null;
    }

    // Look in jlargs to see if animator preferences are supplied, and if so then
    // parse them and return an AnimationPrefs object. Otherwise (or on error) return null.
    protected static AnimationPrefs parse_animprefs() {
        for (int i = 0; i < jlargs.size(); i++) {
            if (jlargs.get(i).equalsIgnoreCase("-prefs")) {
                jlargs.remove(i);
                if (i == jlargs.size()) {
                    System.out.println("Warning: nothing specified after -prefs flag; ignoring");
                    return null;
                }

                String prefstring = jlargs.remove(i);
                AnimationPrefs jc = new AnimationPrefs();
                try {
                    jc.parseInput(prefstring);
                } catch (JuggleExceptionUser jeu) {
                    System.out.println("Error in animator prefs: " + jeu.getMessage() + "; ignoring");
                    return null;
                }
                return jc;
            }
        }
        return null;
    }

    // Look at beginning of jlargs to see if there's a pattern, and if so then
    // parse it and return it. Otherwise print an error message and return null.
    protected static JMLPattern parse_pattern() {
        if (jlargs.size() == 0) {
            System.out.println("Error: expected pattern input, none found");
            return null;
        }

        // first case is a JML-formatted pattern in a file
        if (jlargs.get(0).equalsIgnoreCase("-jml")) {
            jlargs.remove(0);
            if (jlargs.size() == 0) {
                System.out.println("Error: no input path specified after -jml flag");
                return null;
            }

            String inpath = jlargs.remove(0);
            try {
                JMLParser parser = new JMLParser();
                parser.parse(new FileReader(new File(inpath)));

                switch (parser.getFileType()) {
                    case JMLParser.JML_PATTERN:
                        return new JMLPattern(parser.getTree());
                    case JMLParser.JML_LIST:
                        System.out.println("Error: JML file cannot be a pattern list");
                        return null;
                    default:
                        System.out.println("Error: file is not valid JML");
                        return null;
                }
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error parsing JML: " + jeu.getMessage());
                return null;
            } catch (FileNotFoundException fnfe) {
                System.out.println("Error: cannot read from JML file");
                return null;
            } catch (SAXException se) {
                System.out.println("Error: formatting error in JML file");
                return null;
            } catch (IOException ioe) {
                System.out.println("Error: problem reading JML file");
                return null;
            }
        }

        // otherwise assume pattern is in siteswap notation
        String sspattern = jlargs.remove(0);
        JMLPattern pat = null;
        try {
            Notation not = Notation.getNotation("siteswap");
            pat = not.getJMLPattern(sspattern);
        } catch (JuggleExceptionUser jeu) {
            System.out.println("Error: " + jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            System.out.println("Internal Error: " + jei.getMessage());
        }
        return pat;
    }


    // ------------------------------------------------------------------------
    // main entry point for Juggling Lab
    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        // do some os-specific setup
        String osname = System.getProperty("os.name").toLowerCase();
        boolean isMacOS = osname.startsWith("mac os x");
        boolean isWindows = osname.startsWith("windows");
        if (isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        // Figure out what mode to run in based on command line arguments. We
        // want no command line arguments to run the full application, so that
        // it launches correctly when the user double-clicks on the jar.

        boolean run_application = false;
        String firstarg = null;

        if (args.length == 0)
            run_application = true;
        else {
            JugglingLab.jlargs = new ArrayList<String>(Arrays.asList(args));
            firstarg = jlargs.remove(0).toLowerCase();
            run_application = firstarg.equals("start");
        }

        if (run_application) {
            try {
                new ApplicationWindow("Juggling Lab");
            } catch (JuggleExceptionUser jeu) {
                new ErrorDialog(null, jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleFatalException(jei);
            }
            return;
        }

        List<String> modes = Arrays.asList("gen", "anim", "togif", "tojml");
        boolean show_help = !modes.contains(firstarg);

        if (show_help) {
            // Print a help message and return
            String template = guistrings.getString("Version");
            Object[] arg1 = { Constants.version };
            String output = "Juggling Lab " +
                            MessageFormat.format(template, arg1).toLowerCase() + "\n";
            template = guistrings.getString("Copyright_message");
            Object[] arg2 = { Constants.year };
            output += MessageFormat.format(template, arg2) + "\n";
            output += guistrings.getString("GPL_message") + "\n\n";
            output += guistrings.getString("CLI_help1");
            String examples = guistrings.getString("CLI_help2");
            if (isWindows) {
                // replace single quotes with double quotes in Windows examples
                examples = examples.replaceAll("\'", "\"");
            }
            output += examples;
            System.out.println(output);
            return;
        }

        String outpath_string = JugglingLab.parse_outpath();
        Path outpath = null;
        if (outpath_string != null) {
            outpath = Paths.get(outpath_string);

            if (!outpath.isAbsolute() && JugglingLab.base_dir != null)
                outpath = Paths.get(base_dir.toString(), outpath_string);
        }

        AnimationPrefs jc = JugglingLab.parse_animprefs();

        if (firstarg.equals("gen")) {
            // run the siteswap generator
            System.setProperty("java.awt.headless", "true");
            String[] genargs = jlargs.toArray(new String[jlargs.size()]);

            try {
                PrintStream ps = System.out;
                if (outpath != null)
                    ps = new PrintStream(outpath.toFile());
                siteswapGenerator.runGeneratorCLI(genargs, new GeneratorTarget(ps));
            } catch (FileNotFoundException fnfe) {
                System.out.println("Error: cannot write to file path " + outpath.toString());
            }
            if (jc != null)
                System.out.println("Note: animator prefs not used in generator mode; ignored");
            return;
        }

        // all remaining modes require a pattern as input
        JMLPattern pat = JugglingLab.parse_pattern();
        if (pat == null)
            return;

        if (jlargs.size() > 0) {
            // any remaining arguments that parsing didn't consume?
            String arglist = String.join(", ", jlargs);
            System.out.println("Error unrecognized input: " + arglist);
            return;
        }

        if (firstarg.equals("anim")) {
            // open pattern in a window
            try {
                PatternWindow pw = new PatternWindow(pat.getTitle(), pat, jc);
                pw.setExitOnClose(true);
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error: " + jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleFatalException(jei);
            }
            return;
        }

        // all remaining modes are headless (no GUI)
        System.setProperty("java.awt.headless", "true");

        if (firstarg.equals("togif")) {
            // output an animated GIF of the pattern
            if (outpath == null) {
                System.out.println("Error: no output path specified for animated GIF");
                return;
            }

            try {
                Animator anim = new Animator();
                if (jc == null) {
                    jc = anim.getAnimationPrefs();
                    jc.fps = 30.0;      // default frames per sec for GIFs
                }
                anim.setDimension(new Dimension(jc.width, jc.height));
                anim.restartAnimator(pat, jc);

                try {
                    anim.writeGIF(new FileOutputStream(outpath.toFile()), null);
                } catch (FileNotFoundException fnfe) {
                    throw new JuggleExceptionUser("error writing GIF to path " + outpath.toString());
                } catch (IOException ioe) {
                    throw new JuggleExceptionUser("error writing GIF to path " + outpath.toString());
                }
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error: " + jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                System.out.println("Internal Error: " + jei.getMessage());
            }
            return;
        }

        if (firstarg.equals("tojml")) {
            // output pattern to JML
            if (outpath == null)
                System.out.print(pat.toString());
            else {
                try {
                    FileWriter fw = new FileWriter(outpath.toFile());
                    pat.writeJML(fw, true);
                    fw.close();
                } catch (IOException ioe) {
                    System.out.println("Error: problem writing JML to path " + outpath.toString());
                }
            }
            if (jc != null)
                System.out.println("Note: animator prefs not used in jml output mode; ignored");
            return;
        }
    }
}
