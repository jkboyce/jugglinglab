//
// JugglingLab.kt
//
// Juggling Lab is an open-source application for creating and animating
// juggling patterns. https://jugglinglab.org
//
// This is the entry point into Juggling Lab, whether from a usual application
// launch or from one of the command line interfaces.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.*
import jugglinglab.core.ApplicationWindow.Companion.openJMLFile
import jugglinglab.core.ApplicationWindow.Companion.showAboutBox
import jugglinglab.core.PatternWindow.Companion.setExitOnLastClose
import jugglinglab.generator.GeneratorTargetBasic
import jugglinglab.generator.SiteswapGenerator
import jugglinglab.generator.SiteswapTransitioner
import jugglinglab.jml.JMLParser
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.JMLPattern.Companion.fromBasePattern
import jugglinglab.jml.JMLPatternList
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.util.JuggleException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.OpenFilesServer
import jugglinglab.util.ParameterList
import jugglinglab.util.jlGetStringResource
import java.awt.Desktop
import java.awt.Dimension
import java.awt.desktop.AboutEvent
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.prefs.Preferences
import javax.swing.SwingUtilities

//------------------------------------------------------------------------------
// Main entry point for Juggling Lab
//------------------------------------------------------------------------------

fun main(args: Array<String>) {
    JugglingLab.startWithArgs(args)
}

object JugglingLab {
    // platform info
    val isMacOS: Boolean
    val isWindows: Boolean
    val isLinux: Boolean

    // whether we're running from the command line
    val isCLI: Boolean

    // base directory for file operations
    val baseDir: Path

    // command line arguments that we trim as portions are parsed
    private var jlargs: MutableList<String> = mutableListOf()

    init {
        val osname = System.getProperty("os.name").lowercase(Locale.getDefault())
        isMacOS = osname.startsWith("mac os x")
        isWindows = osname.startsWith("windows")
        isLinux = osname.startsWith("linux")

        // Decide on a base directory for file operations. First look for working
        // directory set by an enclosing script, which indicates Juggling Lab is
        // running from the command line.
        var workingDir = System.getenv("JL_WORKING_DIR")
        isCLI = (workingDir != null)

        if (workingDir == null) {
            // Look for a directory saved during previous file operations.
            try {
                workingDir = Preferences.userRoot().node("Juggling Lab").get("base_dir", null)
            } catch (_: Exception) {
            }
        }

        if (workingDir == null) {
            // Otherwise, user.dir (current working directory when Java was invoked)
            // is the most logical choice, UNLESS we're running in an application
            // bundle. For bundled apps user.dir is buried inside the app directory
            // structure so we default to user.home instead.
            val isBundle = System.getProperty("JL_run_as_bundle")
            workingDir = if (isBundle != null && isBundle == "true") {
                System.getProperty("user.home")
            } else {
                System.getProperty("user.dir")
            }
        }

        baseDir = Paths.get(workingDir)
    }

    // Start the application.

    fun startWithArgs(args: Array<String>) {
        if (isMacOS) {
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

        val firstarg = jlargs.removeFirst().lowercase(Locale.getDefault())

        if (firstarg == "open") {
            // double-clicking a .jml file on Windows brings us here
            doOpen()
            return
        }

        if (!isCLI) {
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
            //System.setProperty("java.awt.headless", "true")
            val arglist = jlargs.joinToString(", ")
            println("Error: Unrecognized input: $arglist")
            return
        }

        if (firstarg == "anim") {
            doAnim(pat, jc)
            return
        }

        // All remaining modes are headless (no GUI)
        //System.setProperty("java.awt.headless", "true")

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
        Desktop.getDesktop().setAboutHandler { _: AboutEvent? -> showAboutBox() }
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
                //System.setProperty("java.awt.headless", "true")
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
                        openJMLFile(file)
                    } catch (jeu: JuggleExceptionUser) {
                        val message = jlGetStringResource(Res.string.error_reading_file, file.getName())
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
                filepath = Paths.get(baseDir.toString(), filestr)
            }

            files.add(filepath.toFile())
        }

