//
// SiteswapTransitionerControlSwing.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.jlConstraints
import jugglinglab.util.jlGetStringResource
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import javax.swing.*

internal class SiteswapTransitionerControlSwing : JPanel() {
    private var tf1: JTextField
    private var tf2: JTextField
    private var tf3: JTextField
    private var cb1: JCheckBox
    private var cb2: JCheckBox
    private var cb3: JCheckBox
    private var lab4: JLabel

    init {
        setOpaque(false)
        val gb = GridBagLayout()
        setLayout(gb)

        val p1 = JPanel() // top section
        p1.setLayout(gb)

        val lab1 = JLabel(jlGetStringResource(Res.string.gui_from_pattern))
        p1.add(lab1)
        gb.setConstraints(
            lab1, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 10, 3))
        )
        tf1 = JTextField(15)
        p1.add(tf1)
        gb.setConstraints(
            tf1, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 0, 10, 0))
        )

        val lab2 = JLabel(jlGetStringResource(Res.string.gui_to_pattern))
        p1.add(lab2)
        gb.setConstraints(
            lab2, jlConstraints(GridBagConstraints.LINE_END, 0, 1, Insets(0, 0, 10, 3))
        )
        tf2 = JTextField(15)
        p1.add(tf2)
        gb.setConstraints(
            tf2, jlConstraints(GridBagConstraints.LINE_START, 1, 1, Insets(0, 0, 10, 0))
        )

        val but1 = JButton("\u2195")
        but1.addActionListener { _: ActionEvent? ->
            val temp = tf1.getText()
            tf1.text = tf2.getText()
            tf2.text = temp
        }
        p1.add(but1)
        gb.setConstraints(but1, jlConstraints(GridBagConstraints.LINE_START, 1, 2))

        val p2 = JPanel() // multiplexing section
        p2.setLayout(gb)

        cb1 = JCheckBox(jlGetStringResource(Res.string.gui_multiplexing_in_transitions), null)
        p2.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(5, 0, 0, 0))
        )

        val p3 = JPanel()
        p3.setLayout(gb)
        tf3 = JTextField(3)
        p3.add(tf3)
        gb.setConstraints(tf3, jlConstraints(GridBagConstraints.LINE_START, 1, 0))
        lab4 = JLabel(jlGetStringResource(Res.string.gui_simultaneous_throws))
        p3.add(lab4)
        gb.setConstraints(
            lab4, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 3))
        )

        p2.add(p3)
        gb.setConstraints(
            p3, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 30, 0, 0))
        )

        cb2 = JCheckBox(jlGetStringResource(Res.string.gui_no_simultaneous_catches), null)
        p2.add(cb2)
        gb.setConstraints(
            cb2, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 25, 0, 0))
        )

        cb3 = JCheckBox(jlGetStringResource(Res.string.gui_no_clustered_throws), null)
        p2.add(cb3)
        gb.setConstraints(
            cb3, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 25, 0, 0))
        )

        val p4 = JPanel() // left justify top and multiplexing parts
        p4.setLayout(gb)
        p4.add(p1)
        gb.setConstraints(
            p1,
            jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(30, BORDER, 5, BORDER))
        )
        p4.add(p2)
        gb.setConstraints(
            p2,
            jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(20, BORDER, 5, BORDER))
        )

        add(p4) // the whole panel
        gb.setConstraints(p4, jlConstraints(GridBagConstraints.CENTER, 0, 0))

        // add action listeners to enable/disable items depending on context
        cb1.addItemListener { _: ItemEvent? ->
            val active = cb1.isSelected
            cb2.setEnabled(active)
            cb3.setEnabled(active)
            lab4.setEnabled(active)
            tf3.setEnabled(active)
        }

        resetControl() // apply defaults
    }

    fun resetControl() {
        tf1.text = "" // from pattern
        tf2.text = "" // to pattern
        cb1.setSelected(false) // multiplexing
        tf3.text = "2" // number multiplexed throws
        cb2.setSelected(true) // no simultaneous catches
        cb3.setSelected(false) // allow clustered throws

        cb2.setEnabled(false) // multiplexing off
        cb3.setEnabled(false)
        lab4.setEnabled(false)
        tf3.setEnabled(false)
    }

    val params: String
        get() {
            val sb = StringBuilder(256)

            var fromPattern = tf1.getText()
            if (fromPattern.trim { it <= ' ' }.isEmpty()) {
                fromPattern = "-"
            }
            var toPattern = tf2.getText()
            if (toPattern.trim { it <= ' ' }.isEmpty()) {
                toPattern = "-"
            }
            sb.append(fromPattern).append(" ").append(toPattern)

            if (cb1.isSelected && !tf3.getText().isEmpty()) {
                sb.append(" -m ").append(tf3.getText())
                if (!cb2.isSelected) {
                    sb.append(" -mf")
                }
                if (cb3.isSelected) {
                    sb.append(" -mc")
                }
            }

            return sb.toString()
        }

    companion object {
        const val BORDER: Int = 10
    }
}
