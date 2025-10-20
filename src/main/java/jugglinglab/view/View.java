//
// View.java
//
// This class represents the entire displayed contents of a PatternWindow.
// Subclasses of this are used to show different pattern views, which the
// user can select from a menu on the pattern window.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.view;

import java.awt.Dimension;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import jugglinglab.core.*;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;

public abstract class View extends JPanel {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

  // these should be sequential and in the same order as in the View menu,
  // because of assumptions in PatternWindow's constructor
  public static final int VIEW_NONE = 0;
  public static final int VIEW_SIMPLE = 1;
  public static final int VIEW_EDIT = 2;
  public static final int VIEW_PATTERN = 3;
  public static final int VIEW_SELECTION = 4;

  // used for `view` parameter setting in AnimationPrefs, these must be in the
  // same order as VIEW_ constants above
  public static final String[] viewNames = {
    "simple", "visual_editor", "pattern_editor", "selection_editor",
  };

  protected PatternWindow parent;
  protected ArrayList<JMLPattern> undo;
  protected int undo_index;

  public void setParent(PatternWindow p) {
    parent = p;
  }

  public int getHashCode() {
    JMLPattern pat = getPattern();
    return (pat == null) ? 0 : pat.getHashCode();
  }

  //----------------------------------------------------------------------------
  // Methods to handle undo/redo functionality.
  //
  // The enclosing PatternWindow owns the undo list, so it's preserved when
  // we switch views. All of the methods are here however in case we want to
  // embed the View in something other than a PatternWindow in the future.
  //----------------------------------------------------------------------------

  // For the PatternWindow to pass into a newly-initialized view.

  public void setUndoList(ArrayList<JMLPattern> u, int u_index) {
    undo = u;
    undo_index = u_index;
  }

  // Add a pattern to the undo list.

  public void addToUndoList(JMLPattern p) {
    try {
      JMLPattern pcopy = new JMLPattern(p); // add copy so it won't change
      ++undo_index;
      undo.add(undo_index, pcopy);
      while (undo_index + 1 < undo.size()) {
        undo.remove(undo_index + 1);
      }

      if (parent != null) {
        parent.updateUndoMenu();
      }
    } catch (JuggleExceptionUser jeu) {
      ErrorDialog.handleFatalException(new JuggleExceptionInternal(jeu.getMessage()));
    } catch (JuggleExceptionInternal jei) {
      ErrorDialog.handleFatalException(jei);
    }
  }

  // Undo to the previous save state.

  public void undoEdit() throws JuggleExceptionInternal {
    if (undo_index > 0) {
      try {
        --undo_index;
        JMLPattern pcopy = new JMLPattern(undo.get(undo_index));
        restartView(pcopy, null);

        if (undo_index == 0 || undo_index == undo.size() - 2) {
          if (parent != null) {
            parent.updateUndoMenu();
          }
        }
      } catch (JuggleExceptionUser jeu) {
        // errors here aren't user errors since pattern was successfully
        // animated before
        throw new JuggleExceptionInternal(jeu.getMessage());
      }
    }
  }

  // Redo to the next save state.

  public void redoEdit() throws JuggleExceptionInternal {
    if (undo_index < undo.size() - 1) {
      try {
        ++undo_index;
        JMLPattern pcopy = new JMLPattern(undo.get(undo_index));
        restartView(pcopy, null);

        if (undo_index == 1 || undo_index == undo.size() - 1) {
          if (parent != null) {
            parent.updateUndoMenu();
          }
        }
      } catch (JuggleExceptionUser jeu) {
        throw new JuggleExceptionInternal(jeu.getMessage());
      }
    }
  }

  // For the PatternWindow to retain state when the view changes.

  public int getUndoIndex() {
    return undo_index;
  }

  //----------------------------------------------------------------------------
  // Abstract methods for subclasses to define
  //----------------------------------------------------------------------------

  // restart view with a new pattern and/or preferences
  //
  // note:
  // - a null argument means no update for that item
  // - this method is responsible for setting preferred sizes of all UI
  //   elements, since it may be followed by layout
  public abstract void restartView(JMLPattern p, AnimationPrefs c)
      throws JuggleExceptionUser, JuggleExceptionInternal;

  // restart without changing pattern or preferences
  public abstract void restartView() throws JuggleExceptionUser, JuggleExceptionInternal;

  // size of just the juggler animation, not any extra elements
  public abstract Dimension getAnimationPanelSize();

  public abstract void setAnimationPanelPreferredSize(Dimension d);

  public abstract JMLPattern getPattern();

  public abstract AnimationPrefs getAnimationPrefs();

  public abstract double getZoomLevel();

  public abstract void setZoomLevel(double z);

  public abstract boolean isPaused();

  public abstract void setPaused(boolean pause);

  public abstract void disposeView();

  public abstract void writeGIF(File f);

  //----------------------------------------------------------------------------
  // Utility class for the various View subclasses to use for writing GIFs.
  // This does the processing in a thread separate from the EDT.
  //----------------------------------------------------------------------------

  protected class GIFWriter extends Thread {
    private final AnimationPanel ap;
    private final Runnable cleanup;
    private final File file;
    private final ProgressMonitor pm;
    private final Animator.WriteGIFMonitor wgm;
    private final double fps;

    public GIFWriter(AnimationPanel animpanel, File f, Runnable cleanup_routine) {
      ap = animpanel;
      file = f;
      cleanup = cleanup_routine;

      pm = new ProgressMonitor(parent, guistrings.getString("Saving_animated_GIF"), "", 0, 1);
      pm.setMillisToPopup(200);

      wgm = new Animator.WriteGIFMonitor() {
            @Override
            public void update(int step, int steps_total) {
              SwingUtilities.invokeLater(
                  () -> {
                    pm.setMaximum(steps_total);
                    pm.setProgress(step);
                  });
            }

            @Override
            public boolean isCanceled() {
              return (pm.isCanceled() || GIFWriter.interrupted());
            }
          };

      AnimationPrefs jc = ap.getAnimationPrefs();
      fps = (jc.fps == AnimationPrefs.FPS_DEF) ? 33.3 : jc.fps;
      // Note the GIF header specifies inter-frame delay in terms of
      // hundredths of a second, so only `fps` values like 50, 33 1/3,
      // 25, 20, ... are precisely achieveable.

      setPriority(Thread.MIN_PRIORITY);
      start();
    }

    @Override
    public void run() {
      try {
        ap.getAnimator().writeGIF(new FileOutputStream(file), wgm, fps);
      } catch (IOException ioe) {
        if (file != null) {
          String template = errorstrings.getString("Error_writing_file");
          Object[] arg = {file.toString()};
          ErrorDialog.handleUserException(parent, MessageFormat.format(template, arg));
        } else {
          ErrorDialog.handleFatalException(ioe);
        }
      } catch (JuggleExceptionInternal jei) {
        ErrorDialog.handleFatalException(jei);
      } finally {
        if (cleanup != null) {
          SwingUtilities.invokeLater(cleanup);
        }
      }
    }
  }
}
