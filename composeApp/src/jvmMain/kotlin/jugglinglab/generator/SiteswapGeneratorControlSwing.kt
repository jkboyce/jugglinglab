//
// SiteswapGeneratorControlSwing.kt
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
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import javax.swing.*

internal class SiteswapGeneratorControlSwing : JPanel() {
    private var tf1: JTextField
    private var tf2: JTextField
    private var tf3: JTextField
    private var tf4: JTextField
    private var tf5: JTextField /*tf6,*/
    private var tf7: JTextField /*tf8,*/
    private var tf9: JTextField
    private var cb1: JRadioButton
    private var cb2: JRadioButton /*cb3,*/
    private var cb4: JRadioButton
    private var cb5: JRadioButton
    private var cb6: JRadioButton
    private var cb7: JCheckBox
    private var cb8: JCheckBox
    private var cb9: JCheckBox
    private var cb10: JCheckBox
    private var cb12: JCheckBox
    private var cb13: JCheckBox
    private var cb14: JCheckBox
    private var cb15: JCheckBox
    private var cb16: JCheckBox
    private var cb17: JCheckBox
    private var cb18: JCheckBox
    private var lab1: JLabel
    private var lab2: JLabel /*lab3,*/
    private var lab4: JLabel /*lab5,*/
    private var lab13: JLabel
    private var c1: JComboBox<String?>

