//
// JlFuncJvm.kt
//
// Some useful functions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package jugglinglab.util

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.*
import java.awt.event.ActionEvent
import java.net.URI
import java.text.*
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.min
import kotlin.system.exitProcess

//------------------------------------------------------------------------------
// Helpers for converting numbers to/from strings
//------------------------------------------------------------------------------

private val nf: NumberFormat by lazy {
    // use US-style number formatting for interoperability of JML files across
    // Locales
    NumberFormat.getInstance(Locale.US)
}

@Throws(NumberFormatException::class)
actual fun jlParseFiniteDouble(input: String): Double {
    try {
        val x = nf.parse(input).toDouble()
        if (x.isFinite()) {
            return x
        }
        throw NumberFormatException("not a finite value")
    } catch (_: ParseException) {
        throw NumberFormatException()
    }
}

actual fun jlToStringRounded(value: Double, digits: Int): String {
    val fmt = "###.##########".take(if (digits <= 0) 3 else 4 + min(10, digits))
    val formatter = DecimalFormat(fmt, DecimalFormatSymbols(Locale.US))
    var result = formatter.format(value)
    if (result == "-0") {
        // strange quirk
        result = "0"
    }
    return result
}

//------------------------------------------------------------------------------
// Helpers for message display and error handling
//------------------------------------------------------------------------------

// Show an informational message dialog.

actual fun jlHandleUserMessage(parent: Any?, title: String?, msg: String?) {
    SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
            parent as Component?,
            msg,
            title,
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}

// Show a message dialog for a recoverable user error.

actual fun jlHandleUserException(parent: Any?, msg: String?) {
    SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
            parent as Component?,
            msg,
            jlGetStringResource(Res.string.error),
            JOptionPane.ERROR_MESSAGE
        )
    }
}

// Handle a fatal exception by presenting a window to the user with detailed
// debugging information. The intent is that these exceptions only happen in
// the event of a bug in Juggling Lab, and so we invite users to email us this
// information.

actual fun jlHandleFatalException(e: Exception) {
    SwingUtilities.invokeLater { showInternalErrorWindow(e) }
}

private var internalErrorWindowIsActive = false

private fun showInternalErrorWindow(e: Exception) {
    if (internalErrorWindowIsActive) return

    // diagnostic information displayed in the window
    val message = run {
        val sw = StringWriter()
        sw.write(jlGetStringResource(Res.string.error_internal_msg_part1) + "\n\n")
        sw.write(jlGetStringResource(Res.string.error_internal_msg_part2) + "\n")
        sw.write(jlGetStringResource(Res.string.error_internal_msg_part3) + "\n\n")
        sw.write("Juggling Lab version: ${Constants.VERSION}\n\n")
        if (e is JuggleExceptionInternal) {
            if (e.wrapped != null) {
                e.wrapped?.printStackTrace(PrintWriter(sw))
            } else {
                e.printStackTrace(PrintWriter(sw))
            }
            val pat = e.pattern
            if (pat != null) {
                sw.write("\nJML pattern:\n")
                sw.write(pat.toString())
            }
        } else {
            e.printStackTrace(PrintWriter(sw))
        }
        sw.write("\n")
        sw.toString()
    }

    val exframe = JFrame(jlGetStringResource(Res.string.error_internal_title))

    val exmsg1 = jlGetStringResource(Res.string.error_internal_part1)
    val exmsg2 = jlGetStringResource(Res.string.error_internal_part2)
    val exmsg3 = jlGetStringResource(Res.string.error_internal_part3)
    val exmsg4 = jlGetStringResource(Res.string.error_internal_part4)
    val exmsg5 = jlGetStringResource(Res.string.error_internal_part5)

    val text1 = JLabel(exmsg1).apply { setFont(Font("SansSerif", Font.BOLD, 12)) }
    val text2 = JLabel(exmsg2).apply { setFont(Font("SansSerif", Font.PLAIN, 12)) }
    val text3 = JLabel(exmsg3).apply { setFont(Font("SansSerif", Font.PLAIN, 12)) }
    val text4 = JLabel(exmsg4).apply { setFont(Font("SansSerif", Font.PLAIN, 12)) }
    val text5 = JLabel(exmsg5).apply { setFont(Font("SansSerif", Font.BOLD, 12)) }

    val dumpta = JTextArea().apply {
        text = message
        setCaretPosition(0)
    }
    val jsp = JScrollPane(dumpta).apply {
        preferredSize = Dimension(450, 300)
    }
    val quitbutton = JButton(jlGetStringResource(Res.string.gui_quit)).apply {
        addActionListener { _: ActionEvent? -> exitProcess(0) }
    }
    val okbutton = JButton(jlGetStringResource(Res.string.gui_continue)).apply {
        addActionListener { _: ActionEvent? ->
            exframe.isVisible = false
            exframe.dispose()
            internalErrorWindowIsActive = false
        }
    }
    val butp = JPanel().apply {
        setLayout(FlowLayout(FlowLayout.LEADING))
        add(quitbutton)
        add(okbutton)
    }

    val gb = GridBagLayout().apply {
        setConstraints(text1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10)))
        setConstraints(text2, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(10, 10, 0, 10)))
        setConstraints(text3, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 10, 0, 10)))
        setConstraints(text4, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 10)))
        setConstraints(text5, jlConstraints(GridBagConstraints.LINE_START, 0, 4, Insets(10, 10, 10, 10)))
        setConstraints(jsp, jlConstraints(GridBagConstraints.CENTER, 0, 5, Insets(10, 10, 10, 10)))
        setConstraints(butp, jlConstraints(GridBagConstraints.LINE_END, 0, 6, Insets(10, 10, 10, 10)))
    }

    val exp = JPanel().apply {
        setOpaque(true)
        setLayout(gb)
        add(text1)
        add(text2)
        add(text3)
        add(text4)
        add(text5)
        add(jsp)
        add(butp)
    }

    exframe.apply {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
        contentPane = exp
        applyComponentOrientation(ComponentOrientation.getOrientation(Locale.getDefault()))
        pack()
        isResizable = false
        setLocationRelativeTo(null)  // center frame on screen
        isVisible = true
        internalErrorWindowIsActive = true
    }
}

