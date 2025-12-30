//
// AnimationPrefsDialogSwing.kt
//
// Swing UI version of AnimationPrefsDialog.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.jlGetStringResource
import jugglinglab.util.jlToStringRounded
import java.awt.ComponentOrientation
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.util.Locale
import javax.swing.*

class AnimationPrefsDialogSwing(parent: JFrame?) : AnimationPrefsDialog(parent) {
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

    override fun getPrefs(oldPrefs: AnimationPrefs): AnimationPrefs {
        // Fill in UI elements with current prefs
        tfWidth.text = oldPrefs.width.toString()
        tfHeight.text = oldPrefs.height.toString()
        tfFps.text = jlToStringRounded(oldPrefs.fps, 2)
        tfSlowdown.text = jlToStringRounded(oldPrefs.slowdown, 2)
        tfBorder.text = oldPrefs.borderPixels.toString()
        comboShowground.setSelectedIndex(oldPrefs.showGround)
        cbPaused.setSelected(oldPrefs.startPaused)
        cbMousepause.setSelected(oldPrefs.mousePause)
        cbStereo.setSelected(oldPrefs.stereo)
        cbCatchsounds.setSelected(oldPrefs.catchSound)
        cbBouncesounds.setSelected(oldPrefs.bounceSound)

        try {
            // filter out all the explicit settings above to populate the
            // manual settings box
            val pl = ParameterList(oldPrefs.toString())
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
            jlHandleFatalException(
                JuggleExceptionInternal("Anim Prefs Dialog error: " + jeu.message)
            )
        }

        okSelected = false
        isVisible = true // Blocks until user clicks OK or Cancel
        if (okSelected) {
            return readDialogBox(oldPrefs)
        }
        return oldPrefs
    }

