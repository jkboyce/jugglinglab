// NormalView.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

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


public class NormalView extends View {
    protected Animator ja = null;

    public NormalView(Dimension dim) {
        this.ja = new Animator();
        // ja.setPreferredSize(dim);
        ja.setJAPreferredSize(dim);
        this.setLayout(new BorderLayout());
        this.add(ja, BorderLayout.CENTER);
    }

    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle();
    }

    public void restartView(JMLPattern p, AnimatorPrefs c) throws JuggleExceptionUser, JuggleExceptionInternal {
        ja.restartJuggle(p, c);
    }

    public Dimension getAnimatorSize() {
        return ja.getSize(new Dimension());
    }

    public void dispose() {
        ja.dispose();
    }

	public JMLPattern getPattern() { return ja.getPattern(); }
	
    public boolean getPaused() { return ja.getPaused(); }

    public void setPaused(boolean pause) {
        if (ja.message == null)
            ja.setPaused(pause);
    }
	
	// The following is needed by the GIF saver
    public Animator getAnimator() { return ja; }
}
