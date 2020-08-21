// NotationControl.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.util.ResourceBundle;
import javax.swing.JPanel;

import jugglinglab.util.JuggleExceptionUser;
import jugglinglab.util.JuggleExceptionInternal;
import jugglinglab.util.ParameterList;


// This is the GUI that allows the user to enter a pattern in a given notation.
// It is used by ApplicationPanel to assemble the interface.

public abstract class NotationControl extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // Return a new (uninitialized) pattern in the target notation
    public abstract Pattern newPattern();

    // Return parameters defining Pattern, AnimationPrefs, and View.
    //
    // The control doesn't need to do any error-checking on whether parameters
    // are consistent, etc. -- those will be caught later when the parameters
    // are used to create a Pattern, AnimationPrefs, and so on.
    public abstract ParameterList getParameterList()
            throws JuggleExceptionUser, JuggleExceptionInternal;

    // Reset control to defaults
    public abstract void resetControl();
}
