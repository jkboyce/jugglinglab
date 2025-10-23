//
// LabelDialog.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import java.awt.Component
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class LabelDialog(parent: Component?, title: String?, msg: String?) {
    init {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                parent,
                msg,
                title,
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
}
