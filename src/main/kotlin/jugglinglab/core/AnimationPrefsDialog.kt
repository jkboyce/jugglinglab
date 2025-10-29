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

import jugglinglab.JugglingLab
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
import java.awt.event.ActionListener
import java.text.MessageFormat
import java.util.*
import javax.swing.*

class AnimationPrefsDialog(parent: JFrame?) : JDialog(parent, guistrings.getString("Animation_Preferences"), true) {
    protected lateinit var tf_width: JTextField
    protected lateinit var tf_height: JTextField
    protected lateinit var tf_fps: JTextField
    protected lateinit var tf_slowdown: JTextField
    protected lateinit var tf_border: JTextField
    protected lateinit var combo_showground: JComboBox<String>
    protected lateinit var cb_paused: JCheckBox
    protected lateinit var cb_mousepause: JCheckBox
    protected lateinit var cb_stereo: JCheckBox
    protected lateinit var cb_catchsounds: JCheckBox
    protected lateinit var cb_bouncesounds: JCheckBox
    protected lateinit var tf_other: JTextField
    protected lateinit var but_cancel: JButton
    protected lateinit var but_ok: JButton

    protected var ok_selected: Boolean = false

    init {
        createContents()
        setLocationRelativeTo(parent)

        but_cancel!!.addActionListener(
            ActionListener { e: ActionEvent? ->
                setVisible(false)
                ok_selected = false
            })

        but_ok!!.addActionListener(
            ActionListener { ae: ActionEvent? ->
                setVisible(false)
                ok_selected = true
            })
    }

