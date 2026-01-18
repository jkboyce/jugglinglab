//
// AnimationPrefsDialog.kt
//
// This is the dialog box that allows the user to set animation preferences.
// The dialog does not display when the dialog box is constructed, but when
// getPrefs() is called.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.ui

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.AnimationPrefs
import jugglinglab.util.jlGetStringResource
import androidx.compose.material.*
import androidx.compose.ui.awt.ComposePanel
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JFrame

open class AnimationPrefsDialog(private val parentFrame: JFrame?) : JDialog(
    parentFrame,
    jlGetStringResource(Res.string.gui_animation_preferences),
    true
) {
    private var result: AnimationPrefs? = null

    init {
        isResizable = false
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                result = null
                isVisible = false
            }
        })
    }

    open fun getPrefs(oldPrefs: AnimationPrefs): AnimationPrefs {
        val composePanel = ComposePanel()
        composePanel.setContent {
            MaterialTheme {
                AnimationPrefsControl(
                    initialPrefs = oldPrefs,
                    onConfirm = { newPrefs ->
                        result = newPrefs
                        isVisible = false
                    },
                    onCancel = {
                        result = null
                        isVisible = false
                    }
                )
            }
        }

        contentPane = composePanel
        pack()
        setLocationRelativeTo(parentFrame)
        isVisible = true  // blocks until isVisible=false is called above
        return result ?: oldPrefs
    }
}
