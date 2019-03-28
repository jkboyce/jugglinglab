// NotationControl.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.util.ResourceBundle;
import javax.swing.JPanel;

import jugglinglab.core.AnimationPrefs;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.JuggleExceptionUser;
import jugglinglab.util.JuggleExceptionInternal;


// This is the GUI that allows the user to enter a pattern in a given notation.
// It is used by NotationGUI to assemble the interface.

public abstract class NotationControl extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // reset to defaults
    public abstract void resetNotationControl();

    // try to make a Pattern from the current control settings
    public abstract Pattern getPattern()
            throws JuggleExceptionUser, JuggleExceptionInternal;

    // return AnimationPrefs for pattern
    public abstract AnimationPrefs getAnimationPrefs()
            throws JuggleExceptionUser, JuggleExceptionInternal;
}
