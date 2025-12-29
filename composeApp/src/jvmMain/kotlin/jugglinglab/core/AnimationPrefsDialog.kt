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

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.ui.AnimationPrefsControl
import jugglinglab.util.jlGetStringResource
import androidx.compose.material.*
import androidx.compose.ui.awt.ComposePanel
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
