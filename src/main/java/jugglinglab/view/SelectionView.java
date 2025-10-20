//
// SelectionView.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import jugglinglab.core.*;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;

public class SelectionView extends View {
  protected static final int ROWS = 3;
  protected static final int COLUMNS = 3;
  protected static final int COUNT = ROWS * COLUMNS;
  protected static final int CENTER = (COUNT - 1) / 2;

  protected AnimationPanel[] ja;
  protected JLayeredPane layered;
  protected Mutator mutator;
  protected AnimationPrefs saved_prefs;

  public SelectionView(Dimension dim) {
    ja = new AnimationPanel[COUNT];
    for (int i = 0; i < COUNT; i++) {
      ja[i] = new AnimationPanel();
    }

    // JLayeredPane on the left so we can show a grid of animations with an
    // overlay drawn on top
    layered = makeLayeredPane(dim, makeAnimationGrid(), makeOverlay());
    mutator = new Mutator();

    GridBagLayout gb = new GridBagLayout();
    setLayout(gb);

    add(layered);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridheight = gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = gbc.weighty = 1.0;
    gb.setConstraints(layered, gbc);

    JPanel controls = mutator.getControlPanel();
    add(controls);
    gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridheight = gbc.gridwidth = 1;
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.weighty = 1.0;
    gb.setConstraints(controls, gbc);
  }

  protected JPanel makeAnimationGrid() {
    JPanel grid = new JPanel(new GridLayout(ROWS, COLUMNS));

    for (int i = 0; i < COUNT; i++) {
      grid.add(ja[i]);
    }

    grid.addMouseListener(
        new MouseAdapter() {
          // will only receive mouseReleased events here when one of the
          // AnimationPanel objects dispatches it to us in its
          // mouseReleased() method.
          @Override
          public void mouseReleased(MouseEvent me) {
            Component c = me.getComponent();
            int num = 0;
            while (num < COUNT) {
              if (c == SelectionView.this.ja[num]) {
                break;
              }
              ++num;
            }
            if (num == COUNT) {
              return;
            }
            try {
              SelectionView.this.restartView(ja[num].getPattern(), null);
              if (num != CENTER) {
                addToUndoList(ja[CENTER].getPattern());
              }
            } catch (JuggleExceptionUser jeu) {
              ErrorDialog.handleUserException(parent, jeu.getMessage());
            } catch (JuggleExceptionInternal jei) {
              ErrorDialog.handleFatalException(jei);
            }
          }
        });

    grid.addMouseMotionListener(
        new MouseMotionAdapter() {
          // Dispatched here from one of the AnimationPanels when the
          // user drags the mouse for a camera angle change. Copy to the
          // other animations.
          @Override
          public void mouseDragged(MouseEvent me) {
            Component c = me.getComponent();
            int num = 0;
            while (num < COUNT) {
              if (c == SelectionView.this.ja[num]) {
                break;
              }
              ++num;
            }
            if (num == COUNT) {
              return;
            }
            double[] ca = ja[num].getCameraAngle();
            for (int i = 0; i < COUNT; i++) {
              if (i != num) {
                ja[i].setCameraAngle(ca);
              }
            }
          }
        });
    grid.setOpaque(true);
    return grid;
  }

