//
// AnimationPrefsDialog.kt
//
// This is the dialog box that allows the user to set animation preferences.
// The dialog does not display when the dialog box is constructed, but when
// getPrefs() is called.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.JugglingLab.guistrings
import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.toStringRounded
import java.awt.ComponentOrientation
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.text.MessageFormat
import java.util.Locale
import javax.swing.*

class AnimationPrefsDialog(parent: JFrame?) : JDialog(parent, guistrings.getString("Animation_Preferences"), true) {
    private lateinit var tfWidth: JTextField
    private lateinit var tfHeight: JTextField
    private lateinit var tfFps: JTextField
    private lateinit var tfSlowdown: JTextField
    private lateinit var tfBorder: JTextField
    private lateinit var comboShowground: JComboBox<String>
    private lateinit var cbPaused: JCheckBox
    private lateinit var cbMousepause: JCheckBox
    private lateinit var cbStereo: JCheckBox
    private lateinit var cbCatchsounds: JCheckBox
    private lateinit var cbBouncesounds: JCheckBox
    private lateinit var tfOther: JTextField
    private lateinit var butCancel: JButton
    private lateinit var butOk: JButton

    private var okSelected: Boolean = false

    init {
        createContents()
        setLocationRelativeTo(parent)
        butCancel.addActionListener { _: ActionEvent? ->
            isVisible = false
            okSelected = false
        }
        butOk.addActionListener { _: ActionEvent? ->
            isVisible = false
            okSelected = true
        }
    }

    // Show dialog box and return the new preferences.

    fun getPrefs(oldjc: AnimationPrefs): AnimationPrefs {
        // Fill in UI elements with current prefs
        tfWidth.text = oldjc.width.toString()
        tfHeight.text = oldjc.height.toString()
        tfFps.text = toStringRounded(oldjc.fps, 2)
        tfSlowdown.text = toStringRounded(oldjc.slowdown, 2)
        tfBorder.text = oldjc.border.toString()
        comboShowground.setSelectedIndex(oldjc.showGround)
        cbPaused.setSelected(oldjc.startPause)
        cbMousepause.setSelected(oldjc.mousePause)
        cbStereo.setSelected(oldjc.stereo)
        cbCatchsounds.setSelected(oldjc.catchSound)
        cbBouncesounds.setSelected(oldjc.bounceSound)

        try {
            // filter out all the explicit settings above to populate the
            // manual settings box
            val pl = ParameterList(oldjc.toString())
            val paramsRemove = arrayOf(
                "width",
                "height",
                "fps",
                "slowdown",
                "border",
                "showground",
                "stereo",
                "startpaused",
                "mousepause",
                "catchsound",
                "bouncesound",
            )
            for (param in paramsRemove) {
                pl.removeParameter(param)
            }
            tfOther.text = pl.toString()
            tfOther.setCaretPosition(0)
        } catch (jeu: JuggleExceptionUser) {
            // any error here can't be a user error
            handleFatalException(
                JuggleExceptionInternal("Anim Prefs Dialog error: " + jeu.message)
            )
        }

        okSelected = false
        isVisible = true // Blocks until user clicks OK or Cancel

        if (okSelected) {
            return readDialogBox(oldjc)
        }

        return oldjc
    }