    private fun createContents() {
        // panel of text boxes at the top
        val lab1 = JLabel(jlGetStringResource(Res.string.gui_width))
        tfWidth = JTextField(4).apply {
            setHorizontalAlignment(JTextField.CENTER)
        }
        val lab2 = JLabel(jlGetStringResource(Res.string.gui_height))
        tfHeight = JTextField(4).apply {
            setHorizontalAlignment(JTextField.CENTER)
        }
        val lab3 = JLabel(jlGetStringResource(Res.string.gui_frames_per_second))
        tfFps = JTextField(4).apply {
            setHorizontalAlignment(JTextField.CENTER)
        }
        val lab4 = JLabel(jlGetStringResource(Res.string.gui_slowdown_factor))
        tfSlowdown = JTextField(4).apply {
            setHorizontalAlignment(JTextField.CENTER)
        }
        val lab5 = JLabel(jlGetStringResource(Res.string.gui_border__pixels_))
        tfBorder = JTextField(4).apply {
            setHorizontalAlignment(JTextField.CENTER)
        }
        val lab6 = JLabel(jlGetStringResource(Res.string.gui_prefs_show_ground))
        comboShowground = JComboBox<String>().apply {
            addItem(jlGetStringResource(Res.string.gui_prefs_show_ground_auto))
            addItem(jlGetStringResource(Res.string.gui_prefs_show_ground_yes))
            addItem(jlGetStringResource(Res.string.gui_prefs_show_ground_no))
        }
        // checkboxes farther down
        cbPaused = JCheckBox(jlGetStringResource(Res.string.gui_start_paused))
        cbMousepause = JCheckBox(jlGetStringResource(Res.string.gui_pause_on_mouse_away))
        cbStereo = JCheckBox(jlGetStringResource(Res.string.gui_stereo_display))
        cbCatchsounds = JCheckBox(jlGetStringResource(Res.string.gui_catch_sounds))
        cbBouncesounds = JCheckBox(jlGetStringResource(Res.string.gui_bounce_sounds))
        val labOther = JLabel("Manual settings")
        tfOther = JTextField(15)
        // buttons at the bottom
        butCancel = JButton(jlGetStringResource(Res.string.gui_cancel))
        butOk = JButton(jlGetStringResource(Res.string.gui_ok))

        // assemble subpanels
        val gb = GridBagLayout()

        val p1 = JPanel().apply {
            setLayout(gb)
            add(lab1)
            add(tfWidth)
            add(lab2)
            add(tfHeight)
            add(lab3)
            add(tfFps)
            add(lab4)
            add(tfSlowdown)
            add(lab5)
            add(tfBorder)
            add(lab6)
            add(comboShowground)
        }
        gb.setConstraints(
            lab1, constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 3, 0, 0))
        )
        gb.setConstraints(
            tfWidth, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            lab2, constraints(GridBagConstraints.LINE_START, 1, 1, Insets(0, 3, 0, 0))
        )
        gb.setConstraints(
            tfHeight, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            lab3, constraints(GridBagConstraints.LINE_START, 1, 2, Insets(0, 3, 0, 0))
        )
        gb.setConstraints(
            tfFps, constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            lab4, constraints(GridBagConstraints.LINE_START, 1, 3, Insets(0, 3, 0, 0))
        )
        gb.setConstraints(
            tfSlowdown, constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            lab5, constraints(GridBagConstraints.LINE_START, 1, 4, Insets(0, 3, 0, 0))
        )
        gb.setConstraints(
            tfBorder, constraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            lab6, constraints(GridBagConstraints.LINE_START, 1, 5, Insets(0, 3, 0, 0))
        )
        gb.setConstraints(
            comboShowground,
            constraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel().apply {
            setLayout(gb)
            add(butCancel)
            add(butOk)
        }
        gb.setConstraints(
            butCancel, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            butOk, constraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )

        // whole window
        contentPane.apply {
            setLayout(gb)
            add(p1)
            add(cbPaused)
            add(cbMousepause)
            add(cbStereo)
            add(cbCatchsounds)
            add(cbBouncesounds)
            add(labOther)
            add(tfOther)
            add(p2)
        }
        gb.setConstraints(
            p1,
            constraints(GridBagConstraints.LINE_START, 0, 0, Insets(3, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            cbPaused,
            constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            cbMousepause,
            constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            cbStereo,
            constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            cbCatchsounds,
            constraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            cbBouncesounds,
            constraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, BORDER, 8, BORDER))
        )
        gb.setConstraints(
            labOther,
            constraints(GridBagConstraints.LINE_START, 0, 6, Insets(0, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            tfOther,
            constraints(GridBagConstraints.LINE_START, 0, 7, Insets(0, BORDER, 3, BORDER))
        )
        gb.setConstraints(
            p2,
            constraints(GridBagConstraints.LINE_END, 0, 8, Insets(0, BORDER, BORDER, BORDER))
        )

        getRootPane().setDefaultButton(butOk)  // OK button is default

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
        var newjc = oldjc.copy()

        try {
            tempint = tfWidth.getText().toInt()
            if (tempint >= 0) {
                newjc.width = tempint
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(Res.string.error_number_format, "width")
            jlHandleUserException(this@AnimationPrefsDialogSwing, message)
        }
        try {
            tempint = tfHeight.getText().toInt()
            if (tempint >= 0) {
                newjc.height = tempint
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(Res.string.error_number_format, "height")
            jlHandleUserException(this@AnimationPrefsDialogSwing, message)
        }
        try {
            tempdouble = tfFps.getText().toDouble()
            if (tempdouble > 0.0) {
                newjc.fps = tempdouble
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(Res.string.error_number_format, "fps")
            jlHandleUserException(this@AnimationPrefsDialogSwing, message)
        }
        try {
            tempdouble = tfSlowdown.getText().toDouble()
            if (tempdouble > 0.0) {
                newjc.slowdown = tempdouble
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(Res.string.error_number_format, "slowdown")
            jlHandleUserException(this@AnimationPrefsDialogSwing, message)
        }
        try {
            tempint = tfBorder.getText().toInt()
            if (tempint >= 0) {
                newjc.borderPixels = tempint
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(Res.string.error_number_format, "border")
            jlHandleUserException(this@AnimationPrefsDialogSwing, message)
        }

        newjc.showGround = comboShowground.getSelectedIndex()
        newjc.startPaused = cbPaused.isSelected
        newjc.mousePause = cbMousepause.isSelected
        newjc.stereo = cbStereo.isSelected
        newjc.catchSound = cbCatchsounds.isSelected
        newjc.bounceSound = cbBouncesounds.isSelected

        if (!tfOther.getText().trim { it <= ' ' }.isEmpty()) {
            try {
                newjc = AnimationPrefs().fromString(newjc.toString() + ";" + tfOther.getText())
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(this@AnimationPrefsDialogSwing, jeu.message)
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