//------------------------------------------------------------------------------
// Helpers for GridBagLayout
//------------------------------------------------------------------------------

fun jlConstraints(location: Int, gridX: Int, gridY: Int): GridBagConstraints {
    return GridBagConstraints().apply {
        anchor = location
        fill = GridBagConstraints.NONE
        gridwidth = 1
        gridheight = 1
        gridx = gridX
        gridy = gridY
        weighty = 0.0
        weightx = 0.0
    }
}

fun jlConstraints(location: Int, gridx: Int, gridy: Int, ins: Insets?): GridBagConstraints {
    return jlConstraints(location, gridx, gridy).apply {
        insets = ins ?: Insets(0, 0, 0, 0)
    }
}

//------------------------------------------------------------------------------
// Helpers for execution context information
//------------------------------------------------------------------------------

// platform
fun jlIsMacOs() = System.getProperty("os.name").lowercase().startsWith("mac os x")
fun jlIsWindows() = System.getProperty("os.name").lowercase().startsWith("windows")
fun jlIsLinux() = System.getProperty("os.name").lowercase().startsWith("linux")

// running from the command line?
fun jlIsCli() = (System.getenv("JL_WORKING_DIR") != null)

//------------------------------------------------------------------------------
// Helpers for file opening/saving files
//------------------------------------------------------------------------------

val jlBaseFileDirectory: Path by lazy {
    // Decide on a base directory for file operations. First look for working
    // directory set by an enclosing script, which indicates Juggling Lab is
    // running from the command line.
    var workingDir = System.getenv("JL_WORKING_DIR")

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

    Paths.get(workingDir)
}

val jlJfc: JFileChooser by lazy {
    object : JFileChooser() {
        override fun approveSelection() {
            val f = selectedFile

            if (f.exists() && dialogType == SAVE_DIALOG) {
                val msg = jlGetStringResource(Res.string.gui_jfc_file_exists_message, f.getName())
                val title = jlGetStringResource(Res.string.gui_jfc_file_exists_title)

                val result = JOptionPane.showConfirmDialog(
                    this, msg, title, JOptionPane.YES_NO_CANCEL_OPTION
                )
                when (result) {
                    JOptionPane.YES_OPTION -> {
                        super.approveSelection()
                        return
                    }

                    JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION -> return
                    JOptionPane.CANCEL_OPTION -> {
                        cancelSelection()
                        return
                    }
                }
            }

            try {
                Preferences.userRoot().node("Juggling Lab").put("base_dir", f.getParent())
            } catch (_: Exception) {
            }

            super.approveSelection()
        }
    }
}

