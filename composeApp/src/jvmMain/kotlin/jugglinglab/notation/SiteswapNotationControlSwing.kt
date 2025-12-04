//
// SiteswapNotationControlSwing.kt
//
// Swing UI version of the Siteswap notation control.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.prop.Prop
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.jlConstraints
import jugglinglab.util.getStringResource
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.Locale
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SiteswapNotationControlSwing: JPanel() {
    var tf1: JTextField
    var tf2: JTextField
    var tf3: JTextField
    var tf4: JTextField
    var tf5: JTextField
    var tf6: JTextField
    var cb1: JComboBox<String?>
    var cb2: JComboBox<String?>
    var cb3: JComboBox<String?>

    init {
        this.setOpaque(false)
        this.setLayout(BorderLayout())

        val p1 = JPanel()
        val gb = GridBagLayout()
        p1.setLayout(gb)

        val lab1 = JLabel(getStringResource(Res.string.gui_pattern))
        p1.add(lab1)
        gb.setConstraints(
            lab1, jlConstraints(
                GridBagConstraints.LINE_END, 0, 0, Insets(BORDER, BORDER, 0, HSPACING)
            )
        )
        tf1 = JTextField(15)
        p1.add(tf1)
        gb.setConstraints(
            tf1, jlConstraints(
                GridBagConstraints.LINE_START, 1, 0, Insets(BORDER, 0, 0, BORDER)
            )
        )
        val lab3 = JLabel(getStringResource(Res.string.gui_beats_per_second))
        p1.add(lab3)
        gb.setConstraints(
            lab3, jlConstraints(
                GridBagConstraints.LINE_END, 0, 1, Insets(2 * VSPACING, BORDER, 0, HSPACING)
            )
        )
        tf3 = JTextField(4)
        p1.add(tf3)
        gb.setConstraints(
            tf3, jlConstraints(
                GridBagConstraints.LINE_START, 1, 1, Insets(2 * VSPACING, 0, 0, BORDER)
            )
        )
        val lab2 = JLabel(getStringResource(Res.string.gui_dwell_beats))
        p1.add(lab2)
        gb.setConstraints(
            lab2, jlConstraints(
                GridBagConstraints.LINE_END, 0, 2, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        tf2 = JTextField(4)
        p1.add(tf2)
        gb.setConstraints(
            tf2, jlConstraints(
                GridBagConstraints.LINE_START, 1, 2, Insets(VSPACING, 0, 0, BORDER)
            )
        )

        val lab4 = JLabel(getStringResource(Res.string.gui_hand_movement))
        p1.add(lab4)
        gb.setConstraints(
            lab4, jlConstraints(
                GridBagConstraints.LINE_END, 0, 3, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        cb1 = JComboBox<String?>()
        cb1.addItem(getStringResource(Res.string.gui_mhnhands_name_default))
        for (res in builtinHandsStringResources) {
            cb1.addItem(getStringResource(res))
        }
        cb1.addItem(getStringResource(Res.string.gui_mhnhands_name_custom))
        p1.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(
                GridBagConstraints.LINE_START, 1, 3, Insets(VSPACING, 0, 0, BORDER)
            )
        )
        tf4 = JTextField(15)
        p1.add(tf4)
        gb.setConstraints(
            tf4, jlConstraints(
                GridBagConstraints.LINE_START, 1, 4, Insets(5, 0, 0, BORDER)
            )
        )
        cb1.addActionListener {
            when (val index = cb1.selectedIndex) {
                0 -> {
                    tf4.programmaticChange { text = "" }
                    tf4.repaint()
                }

                (builtinHandsNames.size + 1) -> {}

                else -> {
                    tf4.programmaticChange {
                        text = builtinHandsStrings[index - 1]
                        caretPosition = 0
                    }
                    tf4.repaint()
                }
            }
        }
        tf4.document.addDocumentListener(object : DocumentListener {
            override fun changedUpdate(de: DocumentEvent?) {}
            override fun insertUpdate(de: DocumentEvent?) = handleUpdate()
            override fun removeUpdate(de: DocumentEvent?) = handleUpdate()
            fun handleUpdate() {
                // set to "default" or "custom"
                cb1.selectedIndex = if (tf4.text == "") 0 else (builtinHandsNames.size + 1)
            }
        })

        val lab5 = JLabel(getStringResource(Res.string.gui_body_movement))
        p1.add(lab5)
        gb.setConstraints(
            lab5, jlConstraints(
                GridBagConstraints.LINE_END, 0, 5, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        cb2 = JComboBox<String?>()
        cb2.addItem(getStringResource(Res.string.gui_mhnbody_name_default))
        for (res in builtinBodyStringResources) {
            cb2.addItem(getStringResource(res))
        }
        cb2.addItem(getStringResource(Res.string.gui_mhnbody_name_custom))
        p1.add(cb2)
        gb.setConstraints(
            cb2, jlConstraints(
                GridBagConstraints.LINE_START, 1, 5, Insets(VSPACING, 0, 0, BORDER)
            )
        )
        tf5 = JTextField(15)
        p1.add(tf5)
        gb.setConstraints(
            tf5, jlConstraints(
                GridBagConstraints.LINE_START, 1, 6, Insets(5, 0, 0, BORDER)
            )
        )
        cb2.addActionListener {
            when (val index = cb2.selectedIndex) {
                0 -> {
                    tf5.programmaticChange { text = "" }
                    tf5.repaint()
                }

                (builtinBodyNames.size + 1) -> {}

                else -> {
                    tf5.programmaticChange {
                        text = builtinBodyStrings[index - 1]
                        caretPosition = 0
                    }
                    tf5.repaint()
                }
            }
        }
        tf5.document.addDocumentListener(object : DocumentListener {
            override fun changedUpdate(e: DocumentEvent?) {}
            override fun insertUpdate(e: DocumentEvent?) = handleUpdate()
            override fun removeUpdate(e: DocumentEvent?) = handleUpdate()
            fun handleUpdate() {
                cb2.selectedIndex = if (tf5.text == "") 0 else (builtinBodyNames.size + 1)
            }
        })

        val propLabel = JLabel(getStringResource(Res.string.gui_prop_type))
        p1.add(propLabel)
        gb.setConstraints(
            propLabel, jlConstraints(
                GridBagConstraints.LINE_END, 0, 7, Insets(VSPACING, BORDER, 0, HSPACING)
            )
        )
        cb3 = JComboBox<String?>()
        for (res in Prop.builtinPropsStringResources) {
            cb3.addItem(getStringResource(res))
        }
        p1.add(cb3)
        gb.setConstraints(
            cb3, jlConstraints(
                GridBagConstraints.LINE_START, 1, 7, Insets(VSPACING, 0, 0, BORDER)
            )
        )

        val lab6 = JLabel(getStringResource(Res.string.gui_manual_settings))
        p1.add(lab6)
        gb.setConstraints(
            lab6, jlConstraints(
                GridBagConstraints.LINE_START, 0, 8, Insets(2 * VSPACING, BORDER, 0, HSPACING)
            )
        )
        tf6 = JTextField(25)
        p1.add(tf6)
        val gbc6: GridBagConstraints = jlConstraints(
            GridBagConstraints.LINE_END, 0, 9, Insets(5, BORDER + HSPACING, 0, BORDER)
        )
        gbc6.gridwidth = 2
        gb.setConstraints(tf6, gbc6)

        this.resetControl()
        this.add(p1, BorderLayout.PAGE_START)
    }

    // Execute a block of code on a JTextField without triggering its DocumentListeners.

    private fun JTextField.programmaticChange(action: JTextField.() -> Unit) {
        // The getDocumentListeners() method is on AbstractDocument, not the Document interface.
        val doc = document as? javax.swing.text.AbstractDocument
        val listeners = doc?.documentListeners ?: emptyArray()
        listeners.forEach { doc?.removeDocumentListener(it) }
        try {
            this.action()
        } finally {
            listeners.forEach { doc?.addDocumentListener(it) }
        }
    }

    fun newPattern(): Pattern {
        return SiteswapPattern()
    }

    fun resetControl() {
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
    val parameterList: ParameterList
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
        const val BORDER: Int = 10
        const val HSPACING: Int = 5
        const val VSPACING: Int = 12
    }
}
