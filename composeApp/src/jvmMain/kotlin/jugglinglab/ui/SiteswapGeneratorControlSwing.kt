//
// SiteswapGeneratorControlSwing.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

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
    private var tf5: JTextField
    private var tf7: JTextField

    // Filters
    private var cb7: JCheckBox
    private var cb8: JCheckBox
    private var cb9: JCheckBox
    private var cb10: JCheckBox

    // Passing Filters
    private var cb15: JCheckBox
    private var cb17: JCheckBox
    private var cb18: JCheckBox

    // Multiplexing Filters
    private var cb13: JCheckBox
    private var cb14: JCheckBox
    private var cb16: JCheckBox

    private var lab1: JLabel
    private var lab2: JLabel
    private var lab4: JLabel

    // ComboBoxes on left
    private var comboJugglers: JComboBox<String>
    private var comboRhythm: JComboBox<String>
    private var comboMultiplexing: JComboBox<String>
    private var comboCompositions: JComboBox<String>

    init {
        setOpaque(false)
        val gb = GridBagLayout()
        setLayout(gb)

        // --- Top Section: Balls, Max Throw, Period ---
        val pTop = JPanel()
        pTop.setLayout(gb)

        val labBalls = JLabel(jlGetStringResource(Res.string.gui_balls))
        pTop.add(labBalls)
        gb.setConstraints(labBalls, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 3)))

        tf1 = JTextField(3)
        pTop.add(tf1)
        gb.setConstraints(tf1, jlConstraints(GridBagConstraints.LINE_START, 1, 0))

        val labMaxThrow = JLabel(jlGetStringResource(Res.string.gui_max__throw))
        pTop.add(labMaxThrow)
        gb.setConstraints(labMaxThrow, jlConstraints(GridBagConstraints.LINE_END, 2, 0, Insets(0, 15, 0, 3)))

        tf2 = JTextField(3)
        pTop.add(tf2)
        gb.setConstraints(tf2, jlConstraints(GridBagConstraints.LINE_START, 3, 0))

        val labPeriod = JLabel(jlGetStringResource(Res.string.gui_period))
        pTop.add(labPeriod)
        gb.setConstraints(labPeriod, jlConstraints(GridBagConstraints.LINE_END, 4, 0, Insets(0, 15, 0, 3)))

        tf3 = JTextField(3)
        pTop.add(tf3)
        gb.setConstraints(tf3, jlConstraints(GridBagConstraints.LINE_START, 5, 0))

        add(pTop)
        gb.setConstraints(pTop, jlConstraints(GridBagConstraints.CENTER, 0, 0, Insets(BORDER, BORDER, 5, BORDER)))

        // --- Main Section: Settings (Left) & Filters (Right) ---
        val pMain = JPanel()
        pMain.setLayout(gb)

        // Left Column: Settings
        val pLeft = JPanel()
        pLeft.setLayout(gb)

        // Jugglers
        val labJugglers = JLabel(jlGetStringResource(Res.string.gui_jugglers))
        pLeft.add(labJugglers)
        gb.setConstraints(labJugglers, jlConstraints(GridBagConstraints.LINE_START, 0, 0))

        comboJugglers = JComboBox()
        for (i in 1..6) comboJugglers.addItem("$i")
        pLeft.add(comboJugglers)
        val cJugglers = jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 10, 0, 0))
        cJugglers.fill = GridBagConstraints.HORIZONTAL
        gb.setConstraints(comboJugglers, cJugglers)

        // Rhythm
        val labRhythm = JLabel(jlGetStringResource(Res.string.gui_rhythm))
        pLeft.add(labRhythm)
        gb.setConstraints(labRhythm, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(8, 0, 0, 0)))

        comboRhythm = JComboBox()
        comboRhythm.addItem(jlGetStringResource(Res.string.gui_asynch))
        comboRhythm.addItem(jlGetStringResource(Res.string.gui_synch))
        pLeft.add(comboRhythm)
        val cRhythm = jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 0))
        cRhythm.fill = GridBagConstraints.HORIZONTAL
        gb.setConstraints(comboRhythm, cRhythm)

        // Multiplexing
        val labMultiplexing = JLabel(jlGetStringResource(Res.string.gui_multiplexing))
        pLeft.add(labMultiplexing)
        gb.setConstraints(labMultiplexing, jlConstraints(GridBagConstraints.LINE_START, 0, 4, Insets(8, 0, 0, 0)))

        comboMultiplexing = JComboBox()
        comboMultiplexing.addItem(jlGetStringResource(Res.string.gui_multiplexing_none))
        comboMultiplexing.addItem("2")
        comboMultiplexing.addItem("3")
        comboMultiplexing.addItem("4")
        pLeft.add(comboMultiplexing)
        val cMultiplexing = jlConstraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, 10, 0, 0))
        cMultiplexing.fill = GridBagConstraints.HORIZONTAL
        gb.setConstraints(comboMultiplexing, cMultiplexing)

        // Compositions
        val labCompositions = JLabel(jlGetStringResource(Res.string.gui_compositions))
        pLeft.add(labCompositions)
        gb.setConstraints(labCompositions, jlConstraints(GridBagConstraints.LINE_START, 0, 6, Insets(8, 0, 0, 0)))

        comboCompositions = JComboBox()
        comboCompositions.addItem(jlGetStringResource(Res.string.gui_all))
        comboCompositions.addItem(jlGetStringResource(Res.string.gui_non_obvious))
        comboCompositions.addItem(jlGetStringResource(Res.string.gui_none__prime_only_))
        pLeft.add(comboCompositions)
        val cCompositions = jlConstraints(GridBagConstraints.LINE_START, 0, 7, Insets(0, 10, 0, 0))
        cCompositions.fill = GridBagConstraints.HORIZONTAL
        gb.setConstraints(comboCompositions, cCompositions)

        pMain.add(pLeft)
        // Reduced right inset from 20 to 5 to reduce space between columns
        gb.setConstraints(pLeft, jlConstraints(GridBagConstraints.FIRST_LINE_START, 0, 0, Insets(0, 0, 0, 5)))

        // Right Column: Filters
        val pRight = JPanel()
        pRight.setLayout(gb)

        val labFind = JLabel(jlGetStringResource(Res.string.gui_find))
        pRight.add(labFind)
        gb.setConstraints(labFind, jlConstraints(GridBagConstraints.LINE_START, 0, 0))

        cb7 = JCheckBox(jlGetStringResource(Res.string.gui_ground_state_patterns), null)
        pRight.add(cb7)
        gb.setConstraints(cb7, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 10, 0, 0)))

        cb8 = JCheckBox(jlGetStringResource(Res.string.gui_excited_state_patterns), null)
        pRight.add(cb8)
        gb.setConstraints(cb8, jlConstraints(GridBagConstraints.LINE_START, 0, 2, Insets(0, 10, 0, 0)))

        cb9 = JCheckBox(jlGetStringResource(Res.string.gui_transition_throws), null)
        pRight.add(cb9)
        gb.setConstraints(cb9, jlConstraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 10, 0, 0)))

        cb10 = JCheckBox(jlGetStringResource(Res.string.gui_pattern_rotations), null)
        pRight.add(cb10)
        gb.setConstraints(cb10, jlConstraints(GridBagConstraints.LINE_START, 0, 4, Insets(0, 10, 0, 0)))

        // Passing specific filters
        cb17 = JCheckBox(jlGetStringResource(Res.string.gui_juggler_permutations), null)
        pRight.add(cb17)
        gb.setConstraints(cb17, jlConstraints(GridBagConstraints.LINE_START, 0, 5, Insets(0, 10, 0, 0)))

        cb15 = JCheckBox(jlGetStringResource(Res.string.gui_connected_patterns), null)
        pRight.add(cb15)
        gb.setConstraints(cb15, jlConstraints(GridBagConstraints.LINE_START, 0, 6, Insets(0, 10, 0, 0)))

        cb18 = JCheckBox(jlGetStringResource(Res.string.gui_symmetric_patterns), null)
        pRight.add(cb18)
        gb.setConstraints(cb18, jlConstraints(GridBagConstraints.LINE_START, 0, 7, Insets(0, 10, 0, 0)))

        // Multiplexing specific filters
        cb13 = JCheckBox(jlGetStringResource(Res.string.gui_no_simultaneous_catches), null)
        pRight.add(cb13)
        gb.setConstraints(cb13, jlConstraints(GridBagConstraints.LINE_START, 0, 8, Insets(0, 10, 0, 0)))

        cb14 = JCheckBox(jlGetStringResource(Res.string.gui_no_clustered_throws), null)
        pRight.add(cb14)
        gb.setConstraints(cb14, jlConstraints(GridBagConstraints.LINE_START, 0, 9, Insets(0, 10, 0, 0)))

        cb16 = JCheckBox(jlGetStringResource(Res.string.gui_true_multiplexing), null)
        pRight.add(cb16)
        gb.setConstraints(cb16, jlConstraints(GridBagConstraints.LINE_START, 0, 10, Insets(0, 10, 0, 0)))

        pMain.add(pRight)
        gb.setConstraints(pRight, jlConstraints(GridBagConstraints.FIRST_LINE_START, 1, 0))

        add(pMain)
        gb.setConstraints(pMain, jlConstraints(GridBagConstraints.CENTER, 0, 1, Insets(5, BORDER, 5, BORDER)))

        // --- Bottom Section ---
        val pBottom = JPanel()
        pBottom.setLayout(gb)

        lab1 = JLabel(jlGetStringResource(Res.string.gui_exclude_these_throws))
        pBottom.add(lab1)
        gb.setConstraints(lab1, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 3)))

        tf4 = JTextField(10)
        pBottom.add(tf4)
        gb.setConstraints(tf4, jlConstraints(GridBagConstraints.LINE_START, 1, 0))

        lab2 = JLabel(jlGetStringResource(Res.string.gui_include_these_throws))
        pBottom.add(lab2)
        gb.setConstraints(lab2, jlConstraints(GridBagConstraints.LINE_END, 0, 1, Insets(0, 0, 0, 3)))

        tf5 = JTextField(10)
        pBottom.add(tf5)
        gb.setConstraints(tf5, jlConstraints(GridBagConstraints.LINE_START, 1, 1))

        lab4 = JLabel(jlGetStringResource(Res.string.gui_passing_communication_delay))
        pBottom.add(lab4)
        gb.setConstraints(lab4, jlConstraints(GridBagConstraints.LINE_END, 0, 2, Insets(3, 0, 0, 3)))

        tf7 = JTextField(3)
        pBottom.add(tf7)
        gb.setConstraints(tf7, jlConstraints(GridBagConstraints.LINE_START, 1, 2, Insets(3, 0, 0, 0)))

        add(pBottom)
        gb.setConstraints(pBottom, jlConstraints(GridBagConstraints.CENTER, 0, 2, Insets(5, BORDER, 5, BORDER)))

        // --- Event Listeners ---

        // Jugglers listener
        comboJugglers.addItemListener { _: ItemEvent? ->
            val jugglers = comboJugglers.selectedIndex + 1
            if (jugglers > 1) {
                cb15.isEnabled = true
                cb17.isEnabled = cb7.isSelected && cb8.isSelected
                cb18.isEnabled = true
                if (cb7.isSelected && !cb8.isSelected) {
                    lab4.isEnabled = true
                    tf7.isEnabled = true
                } else {
                    lab4.isEnabled = false
                    tf7.isEnabled = false
                }
            } else {
                cb15.isEnabled = false
                cb17.isEnabled = false
                cb18.isEnabled = false
                lab4.isEnabled = false
                tf7.isEnabled = false
            }
            comboJugglers.transferFocus()
        }

        // Multiplexing listener
        comboMultiplexing.addItemListener { _: ItemEvent? ->
            val isMultiplexing = comboMultiplexing.selectedIndex > 0
            cb13.isEnabled = isMultiplexing
            cb14.isEnabled = isMultiplexing
            cb16.isEnabled = isMultiplexing
        }

        // ground/excited state options affect other checkboxes
        val temp = ActionListener { _: ActionEvent? ->
            val jugglers = comboJugglers.selectedIndex + 1
            if (!cb7.isSelected || cb8.isSelected) {
                lab4.isEnabled = false
                tf7.isEnabled = false
            } else {
                if (jugglers > 1) {
                    lab4.isEnabled = true
                    tf7.isEnabled = true
                }
            }
            cb17.isEnabled = cb7.isSelected && cb8.isSelected && (jugglers > 1)
            cb9.isEnabled = cb8.isSelected
        }
        cb7.addActionListener(temp)
        cb8.addActionListener(temp)

        resetControl()
    }

    fun resetControl() {
        tf1.text = "5"  // balls
        tf2.text = "7"  // max throw
        tf3.text = "5"  // period

        comboRhythm.selectedIndex = 0  // asynch
        comboCompositions.selectedIndex = 0  // all
        comboMultiplexing.selectedIndex = 0 // none

        cb7.isSelected = true  // ground state
        cb8.isSelected = true  // excited state
        cb9.isSelected = false  // starting/ending sequences
        cb10.isSelected = false  // pattern rotations

        comboJugglers.selectedIndex = 0 // one juggler
        cb17.isSelected = false  // juggler permutations
        cb15.isSelected = true  // connected patterns
        cb18.isSelected = false  // symmetric patterns

        cb13.isSelected = true  // no simultaneous catches
        cb14.isSelected = false  // allow clustered throws
        cb16.isSelected = true  // true multiplexing

        tf4.text = ""  // excluded throws
        tf5.text = ""  // included throws
        tf7.text = "0" // passing communication delay

        // Enablement logic
        cb9.isEnabled = true

        // Passing disabled
        cb17.isEnabled = false
        cb15.isEnabled = false
        cb18.isEnabled = false
        lab4.isEnabled = false
        tf7.isEnabled = false

        // Multiplexing disabled
        cb13.isEnabled = false
        cb14.isEnabled = false
        cb16.isEnabled = false
    }

    val params: String
        get() {
            val sb = StringBuilder(256)

            var maxthrow = tf2.text
            if (maxthrow.trim { it <= ' ' }.isEmpty()) {
                maxthrow = "-"
            }
            var period = tf3.text
            if (period.trim { it <= ' ' }.isEmpty()) {
                period = "-"
            }

            sb.append(tf1.text).append(" ").append(maxthrow).append(" ").append(period)

            if (comboRhythm.selectedIndex == 1) {
                sb.append(" -s")
            }
            val jugglers = comboJugglers.selectedIndex + 1
            if (jugglers > 1) {
                sb.append(" -j ").append(jugglers)
                if (tf7.isEnabled && !tf7.text.isEmpty()) {
                    sb.append(" -d ").append(tf7.text).append(" -l 1")
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

            // Compositions: 0=All (-f), 1=Non-obvious (default), 2=Prime (-prime)
            when (comboCompositions.selectedIndex) {
                0 -> sb.append(" -f")
                2 -> sb.append(" -prime")
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
            if (comboMultiplexing.selectedIndex > 0) {
                val muxVal = comboMultiplexing.selectedItem as String
                sb.append(" -m ").append(muxVal)
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
            if (!tf4.text.isEmpty()) {
                sb.append(" -x ").append(tf4.text)
            }
            if (!tf5.text.isEmpty()) {
                sb.append(" -i ").append(tf5.text)
            }
            sb.append(" -n")

            return sb.toString()
        }

    companion object {
        const val BORDER: Int = 10
    }
}
