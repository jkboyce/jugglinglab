// EditView.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.view;

import java.awt.*;
import java.io.File;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


// This view provides the ability to edit a pattern visually. It features a
// ladder diagram on the right and an animator on the left.

public class EditView extends View {
    protected AnimationEditPanel aep;
    protected JPanel ladder;
    protected JSplitPane jsp;


    public EditView(Dimension dim, JMLPattern pat) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        aep = new AnimationEditPanel();
        aep.setPreferredSize(dim);
        aep.setMinimumSize(new Dimension(10, 10));

        ladder = new JPanel();
        ladder.setLayout(new BorderLayout());
        ladder.setBackground(Color.white);

        // add ladder diagram now to get dimensions correct; will be replaced
        // in restartView()
        ladder.add(new LadderDiagram(pat), BorderLayout.CENTER);

        Locale loc = Locale.getDefault();
        if (ComponentOrientation.getOrientation(loc) == ComponentOrientation.LEFT_TO_RIGHT) {
            jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, aep, ladder);
            jsp.setResizeWeight(1.0);
        } else {
            jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ladder, aep);
            jsp.setResizeWeight(0.0);
        }
        jsp.setBorder(new EmptyBorder(0, 0, 0, 0));
        jsp.setBackground(Color.white);

        setBackground(Color.white);
        setLayout(new BorderLayout());
        add(jsp, BorderLayout.CENTER);
    }

    // View methods

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) throws
                            JuggleExceptionUser, JuggleExceptionInternal {
        boolean changing_jugglers = (p != null && getPattern() != null &&
                  p.getNumberOfJugglers() != getPattern().getNumberOfJugglers());

        aep.restartJuggle(p, c);
        setAnimationPanelPreferredSize(getAnimationPrefs().getSize());

        if (p != null) {
            EditLadderDiagram new_ladder = new EditLadderDiagram(p, parent, this);
            new_ladder.setAnimationPanel(aep);
            // LadderDiagram new_ladder = new LadderDiagram(p);
            // new_ladder.setAnimator(aep.getAnimator());

            aep.addAnimationAttachment(new_ladder);
            aep.deactivateEvent();
            aep.deactivatePosition();

            ladder.removeAll();
            ladder.add(new_ladder, BorderLayout.CENTER);

            if (changing_jugglers && parent != null) {
                // the next line is needed to get the JSplitPane divider to
                // reset during layout
                jsp.resetToPreferredSizes();

                if (parent.isWindowMaximized())
                    parent.validate();
                else
                    parent.pack();
            } else
                ladder.validate();  // to make ladder redraw

            if (parent != null)
                parent.setTitle(p.getTitle());
        }
    }

    @Override
    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        aep.restartJuggle();
    }

    @Override
    public Dimension getAnimationPanelSize() {
        return aep.getSize(new Dimension());
    }

    @Override
    public void setAnimationPanelPreferredSize(Dimension d) {
        aep.setPreferredSize(d);
        jsp.resetToPreferredSizes();
    }

    @Override
    public JMLPattern getPattern() {
        return aep.getPattern();
    }

    @Override
    public AnimationPrefs getAnimationPrefs() {
        return aep.getAnimationPrefs();
    }

    @Override
    public double getZoomLevel() {
        return aep.getZoomLevel();
    }

    @Override
    public void setZoomLevel(double z) {
        aep.setZoomLevel(z);
    }

    @Override
    public boolean isPaused() {
        return aep.isPaused();
    }

    @Override
    public void setPaused(boolean pause) {
        if (aep.message == null)
            aep.setPaused(pause);
    }

    @Override
    public void disposeView() {
        aep.disposeAnimation();
    }

    @Override
    public void writeGIF(File f) {
        aep.writingGIF = true;
        boolean origpause = isPaused();
        setPaused(true);
        jsp.setEnabled(false);
        if (parent != null)
            parent.setResizable(false);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                aep.writingGIF = false;
                setPaused(origpause);
                jsp.setEnabled(true);
                if (parent != null)
                    parent.setResizable(true);
            }
        };

        new View.GIFWriter(aep, f, cleanup);
    }
}
