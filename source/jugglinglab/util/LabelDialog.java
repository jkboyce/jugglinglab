// LabelDialog.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

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

