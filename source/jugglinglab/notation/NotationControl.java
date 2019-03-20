// NotationControl.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.util.*;
import javax.swing.*;
import jugglinglab.util.*;


public abstract class NotationControl extends JPanel {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    public abstract String getPattern();
    public abstract void resetNotationControl();
    public abstract String getHandsName();
}
