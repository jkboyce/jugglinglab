// SimpleView.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.view;

import java.awt.*;
import javax.swing.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class SimpleView extends View {
    protected AnimationPanel ja;


    public SimpleView(Dimension dim) {
        this.ja = new AnimationPanel();
        ja.setAnimationPanelPreferredSize(dim);
        setLayout(new BorderLayout());
        add(ja, BorderLayout.CENTER);
    }

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) throws
                                JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle(p, c);
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
        ja.setAnimationPanelPreferredSize(d);
    }

    @Override
    public JMLPattern getPattern() { return ja.getPattern(); }

    @Override
    public AnimationPrefs getAnimationPrefs() { return ja.getAnimationPrefs(); }

    @Override
    public boolean getPaused() { return ja.getPaused(); }

    @Override
    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
    }

    @Override
    public void disposeView() { ja.disposeAnimation(); }

    @Override
    public void writeGIF() {
        ja.writingGIF = true;
        boolean origpause = getPaused();
        setPaused(true);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                setPaused(origpause);
                ja.writingGIF = false;
            }
        };

        new View.GIFWriter(ja, cleanup);
    }
}