  protected JPanel makeOverlay() {
    JPanel overlay =
        new JPanel() {
          @Override
          public void paintComponent(Graphics g) {
            Dimension d = getSize();
            int xleft = (d.width * ((COLUMNS - 1) / 2)) / COLUMNS;
            int ytop = (d.height * ((ROWS - 1) / 2)) / ROWS;
            int width = d.width / COLUMNS;
            int height = d.height / ROWS;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0));
            g2.setColor(Color.lightGray);
            g2.drawRect(xleft, ytop, width, height);
            g2.dispose();
          }
        };
    overlay.setOpaque(false);
    return overlay;
  }

  protected JLayeredPane makeLayeredPane(Dimension d, JPanel grid, JPanel overlay) {
    JLayeredPane layered = new JLayeredPane();

    layered.setLayout(null);
    layered.add(grid, JLayeredPane.DEFAULT_LAYER);
    layered.add(overlay, JLayeredPane.PALETTE_LAYER);

    // JLayeredPane has no layout manager, so we have to "manually"
    // arrange the components inside when its size changes. This will cause
    // the grid's GridLayout to lay out each individual animation panel.
    layered.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            Dimension d = layered.getSize();
            grid.setBounds(0, 0, d.width, d.height);
            overlay.setBounds(0, 0, d.width, d.height);
            SelectionView.this.validate();
          }
        });

    // ensure the entire grid fits on the screen, rescaling if needed
    int pref_width = COLUMNS * d.width;
    int pref_height = ROWS * d.height;

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    int max_width = screenSize.width - 300; // allocation for controls etc.
    int max_height = screenSize.height - 120;

    if (pref_width > max_width || pref_height > max_height) {
      double scale = Math.min(
              (double) max_width / (double) pref_width, (double) max_height / (double) pref_height);
      pref_width = (int) (scale * pref_width);
      pref_height = (int) (scale * pref_height);
    }
    layered.setPreferredSize(new Dimension(pref_width, pref_height));
    // set initial positions of children, since there is no layout manager
    // see https://docs.oracle.com/javase/tutorial/uiswing/layout/none.html
    grid.setBounds(0, 0, pref_width, pref_height);
    overlay.setBounds(0, 0, pref_width, pref_height);
    return layered;
  }

  //----------------------------------------------------------------------------
  // View methods
  //----------------------------------------------------------------------------

  @Override
  public void restartView(JMLPattern p, AnimationPrefs c)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    AnimationPrefs newjc = null;
    if (c != null) {
      saved_prefs = c;
      newjc = new AnimationPrefs(c);
      // disable startPause for grid of animations
      newjc.startPause = false;
    }

    ja[CENTER].restartJuggle(p, newjc);
    for (int i = 0; i < COUNT; i++) {
      if (i != CENTER) {
        JMLPattern newp = (p == null ? null : mutator.mutatePattern(p));
        ja[i].restartJuggle(newp, newjc);
      }
    }

    setAnimationPanelPreferredSize(getAnimationPrefs().getSize());

    if (p != null) {
      parent.setTitle(p.getTitle());
    }
  }

  @Override
  public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
    for (int i = 0; i < COUNT; i++) {
      ja[i].restartJuggle();
    }
  }

  @Override
  public Dimension getAnimationPanelSize() {
    return ja[CENTER].getSize(new Dimension());
  }

  @Override
  public void setAnimationPanelPreferredSize(Dimension d) {
    // This works differently for this view since the JLayeredPane has no
    // layout manager, so preferred size info can't propagate up from the
    // individual animation panels. So we go the other direction: set a
    // preferred size for the overall JLayeredPane, which gets propagated to
    // the grid (and the individual animations) by the ComponentListener above.
    int width = COLUMNS * d.width;
    int height = ROWS * d.height;
    layered.setPreferredSize(new Dimension(width, height));
  }

  @Override
  public JMLPattern getPattern() {
    return ja[CENTER].getPattern();
  }

  @Override
  public AnimationPrefs getAnimationPrefs() {
    return saved_prefs;
  }

  @Override
  public double getZoomLevel() {
    return ja[CENTER].getZoomLevel();
  }

  @Override
  public void setZoomLevel(double z) {
    for (int i = 0; i < COUNT; i++) {
      ja[i].setZoomLevel(z);
    }
  }

  @Override
  public boolean isPaused() {
    return ja[CENTER].isPaused();
  }

  @Override
  public void setPaused(boolean pause) {
    if (ja[CENTER].message == null) {
      for (int i = 0; i < COUNT; i++) {
        ja[i].setPaused(pause);
      }
    }
  }

  @Override
  public void disposeView() {
    for (int i = 0; i < COUNT; i++) {
      ja[i].disposeAnimation();
    }
  }

  @Override
  public void writeGIF(File f) {
    for (int i = 0; i < COUNT; i++) {
      ja[i].writingGIF = true;
    }
    boolean origpause = isPaused();
    setPaused(true);
    if (parent != null) {
      parent.setResizable(false);
    }

    Runnable cleanup =
        new Runnable() {
          @Override
          public void run() {
            for (int i = 0; i < COUNT; i++) {
              ja[i].writingGIF = false;
            }
            setPaused(origpause);
            if (parent != null) {
              parent.setResizable(true);
            }
          }
        };

    new View.GIFWriter(ja[CENTER], f, cleanup);
  }
}
