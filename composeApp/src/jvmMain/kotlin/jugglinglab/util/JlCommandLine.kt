//
// JlCommandLine.kt
//
// This is the entry point for Juggling Lab as a JVM application. It interprets
// command line arguments and handles them appropriately.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

//import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.*
import jugglinglab.ui.ApplicationWindow
import jugglinglab.ui.PatternWindow
import jugglinglab.generator.GeneratorTargetBasic
import jugglinglab.generator.SiteswapGenerator
import jugglinglab.generator.SiteswapTransitioner
import jugglinglab.jml.JmlParser
import jugglinglab.jml.JmlPattern
import jugglinglab.jml.JmlPatternList
import jugglinglab.renderer.FrameDrawer
//import jugglinglab.util.jlGetStringResource
import java.awt.Desktop
import java.awt.desktop.AboutEvent
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities

object JlCommandLine {
    // command line arguments that we trim as portions are parsed
    private var jlargs: MutableList<String> = mutableListOf()

    //--------------------------------------------------------------------------
    // Command line entry point
    //--------------------------------------------------------------------------

    fun startWithArgs(args: Array<String>) {
        if (jlIsMacOs()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true")
        }

        // Figure out what mode to run in based on command line arguments. We want
        // no command line arguments to run the application, so that it launches
        // when the user double-clicks on the jar.
        jlargs.addAll(listOf(*args))

        val runApplication = when {
            jlargs.isNotEmpty() -> (jlargs[0] == "start")
            else -> true
        }
        if (runApplication) {
            SwingUtilities.invokeLater {
                try {
                    registerAboutHandler()
                    ApplicationWindow("Juggling Lab")
                } catch (jeu: JuggleExceptionUser) {
                    jlHandleUserException(null, jeu.message)
                } catch (jei: JuggleExceptionInternal) {
                    jlHandleFatalException(jei)
                }
            }
            return
        }

        val firstarg = jlargs.removeFirst().lowercase()

        if (firstarg == "open") {
            // double-clicking a .jml file on Windows brings us here
            doOpen()
            return
        }

        if (!jlIsCli()) {
            // the remaining modes are only accessible from the command line
            return
        }

        val showHelp = firstarg !in listOf("gen", "trans", "verify", "anim", "togif", "tojml")
        if (showHelp) {
            doHelp(firstarg)
            return
        }

        // Try to parse an optional output path and/or animation preferences
        val outpath = parseOutpath()
        val jc = parseAnimprefs()

        if (firstarg == "gen") {
            doGen(outpath, jc)
            return
        }

        if (firstarg == "trans") {
            doTrans(outpath, jc)
            return
        }

        if (firstarg == "verify") {
            doVerify(outpath, jc)
            return
        }

        // All remaining modes require a pattern as input
        val pat = parsePattern() ?: return

        // Any remaining arguments that parsing didn't consume?
        if (jlargs.isNotEmpty()) {
            System.setProperty("java.awt.headless", "true")
            val arglist = jlargs.joinToString(", ")
            println("Error: Unrecognized input: $arglist")
            return
        }

        if (firstarg == "anim") {
            doAnim(pat, jc)
            return
        }

        // All remaining modes are headless (no GUI)
        System.setProperty("java.awt.headless", "true")

        if (firstarg == "togif") {
            doTogif(pat, outpath, jc)
            return
        }

        if (firstarg == "tojml") {
            doTojml(pat, outpath, jc)
            return
        }
    }

    //--------------------------------------------------------------------------
    // Helper functions
    //--------------------------------------------------------------------------

    // If possible, install an About handler for getting info about the application.
    // Call this only if we aren't running headless.

    private fun registerAboutHandler() {
        if (!Desktop.isDesktopSupported()) {
            return
        }
        if (!Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
            return
        }
        Desktop.getDesktop().setAboutHandler { _: AboutEvent? -> ApplicationWindow.showAboutBox() }
    }

    // Open the JML file(s) whose paths are given as command-line arguments.

    private fun doOpen() {
        val files = parseFilelist()
        if (files.isEmpty()) {
            return
        }

        // If an instance of the app is already running and installed an
        // OpenFilesHandler, then the OS will handle the file opening that way and
        // we don't need to do anything here.
        //
        // See ApplicationWindow.registerOpenFilesHandler()
        val noOpenFilesHandler =
            (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE))

