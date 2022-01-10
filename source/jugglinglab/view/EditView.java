// EditView.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.view;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


// This view provides the ability to edit a pattern visually. It features a
// ladder diagram on the right and an animator on the left.

public class EditView extends View {
    protected AnimationEditPanel jae;
    protected JPanel ladder;
    protected JSplitPane jsp;


    public EditView(Dimension dim, JMLPattern pat) throws
                    JuggleExceptionUser, JuggleExceptionInternal {
        jae = new AnimationEditPanel();
        jae.setPreferredSize(dim);
        jae.setMinimumSize(new Dimension(10, 10));

        ladder = new JPanel();
        ladder.setLayout(new BorderLayout());
        ladder.setBackground(Color.white);

        // add ladder diagram now to get dimensions correct; will be replaced
        // in restartView()
        ladder.add(new EditLadderDiagram(pat, parent, this), BorderLayout.CENTER);

        Locale loc = JLLocale.getLocale();
        if (ComponentOrientation.getOrientation(loc) == ComponentOrientation.LEFT_TO_RIGHT) {
            jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, jae, ladder);
            jsp.setResizeWeight(1.0);
        } else {
            jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ladder, jae);
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
        boolean changed_jugglers = (p != null && getPattern() != null &&
                  p.getNumberOfJugglers() != getPattern().getNumberOfJugglers());

        jae.restartJuggle(p, c);

        if (p != null) {
            EditLadderDiagram new_ladder = new EditLadderDiagram(p, parent, this);
            new_ladder.setAnimationPanel(jae);
            jae.setLadderDiagram(new_ladder);
            jae.deactivateEvent();

            ladder.removeAll();
            ladder.add(new_ladder, BorderLayout.CENTER);

            if (changed_jugglers && parent != null) {
                // the next line is needed to get the JSplitPane divider to
                // reset during layout
                jsp.resetToPreferredSizes();

                if (parent.isWindowMaximized())
                    parent.validate();
                else
                    parent.pack();
            }

            if (parent != null)
                parent.setTitle(p.getTitle());
        }
    }

    @Override
    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        jae.restartJuggle();
    }

    @Override
    public Dimension getAnimationPanelSize() {
        return jae.getSize(new Dimension());
    }

    @Override
    public void setAnimationPanelPreferredSize(Dimension d) {
        jae.setPreferredSize(d);
        jsp.resetToPreferredSizes();
    }

    @Override
    public JMLPattern getPattern() {
        return jae.getPattern();
    }

    @Override
    public AnimationPrefs getAnimationPrefs() {
        return jae.getAnimationPrefs();
    }

    @Override
    public boolean getPaused() {
        return jae.getPaused();
    }

    @Override
    public void setPaused(boolean pause) {
        if (jae.message == null)
            jae.setPaused(pause);
    }

    @Override
    public void disposeView() {
        jae.disposeAnimation();
    }

    @Override
    public void writeGIF() {
        jae.writingGIF = true;
        jae.deactivateEvent();  // so we don't draw event box in animated GIF
        boolean origpause = getPaused();
        setPaused(true);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                setPaused(origpause);
                jae.writingGIF = false;
            }
        };

        new View.GIFWriter(jae, cleanup);
    }
}
