//
// SaveGifDialogSwing.kt
//
// Dialog for configuring animated GIF export settings (width, height, fps).
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlHandleUserException
import org.jugglinglab.util.jlToStringRounded
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import javax.swing.*

data class GifSettings(
    val width: Int,
    val height: Int,
    val fps: Double
)

class SaveGifDialogSwing(parent: JFrame?) : JDialog(
    parent,
    jlGetStringResource(Res.string.gui_animated_gif_settings),
    true
) {
    private lateinit var tfWidth: JTextField
    private lateinit var tfHeight: JTextField
    private lateinit var tfFps: JTextField
    private lateinit var butCancel: JButton
    private lateinit var butOk: JButton

    private var okSelected: Boolean = false

    init {
        createContents()
        setLocationRelativeTo(parent)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                okSelected = false
                isVisible = false
            }
        })
        butCancel.addActionListener { _: ActionEvent? ->
            okSelected = false
            isVisible = false
        }
        butOk.addActionListener { _: ActionEvent? ->
            if (validateInputs()) {
                okSelected = true
                isVisible = false
            }
        }
    }

    fun getSettings(
        initialWidth: Int,
        initialHeight: Int,
        initialFps: Double
    ): GifSettings? {
        tfWidth.text = initialWidth.toString()
        tfHeight.text = initialHeight.toString()
        tfFps.text = jlToStringRounded(initialFps, 2)

        okSelected = false
        isVisible = true  // blocks until dismissed
        return if (okSelected) readDialogBox() else null
    }

    private fun validateInputs(): Boolean {
        try {
            val width = tfWidth.text.trim().toInt()
            if (width <= 0) {
                val message = jlGetStringResource(Res.string.error_negative_value)
                jlHandleUserException(this, message)
                return false
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(
                Res.string.error_number_format,
                jlGetStringResource(Res.string.gui_width)
            )
            jlHandleUserException(this, message)
            return false
        }

        try {
            val height = tfHeight.text.trim().toInt()
            if (height <= 0) {
                val message = jlGetStringResource(Res.string.error_negative_value)
                jlHandleUserException(this, message)
                return false
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(
                Res.string.error_number_format,
                jlGetStringResource(Res.string.gui_height)
            )
            jlHandleUserException(this, message)
            return false
        }

        try {
            val fps = tfFps.text.trim().toDouble()
            if (fps <= 0.0 || !fps.isFinite()) {
                val message = jlGetStringResource(Res.string.error_negative_value)
                jlHandleUserException(this, message)
                return false
            }
        } catch (_: NumberFormatException) {
            val message = jlGetStringResource(
                Res.string.error_number_format,
                jlGetStringResource(Res.string.gui_frames_per_second)
            )
            jlHandleUserException(this, message)
            return false
        }

        return true
    }

    private fun readDialogBox(): GifSettings {
        val width = tfWidth.text.trim().toInt()
        val height = tfHeight.text.trim().toInt()
        val fps = tfFps.text.trim().toDouble()
        return GifSettings(width, height, fps)
    }

    private fun createContents() {
        val lab1 = JLabel(jlGetStringResource(Res.string.gui_width))
        tfWidth = JTextField(5).apply {
            horizontalAlignment = JTextField.CENTER
        }
        val lab2 = JLabel(jlGetStringResource(Res.string.gui_height))
        tfHeight = JTextField(5).apply {
            horizontalAlignment = JTextField.CENTER
        }
        val lab3 = JLabel(jlGetStringResource(Res.string.gui_frames_per_second))
        tfFps = JTextField(5).apply {
            horizontalAlignment = JTextField.CENTER
        }

        butCancel = JButton(jlGetStringResource(Res.string.gui_cancel))
        butOk = JButton(jlGetStringResource(Res.string.gui_ok))

        val gb = GridBagLayout()

        val p1 = JPanel().apply {
            layout = gb
            add(lab1)
            add(tfWidth)
            add(lab2)
            add(tfHeight)
            add(lab3)
            add(tfFps)
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

        val p2 = JPanel().apply {
            layout = gb
            add(butCancel)
            add(butOk)
        }
        gb.setConstraints(
            butCancel, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        gb.setConstraints(
            butOk, constraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )

        contentPane.apply {
            layout = gb
            add(p1)
            add(p2)
        }
        gb.setConstraints(
            p1,
            constraints(GridBagConstraints.LINE_START, 0, 0, Insets(BORDER, BORDER, 0, BORDER))
        )
        gb.setConstraints(
            p2,
            constraints(GridBagConstraints.LINE_END, 0, 1, Insets(BORDER, BORDER, BORDER, BORDER))
        )

        rootPane.defaultButton = butOk

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))
        pack()
        isResizable = false
    }

    override fun getPreferredSize(): Dimension {
        val pref = super.getPreferredSize()
        val titleFont = UIManager.getFont("InternalFrame.titleFont") ?: font ?: Font("SansSerif", Font.PLAIN, 13)
        val titleWidth = getFontMetrics(titleFont).stringWidth(title ?: "")
        val minTitleWidth = titleWidth + TITLE_CLEARANCE
        return Dimension(maxOf(pref.width, minTitleWidth), pref.height)
    }

    companion object {
        private const val BORDER: Int = 10
        private const val TITLE_CLEARANCE: Int = 100  // 160

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