        if (noOpenFilesHandler) {
            // use a different mechanism to try to hand off the open requests to
            // another instance of Juggling Lab that may be running
            files.removeIf { OpenFilesServer.tryOpenFile(it) }

            if (files.isEmpty()) {
                System.setProperty("java.awt.headless", "true")
                if (Constants.DEBUG_OPEN_SERVER) {
                    println("Open file command handed off; quitting")
                }
                return
            }
        }

        // no other instance of Juggling Lab is running, so launch the full app and
        // have it load the files
        SwingUtilities.invokeLater {
            try {
                registerAboutHandler()
                ApplicationWindow("Juggling Lab")

                for (file in files) {
                    try {
                        ApplicationWindow.openJmlFile(file)
                    } catch (jeu: JuggleExceptionUser) {
                        // val message = jlGetStringResource(Res.string.error_reading_file, file.getName())
                        val message = "Problem reading from file \"${file.getName()}\""
                        val msg = message + ":\n" + jeu.message
                        jlHandleUserException(null, msg)
                    }
                }
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(null, jeu.message)
            } catch (jei: JuggleExceptionInternal) {
                jlHandleFatalException(jei)
            }
        }
    }

    // Read a list of file paths from `jlargs` and return an array of File objects.
    // Relative file paths are converted to absolute paths.
    //
    // In the event of an error, print an error message and return null.

    private fun parseFilelist(): MutableList<File> {
        val files = mutableListOf<File>()

        for (filestr in jlargs) {
            var filestr = filestr
            if (filestr.startsWith("\"")) {
                filestr = filestr.substring(1, filestr.length - 1)
            }

            var filepath = Paths.get(filestr)
            if (!filepath.isAbsolute) {
                filepath = Paths.get(jlBaseFileDirectory.toString(), filestr)
            }

            files.add(filepath.toFile())
        }

        if (files.isEmpty()) {
            val output = "Error: Expected file path(s), none provided"
            if (jlIsCli()) {
                System.setProperty("java.awt.headless", "true")
                println(output)
            } else {
                jlHandleUserException(null, output) // should never happen
            }
        }
        return files
    }

    // Show the help message.

    private fun doHelp(firstarg: String?) {
        System.setProperty("java.awt.headless", "true")
        /*
        val arg1 = jlGetStringResource(Res.string.gui_version, Constants.VERSION)
        var output = "Juggling Lab " + arg1.lowercase(Locale.getDefault()) + "\n"
        val arg2 = jlGetStringResource(Res.string.gui_copyright_message, Constants.YEAR)
        output += arg2 + "\n"
        output += jlGetStringResource(Res.string.gui_gpl_message) + "\n\n"
        output += jlGetStringResource(Res.string.gui_cli_help1)
        var examples = jlGetStringResource(Res.string.gui_cli_help2)
        */
        var output = "Juggling Lab version ${Constants.VERSION}\n" +
                "Copyright © 2002-${Constants.YEAR} Jack Boyce and the Juggling Lab contributors\n" +
                "This program is released under the GNU General Public License v2\n\n" +
                "This is the command line interface to Juggling Lab. Recognized options:\n\n" +
                "   jlab start\n" + "      Launches the application.\n\n" +
                "   jlab open <file1.jml> <file2.jml> ...\n" +
                "      Launches the application and opens the listed JML files.\n\n" +
                "   jlab anim <pattern> [-prefs <prefs>]\n" +
                "      Opens a window with an animation of the given pattern, using the\n" +
                "      given (optional) animation preferences.\n\n" +
                "   jlab gen <gen_options> [-out <path>]\n" +
                "      Runs the siteswap generator and prints a list of patterns, using the\n" +
                "      given set of generator options to define the number of objects, etc.\n" +
                "      Type \"jlab gen\" with no options for a help message. The output may\n" +
                "      optionally be written to a file.\n\n" +
                "   jlab trans <pattern A> <pattern B> [-options] [-out <path>]\n" +
                "      Runs the siteswap transition-finder and prints a list of transitions\n" +
                "      from pattern A to pattern B. Type \"jlab trans\" for a help message. The\n" +
                "      output may optionally be written to a file.\n\n" +
                "   jlab togif <pattern> [-prefs <prefs>] -out <path>\n" +
                "      Saves a pattern animation to a file as an animated GIF, using the\n" +
                "      given (optional) animation preferences.\n\n" +
                "   jlab tojml <pattern> [-out <path>]\n" +
                "      Converts a pattern to JML notation, Juggling Lab's internal XML-based\n" +
                "      pattern description. This may optionally be written to a file.\n\n" +
                "   jlab verify [-tojml] <file1.jml> <file2.jml> ... [-out <path>]\n" +
                "      Checks the validity of the listed JML files. For pattern list files, the\n" +
                "      validity of each line within the list is verified. The output may\n" +
                "      optionally be written to a file.\n\nPattern input:\n" +
                "   <pattern> can take one of three formats:\n\n" +
                "   1. A pattern in siteswap notation, for example 771 or (6x,4)(4,6x).\n" +
                "   2. A siteswap pattern annotated with additional settings, in a semi-\n" +
                "      colon separated format. This is described in more detail at\n" +
                "      https://jugglinglab.org/html/sspanel.html\n" +
                "   3. A JML pattern read in from a file, using the format '-jml <path>'.\n\n" +
                "Animation preferences input:\n" +
                "   <prefs> are optional and are used to override Juggling Lab's default\n" +
                "   preferences. These are given in a semicolon-separated format. See\n" +
                "   https://jugglinglab.org/html/animinfo.html\n\n" +
                "NOTE: Care should be taken when using characters that may be interpreted in\n" +
                "special ways by the command line interpreter. On Windows use double quotes\n" +
                "around settings to avoid confusion, and single quotes on macOS/Unix.\n\n"
        var examples = "Examples:\n   jlab anim '(6x,4)*'\n" +
                "   jlab togif 'pattern=3;dwell=1.0;bps=4.0;hands=(-30)(2.5).(30)(-2.5).(-30)(0).' -out mills.gif\n" +
                "   jlab anim -jml my_favorite_pattern.jml\n" +
                "   jlab anim 771 -prefs 'stereo=true;width=800;height=600'\n" +
                "   jlab anim 5B -prefs 'bouncesound=true'\n   jlab gen 5 7 5\n   jlab trans 5 771"
        if (jlIsWindows()) {
            // replace single quotes with double quotes in Windows examples
            examples = examples.replace("'", "\"")
        }
        output += examples
        println(output)

        if (firstarg != null && firstarg != "help") {
            println("\nUnrecognized option: $firstarg")
        }
    }

    // Look in `jlargs` to see if there's an output path specified, and if so then
    // record it and trim out of `jlargs`. Otherwise return null.

    private fun parseOutpath(): Path? {
        for (i in jlargs.indices) {
            if (jlargs[i].equals("-out", ignoreCase = true)) {
                jlargs.removeAt(i)

                if (i == jlargs.size) {
                    println("Warning: No output path specified after -out flag; ignoring")
                    return null
                }

                val outpathString = jlargs.removeAt(i)
                var outpath = Paths.get(outpathString)
                if (!outpath.isAbsolute) {
                    outpath = Paths.get(jlBaseFileDirectory.toString(), outpathString)
                }

                return outpath
            }
        }
        return null
    }

    // Look in `jlargs` to see if animator preferences are supplied, and if so
    // then parse them and return an AnimationPrefs object. Otherwise (or on
    // error) return null.

    private fun parseAnimprefs(): AnimationPrefs? {
        for (i in jlargs.indices) {
            if (jlargs[i].equals("-prefs", ignoreCase = true)) {
                jlargs.removeAt(i)

                if (i == jlargs.size) {
                    println("Warning: Nothing specified after -prefs flag; ignoring")
                    return null
                }

                try {
                    val pl = ParameterList(jlargs.removeAt(i))
                    val jc = AnimationPrefs.fromParameters(pl)
                    pl.errorIfParametersLeft()
                    return jc
                } catch (jeu: JuggleExceptionUser) {
                    println("Error in animator prefs: ${jeu.message}; ignoring")
                    return null
                }
            }
        }
        return null
    }

    // Run the siteswap generator.

    private fun doGen(outpath: Path?, jc: AnimationPrefs?) {
        //System.setProperty("java.awt.headless", "true")
        try {
            var ps = System.out
            if (outpath != null) {
                ps = PrintStream(outpath.toFile())
            }
            SiteswapGenerator.runGeneratorCLI(jlargs, GeneratorTargetBasic { ps.println(it) })
        } catch (_: FileNotFoundException) {
            println("Error: Problem writing to file path $outpath")
        } catch (_: Throwable) {
            println("stopped")
        }

        if (jc != null) {
            println("Note: Animator prefs not used in generator mode; ignored")
        }
    }

    // Run the siteswap transitioner.

    private fun doTrans(outpath: Path?, jc: AnimationPrefs?) {
        System.setProperty("java.awt.headless", "true")
        try {
            var ps = System.out
            if (outpath != null) {
                ps = PrintStream(outpath.toFile())
            }
            SiteswapTransitioner.runTransitionerCLI(jlargs, GeneratorTargetBasic { ps.println(it) })
        } catch (_: FileNotFoundException) {
            println("Error: Problem writing to file path $outpath")
        } catch (_: Throwable) {
            println("stopped")
        }

        if (jc != null) {
            println("Note: Animator prefs not used in transitions mode; ignored")
        }
    }

    // Verify the validity of JML file(s) whose paths are given as command-line
    // arguments. For pattern lists the validity of each line within the list is
    // verified.

    private fun doVerify(outpath: Path?, jc: AnimationPrefs?) {
        System.setProperty("java.awt.headless", "true")
        val doJmlOutput = run {
            val index = jlargs.indexOfFirst { it.equals("-tojml", ignoreCase = true) }
            if (index == -1) {
                false
            } else {
                jlargs.removeAt(index)
                true
            }
        }
        val files = parseFilelist()
        if (files.isEmpty()) {
            return
        }
        if (jc != null) {
            println("Note: Animator prefs not used in verify mode; ignored\n")
        }

        var ps = System.out
        try {
            if (outpath != null) {
                ps = PrintStream(outpath.toFile())
            }
        } catch (_: FileNotFoundException) {
            println("Error: Problem writing to file path $outpath")
            return
        }

        var errorCount = 0
        var filesWithErrorsCount = 0
        var filesCount = 0
        var patternsCount = 0

        for (file in files) {
            ps.println("Verifying ${file.absolutePath}")
            ++filesCount

            var errorCountCurrentFile = 0

            val parser = JmlParser()
            try {
                parser.parse(file.readText())
            } catch (_: Throwable) {
                ps.println("   Error: Problem reading JML file")
                ++errorCountCurrentFile
            }

            if (errorCountCurrentFile > 0) {
                errorCount += errorCountCurrentFile
                ++filesWithErrorsCount
                continue
            }

            if (parser.fileType == JmlParser.JML_PATTERN) {
                try {
                    ++patternsCount
                    val pat = JmlPattern.fromJmlNode(parser.tree!!)
                    pat.layout
                    ps.println("   OK")
                } catch (je: JuggleException) {
                    ps.println("   Error creating pattern: ${je.message}")
                    ++errorCountCurrentFile
                } catch (_: Throwable) {
                    ps.println("   Error creating pattern")
                    ++errorCountCurrentFile
                }
            } else if (parser.fileType == JmlParser.JML_LIST) {
                var pl: JmlPatternList? = null
                try {
                    pl = JmlPatternList(parser.tree!!)
                } catch (jeu: JuggleExceptionUser) {
                    ps.println("   Error creating pattern list: ${jeu.message}")
                    ++errorCountCurrentFile
                } catch (_: Throwable) {
                    ps.println("   Error creating pattern list")
                    ++errorCountCurrentFile
                }

                if (errorCountCurrentFile > 0) {
                    errorCount += errorCountCurrentFile
                    ++filesWithErrorsCount
                    continue
                }

                for (i in 0..<pl!!.size) {
                    // Verify pattern and animprefs for each line
                    try {
                        val pat = pl.getPatternForLine(i)
                        if (pat != null) {
                            ++patternsCount
                            pat.layout
                            pl.getAnimationPrefsForLine(i)
                            ps.println("   Pattern line ${i + 1}: OK")
                            if (doJmlOutput) {
                                ps.println(pat.toString())
                            }
                        }
                    } catch (je: JuggleException) {
                        ps.println("   Pattern line ${i + 1}: Error: ${je.message}")
                        ++errorCountCurrentFile
                    } catch (_: Throwable) {
                        ps.println("   Pattern line ${i + 1}: Error")
                        ++errorCountCurrentFile
                    }
                }
            } else {
                ps.println("   Error: File is not valid JML")
                ++errorCountCurrentFile
            }

            if (errorCountCurrentFile > 0) {
                errorCount += errorCountCurrentFile
                ++filesWithErrorsCount
            }
        }

        ps.println()
        ps.println("Processed $patternsCount patterns in $filesCount files")

        if (errorCount == 0) {
            ps.println("   All files OK")
        } else {
            ps.println("   Files with errors: $filesWithErrorsCount")
            ps.println("   Total errors found: $errorCount")
        }
    }

    // Look at beginning of `jlargs` to see if there's a pattern, and if so then
    // parse and return it. Otherwise print an error message and return null.

    private fun parsePattern(): JmlPattern? {
        if (jlargs.isEmpty()) {
            println("Error: Expected pattern input, none found")
            return null
        }

        // first case is a JML-formatted pattern in a file
        if (jlargs[0].equals("-jml", ignoreCase = true)) {
            jlargs.removeFirst()
            if (jlargs.isEmpty()) {
                println("Error: No input path specified after -jml flag")
                return null
            }

            val inpathString = jlargs.removeFirst()
            var inpath = Paths.get(inpathString)
            if (!inpath.isAbsolute) {
                inpath = Paths.get(jlBaseFileDirectory.toString(), inpathString)
            }

            try {
                val parser = JmlParser()
                parser.parse(inpath.toFile().readText())

                when (parser.fileType) {
                    JmlParser.JML_PATTERN ->
                        return JmlPattern.fromJmlNode(parser.tree!!)

                    JmlParser.JML_LIST ->
                        println("Error: JML file cannot be a pattern list")

                    else -> println("Error: File is not valid JML")
                }
            } catch (jeu: JuggleExceptionUser) {
                println("Error parsing JML: ${jeu.message}")
            } catch (_: Throwable) {
                println("Error: Problem reading JML file from path $inpath")
            }
            return null
        }

        // otherwise assume pattern is in siteswap notation
        try {
            val config = jlargs.removeFirst()
            return JmlPattern.fromBasePattern("siteswap", config)
        } catch (jeu: JuggleExceptionUser) {
            println("Error: ${jeu.message}")
        } catch (jei: JuggleExceptionInternal) {
            println("Internal Error: ${jei.message}")
        } catch (_: Throwable) {
            println("Internal Error")
        }
        return null
    }

    // Open pattern in a window.

    private fun doAnim(pat: JmlPattern, jc: AnimationPrefs?) {
        SwingUtilities.invokeLater {
            registerAboutHandler()
            PatternWindow(pat.title, pat, jc)
            PatternWindow.setExitOnLastClose(true)
        }
    }

    // Output an animated GIF of the pattern.

    private fun doTogif(pat: JmlPattern, outpath: Path?, jc: AnimationPrefs?) {
        var jc = jc
        if (outpath == null) {
            println("Error: No output path specified for animated GIF")
            return
        }

        try {
            if (jc == null) {
                jc = AnimationPrefs(fps = 33.3)  // default frames per sec for GIFs
                // Note the GIF header specifies inter-frame delay in terms of
                // hundredths of a second, so only `fps` values like 50, 33 1/3,
                // 25, 20, ... are precisely achieveable.
            }
            val drawer = FrameDrawer(PatternAnimationState(pat, jc))
            drawer.writeGIF(FileOutputStream(outpath.toFile()), null, jc.fps)
        } catch (jeu: JuggleExceptionUser) {
            println("Error: ${jeu.message}")
        } catch (jei: JuggleExceptionInternal) {
            println("Internal Error: ${jei.message}")
        } catch (_: IOException) {
            println("Error: Problem writing GIF to path $outpath")
        } catch (_: Throwable) {
            println("General Error")
        }
    }

    // Output pattern to JML.

    private fun doTojml(pat: JmlPattern, outpath: Path?, jc: AnimationPrefs?) {
        if (outpath == null) {
            print(pat.toString())
        } else {
            try {
                val fw = FileWriter(outpath.toFile())
                pat.writeJml(fw, writeTitle = true, writeInfo = true)
                fw.close()
            } catch (_: Throwable) {
                println("Error: Problem writing JML to path $outpath")
            }
        }

        if (jc != null) {
            println("Note: Animator prefs not used in jml output mode; ignored")
        }
    }
}
