//
// UpdateChecker.kt
//
// Background thread that checks online for an updated version of the application.
// If it finds one, open a dialog box that offers to take the user to the
// download location.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.composeapp.generated.resources.*
import java.awt.*
import java.awt.event.ActionEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URISyntaxException
import java.text.MessageFormat
import java.util.*
import javax.swing.*

class UpdateChecker : Thread() {
    init {
        setPriority(MIN_PRIORITY)
        start()
    }

    override fun run() {
        // first download the Juggling Lab home page, looking for the line
        // containing version information; fail quietly if something went wrong
        val line: String = line ?: return

        // Use regular expression matching to find the span tag with
        // id "versionstring" surrounding the version number string we want
        val pattern = ".*versionstring.*?>(.*?)<.*"
        val latestVersion = line.replace(pattern.toRegex(), "$1")
        val runningVersion = jugglinglab.core.Constants.VERSION

        if (latestVersion.isEmpty()
            || jlCompareVersions(latestVersion, runningVersion) <= 0
        ) {
            return
        }

        try {
            sleep(3000)
            SwingUtilities.invokeLater { showUpdateBox(latestVersion) }
        } catch (_: InterruptedException) {
        }
    }

    companion object {
        private val line: String?
            // Download the Juggling Lab home page and return the line with version
            get() {
                var instream: InputStream? = null
                var isr: InputStreamReader? = null
                var line: String? = null

                try {
                    val url = URI(jugglinglab.core.Constants.SITE_URL).toURL()
                    instream = url.openStream()
                    isr = InputStreamReader(instream)
                    val br = BufferedReader(isr)

                    while ((br.readLine().also { line = it }) != null) {
                        if (line!!.contains("versionstring")) {
                            break
                        }
                    }
                } catch (_: URISyntaxException) {
                    // handle errors quietly; no big deal if this background operation fails
                } catch (_: IOException) {
                } finally {
                    try {
                        isr?.close()
                        instream?.close()
                    } catch (_: IOException) {
                    }
                }
                return line
            }

        private fun showUpdateBox(version: String?) {
            val title = getStringResource(Res.string.gui_new_version_available)
            val updateBox = JFrame(title)
            updateBox.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

            val updatePanel = JPanel()
            val gb = GridBagLayout()
            updatePanel.setLayout(gb)

            val message = getStringResource(Res.string.gui_new_version_text1, version)
            val text1 = JLabel(message)
            text1.setFont(Font("SansSerif", Font.PLAIN, 14))
            updatePanel.add(text1)
            gb.setConstraints(
                text1, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(20, 25, 0, 25))
            )

            val message2 = getStringResource(Res.string.gui_new_version_text2, jugglinglab.core.Constants.VERSION)
            val text2 = JLabel(message2)
            text2.setFont(Font("SansSerif", Font.PLAIN, 14))
            updatePanel.add(text2)
            gb.setConstraints(
                text2, constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 25, 0, 25))
            )

            val text3 = JLabel(getStringResource(Res.string.gui_new_version_text3))
            text3.setFont(Font("SansSerif", Font.PLAIN, 14))
            updatePanel.add(text3)
            gb.setConstraints(
                text3, constraints(GridBagConstraints.LINE_START, 0, 3, Insets(20, 25, 5, 25))
            )

            val butp = JPanel()
            butp.setLayout(FlowLayout(FlowLayout.LEADING))
            val cancelbutton = JButton(getStringResource(Res.string.gui_update_cancel))
            cancelbutton.addActionListener { _: ActionEvent? -> updateBox.dispose() }
            butp.add(cancelbutton)

            val yesbutton = JButton(getStringResource(Res.string.gui_update_yes))
            yesbutton.setDefaultCapable(true)
            yesbutton.addActionListener { _: ActionEvent? ->
                val browseSupported =
                    (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                var browseProblem = false

                if (browseSupported) {
                    try {
                        Desktop.getDesktop().browse(URI(jugglinglab.core.Constants.DOWNLOAD_URL))
                    } catch (_: Exception) {
                        browseProblem = true
                    }
                }

                if (!browseSupported || browseProblem) {
                    val template3 = getStringResource(Res.string.gui_download_message)
                    val arguments3 = arrayOf<Any?>(jugglinglab.core.Constants.DOWNLOAD_URL)
                    val message = MessageFormat.format(template3, *arguments3)
                    LabelDialog(updateBox, title, message)
                }
                updateBox.dispose()
            }
            butp.add(yesbutton)

            updatePanel.add(butp)
            gb.setConstraints(
                butp, constraints(GridBagConstraints.LINE_END, 0, 4, Insets(10, 10, 10, 10))
            )

            updatePanel.setOpaque(true)
            updateBox.contentPane = updatePanel
            updateBox.getRootPane().setDefaultButton(yesbutton)

            val loc = Locale.getDefault()
            updateBox.applyComponentOrientation(ComponentOrientation.getOrientation(loc))

            updateBox.pack()
            updateBox.setResizable(false)
            updateBox.setLocationRelativeTo(null) // center frame on screen
            updateBox.isVisible = true
        }
    }
}
