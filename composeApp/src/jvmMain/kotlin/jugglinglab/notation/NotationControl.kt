//
// NotationControl.kt
//
// This is the GUI that allows the user to enter a pattern in a given notation.
// It is used by ApplicationPanel to assemble the interface.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import javax.swing.JPanel

abstract class NotationControl : JPanel() {
    // Return a new (uninitialized) pattern in the target notation
    abstract fun newPattern(): Pattern

    @get:Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract val parameterList: ParameterList

    // Reset control to defaults
    abstract fun resetControl()
}