    private fun createContents() {
        val gb = GridBagLayout()
        contentPane.setLayout(gb)

        // panel of text boxes at the top
        val p1 = JPanel()
        p1.setLayout(gb)

        val lab1 = JLabel(guistrings.getString("Width"))
        p1.add(lab1)
        gb.setConstraints(
            lab1, constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 3, 0, 0))
        )
        tfWidth = JTextField(4)
        tfWidth.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tfWidth)
        gb.setConstraints(
            tfWidth, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 0, 0, 0))
        )

        val lab2 = JLabel(guistrings.getString("Height"))
        p1.add(lab2)
        gb.setConstraints(
            lab2, constraints(GridBagConstraints.LINE_START, 1, 1, Insets(0, 3, 0, 0))
        )
        tfHeight = JTextField(4)
        tfHeight.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tfHeight)
        gb.setConstraints(
            tfHeight, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )

        val lab3 = JLabel(guistrings.getString("Frames_per_second"))
        p1.add(lab3)
        gb.setConstraints(
            lab3, constraints(GridBagConstraints.LINE_START, 1, 2, Insets(0, 3, 0, 0))
        )
        tfFps = JTextField(4)
        tfFps.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tfFps)
        gb.setConstraints(
            tfFps, constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 0, 0, 0))
        )

        val lab4 = JLabel(guistrings.getString("Slowdown_factor"))
        p1.add(lab4)
        gb.setConstraints(
            lab4, constraints(GridBagConstraints.LINE_START, 1, 3, Insets(0, 3, 0, 0))
        )
        tfSlowdown = JTextField(4)
        tfSlowdown.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tfSlowdown)
        gb.setConstraints(
            tfSlowdown, constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 0, 0, 0))
        )

        val lab5 = JLabel(guistrings.getString("Border_(pixels)"))
        p1.add(lab5)
        gb.setConstraints(
            lab5, constraints(GridBagConstraints.LINE_START, 1, 4, Insets(0, 3, 0, 0))
        )
        tfBorder = JTextField(4)
        tfBorder.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tfBorder)
        gb.setConstraints(
            tfBorder, constraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 0, 0, 0))
        )

        val lab6 = JLabel(guistrings.getString("Prefs_show_ground"))
        p1.add(lab6)
        gb.setConstraints(
            lab6, constraints(GridBagConstraints.LINE_START, 1, 5, Insets(0, 3, 0, 0))
        )
        comboShowground = JComboBox<String>()
        comboShowground.addItem(guistrings.getString("Prefs_show_ground_auto"))
        comboShowground.addItem(guistrings.getString("Prefs_show_ground_yes"))
        comboShowground.addItem(guistrings.getString("Prefs_show_ground_no"))
        p1.add(comboShowground)
        gb.setConstraints(
            comboShowground,
            constraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, 0, 0, 0))
        )

        // checkboxes farther down
        cbPaused = JCheckBox(guistrings.getString("Start_paused"))
        cbMousepause = JCheckBox(guistrings.getString("Pause_on_mouse_away"))
        cbStereo = JCheckBox(guistrings.getString("Stereo_display"))
        cbCatchsounds = JCheckBox(guistrings.getString("Catch_sounds"))
        cbBouncesounds = JCheckBox(guistrings.getString("Bounce_sounds"))

        // manual settings
        val labOther = JLabel("Manual settings")
        tfOther = JTextField(15)

        // buttons at the bottom
        val p2 = JPanel()
        p2.setLayout(gb)
        butCancel = JButton(guistrings.getString("Cancel"))

        p2.add(butCancel)
        gb.setConstraints(
            butCancel, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        butOk = JButton(guistrings.getString("OK"))

        p2.add(butOk)
        gb.setConstraints(
            butOk, constraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )

        // now make the whole window
        contentPane.add(p1)
        gb.setConstraints(
            p1,
            constraints(GridBagConstraints.LINE_START, 0, 0, Insets(3, BORDER, 0, BORDER))
        )

        contentPane.add(cbPaused)
        gb.setConstraints(
            cbPaused,
            constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, BORDER, 0, BORDER))
        )
        contentPane.add(cbMousepause)
        gb.setConstraints(
            cbMousepause,
            constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, BORDER, 0, BORDER))
        )
        contentPane.add(cbStereo)
        gb.setConstraints(
            cbStereo,
            constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, BORDER, 0, BORDER))
        )
        contentPane.add(cbCatchsounds)
        gb.setConstraints(
            cbCatchsounds,
            constraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, BORDER, 0, BORDER))
        )
        contentPane.add(cbBouncesounds)
        gb.setConstraints(
            cbBouncesounds,
            constraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, BORDER, 8, BORDER))
        )
        contentPane.add(labOther)
        gb.setConstraints(
            labOther,
            constraints(GridBagConstraints.LINE_START, 0, 6, Insets(0, BORDER, 0, BORDER))
        )
        contentPane.add(tfOther)
        gb.setConstraints(
            tfOther,
            constraints(GridBagConstraints.LINE_START, 0, 7, Insets(0, BORDER, 3, BORDER))
        )

        contentPane.add(p2)
        gb.setConstraints(
            p2,
            constraints(GridBagConstraints.LINE_END, 0, 8, Insets(0, BORDER, BORDER, BORDER))
        )

        getRootPane().setDefaultButton(butOk) // OK button is default

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        pack()
        setResizable(false)
    }

    // Read prefs out of UI elements.

    private fun readDialogBox(oldjc: AnimationPrefs): AnimationPrefs {
        var tempint: Int
        var tempdouble: Double

        // Clone the old preferences so if we get an error we retain as much of
        // it as possible
        var newjc = AnimationPrefs(oldjc)

        try {
            tempint = tfWidth.getText().toInt()
            if (tempint >= 0) {
                newjc.width = tempint
            }
        } catch (_: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("width")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempint = tfHeight.getText().toInt()
            if (tempint >= 0) {
                newjc.height = tempint
            }
        } catch (_: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("height")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempdouble = tfFps.getText().toDouble()
            if (tempdouble > 0.0) {
                newjc.fps = tempdouble
            }
        } catch (_: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("fps")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempdouble = tfSlowdown.getText().toDouble()
            if (tempdouble > 0.0) {
                newjc.slowdown = tempdouble
            }
        } catch (_: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("slowdown")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempint = tfBorder.getText().toInt()
            if (tempint >= 0) {
                newjc.border = tempint
            }
        } catch (_: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("border")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }

        newjc.showGround = comboShowground.getSelectedIndex()
        newjc.startPause = cbPaused.isSelected
        newjc.mousePause = cbMousepause.isSelected
        newjc.stereo = cbStereo.isSelected
        newjc.catchSound = cbCatchsounds.isSelected
        newjc.bounceSound = cbBouncesounds.isSelected

        if (!tfOther.getText().trim { it <= ' ' }.isEmpty()) {
            try {
                newjc = AnimationPrefs().fromString(newjc.toString() + ";" + tfOther.getText())
            } catch (jeu: JuggleExceptionUser) {
                handleUserException(this@AnimationPrefsDialog, jeu.message)
            }
        }
        return newjc
    }

    companion object {
        private const val BORDER: Int = 10

        private fun constraints(
            location: Int, gridx: Int, gridy: Int, ins: Insets?
        ): GridBagConstraints {
            val gbc = GridBagConstraints()
            gbc.anchor = location
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.gridwidth = 1
            gbc.gridheight = 1
            gbc.gridx = gridx
            gbc.gridy = gridy
            gbc.insets = ins
            gbc.weighty = 0.0
            gbc.weightx = 0.0
            return gbc
        }
    }
}
