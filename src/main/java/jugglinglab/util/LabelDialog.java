//
// LabelDialog.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util;

import java.awt.Component;
import javax.swing.*;

public class LabelDialog {
  public LabelDialog(Component parent, String title, String msg) {
    SwingUtilities.invokeLater(
        () -> JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.INFORMATION_MESSAGE));
  }
}
