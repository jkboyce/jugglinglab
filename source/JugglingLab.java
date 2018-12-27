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

import java.applet.Applet;
import java.applet.AudioClip;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import org.xml.sax.SAXException;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.generator.*;
import jugglinglab.notation.*;
import jugglinglab.prop.imageProp;
import jugglinglab.util.*;


public class JugglingLab {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }


    public static void loadMediaResources() {
        // Load sound and graphics resources for later use
        AudioClip[] clips = new AudioClip[2];
        URL catchurl = JugglingLab.class.getResource("/resources/catch.au");
        if (catchurl != null)
            clips[0] = Applet.newAudioClip(catchurl);
        URL bounceurl = JugglingLab.class.getResource("/resources/bounce.au");
        if (bounceurl != null)
            clips[1] = Applet.newAudioClip(bounceurl);
        Animator.setAudioClips(clips);

        URL ballurl = JugglingLab.class.getResource("/resources/ball.png");
        imageProp.setDefaultPropImage(ballurl);
    }


    // command line arguments as an ArrayList that we trim as portions are parsed
    protected static ArrayList<String> jlargs = null;


    // Look in jlargs to see if there's an output path specified, and if so
    // then record it and trim out of jlargs. Otherwise return null.
    protected static String parse_outpath() {
        for (int i = 0; i < jlargs.size(); i++) {
            if (jlargs.get(i).equalsIgnoreCase("-out")) {
                if (i == (jlargs.size() - 1)) {
                    System.out.println("Warning: no output path specified after -out flag; ignoring");
                    jlargs.remove(i);
                    return null;
                }

                jlargs.remove(i);
                return jlargs.remove(i);
            }
        }
        return null;
    }

    // Look in jlargs to see if animator preferences are supplied, and if so then
    // parse them and return an AnimatorPrefs object. Otherwise (or on error) return null.
    protected static AnimatorPrefs parse_animprefs() {
        for (int i = 0; i < jlargs.size(); i++) {
            if (jlargs.get(i).equalsIgnoreCase("-prefs")) {
                if (i == (jlargs.size() - 1)) {
                    System.out.println("Warning: nothing specified after -prefs flag; ignoring");
                    jlargs.remove(i);
                    return null;
                }

                jlargs.remove(i);
                String prefstring = jlargs.remove(i);
                AnimatorPrefs jc = new AnimatorPrefs();
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
    // parse it at return it. Otherwise print an error message and return null.
    protected static JMLPattern parse_pattern() {
        if (jlargs.size() == 0) {
            System.out.println("Error: expected pattern input, none found");
            return null;
        }

        // first case is a JML-formatted pattern in a file
        if (jlargs.get(0).equalsIgnoreCase("-jml")) {
            if (jlargs.size() == 1) {
                System.out.println("Error: no input path specified after -jml flag");
                return null;
            }

            jlargs.remove(0);
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
            ErrorDialog.handleException(jei);
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
        if (isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        // Figure out what mode to run in based on command line arguments. We
        // want no command line arguments to run the full application, so that
        // it works correctly e.g. when the user double-clicks on the jar.

        boolean run_application = false;
        String firstarg = null;

        if (args.length == 0)
            run_application = true;
        else {
            JugglingLab.jlargs = new ArrayList<String>(Arrays.asList(args));
            firstarg = jlargs.remove(0);
            run_application = firstarg.equalsIgnoreCase("start");
        }

        if (run_application) {
            try {
                JugglingLab.loadMediaResources();
                new ApplicationWindow("Juggling Lab");
            } catch (JuggleExceptionUser jeu) {
                new ErrorDialog(null, jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleException(jei);
            }
            return;
        }

        List<String> modes = Arrays.asList("gen", "anim", "togif", "tojml");
        boolean show_help = !modes.contains(firstarg.toLowerCase());

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
            output += guistrings.getString("CLI_help");

            System.out.println(output);
            return;
        }

        String outpath = JugglingLab.parse_outpath();
        AnimatorPrefs jc = JugglingLab.parse_animprefs();

        if (firstarg.equalsIgnoreCase("gen")) {
            // run the siteswap generator
            System.setProperty("java.awt.headless", "true");
            String[] genargs = jlargs.toArray(new String[jlargs.size()]);

            try {
                PrintStream ps = System.out;
                if (outpath != null)
                    ps = new PrintStream(new File(outpath));
                siteswapGenerator.runGeneratorCLI(genargs, new GeneratorTarget(ps));
            } catch (FileNotFoundException fnfe) {
                System.out.println("Error: cannot write to file path " + outpath);
            }
            if (jc != null)
                System.out.println("Warning: animator prefs not used in generator mode; ignored");
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

        if (firstarg.equalsIgnoreCase("anim")) {
            // open pattern in a window
            try {
                JugglingLab.loadMediaResources();
                PatternWindow pw = new PatternWindow(pat.getTitle(), pat, jc);
                pw.setExitOnClose(true);
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error: " + jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                ErrorDialog.handleException(jei);
            }
            return;
        }

        // all remaining modes are headless (no GUI)
        System.setProperty("java.awt.headless", "true");

        if (firstarg.equalsIgnoreCase("togif")) {
            // output an animated GIF of the pattern
            if (outpath == null) {
                System.out.println("Error: no output path specified for animated GIF");
                return;
            }

            try {
                Animator ja = new Animator();
                if (jc == null)
                    jc = ja.getAnimatorPrefs();
                ja.setSize(jc.width, jc.height);
                ja.restartJuggle(pat, jc, false);
                ja.writeGIFAnim_CLI(outpath);
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error: " + jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                System.out.println("Internal Error: " + jei.getMessage());
            }
            return;
        }

        if (firstarg.equalsIgnoreCase("tojml")) {
            // output pattern to JML
            if (outpath == null)
                System.out.print(pat.toString());
            else {
                try {
                    FileWriter fw = new FileWriter(new File(outpath));
                    pat.writeJML(fw, true);
                    fw.close();
                } catch (IOException ioe) {
                    System.out.println("Error: problem writing JML to path " + outpath);
                }
            }
            if (jc != null)
                System.out.println("Warning: animator prefs not used in jml output mode; ignored");
            return;
        }
    }
}
