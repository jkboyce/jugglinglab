//
// MHNNotationControl.kt
//
// This class is abstract because MHNPattern is abstract; there is no way to
// implement newPattern(). The UI panel created here is inherited by
// SiteswapNotationControl and it may be useful for other notations as well.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.JugglingLab.guistrings
import jugglinglab.prop.Prop
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.constraints
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.util.Locale
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

abstract class MHNNotationControl : NotationControl() {
    // text fields in control panel
    protected var tf1: JTextField
    protected var tf2: JTextField
    protected var tf3: JTextField
    protected var tf4: JTextField
    protected var tf5: JTextField
    protected var tf6: JTextField
    protected var cb1: JComboBox<String?>
    protected var cb2: JComboBox<String?>
    protected var cb3: JComboBox<String?>
    protected var cb1Selected: Boolean = false
    protected var cb2Selected: Boolean = false

    init {
        this.setOpaque(false)
        this.setLayout(BorderLayout())

        val p1 = JPanel()
        val gb = GridBagLayout()
        p1.setLayout(gb)

        val lab1 = JLabel(guistrings.getString("Pattern"))
        p1.add(lab1)
        gb.setConstraints(
            lab1, constraints(
                GridBagConstraints.LINE_END, 0, 0, Insets(BORDER, BORDER, 0, HSPACING)
            )
        )
        tf1 = JTextField(15)
        p1.add(tf1)
        gb.setConstraints(
            tf1, constraints(
                GridBagConstraints.LINE_START, 1, 0, Insets(BORDER, 0, 0, BORDER)
            )
        )
        val lab3 = JLabel(guistrings.getString("Beats_per_second"))
        p1.add(lab3)
        gb.setConstraints(
            lab3, constraints(
                GridBagConstraints.LINE_END, 0, 1, Insets(2 * VSPACING, BORDER, 0, HSPACING)
            )
        )
        tf3 = JTextField(4)
        p1.add(tf3)
        gb.setConstraints(
            tf3, constraints(
                GridBagConstraints.LINE_START, 1, 1, Insets(2 * VSPACING, 0, 0, BORDER)
            )
        )
        val lab2 = JLabel(guistrings.getString("Dwell_beats"))
        p1.add(lab2)
        gb.setConstraints(
            lab2, constraints(
                GridBagConstraints.LINE_END, 0, 2, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        tf2 = JTextField(4)
        p1.add(tf2)
        gb.setConstraints(
            tf2, constraints(
                GridBagConstraints.LINE_START, 1, 2, Insets(VSPACING, 0, 0, BORDER)
            )
        )

        val lab4 = JLabel(guistrings.getString("Hand_movement"))
        p1.add(lab4)
        gb.setConstraints(
            lab4, constraints(
                GridBagConstraints.LINE_END, 0, 3, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        cb1 = JComboBox<String?>()
        cb1.addItem(guistrings.getString("MHNHands_name_default"))
        for (builtinHandsName in builtinHandsNames) {
            val item = "MHNHands_name_$builtinHandsName"
            cb1.addItem(guistrings.getString(item))
        }
        cb1.addItem(guistrings.getString("MHNHands_name_custom"))
        p1.add(cb1)
        gb.setConstraints(
            cb1, constraints(
                GridBagConstraints.LINE_START, 1, 3, Insets(VSPACING, 0, 0, BORDER)
            )
        )
        tf4 = JTextField(15)
        p1.add(tf4)
        gb.setConstraints(
            tf4, constraints(
                GridBagConstraints.LINE_START, 1, 4, Insets(5, 0, 0, BORDER)
            )
        )
        cb1.addActionListener { _: ActionEvent? ->
            val index = cb1.getSelectedIndex()
            cb1Selected = true

            // System.out.println("Selected item number "+index);
            when (index) {
                0 -> {
                    tf4.text = ""
                    tf4.setEnabled(false)
                }

                (builtinHandsNames.size + 1) -> {
                    tf4.setEnabled(true)
                }

                else -> {
                    tf4.text = builtinHandsStrings[index - 1]
                    tf4.setCaretPosition(0)
                    tf4.setEnabled(true)
                }
            }
        }
        tf4.document
            .addDocumentListener(
                object : DocumentListener {
                    override fun changedUpdate(de: DocumentEvent?) {}

                    override fun insertUpdate(de: DocumentEvent?) {
                        if (!cb1Selected) {
                            cb1.setSelectedIndex(builtinHandsNames.size + 1)
                        }
                        cb1Selected = false
                    }

                    override fun removeUpdate(de: DocumentEvent?) {
                        if (!cb1Selected) {
                            cb1.setSelectedIndex(builtinHandsNames.size + 1)
                        }
                    }
                })

        val lab5 = JLabel(guistrings.getString("Body_movement"))
        p1.add(lab5)
        gb.setConstraints(
            lab5, constraints(
                GridBagConstraints.LINE_END, 0, 5, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        cb2 = JComboBox<String?>()
        cb2.addItem(guistrings.getString("MHNBody_name_default"))
        for (builtinBodyName in builtinBodyNames) {
            val item = "MHNBody_name_$builtinBodyName"
            cb2.addItem(guistrings.getString(item))
        }
        cb2.addItem(guistrings.getString("MHNBody_name_custom"))
        p1.add(cb2)
        gb.setConstraints(
            cb2, constraints(
                GridBagConstraints.LINE_START, 1, 5, Insets(VSPACING, 0, 0, BORDER)
            )
        )
        tf5 = JTextField(15)
        p1.add(tf5)
        gb.setConstraints(
            tf5, constraints(
                GridBagConstraints.LINE_START, 1, 6, Insets(5, 0, 0, BORDER)
            )
        )
        cb2.addActionListener { _: ActionEvent? ->
            val index = cb2.getSelectedIndex()
            cb2Selected = true

            // System.out.println("Selected item number "+index);
            when (index) {
                0 -> {
                    tf5.text = ""
                    tf5.setEnabled(false)
                }

                (builtinBodyNames.size + 1) -> {
                    tf5.setEnabled(true)
                }

                else -> {
                    tf5.text = builtinBodyStrings[index - 1]
                    tf5.setCaretPosition(0)
                    tf5.setEnabled(true)
                }
            }
        }
        tf5.document
            .addDocumentListener(
                object : DocumentListener {
                    override fun changedUpdate(de: DocumentEvent?) {}

                    override fun insertUpdate(de: DocumentEvent?) {
                        if (!cb2Selected) {
                            cb2.setSelectedIndex(builtinBodyNames.size + 1)
                        }
                        cb2Selected = false
                    }

                    override fun removeUpdate(de: DocumentEvent?) {
                        if (!cb2Selected) {
                            cb2.setSelectedIndex(builtinBodyNames.size + 1)
                        }
                    }
                })

        val propLabel = JLabel(guistrings.getString("Prop_type"))
        p1.add(propLabel)
        gb.setConstraints(
            propLabel, constraints(
                GridBagConstraints.LINE_END, 0, 7, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        cb3 = JComboBox<String?>()
        for (i in Prop.builtinProps.indices) {
            val item = "Prop_name_" + Prop.builtinProps[i].lowercase(Locale.getDefault())
            cb3.addItem(guistrings.getString(item))
        }
        p1.add(cb3)
        gb.setConstraints(
            cb3, constraints(
                GridBagConstraints.LINE_START, 1, 7, Insets(VSPACING, 0, 0, BORDER)
            )
        )

        val lab6 = JLabel(guistrings.getString("Manual_settings"))
        p1.add(lab6)
        gb.setConstraints(
            lab6, constraints(
                GridBagConstraints.LINE_START, 0, 8, Insets(2 * VSPACING, BORDER, 0, HSPACING)
            )
        )
        tf6 = JTextField(25)
        p1.add(tf6)
        val gbc6: GridBagConstraints = constraints(
            GridBagConstraints.LINE_END, 0, 9, Insets(5, BORDER + HSPACING, 0, BORDER)
        )
        gbc6.gridwidth = 2
        gb.setConstraints(tf6, gbc6)

        this.resetControl()
        this.add(p1, BorderLayout.PAGE_START)
    }

    override fun resetControl() {
        tf1.text = "3" // pattern
        tf2.text = jlToStringRounded(MHNPattern.DWELL_DEFAULT, 2) // dwell beats
        tf3.text = "" // beats per second
        tf4.text = ""
        cb1.setSelectedIndex(0)
        tf5.text = ""
        cb2.setSelectedIndex(0)
        tf6.text = ""
        cb3.setSelectedIndex(0)
    }

    @get:Throws(JuggleExceptionUser::class)
    override val parameterList: ParameterList
        get() {
            val sb = StringBuilder(256)
            sb.append("pattern=")
            sb.append(tf1.getText())
            if (cb3.getSelectedIndex() != 0) sb.append(";prop=")
                .append(Prop.builtinProps[cb3.getSelectedIndex()].lowercase(Locale.getDefault()))
            if (!tf2.getText().isEmpty()) {
                if (tf2.getText() != jlToStringRounded(MHNPattern.DWELL_DEFAULT, 2)) {
                    sb.append(";dwell=")
                    sb.append(tf2.getText())
                }
            }
            if (!tf3.getText().isEmpty()) {
                sb.append(";bps=")
                sb.append(tf3.getText())
            }
            if (!tf4.getText().isEmpty()) {
                sb.append(";hands=")
                sb.append(tf4.getText())
            }
            if (!tf5.getText().isEmpty()) {
                sb.append(";body=")
                sb.append(tf5.getText())
            }
            if (!tf6.getText().isEmpty()) {
                sb.append(";")
                sb.append(tf6.getText())
            }

            val pl = ParameterList(sb.toString())

            // check if we want to add a non-default title
            if (pl.getParameter("title") == null) {
                val hss = pl.getParameter("hss")
                val handsIndex = cb1.getSelectedIndex()
                val bodyIndex = cb2.getSelectedIndex()

                if (hss != null) {
                    val title = "oss: " + pl.getParameter("pattern") + "  hss: " + hss
                    pl.addParameter("title", title)
                } else if (handsIndex > 0) {
                    // if hands are not default, apply a title
                    val title = pl.getParameter("pattern") + " " + cb1.getItemAt(handsIndex)
                    pl.addParameter("title", title)
                } else if (bodyIndex > 0) {
                    // if body movement is not default, apply a title
                    val title = pl.getParameter("pattern") + " " + cb2.getItemAt(bodyIndex)
                    pl.addParameter("title", title)
                }
            }

            return pl
        }

    companion object {
        protected val builtinHandsNames: List<String> = listOf(
            "inside",
            "outside",
            "half",
            "Mills",
        )
        protected val builtinHandsStrings: List<String> = listOf(
            "(10)(32.5).",
            "(32.5)(10).",
            "(32.5)(10).(10)(32.5).",
            "(-25)(2.5).(25)(-2.5).(-25)(0).",
        )

        protected val builtinBodyNames: List<String> = listOf(
            "line",
            "feed",
            "backtoback",
            "sidetoside",
            "circles",
        )
        protected val builtinBodyStrings: List<String> = listOf(
            "<(90).|(270,-125).|(90,125).|(270,-250).|(90,250).|(270,-375).>",
            "<(90,75).|(270,-75,50).|(270,-75,-50).|(270,-75,150).|(270,-75,-150).>",
            "<(270,35).|(90,-35).|(0,0,35).|(180,0,-35).>",
            "<(0).|(0,100).|(0,-100).|(0,200).|(0,-200).|(0,300).>",
            "(0,75,0)...(90,0,75)...(180,-75,0)...(270,0,-75)...",
        )

        protected const val BORDER: Int = 10
        protected const val HSPACING: Int = 5
        protected const val VSPACING: Int = 12
    }
}
