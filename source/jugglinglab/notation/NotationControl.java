// NotationControl.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.util.ResourceBundle;
import javax.swing.JPanel;


// This is the GUI that allows the user to enter a pattern in a given notation.
// It is used by NotationGUI to assemble the interface.

public abstract class NotationControl extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    // create a new empty pattern
    public abstract Pattern newPattern();

    // read the elements in the UI to generate a config string that can be
    // used by Pattern.fromString()
    public abstract String getConfigString();

    // reset to defaults
    public abstract void resetNotationControl();

    // optional string that's attached to JMLPattern title
    public abstract String getHandsName();
}
