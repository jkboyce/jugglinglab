//
// ErrorDialog.kt
//
// Utility class for displaying error dialogs to the user. Note these methods
// can be called from any thread.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.core.Constants
import jugglinglab.JugglingLab.errorstrings
import jugglinglab.JugglingLab.guistrings
import java.awt.*
import java.awt.event.ActionEvent
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import javax.swing.*
import kotlin.system.exitProcess

object ErrorDialog {
    // Show a message dialog for a recoverable user error.

    @JvmStatic
    fun handleUserException(parent: Component?, msg: String?) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                parent,
                msg,
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    // Handle a fatal exception by presenting a window to the user with detailed
    // debugging information. The intent is that these exceptions only happen in
    // the event of a bug in Juggling Lab, and so we invite users to email us this
    // information.

    @JvmStatic
    fun handleFatalException(e: Exception) {
        SwingUtilities.invokeLater { showInternalErrorWindow(e) }
    }

    private fun showInternalErrorWindow(e: Exception) {
        val exmsg1 = errorstrings.getString("Error_internal_part1")
        val exmsg2 = errorstrings.getString("Error_internal_part2")
        val exmsg3 = errorstrings.getString("Error_internal_part3")
        val exmsg4 = errorstrings.getString("Error_internal_part4")
        val exmsg5 = errorstrings.getString("Error_internal_part5")

        // diagnostic information displayed in the window
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        sw.write(errorstrings.getString("Error_internal_msg_part1") + "\n\n")
        sw.write(
            (errorstrings.getString("Error_internal_msg_part2")
                    + "\n"
                    + errorstrings.getString("Error_internal_msg_part3")
                    + "\n\n")
        )
        sw.write("Juggling Lab version: " + Constants.version + "\n\n")
        e.printStackTrace(pw)
        if (e is JuggleExceptionInternal) {
            val pat = e.pat
            if (pat != null) {
                sw.write("\nJML pattern:\n")
                sw.write(pat.toString())
            }
        }
        sw.write("\n")

        val exframe = JFrame(errorstrings.getString("Error_internal_title"))
        exframe.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)

        val exp = JPanel()
        exp.setOpaque(true)
        val gb = GridBagLayout()
        exp.setLayout(gb)

        val text1 = JLabel(exmsg1)
        text1.setFont(Font("SansSerif", Font.BOLD, 12))
        exp.add(text1)
        gb.setConstraints(
            text1, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )

        val text2 = JLabel(exmsg2)
        text2.setFont(Font("SansSerif", Font.PLAIN, 12))
        exp.add(text2)
        gb.setConstraints(
            text2, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(10, 10, 0, 10))
        )

        val text3 = JLabel(exmsg3)
        text3.setFont(Font("SansSerif", Font.PLAIN, 12))
        exp.add(text3)
        gb.setConstraints(
            text3, constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 10, 0, 10))
        )

        val text4 = JLabel(exmsg4)
        text4.setFont(Font("SansSerif", Font.PLAIN, 12))
        exp.add(text4)
        gb.setConstraints(
            text4, constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 10))
        )

        val text5 = JLabel(exmsg5)
        text5.setFont(Font("SansSerif", Font.BOLD, 12))
        exp.add(text5)
        gb.setConstraints(
            text5, constraints(GridBagConstraints.LINE_START, 0, 4, Insets(10, 10, 10, 10))
        )

        val dumpta = JTextArea()
        dumpta.text = sw.toString()
        dumpta.setCaretPosition(0)
        val jsp = JScrollPane(dumpta)
        jsp.preferredSize = Dimension(450, 300)
        exp.add(jsp)
        gb.setConstraints(
            jsp, constraints(GridBagConstraints.CENTER, 0, 5, Insets(10, 10, 10, 10))
        )

        val butp = JPanel()
        butp.setLayout(FlowLayout(FlowLayout.LEADING))
        val quitbutton = JButton(guistrings.getString("Quit"))
        quitbutton.addActionListener { _: ActionEvent? -> exitProcess(0) }
        butp.add(quitbutton)
        val okbutton = JButton(guistrings.getString("Continue"))
        okbutton.addActionListener { _: ActionEvent? ->
            exframe.isVisible = false
            exframe.dispose()
        }
        butp.add(okbutton)
        exp.add(butp)
        gb.setConstraints(
            butp, constraints(GridBagConstraints.LINE_END, 0, 6, Insets(10, 10, 10, 10))
        )

        exframe.contentPane = exp

        val loc = Locale.getDefault()
        exframe.applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        exframe.pack()
        exframe.isResizable = false
        exframe.setLocationRelativeTo(null)  // center frame on screen
        exframe.isVisible = true
    }
}
