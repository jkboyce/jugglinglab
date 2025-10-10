//
// EditView.java
//
// This view provides the ability to edit a pattern visually. It features a
// ladder diagram on the right and an animator on the left.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view;

import java.awt.*;
import java.io.File;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;

public class EditView extends View {
  protected AnimationPanel ap;
  protected JPanel ladder;
  protected JSplitPane jsp;

  public EditView(Dimension dim, JMLPattern pat)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    ap = new AnimationEditPanel();
    // ap = new AnimationPanel();
    ap.setPreferredSize(dim);
    ap.setMinimumSize(new Dimension(10, 10));

    ladder = new JPanel();
    ladder.setLayout(new BorderLayout());
    ladder.setBackground(Color.white);

    // add ladder diagram now to get dimensions correct; will be replaced
    // in restartView()
    ladder.add(new LadderDiagram(pat), BorderLayout.CENTER);

    Locale loc = Locale.getDefault();
    if (ComponentOrientation.getOrientation(loc) == ComponentOrientation.LEFT_TO_RIGHT) {
      jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ap, ladder);
      jsp.setResizeWeight(1.0);
    } else {
      jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ladder, ap);
      jsp.setResizeWeight(0.0);
    }
    jsp.setBorder(new EmptyBorder(0, 0, 0, 0));
    jsp.setBackground(Color.white);

    setBackground(Color.white);
    setLayout(new BorderLayout());
    add(jsp, BorderLayout.CENTER);
  }

  //----------------------------------------------------------------------------
  // View methods
  //----------------------------------------------------------------------------

  @Override
  public void restartView(JMLPattern p, AnimationPrefs c)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    boolean changing_jugglers =
        (p != null
            && getPattern() != null
            && p.getNumberOfJugglers() != getPattern().getNumberOfJugglers());

    ap.restartJuggle(p, c);
    setAnimationPanelPreferredSize(getAnimationPrefs().getSize());

    if (p == null) return;

    LadderDiagram new_ladder = null;
    if (ap instanceof AnimationEditPanel) new_ladder = new EditLadderDiagram(p, parent, this);
    else new_ladder = new LadderDiagram(p);

    new_ladder.setAnimationPanel(ap);

    ap.removeAllAttachments();
    ap.addAnimationAttachment(new_ladder);

    if (ap instanceof AnimationEditPanel) {
      AnimationEditPanel aep = (AnimationEditPanel) ap;
      aep.deactivateEvent();
      aep.deactivatePosition();
    }

    ladder.removeAll();
    ladder.add(new_ladder, BorderLayout.CENTER);

    if (changing_jugglers && parent != null) {
      // the next line is needed to get the JSplitPane divider to
      // reset during layout
      jsp.resetToPreferredSizes();

      if (parent.isWindowMaximized()) parent.validate();
      else parent.pack();
    } else ladder.validate(); // to make ladder redraw

    if (parent != null) parent.setTitle(p.getTitle());
  }

  @Override
  public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
    ap.restartJuggle();
  }

  @Override
  public Dimension getAnimationPanelSize() {
    return ap.getSize(new Dimension());
  }

  @Override
  public void setAnimationPanelPreferredSize(Dimension d) {
    ap.setPreferredSize(d);
    jsp.resetToPreferredSizes();
  }

  @Override
  public JMLPattern getPattern() {
    return ap.getPattern();
  }

  @Override
  public AnimationPrefs getAnimationPrefs() {
    return ap.getAnimationPrefs();
  }

  @Override
  public double getZoomLevel() {
    return ap.getZoomLevel();
  }

  @Override
  public void setZoomLevel(double z) {
    ap.setZoomLevel(z);
  }

  @Override
  public boolean isPaused() {
    return ap.isPaused();
  }

  @Override
  public void setPaused(boolean pause) {
    if (ap.message == null) ap.setPaused(pause);
  }

  @Override
  public void disposeView() {
    ap.disposeAnimation();
  }

  @Override
  public void writeGIF(File f) {
    ap.writingGIF = true;
    boolean origpause = isPaused();
    setPaused(true);
    jsp.setEnabled(false);
    if (parent != null) parent.setResizable(false);

    Runnable cleanup =
        new Runnable() {
          @Override
          public void run() {
            ap.writingGIF = false;
            setPaused(origpause);
            jsp.setEnabled(true);
            if (parent != null) parent.setResizable(true);
          }
        };

    new View.GIFWriter(ap, f, cleanup);
  }
}
