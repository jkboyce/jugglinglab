// JugglingLab.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab;

import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.Dimension;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;
import org.xml.sax.SAXException;

import jugglinglab.core.*;
import jugglinglab.jml.JMLParser;
import jugglinglab.jml.JMLPattern;
import jugglinglab.generator.SiteswapGenerator;
import jugglinglab.generator.SiteswapTransitioner;
import jugglinglab.generator.GeneratorTarget;
import jugglinglab.notation.Pattern;
import jugglinglab.notation.SiteswapPattern;
import jugglinglab.util.*;


public class JugglingLab {
    // Localized strings for UI
    public static ResourceBundle guistrings;
    public static ResourceBundle errorstrings;

    // Platform info
    public static boolean isMacOS;
    public static boolean isWindows;
    public static boolean isLinux;

    // Base directory for file operations
    public static Path base_dir;

    static {
        JugglingLab.guistrings = JLLocale.getBundle("GUIStrings");
        JugglingLab.errorstrings = JLLocale.getBundle("ErrorStrings");

        String osname = System.getProperty("os.name").toLowerCase();
        isMacOS = osname.startsWith("mac os x");
        isWindows = osname.startsWith("windows");
        isLinux = osname.startsWith("linux");

        // Decide on a base directory for file operations. First look for
        // working directory set by an enclosing script, which indicates Juggling
        // Lab is running from the command line.
        String working_dir = System.getenv("JL_WORKING_DIR");

        if (working_dir == null) {
            // If not found, then user.dir (current working directory when Java
            // was invoked) is the most logical choice, UNLESS we're running in
            // an application bundle. For bundled apps user.dir is buried inside
            // the app directory structure so we default to user.home instead.
            String isBundle = System.getProperty("JL_run_as_bundle");

            if (isBundle == null || !isBundle.equals("true"))
                working_dir = System.getProperty("user.dir");
            else
                working_dir = System.getProperty("user.home");
        }

        base_dir = Paths.get(working_dir);
    }

    // command line arguments as an ArrayList that we trim as portions are parsed
    private static ArrayList<String> jlargs;

