// SimpleView.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.view;

import java.awt.*;
import javax.swing.*;
import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class SimpleView extends View {
    protected AnimationPanel ja = null;

    public SimpleView(Dimension dim) {
        this.ja = new AnimationPanel();
        ja.setAnimationPanelPreferredSize(dim);
        this.setLayout(new BorderLayout());
        this.add(ja, BorderLayout.CENTER);
    }

    @Override
    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle();
    }

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) throws JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle(p, c);
    }

    @Override
    public void setAnimationPanelPreferredSize(Dimension d) {
        ja.setAnimationPanelPreferredSize(d);
    }

    @Override
    public Dimension getAnimationPanelSize() {
        return ja.getSize(new Dimension());
    }

    @Override
    public AnimationPanel getAnimationPanel() { return ja; }

    @Override
    public void disposeView() { ja.dispose(); }

    @Override
	public JMLPattern getPattern() { return ja.getPattern(); }

    @Override
    public boolean getPaused() { return ja.getPaused(); }

    @Override
    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
    }
}