// Sanitize filename based on platform restrictions.
//
// See e.g.:
// https://stackoverflow.com/questions/1976007/
//       what-characters-are-forbidden-in-windows-and-linux-directory-names

fun jlSanitizeFilename(fname: String): String {
    val index = fname.lastIndexOf(".")

    val base = if (index >= 0) fname.take(index) else fname
    val extension = if (index >= 0) fname.substring(index) else ""

    if (jlIsMacOs()) {
        // remove all instances of `:` and `/`
        var b = base.replace("[:/]".toRegex(), "")

        // remove leading `.` and space
        while (b.startsWith(".") || b.startsWith(" ")) {
            b = b.substring(1)
        }

        if (b.isEmpty()) {
            b = "Pattern"
        }

        return b + extension
    } else if (jlIsWindows()) {
        // remove all instances of `\/?:*"`
        var b = base.replace("[/?:*\"]".toRegex(), "")

        // disallow strings with `><|`
        var forbidden = (b.contains(">"))
        forbidden = forbidden || (b.contains("<"))
        forbidden = forbidden || (b.contains("|"))

        if (forbidden || b.isEmpty()) {
            b = "Pattern"
        }

        return b + extension
    } else if (jlIsLinux()) {
        // change all `/` to `:`
        var b = base.replace("/".toRegex(), ":")

        // remove leading `.` and space
        while (b.startsWith(".") || b.startsWith(" ")) {
            b = b.substring(1)
        }

        if (b.isEmpty()) {
            b = "Pattern"
        }

        return b + extension
    } else {
        return fname
    }
}

@Throws(JuggleExceptionUser::class)
fun jlErrorIfNotSanitized(fname: String) {
    if (fname == jlSanitizeFilename(fname)) {
        return
    }
    throw JuggleExceptionUser(jlGetStringResource(Res.string.error_saving_disallowed_character))
}

//------------------------------------------------------------------------------
// Helpers for loading resources (UI strings, error messages, images, ...)
//------------------------------------------------------------------------------

// Load an image from a URL string.
//
// In the event of a problem, throw a JuggleExceptionUser with a relevant message.

@Throws(JuggleExceptionUser::class)
actual fun jlLoadComposeImageFromUrl(urlString: String): ImageBitmap {
    try {
        val awtImage = ImageIO.read(URI(urlString).toURL())
        val mt = MediaTracker(object : Component() {})
        try {
            mt.addImage(awtImage, 0)
            mt.waitForAll()
        } catch (_: InterruptedException) {
        }
        if (mt.isErrorAny()) {
            // could be bad image data, but is usually a nonexistent file
            throw JuggleExceptionUser(jlGetStringResource(Res.string.error_bad_file))
        }
        return awtImage.toComposeImageBitmap()
    } catch (_: IOException) {
        throw JuggleExceptionUser(jlGetStringResource(Res.string.error_bad_file))
    } catch (_: SecurityException) {
        throw JuggleExceptionUser(jlGetStringResource(Res.string.error_security_restriction))
    }
}

//------------------------------------------------------------------------------
// Other
//------------------------------------------------------------------------------

// Return the native screen refresh rate.

actual fun jlGetScreenFps(): Double {
    var fpsScreen = 0.0
    try {
        val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        if (devices.isNotEmpty()) {
            fpsScreen = devices[0]!!.getDisplayMode().refreshRate.toDouble()
            // refreshRate returns 0 when refresh is unknown
        }
    } catch (_: Exception) {
        // HeadlessException when running headless (from CLI)
    }
    return if (fpsScreen < 20) 60.0 else fpsScreen
}

// Return platform information.

actual fun jlGetCurrentPlatform(): String {
    return System.getProperty("os.name") + " " + System.getProperty("os.version")
}

actual fun jlGetAboutBoxPlatform(): String {
    val javaVersion = System.getProperty("java.version")
    val javaVmName = System.getProperty("java.vm.name")
    val javaVmVersion = System.getProperty("java.vm.version")

    return "Java version $javaVersion\n$javaVmName ($javaVmVersion)"
}

// Return true if Swing-based UI should be used.

fun jlIsSwing(): Boolean {
    val isCompose = System.getProperty("JL_compose_ui")
    return !(isCompose?.equals("true", ignoreCase = true) ?: false)
}