    // Look in jlargs to see if there's an output path specified, and if so
    // then record it and trim out of jlargs. Otherwise return null.
    private static String parse_outpath() {
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
    private static AnimationPrefs parse_animprefs() {
        for (int i = 0; i < jlargs.size(); i++) {
            if (jlargs.get(i).equalsIgnoreCase("-prefs")) {
                jlargs.remove(i);
                if (i == jlargs.size()) {
                    System.out.println("Warning: nothing specified after -prefs flag; ignoring");
                    return null;
                }

                try {
                    ParameterList pl = new ParameterList(jlargs.remove(i));
                    AnimationPrefs jc = (new AnimationPrefs()).fromParameters(pl);
                    pl.errorIfParametersLeft();
                    return jc;
                } catch (JuggleExceptionUser jeu) {
                    System.out.println("Error in animator prefs: " + jeu.getMessage() + "; ignoring");
                    return null;
                }
            }
        }
        return null;
    }

    protected static class PatternRecord {
        JMLPattern jml;
        Pattern pat;
        String base_notation;
        String base_config;
    }

    // Look at beginning of jlargs to see if there's a pattern, and if so then
    // parse it and return it. Otherwise print an error message and return null.
    private static PatternRecord parse_pattern() {
        if (jlargs.size() == 0) {
            System.out.println("Error: expected pattern input, none found");
            return null;
        }

        PatternRecord result = new PatternRecord();

        // first case is a JML-formatted pattern in a file
        if (jlargs.get(0).equalsIgnoreCase("-jml")) {
            jlargs.remove(0);
            if (jlargs.size() == 0) {
                System.out.println("Error: no input path specified after -jml flag");
                return null;
            }

            String inpath_string = jlargs.remove(0);
            Path inpath = Paths.get(inpath_string);
            if (!inpath.isAbsolute() && JugglingLab.base_dir != null)
                inpath = Paths.get(base_dir.toString(), inpath_string);

            try {
                JMLParser parser = new JMLParser();
                parser.parse(new FileReader(inpath.toFile()));

                switch (parser.getFileType()) {
                    case JMLParser.JML_PATTERN:
                        result.jml = new JMLPattern(parser.getTree());
                        return result;
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
            } catch (SAXException se) {
                System.out.println("Error: formatting error in JML file");
                return null;
            } catch (IOException ioe) {
                System.out.println("Error: problem reading JML file from path " + inpath.toString());
                return null;
            }
        }

        // otherwise assume pattern is in siteswap notation
        try {
            result.base_notation = "Siteswap";
            result.base_config = jlargs.remove(0);
            result.pat = (new SiteswapPattern()).fromString(result.base_config);
            result.jml = result.pat.asJMLPattern();
            return result;
        } catch (JuggleExceptionUser jeu) {
            System.out.println("Error: " + jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            System.out.println("Internal Error: " + jei.getMessage());
        }
        return null;
    }


    // ------------------------------------------------------------------------
    // main entry point for Juggling Lab
    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        // Make menus more Mac-like on macOS
        if (isMacOS)
            System.setProperty("apple.laf.useScreenMenuBar", "true");

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().setAboutHandler(new AboutHandler() {
                @Override
                public void handleAbout(AboutEvent e) {
                    ApplicationWindow.showAboutBox();
                }
            });
        }

        // Figure out what mode to run in based on command line arguments. We
        // want no command line arguments to run the full application, so that
        // it launches correctly when the user double-clicks on the jar.

        boolean run_application = true;
        String firstarg = null;

        if (args.length > 0) {
            JugglingLab.jlargs = new ArrayList<String>(Arrays.asList(args));
            firstarg = jlargs.remove(0).toLowerCase();
            run_application = firstarg.equals("start");
        }

        if (run_application) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        new ApplicationWindow("Juggling Lab");
                    } catch (JuggleExceptionUser jeu) {
                        new ErrorDialog(null, jeu.getMessage());
                    } catch (JuggleExceptionInternal jei) {
                        ErrorDialog.handleFatalException(jei);
                    }
                }
            });
            return;
        }

        List<String> modes = Arrays.asList("gen", "trans", "anim", "togif", "tojml");
        boolean show_help = !modes.contains(firstarg);

        if (show_help) {
            // Print a help message and return
            System.setProperty("java.awt.headless", "true");
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

            if (firstarg != null && !firstarg.equals("help"))
                System.out.println("\nUnrecognized option: " + firstarg);
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
                SiteswapGenerator.runGeneratorCLI(genargs, new GeneratorTarget(ps));
            } catch (FileNotFoundException fnfe) {
                System.out.println("Error: problem writing to file path " + outpath.toString());
            }
            if (jc != null)
                System.out.println("Note: animator prefs not used in generator mode; ignored");
            return;
        }

        if (firstarg.equals("trans")) {
            // run the siteswap transitioner
            System.setProperty("java.awt.headless", "true");
            String[] transargs = jlargs.toArray(new String[jlargs.size()]);

            try {
                PrintStream ps = System.out;
                if (outpath != null)
                    ps = new PrintStream(outpath.toFile());
                SiteswapTransitioner.runTransitionerCLI(transargs, new GeneratorTarget(ps));
            } catch (FileNotFoundException fnfe) {
                System.out.println("Error: problem writing to file path " + outpath.toString());
            }
            if (jc != null)
                System.out.println("Note: animator prefs not used in transitions mode; ignored");
            return;
        }

        // all remaining modes require a pattern as input
        PatternRecord pr = JugglingLab.parse_pattern();
        if (pr == null)
            return;

        if (jlargs.size() > 0) {
            // any remaining arguments that parsing didn't consume?
            System.setProperty("java.awt.headless", "true");
            String arglist = String.join(", ", jlargs);
            System.out.println("Error unrecognized input: " + arglist);
            return;
        }

        if (firstarg.equals("anim")) {
            // open pattern in a window
            final JMLPattern fpat = pr.jml;
            final AnimationPrefs fjc = jc;

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        PatternWindow pw = new PatternWindow(fpat.getTitle(), fpat, fjc);
                        if (pr.base_notation != null && pr.base_config != null)
                            pw.setBasePattern(pr.base_notation, pr.pat.toString());

                        PatternWindow.setExitOnLastClose(true);
                    } catch (JuggleExceptionUser jeu) {
                        System.out.println("Error: " + jeu.getMessage());
                    } catch (JuggleExceptionInternal jei) {
                        ErrorDialog.handleFatalException(jei);
                    }
                }
            });
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
                    jc.fps = 33.3;      // default frames per sec for GIFs
                }
                anim.setDimension(new Dimension(jc.width, jc.height));
                anim.restartAnimator(pr.jml, jc);
                anim.writeGIF(new FileOutputStream(outpath.toFile()), null);
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error: " + jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
                System.out.println("Internal Error: " + jei.getMessage());
            } catch (IOException ioe) {
                System.out.println("Error: problem writing GIF to path " + outpath.toString());
            }
            return;
        }

        if (firstarg.equals("tojml")) {
            // output pattern to JML
            if (outpath == null)
                System.out.print(pr.jml.toString());
            else {
                try {
                    FileWriter fw = new FileWriter(outpath.toFile());
                    pr.jml.writeJML(fw, true);
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