    // Show dialog box and return the new preferences.
    fun getPrefs(oldjc: AnimationPrefs): AnimationPrefs {
        // Fill in UI elements with current prefs
        tf_width!!.setText(oldjc.width.toString())
        tf_height!!.setText(oldjc.height.toString())
        tf_fps!!.setText(toStringRounded(oldjc.fps, 2))
        tf_slowdown!!.setText(toStringRounded(oldjc.slowdown, 2))
        tf_border!!.setText(oldjc.border.toString())
        combo_showground!!.setSelectedIndex(oldjc.showGround)
        cb_paused!!.setSelected(oldjc.startPause)
        cb_mousepause!!.setSelected(oldjc.mousePause)
        cb_stereo!!.setSelected(oldjc.stereo)
        cb_catchsounds!!.setSelected(oldjc.catchSound)
        cb_bouncesounds!!.setSelected(oldjc.bounceSound)

        try {
            // filter out all the explicit settings above to populate the
            // manual settings box
            val pl = ParameterList(oldjc.toString())
            val params_remove = arrayOf<String?>(
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
            for (param in params_remove) {
                pl.removeParameter(param!!)
            }
            tf_other!!.setText(pl.toString())
            tf_other!!.setCaretPosition(0)
        } catch (jeu: JuggleExceptionUser) {
            // any error here can't be a user error
            handleFatalException(
                JuggleExceptionInternal("Anim Prefs Dialog error: " + jeu.message)
            )
        }

        ok_selected = false
        setVisible(true) // Blocks until user clicks OK or Cancel

        if (ok_selected) {
            return readDialogBox(oldjc)
        }

        return oldjc
    }

    protected fun createContents() {
        val gb = GridBagLayout()
        getContentPane().setLayout(gb)

        // panel of text boxes at the top
        val p1 = JPanel()
        p1.setLayout(gb)

        val lab1: JLabel = JLabel(guistrings.getString("Width"))
        p1.add(lab1)
        gb.setConstraints(
            lab1, make_constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 3, 0, 0))
        )
        tf_width = JTextField(4)
        tf_width!!.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tf_width)
        gb.setConstraints(
            tf_width, make_constraints(GridBagConstraints.LINE_START, 0, 0, Insets(0, 0, 0, 0))
        )

        val lab2: JLabel = JLabel(guistrings.getString("Height"))
        p1.add(lab2)
        gb.setConstraints(
            lab2, make_constraints(GridBagConstraints.LINE_START, 1, 1, Insets(0, 3, 0, 0))
        )
        tf_height = JTextField(4)
        tf_height!!.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tf_height)
        gb.setConstraints(
            tf_height, make_constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )

        val lab3: JLabel = JLabel(guistrings.getString("Frames_per_second"))
        p1.add(lab3)
        gb.setConstraints(
            lab3, make_constraints(GridBagConstraints.LINE_START, 1, 2, Insets(0, 3, 0, 0))
        )
        tf_fps = JTextField(4)
        tf_fps!!.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tf_fps)
        gb.setConstraints(
            tf_fps, make_constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 0, 0, 0))
        )

        val lab4: JLabel = JLabel(guistrings.getString("Slowdown_factor"))
        p1.add(lab4)
        gb.setConstraints(
            lab4, make_constraints(GridBagConstraints.LINE_START, 1, 3, Insets(0, 3, 0, 0))
        )
        tf_slowdown = JTextField(4)
        tf_slowdown!!.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tf_slowdown)
        gb.setConstraints(
            tf_slowdown, make_constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 0, 0, 0))
        )

        val lab5: JLabel = JLabel(guistrings.getString("Border_(pixels)"))
        p1.add(lab5)
        gb.setConstraints(
            lab5, make_constraints(GridBagConstraints.LINE_START, 1, 4, Insets(0, 3, 0, 0))
        )
        tf_border = JTextField(4)
        tf_border!!.setHorizontalAlignment(JTextField.CENTER)
        p1.add(tf_border)
        gb.setConstraints(
            tf_border, make_constraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 0, 0, 0))
        )

        val lab6: JLabel = JLabel(guistrings.getString("Prefs_show_ground"))
        p1.add(lab6)
        gb.setConstraints(
            lab6, make_constraints(GridBagConstraints.LINE_START, 1, 5, Insets(0, 3, 0, 0))
        )
        combo_showground = JComboBox<String>()
        combo_showground!!.addItem(guistrings.getString("Prefs_show_ground_auto"))
        combo_showground!!.addItem(guistrings.getString("Prefs_show_ground_yes"))
        combo_showground!!.addItem(guistrings.getString("Prefs_show_ground_no"))
        p1.add(combo_showground)
        gb.setConstraints(
            combo_showground,
            make_constraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, 0, 0, 0))
        )

        // checkboxes farther down
        cb_paused = JCheckBox(guistrings.getString("Start_paused"))
        cb_mousepause = JCheckBox(guistrings.getString("Pause_on_mouse_away"))
        cb_stereo = JCheckBox(guistrings.getString("Stereo_display"))
        cb_catchsounds = JCheckBox(guistrings.getString("Catch_sounds"))
        cb_bouncesounds = JCheckBox(guistrings.getString("Bounce_sounds"))

        // manual settings
        val lab_other = JLabel("Manual settings")
        tf_other = JTextField(15)

        // buttons at the bottom
        val p2 = JPanel()
        p2.setLayout(gb)
        but_cancel = JButton(guistrings.getString("Cancel"))

        p2.add(but_cancel)
        gb.setConstraints(
            but_cancel, make_constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        but_ok = JButton(guistrings.getString("OK"))

        p2.add(but_ok)
        gb.setConstraints(
            but_ok, make_constraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )

        // now make the whole window
        getContentPane().add(p1)
        gb.setConstraints(
            p1,
            make_constraints(GridBagConstraints.LINE_START, 0, 0, Insets(3, BORDER, 0, BORDER))
        )

        getContentPane().add(cb_paused)
        gb.setConstraints(
            cb_paused,
            make_constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, BORDER, 0, BORDER))
        )
        getContentPane().add(cb_mousepause)
        gb.setConstraints(
            cb_mousepause,
            make_constraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, BORDER, 0, BORDER))
        )
        getContentPane().add(cb_stereo)
        gb.setConstraints(
            cb_stereo,
            make_constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, BORDER, 0, BORDER))
        )
        getContentPane().add(cb_catchsounds)
        gb.setConstraints(
            cb_catchsounds,
            make_constraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, BORDER, 0, BORDER))
        )
        getContentPane().add(cb_bouncesounds)
        gb.setConstraints(
            cb_bouncesounds,
            make_constraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, BORDER, 8, BORDER))
        )
        getContentPane().add(lab_other)
        gb.setConstraints(
            lab_other,
            make_constraints(GridBagConstraints.LINE_START, 0, 6, Insets(0, BORDER, 0, BORDER))
        )
        getContentPane().add(tf_other)
        gb.setConstraints(
            tf_other,
            make_constraints(GridBagConstraints.LINE_START, 0, 7, Insets(0, BORDER, 3, BORDER))
        )

        getContentPane().add(p2)
        gb.setConstraints(
            p2,
            make_constraints(GridBagConstraints.LINE_END, 0, 8, Insets(0, BORDER, BORDER, BORDER))
        )

        getRootPane().setDefaultButton(but_ok) // OK button is default

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        pack()
        setResizable(false)
    }

    // Read prefs out of UI elements.
    protected fun readDialogBox(oldjc: AnimationPrefs): AnimationPrefs {
        var tempint: Int
        var tempdouble: Double

        // Clone the old preferences so if we get an error we retain as much of
        // it as possible
        var newjc = AnimationPrefs(oldjc)

        try {
            tempint = tf_width!!.getText().toInt()
            if (tempint >= 0) {
                newjc.width = tempint
            }
        } catch (e: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("width")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempint = tf_height!!.getText().toInt()
            if (tempint >= 0) {
                newjc.height = tempint
            }
        } catch (e: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("height")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempdouble = tf_fps!!.getText().toDouble()
            if (tempdouble > 0.0) {
                newjc.fps = tempdouble
            }
        } catch (e: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("fps")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempdouble = tf_slowdown!!.getText().toDouble()
            if (tempdouble > 0.0) {
                newjc.slowdown = tempdouble
            }
        } catch (e: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("slowdown")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }
        try {
            tempint = tf_border!!.getText().toInt()
            if (tempint >= 0) {
                newjc.border = tempint
            }
        } catch (e: NumberFormatException) {
            val template: String = errorstrings.getString("Error_number_format")
            val arguments = arrayOf<Any?>("border")
            handleUserException(this@AnimationPrefsDialog, MessageFormat.format(template, *arguments))
        }

        newjc.showGround = combo_showground!!.getSelectedIndex()
        newjc.startPause = cb_paused!!.isSelected()
        newjc.mousePause = cb_mousepause!!.isSelected()
        newjc.stereo = cb_stereo!!.isSelected()
        newjc.catchSound = cb_catchsounds!!.isSelected()
        newjc.bounceSound = cb_bouncesounds!!.isSelected()

        if (!tf_other!!.getText().trim { it <= ' ' }.isEmpty()) {
            try {
                newjc = AnimationPrefs().fromString(newjc.toString() + ";" + tf_other!!.getText())
            } catch (jeu: JuggleExceptionUser) {
                handleUserException(this@AnimationPrefsDialog, jeu.message)
            }
        }

        return newjc
    }

    companion object {
        val guistrings: ResourceBundle = JugglingLab.guistrings
        val errorstrings: ResourceBundle = JugglingLab.errorstrings
        protected const val BORDER: Int = 10

        protected fun make_constraints(
            location: Int, gridx: Int, gridy: Int, ins: Insets?
        ): GridBagConstraints {
            val gbc = GridBagConstraints()

            gbc.anchor = location
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.gridwidth = 1
            gbc.gridheight = gbc.gridwidth
            gbc.gridx = gridx
            gbc.gridy = gridy
            gbc.insets = ins
            gbc.weighty = 0.0
            gbc.weightx = gbc.weighty
            return gbc
        }
    }
}