        if (files.isEmpty()) {
            val output = "Error: Expected file path(s), none provided"
            if (isCLI) {
                //System.setProperty("java.awt.headless", "true")
                println(output)
            } else {
                jlHandleUserException(null, output) // should never happen
            }
        }
        return files
    }

    // Show the help message.

    private fun doHelp(firstarg: String?) {
        //System.setProperty("java.awt.headless", "true")
        val arg1 = jlGetStringResource(Res.string.gui_version, Constants.VERSION)
        var output = "Juggling Lab " + arg1.lowercase(Locale.getDefault()) + "\n"
        val arg2 = jlGetStringResource(Res.string.gui_copyright_message, Constants.YEAR)
        output += arg2 + "\n"
        output += jlGetStringResource(Res.string.gui_gpl_message) + "\n\n"
        output += jlGetStringResource(Res.string.gui_cli_help1)
        var examples = jlGetStringResource(Res.string.gui_cli_help2)
        if (isWindows) {
            // replace single quotes with double quotes in Windows examples
            examples = examples.replace("'".toRegex(), "\"")
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
                    outpath = Paths.get(baseDir.toString(), outpathString)
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
                    val jc = AnimationPrefs().fromParameters(pl)
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
        }

        if (jc != null) {
            println("Note: Animator prefs not used in generator mode; ignored")
        }
    }

    // Run the siteswap transitioner.

    private fun doTrans(outpath: Path?, jc: AnimationPrefs?) {
        //System.setProperty("java.awt.headless", "true")
        try {
            var ps = System.out
            if (outpath != null) {
                ps = PrintStream(outpath.toFile())
            }
            SiteswapTransitioner.runTransitionerCLI(jlargs, GeneratorTargetBasic { ps.println(it) })
        } catch (_: FileNotFoundException) {
            println("Error: Problem writing to file path $outpath")
        }

        if (jc != null) {
            println("Note: Animator prefs not used in transitions mode; ignored")
        }
    }

    // Verify the validity of JML file(s) whose paths are given as command-line
    // arguments. For pattern lists the validity of each line within the list is
    // verified.

    private fun doVerify(outpath: Path?, jc: AnimationPrefs?) {
        //System.setProperty("java.awt.headless", "true")
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

            val parser = JMLParser()
            try {
                parser.parse(file.readText())
            } catch (_: Exception) {
                ps.println("   Error: Problem reading JML file")
                ++errorCountCurrentFile
            }

            if (errorCountCurrentFile > 0) {
                errorCount += errorCountCurrentFile
                ++filesWithErrorsCount
                continue
            }

            if (parser.fileType == JMLParser.JML_PATTERN) {
                try {
                    ++patternsCount
                    val pat = JMLPattern.fromJMLNode(parser.tree!!)
                    pat.layout
                    ps.println("   OK")
                } catch (je: JuggleException) {
                    ps.println("   Error creating pattern: ${je.message}")
                    ++errorCountCurrentFile
                }
            } else if (parser.fileType == JMLParser.JML_LIST) {
                var pl: JMLPatternList? = null
                try {
                    pl = JMLPatternList(parser.tree!!)
                } catch (jeu: JuggleExceptionUser) {
                    ps.println("   Error creating pattern list: ${jeu.message}")
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

    private fun parsePattern(): JMLPattern? {
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
                inpath = Paths.get(baseDir.toString(), inpathString)
            }

            try {
                val parser = JMLParser()
                parser.parse(inpath.toFile().readText())

                when (parser.fileType) {
                    JMLParser.JML_PATTERN ->
                        return JMLPattern.fromJMLNode(parser.tree!!)
                    JMLParser.JML_LIST ->
                        println("Error: JML file cannot be a pattern list")
                    else -> println("Error: File is not valid JML")
                }
            } catch (jeu: JuggleExceptionUser) {
                println("Error parsing JML: ${jeu.message}")
            } catch (_: Exception) {
                println("Error: Problem reading JML file from path $inpath")
            }
            return null
        }

        // otherwise assume pattern is in siteswap notation
        try {
            val config = jlargs.removeFirst()
            return fromBasePattern("siteswap", config)
        } catch (jeu: JuggleExceptionUser) {
            println("Error: ${jeu.message}")
        } catch (jei: JuggleExceptionInternal) {
            println("Internal Error: ${jei.message}")
        }
        return null
    }

    // Open pattern in a window.

    private fun doAnim(pat: JMLPattern, jc: AnimationPrefs?) {
        SwingUtilities.invokeLater {
            registerAboutHandler()
            PatternWindow(pat.title, pat, jc)
            setExitOnLastClose(true)
        }
    }

    // Output an animated GIF of the pattern.

    private fun doTogif(pat: JMLPattern?, outpath: Path?, jc: AnimationPrefs?) {
        var jc = jc
        if (outpath == null) {
            println("Error: No output path specified for animated GIF")
            return
        }

        try {
            val anim = Animator()
            if (jc == null) {
                jc = anim.animationPrefs
                jc.fps = 33.3 // default frames per sec for GIFs
                // Note the GIF header specifies inter-frame delay in terms of
                // hundredths of a second, so only `fps` values like 50, 33 1/3,
                // 25, 20, ... are precisely achieveable.
            }
            anim.dimension = Dimension(jc.width, jc.height)
            anim.restartAnimator(pat, jc)
            anim.writeGIF(FileOutputStream(outpath.toFile()), null, jc.fps)
        } catch (jeu: JuggleExceptionUser) {
            println("Error: ${jeu.message}")
        } catch (jei: JuggleExceptionInternal) {
            println("Internal Error: ${jei.message}")
        } catch (_: IOException) {
            println("Error: Problem writing GIF to path $outpath")
        }
    }

    // Output pattern to JML.

    private fun doTojml(pat: JMLPattern, outpath: Path?, jc: AnimationPrefs?) {
        if (outpath == null) {
            print(pat.toString())
        } else {
            try {
                val fw = FileWriter(outpath.toFile())
                pat.writeJML(fw, writeTitle = true, writeInfo = true)
                fw.close()
            } catch (_: IOException) {
                println("Error: Problem writing JML to path $outpath")
            }
        }

        if (jc != null) {
            println("Note: Animator prefs not used in jml output mode; ignored")
        }
    }
}
