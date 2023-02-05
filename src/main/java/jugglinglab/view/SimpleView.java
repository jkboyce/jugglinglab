// SimpleView.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.view;

import java.awt.*;
import java.io.File;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class SimpleView extends View {
    protected AnimationPanel ja;


    public SimpleView(Dimension dim) {
        ja = new AnimationPanel();
        ja.setPreferredSize(dim);
        ja.setMinimumSize(new Dimension(10, 10));
        setLayout(new BorderLayout());
        add(ja, BorderLayout.CENTER);
    }

    // View methods

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) throws
                                JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle(p, c);
        setAnimationPanelPreferredSize(getAnimationPrefs().getSize());

        if (p != null && parent != null)
            parent.setTitle(p.getTitle());
    }

    @Override
    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle();
    }

    @Override
    public Dimension getAnimationPanelSize() {
        return ja.getSize(new Dimension());
    }

    @Override
    public void setAnimationPanelPreferredSize(Dimension d) {
        ja.setPreferredSize(d);
    }

    @Override
    public JMLPattern getPattern() {
        return ja.getPattern();
    }

    @Override
    public AnimationPrefs getAnimationPrefs() {
        return ja.getAnimationPrefs();
    }

    @Override
    public double getZoomLevel() {
        return ja.getZoomLevel();
    }

    @Override
    public void setZoomLevel(double z) {
        ja.setZoomLevel(z);
    }

    @Override
    public boolean isPaused() {
        return ja.isPaused();
    }

    @Override
    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
    }

    @Override
    public void disposeView() {
        ja.disposeAnimation();
    }

    @Override
    public void writeGIF(File f) {
        ja.writingGIF = true;
        boolean origpause = isPaused();
        setPaused(true);
        if (parent != null)
            parent.setResizable(false);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                ja.writingGIF = false;
                setPaused(origpause);
                if (parent != null)
                    parent.setResizable(true);
            }
        };

        new View.GIFWriter(ja, f, cleanup);
    }
}