    init {
        setOpaque(false)
        val gb = GridBagLayout()
        setLayout(gb)

        val p2 = JPanel()  // top section
        p2.setLayout(gb)
        val lab6 = JLabel(jlGetStringResource(Res.string.gui_balls))
        p2.add(lab6)
        gb.setConstraints(
            lab6, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 3))
        )
        tf1 = JTextField(3)
        p2.add(tf1)
        gb.setConstraints(tf1, jlConstraints(GridBagConstraints.LINE_START, 1, 0))
        val lab7 = JLabel(jlGetStringResource(Res.string.gui_max__throw))
        p2.add(lab7)
        gb.setConstraints(
            lab7, jlConstraints(GridBagConstraints.LINE_END, 2, 0, Insets(0, 15, 0, 3))
        )
        tf2 = JTextField(3)
        p2.add(tf2)
        gb.setConstraints(tf2, jlConstraints(GridBagConstraints.LINE_START, 3, 0))
        val lab8 = JLabel(jlGetStringResource(Res.string.gui_period))
        p2.add(lab8)
        gb.setConstraints(
            lab8, jlConstraints(GridBagConstraints.LINE_END, 4, 0, Insets(0, 15, 0, 3))
        )
        tf3 = JTextField(3)
        p2.add(tf3)
        gb.setConstraints(tf3, jlConstraints(GridBagConstraints.LINE_START, 5, 0))

        val p6 = JPanel() // Jugglers/Rhythm section
        p6.setLayout(gb)
        val lab14 = JLabel(jlGetStringResource(Res.string.gui_jugglers))
        p6.add(lab14)
        gb.setConstraints(lab14, jlConstraints(GridBagConstraints.LINE_START, 0, 0))
        c1 = JComboBox<String?>()
        for (i in 1..6) c1.addItem("$i   ")
        p6.add(c1)
        gb.setConstraints(
            c1, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 10, 0, 0))
        )
        val lab9 = JLabel(jlGetStringResource(Res.string.gui_rhythm))
        p6.add(lab9)
        gb.setConstraints(
            lab9, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(8, 0, 0, 0))
        )
        val bg1 = ButtonGroup()
        cb1 = JRadioButton(jlGetStringResource(Res.string.gui_asynch))
        bg1.add(cb1)
        p6.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 0))
        )
        cb2 = JRadioButton(jlGetStringResource(Res.string.gui_synch))
        bg1.add(cb2)
        p6.add(cb2)
        gb.setConstraints(
            cb2, jlConstraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 10, 0, 0))
        )
        /*
        cb3 = new JRadioButton("passing");
        bg1.add(cb3);
        p6.add(cb3);
        gb.setConstraints(cb3, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 5,
                                                new Insets(0, 10, 0, 0)));
        */
        val p7 = JPanel() // Compositions section
        p7.setLayout(gb)
        val lab10 = JLabel(jlGetStringResource(Res.string.gui_compositions))
        p7.add(lab10)
        gb.setConstraints(
            lab10, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(5, 0, 0, 0))
        )
        val bg2 = ButtonGroup()
        cb5 = JRadioButton(jlGetStringResource(Res.string.gui_all))
        bg2.add(cb5)
        p7.add(cb5)
        gb.setConstraints(
            cb5, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 10, 0, 0))
        )
        cb4 = JRadioButton(jlGetStringResource(Res.string.gui_non_obvious))
        bg2.add(cb4)
        p7.add(cb4)
        gb.setConstraints(
            cb4, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 10, 0, 0))
        )
        cb6 = JRadioButton(jlGetStringResource(Res.string.gui_none__prime_only_))
        bg2.add(cb6)
        p7.add(cb6)
        gb.setConstraints(
            cb6, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 0))
        )

        val p8 = JPanel() // Find section
        p8.setLayout(gb)
        val lab11 = JLabel(jlGetStringResource(Res.string.gui_find))
        p8.add(lab11)
        gb.setConstraints(lab11, jlConstraints(GridBagConstraints.LINE_START, 0, 0))
        cb7 = JCheckBox(jlGetStringResource(Res.string.gui_ground_state_patterns), null)
        p8.add(cb7)
        gb.setConstraints(
            cb7, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 10, 0, 0))
        )
        cb8 = JCheckBox(jlGetStringResource(Res.string.gui_excited_state_patterns), null)
        p8.add(cb8)
        gb.setConstraints(
            cb8, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 10, 0, 0))
        )
        cb9 = JCheckBox(jlGetStringResource(Res.string.gui_transition_throws), null)
        p8.add(cb9)
        gb.setConstraints(
            cb9, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 0))
        )
        cb10 = JCheckBox(jlGetStringResource(Res.string.gui_pattern_rotations), null)
        p8.add(cb10)
        gb.setConstraints(
            cb10, jlConstraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 10, 0, 0))
        )
        cb17 = JCheckBox(jlGetStringResource(Res.string.gui_juggler_permutations), null)
        p8.add(cb17)
        gb.setConstraints(
            cb17, jlConstraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, 10, 0, 0))
        )
        cb15 = JCheckBox(jlGetStringResource(Res.string.gui_connected_patterns), null)
        p8.add(cb15)
        gb.setConstraints(
            cb15, jlConstraints(GridBagConstraints.LINE_START, 0, 6, Insets(0, 10, 0, 0))
        )
        cb18 = JCheckBox(jlGetStringResource(Res.string.gui_symmetric_patterns), null)
        p8.add(cb18)
        gb.setConstraints(
            cb18, jlConstraints(GridBagConstraints.LINE_START, 0, 7, Insets(0, 10, 0, 0))
        )

        val p9 = JPanel()  // Multiplexing section
        p9.setLayout(gb)
        cb12 = JCheckBox(jlGetStringResource(Res.string.gui_multiplexing), null)
        cb12.setHorizontalTextPosition(SwingConstants.LEFT)
        p9.add(cb12)
        gb.setConstraints(
            cb12, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(1, 0, 0, 0))
        )

        val p3 = JPanel()
        p3.setLayout(gb)
        lab13 = JLabel(jlGetStringResource(Res.string.gui_simultaneous_throws))
        p3.add(lab13)
        gb.setConstraints(
            lab13, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 3))
        )
        tf9 = JTextField(3)
        p3.add(tf9)
        gb.setConstraints(tf9, jlConstraints(GridBagConstraints.LINE_START, 1, 0))

        p9.add(p3)
        gb.setConstraints(
            p3, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 15, 0, 0))
        )

        cb13 = JCheckBox(jlGetStringResource(Res.string.gui_no_simultaneous_catches), null)
        p9.add(cb13)
        gb.setConstraints(
            cb13, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 10, 0, 0))
        )

        cb14 = JCheckBox(jlGetStringResource(Res.string.gui_no_clustered_throws), null)
        p9.add(cb14)
        gb.setConstraints(
            cb14, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 0))
        )

        cb16 = JCheckBox(jlGetStringResource(Res.string.gui_true_multiplexing), null)
        p9.add(cb16)
        gb.setConstraints(
            cb16, jlConstraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 10, 0, 0))
        )

        val p4 = JPanel()  // entire middle section
        p4.setLayout(gb)
        p4.add(p6)
        gb.setConstraints(p6, jlConstraints(GridBagConstraints.FIRST_LINE_START, 0, 0))
        p4.add(p7)
        gb.setConstraints(p7, jlConstraints(GridBagConstraints.FIRST_LINE_START, 0, 1))
        p4.add(p8)
        gb.setConstraints(p8, jlConstraints(GridBagConstraints.FIRST_LINE_START, 1, 0))
        p4.add(p9)
        gb.setConstraints(p9, jlConstraints(GridBagConstraints.FIRST_LINE_START, 1, 1))

        val p1 = JPanel()  // bottom section
        p1.setLayout(gb)
        lab1 = JLabel(jlGetStringResource(Res.string.gui_exclude_these_throws))
        p1.add(lab1)
        gb.setConstraints(
            lab1, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 3))
        )
        tf4 = JTextField(10)
        p1.add(tf4)
        gb.setConstraints(tf4, jlConstraints(GridBagConstraints.LINE_START, 1, 0))
        lab2 = JLabel(jlGetStringResource(Res.string.gui_include_these_throws))
        p1.add(lab2)
        gb.setConstraints(
            lab2, jlConstraints(GridBagConstraints.LINE_END, 0, 1, Insets(0, 0, 0, 3))
        )
        tf5 = JTextField(10)
        p1.add(tf5)
        gb.setConstraints(tf5, jlConstraints(GridBagConstraints.LINE_START, 1, 1))
        /*
        tf6 = new JTextField(10);
        p1.add(tf6);
        gb.setConstraints(tf6, JLFunc.constraints(GridBagConstraints.LINE_START, 0, 2));
        */
        lab4 = JLabel(jlGetStringResource(Res.string.gui_passing_communication_delay))
        p1.add(lab4)
        gb.setConstraints(
            lab4, jlConstraints(GridBagConstraints.LINE_END, 0, 2, Insets(3, 0, 0, 3))
        )
        tf7 = JTextField(3)
        p1.add(tf7)
        gb.setConstraints(
            tf7, jlConstraints(GridBagConstraints.LINE_START, 1, 2, Insets(3, 0, 0, 0))
        )
        /*
        tf8 = new JTextField(3);
        p1.add(tf8);
        gb.setConstraints(tf8, JLFunc.constraints(GridBagConstraints.LINE_END, 0, 4));
        lab3 = new JLabel(guistrings.getString("Exclude_these_passes"));
        p1.add(lab3);
        gb.setConstraints(lab3, JLFunc.constraints(GridBagConstraints.LINE_END, 1, 2,
                                                new Insets(0, 0, 0, 3)));
        lab5 = new JLabel("Passing leader slot number");
        p1.add(lab5);
        gb.setConstraints(lab5, JLFunc.constraints(GridBagConstraints.LINE_START, 1, 4,
                                                new Insets(0, 0, 0, 3)));
        */
        add(p2)
        gb.setConstraints(
            p2,
            jlConstraints(GridBagConstraints.CENTER, 0, 0, Insets(BORDER, BORDER, 5, BORDER))
        )
        add(p4)
        gb.setConstraints(
            p4, jlConstraints(GridBagConstraints.CENTER, 0, 1, Insets(5, BORDER, 5, BORDER))
        )
        add(p1)
        gb.setConstraints(
            p1, jlConstraints(GridBagConstraints.CENTER, 0, 2, Insets(5, BORDER, 5, BORDER))
        )

        // add action listeners to enable/disable items depending on context
        c1.addItemListener { _: ItemEvent? ->
            if (c1.getSelectedIndex() > 0) {
                // lab3.setEnabled(true);
                // lab5.setEnabled(true);
                // tf6.setEnabled(true);
                cb15.setEnabled(true)
                cb17.setEnabled(cb7.isSelected && cb8.isSelected)
                cb18.setEnabled(true)
                if (cb7.isSelected && !cb8.isSelected) {
                    lab4.setEnabled(true)
                    tf7.setEnabled(true)
                } else {
                    lab4.setEnabled(false)
                    tf7.setEnabled(false)
                }
                // tf8.setEnabled(true);
                // lab1.setText(guistrings.getString("Exclude_these_self_throws"));
                // lab2.setText(guistrings.getString("Include_these_self_throws"));
            } else {
                // lab3.setEnabled(false);
                // lab5.setEnabled(false);
                // tf6.setEnabled(false);
                cb15.setEnabled(false)
                cb17.setEnabled(false)
                cb18.setEnabled(false)
                lab4.setEnabled(false)
                tf7.setEnabled(false)
                // tf8.setEnabled(false);
                // lab1.setText(guistrings.getString("Exclude_these_throws"));
                // lab2.setText(guistrings.getString("Include_these_throws"));
            }
            // Transfer focus back up so that the run button works
            c1.transferFocus()
        }

        cb12.addItemListener { _: ItemEvent? ->
            val active = cb12.isSelected
            cb13.setEnabled(active)
            cb14.setEnabled(active)
            lab13.setEnabled(active)
            tf9.setEnabled(active)
            cb16.setEnabled(active)
        }

        val temp =
            ActionListener { _: ActionEvent? ->
                if (!cb7.isSelected || cb8.isSelected) {
                    lab4.setEnabled(false)
                    tf7.setEnabled(false)
                } else {
                    if (c1.getSelectedIndex() > 0) {
                        lab4.setEnabled(true)
                        tf7.setEnabled(true)
                    }
                }
                cb17.setEnabled(cb7.isSelected && cb8.isSelected && (c1.getSelectedIndex() > 0))
                cb9.setEnabled(cb8.isSelected)
            }
        cb7.addActionListener(temp)
        cb8.addActionListener(temp)

        resetControl()
    }

    fun resetControl() {
        tf1.text = "5"  // balls
        tf2.text = "7"  // max throw
        tf3.text = "5"  // period
        cb1.setSelected(true)  // asynch mode
        cb5.setSelected(true)  // show all compositions
        cb7.setSelected(true)  // ground state patterns
        cb8.setSelected(true)  // excited state patterns
        cb9.setSelected(false)  // starting/ending sequences
        cb10.setSelected(false)  // pattern rotations
        cb17.setSelected(false)  // juggler permutations
        cb15.setSelected(true)  // connected patterns
        cb18.setSelected(false)  // symmetric patterns
        cb12.setSelected(false)  // multiplexing
        tf9.text = "2"  // number of multiplexed throws
        cb13.setSelected(true)  // no simultaneous catches
        cb14.setSelected(false)  // allow clustered throws
        cb16.setSelected(false)  // true multiplexing
        tf4.text = ""  // excluded throws
        tf5.text = ""  // included throws
        // tf6.setText("");  // excluded passes
        tf7.text = "0" // passing communication delay
        // tf8.setText("1");  // passing leader slot number
        c1.setSelectedIndex(0)  // one juggler

        // lab3.setEnabled(false);
        cb9.setEnabled(true)
        cb17.setEnabled(false)
        cb15.setEnabled(false)
        cb18.setEnabled(false)
        lab4.setEnabled(false)  // passing communication delay
        // lab5.setEnabled(false);
        // tf6.setEnabled(false);
        tf7.setEnabled(false)

        // tf8.setEnabled(false);
        lab13.setEnabled(false)  // number of multiplexed throws label
        tf9.setEnabled(false)
        cb13.setEnabled(false)
        cb14.setEnabled(false)
        cb16.setEnabled(false)
    }

    val params: String
        get() {
            val sb = StringBuilder(256)

            var maxthrow = tf2.getText()
            if (maxthrow.trim { it <= ' ' }.isEmpty()) {
                maxthrow = "-"
            }
            var period = tf3.getText()
            if (period.trim { it <= ' ' }.isEmpty()) {
                period = "-"
            }

            sb.append(tf1.getText()).append(" ").append(maxthrow).append(" ").append(period)

            if (cb2.isSelected) {
                sb.append(" -s")
            }
            val jugglers = c1.getSelectedIndex() + 1
            if (jugglers > 1) {
                sb.append(" -j ").append(jugglers)
                if (tf7.isEnabled && !tf7.getText().isEmpty()) {
                    sb.append(" -d ").append(tf7.getText()).append(" -l 1")
                }

                if (cb17.isEnabled) {
                    if (cb17.isSelected) {
                        sb.append(" -jp")
                    }
                } else sb.append(" -jp")

                if (cb15.isSelected) {
                    sb.append(" -cp")
                }
                if (cb18.isSelected) {
                    sb.append(" -sym")
                }
            }
            if (cb5.isSelected) {
                sb.append(" -f")
            } else if (cb6.isSelected) {
                sb.append(" -prime")
            }
            if (cb7.isSelected && !cb8.isSelected) {
                sb.append(" -g")
            } else if (!cb7.isSelected && cb8.isSelected) {
                sb.append(" -ng")
            }
            if (!cb9.isEnabled || !cb9.isSelected) {
                sb.append(" -se")
            }
            if (cb10.isSelected) {
                sb.append(" -rot")
            }
            if (cb12.isSelected && !tf9.getText().isEmpty()) {
                sb.append(" -m ").append(tf9.getText())
                if (!cb13.isSelected) {
                    sb.append(" -mf")
                }
                if (cb14.isSelected) {
                    sb.append(" -mc")
                }
                if (cb16.isSelected) {
                    sb.append(" -mt")
                }
            }
            if (!tf4.getText().isEmpty()) {
                sb.append(" -x ").append(tf4.getText())
            }
            if (!tf5.getText().isEmpty()) {
                sb.append(" -i ").append(tf5.getText())
            }
            sb.append(" -n")

            return sb.toString()
        }

    companion object {
        const val BORDER: Int = 10
    }
}
