// JugglingLab.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

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
    public static final ResourceBundle guistrings;
    public static final ResourceBundle errorstrings;

    // Platform info
    public static final boolean isMacOS;
    public static final boolean isWindows;
    public static final boolean isLinux;

    // Whether we're running from the command line
    public static final boolean isCLI;

    // Base directory for file operations
    public static final Path base_dir;

    // Command line arguments as an ArrayList that we trim as portions are parsed
    private static ArrayList<String> jlargs;

    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");

        String osname = System.getProperty("os.name").toLowerCase();
        isMacOS = osname.startsWith("mac os x");
        isWindows = osname.startsWith("windows");
        isLinux = osname.startsWith("linux");

        // Decide on a base directory for file operations. First look for
        // working directory set by an enclosing script, which indicates Juggling
        // Lab is running from the command line.
        String working_dir = System.getenv("JL_WORKING_DIR");
        isCLI = (working_dir != null);

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


    //-------------------------------------------------------------------------
    // Main entry point for Juggling Lab
    //-------------------------------------------------------------------------

    public static void main(String[] args) {
        // Make menus more Mac-like on macOS
        if (isMacOS)
            System.setProperty("apple.laf.useScreenMenuBar", "true");

        // Figure out what mode to run in based on command line arguments. We
        // want no command line arguments to run the full application, so that
        // it launches when the user double-clicks on the jar.

        boolean run_application = true;
        String firstarg = null;

        if (args.length > 0) {
            jlargs = new ArrayList<String>(Arrays.asList(args));
            firstarg = jlargs.remove(0).toLowerCase();
            run_application = firstarg.equals("start");
        }

        if (run_application) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        registerAboutHandler();
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

        // Double-clicking a .jml file on Windows brings us here
        if (firstarg.equals("open")) {
            doOpen();
            return;
        }

        //
        // The remaining modes are only accessible from the command line
        //

        if (!isCLI)
            return;

        List<String> modes = Arrays.asList("gen", "trans", "anim", "togif", "tojml");
        boolean show_help = !modes.contains(firstarg);

        if (show_help) {
            doHelp(firstarg);
            return;
        }

        // Try to parse an optional output path and/or animation preferences
        Path outpath = parse_outpath();
        AnimationPrefs jc = parse_animprefs();

        if (firstarg.equals("gen")) {
            doGen(outpath, jc);
            return;
        }

        if (firstarg.equals("trans")) {
            doTrans(outpath, jc);
            return;
        }

        // All remaining modes require a pattern as input
        JMLPattern pat = parse_pattern();
        if (pat == null)
            return;

        // Any remaining arguments that parsing didn't consume?
        if (jlargs.size() > 0) {
            System.setProperty("java.awt.headless", "true");
            String arglist = String.join(", ", jlargs);
            System.out.println("Error: Unrecognized input: " + arglist);
            return;
        }

        if (firstarg.equals("anim")) {
            doAnim(pat, jc);
            return;
        }

        // All remaining modes are headless (no GUI)
        System.setProperty("java.awt.headless", "true");

        if (firstarg.equals("togif")) {
            doTogif(pat, outpath, jc);
            return;
        }

        if (firstarg.equals("tojml")) {
            doTojml(pat, outpath, jc);
            return;
        }
    }

    //-------------------------------------------------------------------------
    // Helper functions
    //-------------------------------------------------------------------------

    // If possible, install an About handler for getting info about the application.
    // Call this only if we aren't running headless
    private static void registerAboutHandler() {
        if (!Desktop.isDesktopSupported())
            return;

        if (!Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT))
            return;
        
        Desktop.getDesktop().setAboutHandler(new AboutHandler() {
            @Override
            public void handleAbout(AboutEvent e) {
                ApplicationWindow.showAboutBox();
            }
        });
    }

    // Open the JML file whose path is given as a command-line argument
    private static void doOpen() {
        if (jlargs.size() != 1) {
            String output = "Error: Expected 1 argument after 'open', got " + jlargs.size();

            if (isCLI) {
                System.setProperty("java.awt.headless", "true");
                System.out.println(output);
            } else {
                // shouldn't ever happen
                new ErrorDialog(null, output);
            }
            return;
        }

        String filepath = jlargs.remove(0);
        if (filepath.startsWith("\""))
            filepath = filepath.substring(1, filepath.length() - 1);

        final File file = new File(filepath);

        // First try to hand off the open request to another instance of
        // Juggling Lab that may be running
        boolean noOpenFilesHandler = (!Desktop.isDesktopSupported() ||
            !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE));

        if (noOpenFilesHandler) {
            if (OpenFilesServer.tryOpenFile(file)) {
                System.setProperty("java.awt.headless", "true");
                if (Constants.DEBUG_OPEN_SERVER)
                    System.out.println("Open file command handed off; quitting");
                return;
            }
        }

        // If that doesn't work, launch the app and open the file
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    registerAboutHandler();
                    new ApplicationWindow("Juggling Lab");
                    ApplicationWindow.openJMLFile(file);
                } catch (JuggleExceptionUser jeu) {
                    String template = errorstrings.getString("Error_reading_file");
                    Object[] arguments = { file.getName() };
                    String msg = MessageFormat.format(template, arguments) +
                                 ":\n" + jeu.getMessage();
                    new ErrorDialog(null, msg);
                } catch (JuggleExceptionInternal jei) {
                    ErrorDialog.handleFatalException(jei);
                }
            }
        });
    }

    // Show the help message
    private static void doHelp(String firstarg) {
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
    }

    // Look in jlargs to see if there's an output path specified, and if so
    // then record it and trim out of jlargs. Otherwise return null.
    private static Path parse_outpath() {
        for (int i = 0; i < jlargs.size(); i++) {
            if (jlargs.get(i).equalsIgnoreCase("-out")) {
                jlargs.remove(i);

                if (i == jlargs.size()) {
                    System.out.println("Warning: No output path specified after -out flag; ignoring");
                    return null;
                }

                String outpath_string = jlargs.remove(i);
                Path outpath = Paths.get(outpath_string);

                if (!outpath.isAbsolute() && base_dir != null)
                    outpath = Paths.get(base_dir.toString(), outpath_string);

                return outpath;
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
                    System.out.println("Warning: Nothing specified after -prefs flag; ignoring");
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

    // Run the siteswap generator
    private static void doGen(Path outpath, AnimationPrefs jc) {
        System.setProperty("java.awt.headless", "true");
        String[] genargs = jlargs.toArray(new String[jlargs.size()]);

        try {
            PrintStream ps = System.out;
            if (outpath != null)
                ps = new PrintStream(outpath.toFile());
            SiteswapGenerator.runGeneratorCLI(genargs, new GeneratorTarget(ps));
        } catch (FileNotFoundException fnfe) {
            System.out.println("Error: Problem writing to file path " + outpath.toString());
        }

        if (jc != null)
            System.out.println("Note: Animator prefs not used in generator mode; ignored");
    }

    // Run the siteswap transitioner
    private static void doTrans(Path outpath, AnimationPrefs jc) {
        System.setProperty("java.awt.headless", "true");
        String[] transargs = jlargs.toArray(new String[jlargs.size()]);

        try {
            PrintStream ps = System.out;
            if (outpath != null)
                ps = new PrintStream(outpath.toFile());
            SiteswapTransitioner.runTransitionerCLI(transargs, new GeneratorTarget(ps));
        } catch (FileNotFoundException fnfe) {
            System.out.println("Error: Problem writing to file path " + outpath.toString());
        }

        if (jc != null)
            System.out.println("Note: Animator prefs not used in transitions mode; ignored");
    }

    // Look at beginning of jlargs to see if there's a pattern, and if so then
    // parse it and return it. Otherwise print an error message and return null.
    private static JMLPattern parse_pattern() {
        if (jlargs.size() == 0) {
            System.out.println("Error: Expected pattern input, none found");
            return null;
        }

        // First case is a JML-formatted pattern in a file
        if (jlargs.get(0).equalsIgnoreCase("-jml")) {
            jlargs.remove(0);
            if (jlargs.size() == 0) {
                System.out.println("Error: No input path specified after -jml flag");
                return null;
            }

            String inpath_string = jlargs.remove(0);
            Path inpath = Paths.get(inpath_string);
            if (!inpath.isAbsolute() && base_dir != null)
                inpath = Paths.get(base_dir.toString(), inpath_string);

            try {
                JMLParser parser = new JMLParser();
                parser.parse(new FileReader(inpath.toFile()));

                switch (parser.getFileType()) {
                    case JMLParser.JML_PATTERN:
                        return new JMLPattern(parser.getTree());
                    case JMLParser.JML_LIST:
                        System.out.println("Error: JML file cannot be a pattern list");
                        break;
                    default:
                        System.out.println("Error: File is not valid JML");
                        break;
                }
            } catch (JuggleExceptionUser jeu) {
                System.out.println("Error parsing JML: " + jeu.getMessage());
            } catch (SAXException se) {
                System.out.println("Error: Formatting error in JML file");
            } catch (IOException ioe) {
                System.out.println("Error: Problem reading JML file from path " + inpath.toString());
            }
            return null;
        }

        // Otherwise assume pattern is in siteswap notation
        try {
            String config = jlargs.remove(0);
            return JMLPattern.fromBasePattern("siteswap", config);
        } catch (JuggleExceptionUser jeu) {
            System.out.println("Error: " + jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            System.out.println("Internal Error: " + jei.getMessage());
        }
        return null;
    }

    // Open pattern in a window
    private static void doAnim(JMLPattern pat, AnimationPrefs jc) {
        final JMLPattern fpat = pat;
        final AnimationPrefs fjc = jc;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    registerAboutHandler();
                    PatternWindow pw = new PatternWindow(fpat.getTitle(), fpat, fjc);
                    PatternWindow.setExitOnLastClose(true);
                } catch (JuggleExceptionUser jeu) {
                    System.out.println("Error: " + jeu.getMessage());
                } catch (JuggleExceptionInternal jei) {
                    ErrorDialog.handleFatalException(jei);
                }
            }
        });
    }

    // Output an animated GIF of the pattern
    private static void doTogif(JMLPattern pat, Path outpath, AnimationPrefs jc) {
        if (outpath == null) {
            System.out.println("Error: No output path specified for animated GIF");
            return;
        }

        try {
            Animator anim = new Animator();
            if (jc == null) {
                jc = anim.getAnimationPrefs();
                jc.fps = 33.3;  // default frames per sec for GIFs
            }
            anim.setDimension(new Dimension(jc.width, jc.height));
            anim.restartAnimator(pat, jc);
            anim.writeGIF(new FileOutputStream(outpath.toFile()), null);
        } catch (JuggleExceptionUser jeu) {
            System.out.println("Error: " + jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            System.out.println("Internal Error: " + jei.getMessage());
        } catch (IOException ioe) {
            System.out.println("Error: Problem writing GIF to path " + outpath.toString());
        }
    }

    // Output pattern to JML
    private static void doTojml(JMLPattern pat, Path outpath, AnimationPrefs jc) {
        if (outpath == null)
            System.out.print(pat.toString());
        else {
            try {
                FileWriter fw = new FileWriter(outpath.toFile());
                pat.writeJML(fw, true);
                fw.close();
            } catch (IOException ioe) {
                System.out.println("Error: Problem writing JML to path " + outpath.toString());
            }
        }

        if (jc != null)
            System.out.println("Note: Animator prefs not used in jml output mode; ignored");
    }
}
