// LabelDialog.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.awt.Component;
import javax.swing.*;


public class LabelDialog {
    public LabelDialog(Component parent, String title, String msg) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }
}

